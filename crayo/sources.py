"""The websites Crayo looks at.

Every source is a function `source(term, emit)`. It finds drawings for `term` and calls
`emit(drawing)` for each one right away, so results can appear on screen while the search
is still running. A drawing is a dict with:

    title   short name
    source  website name
    thumb   address of a preview picture
    full    list of addresses to try for the print version, best first

To add a website: write one function here and add it to SOURCES.
For WordPress-style coloring blogs it is enough to add the domain to ARTICLE_SITES.
"""
import html
import os
import re
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import quote, urljoin, urlparse

import requests

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36"}
TIMEOUT = 12


def _get(url, **kwargs):
    return requests.get(url, headers=HEADERS, timeout=TIMEOUT, **kwargs)


# --------------------------------------------------------------------------- supercoloring.com

# The site always answers with its best 100, so we ask in several ways to get different ones.
SUPERCOLORING_VARIANTS = ["{t}", "cute {t}", "baby {t}", "easy {t}", "realistic {t}", "cartoon {t}", "{t} head", "big {t}"]


def supercoloring(term, emit):
    def one(variant):
        page = _get(f"https://www.supercoloring.com/search/coloring/{quote(variant.format(t=term))}").text
        for tag in re.findall(r"<img[^>]*>", page):
            alt = re.search(r'alt="([^"]*)"', tag)
            src = re.search(r'(?:data-src|src)="([^"]*)"', tag)
            if not alt or not src or "/coloring/" not in src.group(1):
                continue
            thumb = src.group(1)
            base = re.sub(r"-(sm|md|lg)\.(webp|png)$", "", thumb)
            emit({
                "title": html.unescape(alt.group(1)).split(" from ")[0],
                "source": "supercoloring.com",
                "thumb": thumb,
                # The original is a PNG; not every size exists for every drawing.
                "full": [base + "-lg.png", base + "-md.png", base + ".png", thumb],
            })

    with ThreadPoolExecutor(4) as pool:
        list(pool.map(_safe(one), SUPERCOLORING_VARIANTS))


# --------------------------------------------------------------------------- coloring blogs

# Blogs that have a normal search (?s=word). Every article found holds many drawings.
ARTICLE_SITES = [
    "mondaymandala.com",
    "bestcoloringpagesforkids.com",
    "coloringpagesfortoddlers.com",
    "lovelycoloring.com",
    "freecoloringpages.net",
]
MAX_ARTICLES_PER_SITE = 10
JUNK = re.compile(r"logo|avatar|icon|banner|favicon|gravatar|emoji|sprite|placeholder|button|header|footer|"
                  r"sidebar|social|pinterest|facebook|plugins|themes|featured|cropped|thumb-?nail|ads?-", re.I)
ARTICLE_SKIP = re.compile(r"/(tag|category|page|author|feed|wp-|search|comments|privacy|contact|about)\b|\.(jpg|png|xml)$|/feed", re.I)


def _word_pattern(term):
    """Matches the search words in a web address, allowing plurals: dragon -> dragon, dragons."""
    words = [re.escape(w) for w in re.split(r"\W+", term.lower()) if w]
    return re.compile(r"[-/]" + r"[-/]".join(f"{w}s?" for w in words) + r"(?![a-z])") if words else None


def _image_urls(page, base):
    seen = set()
    for tag in re.findall(r"<img[^>]*>", page):
        alt = re.search(r'alt="([^"]*)"', tag)
        for attr in ("data-lazy-src", "data-src", "data-original", "src"):
            match = re.search(rf'{attr}="([^"]+)"', tag)
            if not match:
                continue
            url = urljoin(base, html.unescape(match.group(1)))
            path = urlparse(url).path.lower()
            if not path.endswith((".jpg", ".jpeg", ".png", ".webp")) or JUNK.search(path):
                continue
            # Blogs store small copies named name-300x200.jpg; the original is name.jpg.
            original = re.sub(r"-\d{2,4}x\d{2,4}(?=\.\w+$)", "", url)
            if original in seen:
                break
            seen.add(original)
            name = html.unescape(alt.group(1)).strip() if alt and alt.group(1).strip() else \
                os.path.splitext(os.path.basename(path))[0].replace("-", " ")
            yield name, url, original
            break


def _article_source(domain, term, emit):
    wanted = _word_pattern(term)
    if not wanted:
        return
    slug = "-".join(re.split(r"\W+", term.lower()))
    articles = []
    # Articles found through the site's own search, plus the usual address of a topic page.
    for page_no in (1, 2):
        path = "" if page_no == 1 else f"page/{page_no}/"
        try:
            page = _get(f"https://{domain}/{path}?s={quote(term)}").text
        except requests.RequestException:
            break
        for link in re.findall(r'href="(https?://(?:www\.)?' + re.escape(domain) + r'/[^"#?]*)"', page):
            if wanted.search(link.lower()) and not ARTICLE_SKIP.search(link) and link not in articles:
                articles.append(link)
    for guess in (f"{slug}-coloring-pages/", f"{slug}s-coloring-pages/", f"{slug}-coloring-pages.html", f"{slug}s-coloring-pages.html"):
        articles.append(f"https://{domain}/{guess}")
        articles.append(f"https://www.{domain}/{guess}")

    def read(article):
        try:
            response = _get(article)
            if response.status_code != 200:
                return
            for name, thumb, original in _image_urls(response.text, article):
                emit({
                    "title": name[:80], "source": domain, "thumb": thumb,
                    "full": [original, thumb] if original != thumb else [thumb],
                })
        except requests.RequestException:
            pass

    with ThreadPoolExecutor(5) as pool:
        list(pool.map(read, articles[:MAX_ARTICLES_PER_SITE * 3]))


def article_sites(term, emit):
    with ThreadPoolExecutor(len(ARTICLE_SITES)) as pool:
        list(pool.map(_safe(lambda d: _article_source(d, term, emit)), ARTICLE_SITES))


# --------------------------------------------------------------------------- openclipart.org

def openclipart(term, emit):
    def one(query):
        page = _get(f"https://openclipart.org/search/?query={quote(query)}").text
        for ident, alt in re.findall(r'<img src="/image/800px/(\d+)" alt="([^"]*)"', page):
            emit({
                "title": html.unescape(alt), "source": "openclipart.org",
                "thumb": f"https://openclipart.org/image/800px/{ident}",
                "full": [f"https://openclipart.org/image/2400px/{ident}", f"https://openclipart.org/image/800px/{ident}"],
            })

    with ThreadPoolExecutor(2) as pool:
        list(pool.map(_safe(one), [f"{term} outline", f"{term} coloring"]))


# --------------------------------------------------------------------------- pixabay.com

def pixabay(term, emit):
    """Only used when PIXABAY_KEY is set (a free key from pixabay.com/api/docs)."""
    key = os.environ.get("PIXABAY_KEY")
    if not key:
        return
    data = _get("https://pixabay.com/api/", params={
        "key": key, "q": f"{term} coloring outline", "image_type": "vector", "per_page": 100,
    }).json()
    for hit in data.get("hits", []):
        emit({
            "title": hit.get("tags", term), "source": "pixabay.com", "thumb": hit["webformatURL"],
            "full": [hit.get("largeImageURL", hit["webformatURL"]), hit["webformatURL"]],
        })


# ---------------------------------------------------------------------------

def _safe(function):
    """One broken page must not stop the rest of a source."""
    def wrapper(*args):
        try:
            return function(*args)
        except Exception as error:
            print(f"[{getattr(function, '__name__', 'source')}] {type(error).__name__}: {error}")
    return wrapper


SOURCES = [supercoloring, article_sites, openclipart, pixabay]
# Image servers the app is allowed to download from.
ALLOWED_HOSTS = ["cdn.supercoloring.com", "openclipart.org", "pixabay.com", "wp.com"] + ARTICLE_SITES
