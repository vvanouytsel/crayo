"""Crayo: search coloring pages on the web, pick some, print them."""
import hashlib
from concurrent.futures import ThreadPoolExecutor, wait
from urllib.parse import urlparse

import requests
from flask import Flask, Response, abort, jsonify, request, send_from_directory

from crayo.imagecheck import analyse
from crayo.sources import ALLOWED_HOSTS, HEADERS, SOURCES
from crayo.translate import search_terms

app = Flask(__name__, static_folder="static", static_url_path="")

SITE_PATIENCE = 8    # seconds to wait for the websites to answer
THUMB_PATIENCE = 12  # seconds to wait for each preview picture

DRAWINGS = {}      # id -> drawing details, so images can be looked up later
THUMBS = {}        # id -> (bytes, content type)
FULLS = {}         # id -> (bytes, content type)


def allowed(url):
    host = urlparse(url).hostname or ""
    return url.startswith("https://") and any(host == h or host.endswith("." + h) for h in ALLOWED_HOSTS)


def download(url, timeout=40):
    if not allowed(url):
        return None
    try:
        r = requests.get(url, headers=HEADERS, timeout=timeout)
        kind = r.headers.get("Content-Type", "")
        if r.ok and kind.startswith("image/"):
            return r.content, kind
    except requests.RequestException:
        pass
    return None


def check(drawing):
    """Downloads the preview, judges it, and remembers it. Returns None if it can't be loaded."""
    got = download(drawing["thumb"], timeout=THUMB_PATIENCE)
    if not got:
        return None
    try:
        ok, facts = analyse(got[0])
    except Exception:
        return None
    ident = hashlib.sha1(drawing["thumb"].encode()).hexdigest()[:12]
    THUMBS[ident] = got
    drawing.update(id=ident, looks_ok=ok, **facts)
    DRAWINGS[ident] = drawing
    return drawing


def run_source(source, term):
    try:
        return source(term)
    except Exception as error:
        print(f"[{source.__name__}] failed for '{term}': {error}")
        return []


@app.get("/api/search")
def search():
    query = request.args.get("q", "").strip()
    if not query:
        return jsonify(results=[], terms=[])
    terms = search_terms(query)

    # Ask every site at the same time, but never wait long for a slow one.
    pool = ThreadPoolExecutor(8)
    jobs = [pool.submit(run_source, s, t) for s in SOURCES for t in terms]
    done, _late = wait(jobs, timeout=SITE_PATIENCE)
    pool.shutdown(wait=False, cancel_futures=True)
    drawings, seen = [], set()
    for job in jobs:
        for item in (job.result() if job in done else []):
            if item["thumb"] not in seen:
                seen.add(item["thumb"])
                drawings.append(item)

    with ThreadPoolExecutor(12) as pool:
        checked = [d for d in pool.map(check, drawings) if d]

    checked.sort(key=lambda d: not d["looks_ok"])  # good-looking ones first, order kept otherwise
    return jsonify(terms=terms, results=[{
        "id": d["id"], "title": d["title"], "source": d["source"],
        "looks_ok": d["looks_ok"], "width": d["width"], "height": d["height"],
    } for d in checked])


@app.get("/img/<ident>")
def thumb(ident):
    if ident not in THUMBS:
        abort(404)
    data, kind = THUMBS[ident]
    return Response(data, content_type=kind, headers={"Cache-Control": "max-age=86400"})


@app.get("/full/<ident>")
def full(ident):
    if ident not in DRAWINGS:
        abort(404)
    if ident not in FULLS:
        for url in DRAWINGS[ident]["full"]:
            got = download(url)
            if got:
                FULLS[ident] = got
                break
        else:
            FULLS[ident] = THUMBS[ident]
    data, kind = FULLS[ident]
    return Response(data, content_type=kind, headers={"Cache-Control": "max-age=86400"})


@app.get("/")
def home():
    return send_from_directory("static", "index.html")


if __name__ == "__main__":
    print("Crayo is running. Open http://localhost:5000 in your browser.")
    app.run(host="127.0.0.1", port=5000, threaded=True)
