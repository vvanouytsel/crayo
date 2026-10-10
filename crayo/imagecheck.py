"""Looks at a picture and decides whether it looks like a coloring page.

The check is deliberately forgiving: a coloring page is mostly white paper with dark lines.
Shading, small watermarks and thin lines are allowed through.
"""
import io

from PIL import Image

MIN_SIDE = 250          # pixels on the short side
MIN_WHITE = 0.55        # share of near-white pixels
MAX_COLOR = 0.12        # share of clearly colored pixels
MAX_INK = 0.45          # share of dark pixels (solid black blobs are not for coloring)


def analyse(data):
    """Returns (looks_ok, facts) for the raw image bytes."""
    image = Image.open(io.BytesIO(data))
    if image.mode in ("RGBA", "LA", "P"):
        image = image.convert("RGBA")
        paper = Image.new("RGBA", image.size, "white")
        paper.alpha_composite(image)
        image = paper
    image = image.convert("RGB")
    width, height = image.size

    small = image.copy()
    small.thumbnail((200, 200))
    pixels = list(small.getdata())
    total = len(pixels)
    white = sum(1 for r, g, b in pixels if min(r, g, b) > 225) / total
    ink = sum(1 for r, g, b in pixels if max(r, g, b) < 130) / total
    color = sum(1 for r, g, b in pixels if max(r, g, b) - min(r, g, b) > 50) / total

    ok = min(width, height) >= MIN_SIDE and white >= MIN_WHITE and color <= MAX_COLOR and ink <= MAX_INK
    return ok, {"width": width, "height": height, "white": round(white, 2), "color": round(color, 2), "ink": round(ink, 2)}
