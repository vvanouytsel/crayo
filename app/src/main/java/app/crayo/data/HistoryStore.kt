package app.crayo.data

import android.graphics.Bitmap
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A coloring page that was printed. `image` is the exact picture that was sent to the printer. */
data class HistoryItem(
    val id: String,
    val title: String,
    val source: String,
    val printedAt: Long,   // milliseconds since 1970 of the most recent print
    val count: Int,        // how many times it was printed
    val image: File,
)

/** Remembers what was printed, on the phone. Newest first. Survives closing the app and clearing the cache. */
class HistoryStore(private val dir: File) {
    private val index = File(dir, "history.json")

    init {
        dir.mkdirs()
    }

    fun imageFile(id: String) = File(dir, "$id.png")

    fun load(): List<HistoryItem> = try {
        val list = JSONArray(index.readText())
        (0 until list.length()).mapNotNull { position ->
            val item = list.getJSONObject(position)
            val id = item.getString("id")
            val image = imageFile(id)
            if (!image.exists()) null else HistoryItem(
                id, item.getString("title"), item.getString("source"),
                item.getLong("printedAt"), item.optInt("count", 1), image,
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(items: List<HistoryItem>) {
        val list = JSONArray()
        items.forEach {
            list.put(
                JSONObject().put("id", it.id).put("title", it.title).put("source", it.source)
                    .put("printedAt", it.printedAt).put("count", it.count)
            )
        }
        index.writeText(list.toString())
    }

    fun writeImage(id: String, bitmap: Bitmap) {
        imageFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
