"""Turns a Dutch search word into English so the coloring sites understand it."""
import requests

# Common words kids ask for. Anything not listed here goes to a free online translator.
DUTCH_TO_ENGLISH = {
    "draak": "dragon", "draken": "dragons", "eenhoorn": "unicorn", "dinosaurus": "dinosaur",
    "hond": "dog", "kat": "cat", "poes": "cat", "konijn": "rabbit", "paard": "horse",
    "pony": "pony", "koe": "cow", "varken": "pig", "schaap": "sheep", "kip": "chicken",
    "eend": "duck", "vis": "fish", "haai": "shark", "walvis": "whale", "dolfijn": "dolphin",
    "kikker": "frog", "slang": "snake", "schildpad": "turtle", "olifant": "elephant",
    "leeuw": "lion", "tijger": "tiger", "aap": "monkey", "giraf": "giraffe", "zebra": "zebra",
    "beer": "bear", "wolf": "wolf", "vos": "fox", "uil": "owl", "vlinder": "butterfly",
    "bij": "bee", "lieveheersbeestje": "ladybug", "spin": "spider", "slak": "snail",
    "vogel": "bird", "pinguin": "penguin", "krokodil": "crocodile", "nijlpaard": "hippo",
    "auto": "car", "vrachtwagen": "truck", "brandweerauto": "fire truck", "brandweer": "firefighter",
    "politieauto": "police car", "tractor": "tractor", "trein": "train", "vliegtuig": "airplane",
    "helikopter": "helicopter", "boot": "boat", "schip": "ship", "raket": "rocket",
    "fiets": "bicycle", "motor": "motorcycle", "prinses": "princess", "prins": "prince",
    "kasteel": "castle", "ridder": "knight", "fee": "fairy", "zeemeermin": "mermaid",
    "piraat": "pirate", "heks": "witch", "spook": "ghost", "monster": "monster",
    "robot": "robot", "clown": "clown", "bloem": "flower", "bloemen": "flowers",
    "boom": "tree", "huis": "house", "zon": "sun", "maan": "moon", "ster": "star",
    "regenboog": "rainbow", "wolk": "cloud", "hart": "heart", "taart": "cake",
    "ijsje": "ice cream", "pizza": "pizza", "appel": "apple", "banaan": "banana",
    "kerst": "christmas", "kerstman": "santa", "kerstboom": "christmas tree",
    "sinterklaas": "santa", "pasen": "easter", "paashaas": "easter bunny",
    "halloween": "halloween", "pompoen": "pumpkin", "sneeuwman": "snowman",
    "voetbal": "football", "superheld": "superhero", "dier": "animal", "dieren": "animals",
    "bouwplaats": "construction", "graafmachine": "excavator", "ruimte": "space",
    "astronaut": "astronaut", "planeet": "planet", "mandala": "mandala",
}


def to_english(text):
    """Returns the English search words for `text`. Falls back to the original on any problem."""
    text = text.strip().lower()
    if not text:
        return text
    words = [DUTCH_TO_ENGLISH.get(w, w) for w in text.split()]
    translated = " ".join(words)
    if translated != text or all(w in DUTCH_TO_ENGLISH.values() for w in words):
        return translated
    try:
        r = requests.get(
            "https://api.mymemory.translated.net/get",
            params={"q": text, "langpair": "nl|en"},
            timeout=5,
        )
        result = r.json()["responseData"]["translatedText"].strip().lower()
        # The free service sometimes answers with an error message in capitals; ignore odd answers.
        if result and len(result) < 60 and "mymemory" not in result and "invalid" not in result:
            return result
    except Exception:
        pass
    return text


def search_terms(text):
    """English first, then what the user typed (in case it was already English or a name)."""
    english = to_english(text)
    original = text.strip().lower()
    return [english] if english == original else [english, original]
