"""The websites Crayo looks at. Each source turns a search word into a list of drawings.

To add a website: write a function that returns a list of dicts with
`title`, `source`, `thumb` and `full` (a list of image addresses to try, best first),
then add it to SOURCES at the bottom.
"""
import html
import os
import re
from urllib.parse import quote

import requests

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36"}
TIMEOUT = 10


def _get(url, **kwargs):
    return requests.get(url, headers=HEADERS, timeout=TIMEOUT, **kwargs)


def supercoloring(term, limit=40):
    page = _get(f"https://www.supercoloring.com/search/coloring/{quote(term)}").text
    found = []
    for tag in re.findall(r"<img[^>]*>", page):
        alt = re.search(r'alt="([^"]*)"', tag)
        src = re.search(r'(?:data-src|src)="([^"]*)"', tag)
        if not alt or not src or "/coloring/" not in src.group(1):
            continue
        thumb = src.group(1)
        base = re.sub(r"-(sm|md|lg)\.(webp|png)$", "", thumb)
        found.append({
            "title": html.unescape(alt.group(1)).split(" from ")[0],
            "source": "supercoloring.com",
            "thumb": thumb,
            # The original download is a PNG; some sizes are not available for every drawing.
            "full": [base + "-lg.png", base + "-md.png", base + ".png", thumb],
        })
        if len(found) >= limit:
            break
    return found


def openclipart(term, limit=30):
    found = []
    for query in (f"{term} outline", f"{term} coloring"):
        page = _get(f"https://openclipart.org/search/?query={quote(query)}").text
        for ident, alt in re.findall(r'<img src="/image/800px/(\d+)" alt="([^"]*)"', page):
            found.append({
                "title": html.unescape(alt),
                "source": "openclipart.org",
                "thumb": f"https://openclipart.org/image/800px/{ident}",
                "full": [f"https://openclipart.org/image/2400px/{ident}", f"https://openclipart.org/image/800px/{ident}"],
            })
    return found[:limit]


def pixabay(term, limit=30):
    """Only used when PIXABAY_KEY is set (a free key from pixabay.com/api/docs)."""
    key = os.environ.get("PIXABAY_KEY")
    if not key:
        return []
    data = _get("https://pixabay.com/api/", params={
        "key": key, "q": f"{term} coloring outline", "image_type": "vector", "per_page": limit,
    }).json()
    return [{
        "title": hit.get("tags", term),
        "source": "pixabay.com",
        "thumb": hit["webformatURL"],
        "full": [hit.get("largeImageURL", hit["webformatURL"]), hit["webformatURL"]],
    } for hit in data.get("hits", [])]


SOURCES = [supercoloring, openclipart, pixabay]
# Image servers the app is allowed to download from.
ALLOWED_HOSTS = ("cdn.supercoloring.com", "openclipart.org", "pixabay.com")
