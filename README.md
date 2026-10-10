# Crayo

Type a word (English or Dutch), see real coloring pages from the web, tick the ones you like, and print them on A4.

## Start it

1. Install [Python](https://www.python.org/downloads/) (tick "Add Python to PATH" during setup).
2. Double-click `start.bat`. The first time it sets itself up, then opens http://localhost:5000.

To print: tick some drawings, press **Print**, and pick your printer in the browser's print window. Each drawing gets its own A4 page; wide drawings are turned sideways so they fill the page.

## How it works

- `crayo/translate.py` turns Dutch words into English (built-in word list, then a free online translator).
- `crayo/sources.py` asks the coloring websites. Add a new website by writing one function there.
- `crayo/imagecheck.py` looks at each picture and checks that it is a coloring page: mostly white paper, no photo colors, and big enough. It is forgiving on purpose. Drawings that fail are hidden behind a "Show more" link instead of being thrown away.
- `app.py` is the small web server, and `static/index.html` is the page you see.

## Websites used

Pictures appear on screen while the search is still running, so you can start picking right away.

| Site | Status |
|---|---|
| supercoloring.com | Main source, searched in 8 different ways to get more than its usual 100 |
| mondaymandala.com, bestcoloringpagesforkids.com, coloringpagesfortoddlers.com, lovelycoloring.com, freecoloringpages.net | Coloring blogs: Crayo opens the matching articles and collects every page in them |
| openclipart.org | Often slow; whatever it finds in time is shown |
| pixabay.com | Optional: set the `PIXABAY_KEY` environment variable to a free key from https://pixabay.com/api/docs/ |

To add another coloring blog, add its domain to `ARTICLE_SITES` in `crayo/sources.py`.

Crayo is for printing at home. Drawings belong to their makers; please do not republish them.
