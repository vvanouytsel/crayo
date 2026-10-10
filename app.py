"""Crayo: search coloring pages on the web, pick some, print them."""
import hashlib
import io
import json
import queue
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlparse

import requests
from flask import Flask, Response, abort, request, send_from_directory, stream_with_context
from PIL import Image

from crayo.imagecheck import analyse
from crayo.sources import ALLOWED_HOSTS, HEADERS, SOURCES
from crayo.translate import search_terms

app = Flask(__name__, static_folder="static", static_url_path="")

SEARCH_TIME_LIMIT = 90   # seconds before a search stops waiting for slow websites
THUMB_PATIENCE = 12      # seconds to wait for each preview picture
PREVIEW_SIZE = 420       # pixels; previews are shrunk so hundreds fit in memory

DRAWINGS = {}      # id -> drawing details, so the print version can be found later
THUMBS = {}        # id -> (bytes, content type)
FULLS = {}         # id -> (bytes, content type)


def allowed(url):
    host = urlparse(url).hostname or ""
    return url.startswith("https://") and any(host == h or host.endswith("." + h) for h in ALLOWED_HOSTS)


def download(url, timeout=40):
    if not allowed(url):
        return None
    try:
        headers = dict(HEADERS, Referer=f"https://{urlparse(url).hostname}/")
        r = requests.get(url, headers=headers, timeout=timeout)
        kind = r.headers.get("Content-Type", "")
        if r.ok and kind.startswith("image/"):
            return r.content, kind
    except requests.RequestException:
        pass
    return None


def shrink(data):
    """Smaller copy for the grid. The full size is fetched again only when printing."""
    image = Image.open(io.BytesIO(data))
    if image.mode not in ("RGB", "L"):
        image = image.convert("RGBA")
        paper = Image.new("RGBA", image.size, "white")
        paper.alpha_composite(image)
        image = paper.convert("RGB")
    image.thumbnail((PREVIEW_SIZE, PREVIEW_SIZE))
    out = io.BytesIO()
    image.save(out, "PNG", optimize=False)
    return out.getvalue(), "image/png"


def check(drawing):
    """Downloads the preview, judges it, and remembers it. Returns None if it can't be used."""
    got = download(drawing["thumb"], timeout=THUMB_PATIENCE)
    if not got:
        return None
    try:
        ok, facts = analyse(got[0])
        preview = shrink(got[0])
    except Exception:
        return None
    ident = hashlib.sha1(drawing["thumb"].encode()).hexdigest()[:12]
    THUMBS[ident] = preview
    drawing.update(id=ident, looks_ok=ok, **facts)
    DRAWINGS[ident] = drawing
    return drawing


def find_drawings(terms, results, stop):
    """Runs every website at once. Each drawing is checked as soon as it is found and put in `results`."""
    seen, lock = set(), threading.Lock()
    checkers = ThreadPoolExecutor(24)
    checks = []

    def emit(drawing):
        with lock:
            if stop.is_set() or drawing["thumb"] in seen:
                return
            seen.add(drawing["thumb"])
            checks.append(checkers.submit(lambda: (lambda d: d and results.put(d))(check(drawing))))

    def run(source, term):
        try:
            source(term, emit)
        except Exception as error:
            print(f"[{source.__name__}] failed for '{term}': {error}")

    sites = ThreadPoolExecutor(len(SOURCES) * len(terms))
    jobs = [sites.submit(run, s, t) for s in SOURCES for t in terms]

    def finished():
        with lock:
            return all(j.done() for j in jobs) and all(c.done() for c in checks)

    return finished, lambda: (sites.shutdown(wait=False, cancel_futures=True),
                              checkers.shutdown(wait=False, cancel_futures=True))


@app.get("/api/search")
def search():
    """Answers with one JSON line per drawing, sent the moment it is ready."""
    query = request.args.get("q", "").strip()
    terms = search_terms(query) if query else []

    @stream_with_context
    def lines():
        yield json.dumps({"type": "terms", "terms": terms}) + "\n"
        if not terms:
            yield json.dumps({"type": "done", "total": 0}) + "\n"
            return
        results, stop = queue.Queue(), threading.Event()
        finished, cancel = find_drawings(terms, results, stop)
        started, total = time.time(), 0
        try:
            while True:
                try:
                    d = results.get(timeout=0.25)
                    total += 1
                    yield json.dumps({"type": "item", "id": d["id"], "title": d["title"], "source": d["source"],
                                      "looks_ok": d["looks_ok"], "width": d["width"], "height": d["height"]}) + "\n"
                except queue.Empty:
                    if finished() and results.empty():
                        break
                    if time.time() - started > SEARCH_TIME_LIMIT:
                        break
                    yield "\n"  # keeps the connection alive and notices when the page is closed
        finally:
            stop.set()
            cancel()
        yield json.dumps({"type": "done", "total": total}) + "\n"

    return Response(lines(), mimetype="application/x-ndjson", headers={"Cache-Control": "no-cache"})


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
