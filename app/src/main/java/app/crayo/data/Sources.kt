package app.crayo.data

import android.net.Uri
import androidx.core.text.HtmlCompat
import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One coloring page found on a website. */
data class Drawing(
    val title: String,
    val source: String,
    val thumb: String,        // address of a small preview picture
    val full: List<String>,   // addresses to try for the print version, best first
)

typealias Emit = suspend (Drawing) -> Unit

/**
 * The websites Crayo looks at. Each source finds drawings for a search word and calls
 * `emit` for every one right away, so they can appear on screen while the search runs.
 *
 * To add a website: write one function here and add it to `all`.
 * For coloring blogs it is enough to add the domain to `articleSites`.
 */
object Sources {
    val all: List<suspend (String, Emit) -> Unit> = listOf(
        { term, emit -> supercoloring(term, emit) },
        { term, emit -> blogs(term, emit) },
        { term, emit -> openclipart(term, emit) },
    )

    private val imgTag = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)
    private val altAttr = Regex("""alt="([^"]*)"""")
    private val srcAttr = Regex("""(?:data-src|src)="([^"]*)"""")

    private fun unescape(text: String): String = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    private fun resolve(base: String, link: String): String =
        try { URI(base).resolve(link).toString() } catch (e: Exception) { link }

    // ----------------------------------------------------------------- supercoloring.com

    // The site always answers with its best 100, so we ask in several ways to get different ones.
    private val supercoloringVariants = listOf("%s", "cute %s", "baby %s", "easy %s", "realistic %s", "cartoon %s", "%s head", "big %s")

    private suspend fun supercoloring(term: String, emit: Emit) = coroutineScope {
        supercoloringVariants.map { variant ->
            async {
                val query = Uri.encode(variant.format(term))
                val page = Http.text("https://www.supercoloring.com/search/coloring/$query") ?: return@async
                for (tag in imgTag.findAll(page)) {
                    val alt = altAttr.find(tag.value)?.groupValues?.get(1) ?: continue
                    val thumb = srcAttr.find(tag.value)?.groupValues?.get(1) ?: continue
                    if ("/coloring/" !in thumb) continue
                    val base = thumb.replace(Regex("-(sm|md|lg)\\.(webp|png)$"), "")
                    emit(
                        Drawing(
                            title = unescape(alt).substringBefore(" from "),
                            source = "supercoloring.com",
                            thumb = thumb,
                            // The original is a PNG; not every size exists for every drawing.
                            full = listOf("$base-lg.png", "$base-md.png", "$base.png", thumb),
                        )
                    )
                }
            }
        }.awaitAll()
    }

    // ----------------------------------------------------------------- coloring blogs

    // Blogs with a normal search (?s=word). Every article found holds many drawings.
    private val articleSites = listOf(
        "mondaymandala.com",
        "bestcoloringpagesforkids.com",
        "coloringpagesfortoddlers.com",
        "lovelycoloring.com",
        "freecoloringpages.net",
    )
    private const val MAX_ARTICLES_PER_SITE = 10
    private val junk = Regex(
        "logo|avatar|icon|banner|favicon|gravatar|emoji|sprite|placeholder|button|header|footer|" +
            "sidebar|social|pinterest|facebook|plugins|themes|featured|cropped|thumb-?nail|ads?-",
        RegexOption.IGNORE_CASE,
    )
    private val articleSkip = Regex(
        "/(tag|category|page|author|feed|wp-|search|comments|privacy|contact|about)\\b|\\.(jpg|png|xml)$|/feed",
        RegexOption.IGNORE_CASE,
    )

    /** Matches the search words in a web address, allowing plurals: dragon, dragons. */
    private fun wordPattern(term: String): Regex? {
        val words = term.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return Regex("[-/]" + words.joinToString("[-/]") { Regex.escape(it) + "s?" } + "(?![a-z])")
    }

    private data class Found(val name: String, val thumb: String, val original: String)

    private fun imagesIn(page: String, base: String): List<Found> {
        val seen = HashSet<String>()
        val found = ArrayList<Found>()
        for (tag in imgTag.findAll(page).map { it.value }) {
            val alt = altAttr.find(tag)?.groupValues?.get(1)
            for (attribute in listOf("data-lazy-src", "data-src", "data-original", "src")) {
                val match = Regex("""$attribute="([^"]+)"""").find(tag) ?: continue
                val url = resolve(base, unescape(match.groupValues[1]))
                val path = try { URI(url).path.lowercase() } catch (e: Exception) { "" }
                if (!(path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")) ||
                    junk.containsMatchIn(path)
                ) continue
                // Blogs store small copies named name-300x200.jpg; the original is name.jpg.
                val original = url.replace(Regex("-\\d{2,4}x\\d{2,4}(?=\\.\\w+$)"), "")
                if (!seen.add(original)) break
                val name = (alt?.let { unescape(it).trim() }).orEmpty()
                    .ifEmpty { path.substringAfterLast('/').substringBeforeLast('.').replace('-', ' ') }
                found += Found(name, url, original)
                break
            }
        }
        return found
    }

    private suspend fun articlesOf(domain: String, term: String, emit: Emit) = coroutineScope {
        val wanted = wordPattern(term) ?: return@coroutineScope
        val slug = term.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.joinToString("-")
        val articles = ArrayList<String>()
        // Articles found through the site's own search, plus the usual address of a topic page.
        for (pageNumber in 1..2) {
            val path = if (pageNumber == 1) "" else "page/$pageNumber/"
            val page = Http.text("https://$domain/$path?s=${Uri.encode(term)}") ?: break
            val links = Regex("href=\"(https?://(?:www\\.)?" + Regex.escape(domain) + "/[^\"#?]*)\"")
            for (link in links.findAll(page).map { it.groupValues[1] }) {
                if (wanted.containsMatchIn(link.lowercase()) && !articleSkip.containsMatchIn(link) && link !in articles) articles += link
            }
        }
        for (guess in listOf("$slug-coloring-pages/", "${slug}s-coloring-pages/", "$slug-coloring-pages.html", "${slug}s-coloring-pages.html")) {
            articles += "https://$domain/$guess"
            articles += "https://www.$domain/$guess"
        }
        val gate = Semaphore(5)
        articles.take(MAX_ARTICLES_PER_SITE * 3).map { article ->
            async {
                gate.withPermit {
                    val page = Http.text(article) ?: return@withPermit
                    for (image in imagesIn(page, article)) {
                        emit(
                            Drawing(
                                title = image.name.take(80), source = domain, thumb = image.thumb,
                                full = if (image.original != image.thumb) listOf(image.original, image.thumb) else listOf(image.thumb),
                            )
                        )
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun blogs(term: String, emit: Emit) = coroutineScope {
        articleSites.map { domain -> async { articlesOf(domain, term, emit) } }.awaitAll()
    }

    // ----------------------------------------------------------------- openclipart.org

    private suspend fun openclipart(term: String, emit: Emit) = coroutineScope {
        listOf("$term outline", "$term coloring").map { query ->
            async {
                val page = Http.text("https://openclipart.org/search/?query=${Uri.encode(query)}") ?: return@async
                for (match in Regex("""<img src="/image/800px/(\d+)" alt="([^"]*)"""").findAll(page)) {
                    val id = match.groupValues[1]
                    emit(
                        Drawing(
                            title = unescape(match.groupValues[2]), source = "openclipart.org",
                            thumb = "https://openclipart.org/image/800px/$id",
                            full = listOf("https://openclipart.org/image/2400px/$id", "https://openclipart.org/image/800px/$id"),
                        )
                    )
                }
            }
        }.awaitAll()
    }
}
