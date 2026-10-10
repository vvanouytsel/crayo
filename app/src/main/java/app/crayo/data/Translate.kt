package app.crayo.data

import java.net.URLEncoder
import org.json.JSONObject

/** Turns a Dutch search word into English so the coloring sites understand it. */
object Translate {
    // Common words kids ask for. Anything not listed here goes to a free online translator.
    private val dutchToEnglish = mapOf(
        "draak" to "dragon", "draken" to "dragons", "eenhoorn" to "unicorn", "dinosaurus" to "dinosaur",
        "hond" to "dog", "kat" to "cat", "poes" to "cat", "konijn" to "rabbit", "paard" to "horse",
        "pony" to "pony", "koe" to "cow", "varken" to "pig", "schaap" to "sheep", "kip" to "chicken",
        "eend" to "duck", "vis" to "fish", "haai" to "shark", "walvis" to "whale", "dolfijn" to "dolphin",
        "kikker" to "frog", "slang" to "snake", "schildpad" to "turtle", "olifant" to "elephant",
        "leeuw" to "lion", "tijger" to "tiger", "aap" to "monkey", "giraf" to "giraffe", "zebra" to "zebra",
        "beer" to "bear", "wolf" to "wolf", "vos" to "fox", "uil" to "owl", "vlinder" to "butterfly",
        "bij" to "bee", "lieveheersbeestje" to "ladybug", "spin" to "spider", "slak" to "snail",
        "vogel" to "bird", "pinguin" to "penguin", "krokodil" to "crocodile", "nijlpaard" to "hippo",
        "auto" to "car", "vrachtwagen" to "truck", "brandweerauto" to "fire truck", "brandweer" to "firefighter",
        "politieauto" to "police car", "tractor" to "tractor", "trein" to "train", "vliegtuig" to "airplane",
        "helikopter" to "helicopter", "boot" to "boat", "schip" to "ship", "raket" to "rocket",
        "fiets" to "bicycle", "motor" to "motorcycle", "prinses" to "princess", "prins" to "prince",
        "kasteel" to "castle", "ridder" to "knight", "fee" to "fairy", "zeemeermin" to "mermaid",
        "piraat" to "pirate", "heks" to "witch", "spook" to "ghost", "monster" to "monster",
        "robot" to "robot", "clown" to "clown", "bloem" to "flower", "bloemen" to "flowers",
        "boom" to "tree", "huis" to "house", "zon" to "sun", "maan" to "moon", "ster" to "star",
        "regenboog" to "rainbow", "wolk" to "cloud", "hart" to "heart", "taart" to "cake",
        "ijsje" to "ice cream", "pizza" to "pizza", "appel" to "apple", "banaan" to "banana",
        "kerst" to "christmas", "kerstman" to "santa", "kerstboom" to "christmas tree",
        "sinterklaas" to "santa", "pasen" to "easter", "paashaas" to "easter bunny",
        "halloween" to "halloween", "pompoen" to "pumpkin", "sneeuwman" to "snowman",
        "voetbal" to "football", "superheld" to "superhero", "dier" to "animal", "dieren" to "animals",
        "bouwplaats" to "construction", "graafmachine" to "excavator", "ruimte" to "space",
        "astronaut" to "astronaut", "planeet" to "planet", "mandala" to "mandala",
    )

    private suspend fun toEnglish(text: String): String {
        val words = text.split(Regex("\\s+")).map { dutchToEnglish[it] ?: it }
        val translated = words.joinToString(" ")
        if (translated != text || words.all { it in dutchToEnglish.values }) return translated
        return try {
            val url = "https://api.mymemory.translated.net/get?q=${URLEncoder.encode(text, "UTF-8")}&langpair=nl%7Cen"
            val answer = JSONObject(Http.text(url) ?: return text)
                .getJSONObject("responseData").getString("translatedText").trim().lowercase()
            // The free service sometimes answers with an error message; ignore odd answers.
            if (answer.isNotEmpty() && answer.length < 60 && "mymemory" !in answer && "invalid" !in answer) answer else text
        } catch (e: Exception) {
            text
        }
    }

    /** English first, then what the user typed (in case it was already English or a name). */
    suspend fun searchTerms(typed: String): List<String> {
        val original = typed.trim().lowercase()
        if (original.isEmpty()) return emptyList()
        val english = toEnglish(original)
        return if (english == original) listOf(english) else listOf(english, original)
    }
}
