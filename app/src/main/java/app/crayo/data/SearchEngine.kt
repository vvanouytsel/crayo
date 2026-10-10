package app.crayo.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A coloring page that was found, checked, and has a preview on disk. */
data class Result(
    val id: String,
    val title: String,
    val source: String,
    val looksOk: Boolean,
    val width: Int,
    val height: Int,
    val full: List<String>,
    val preview: File,
)

class SearchEngine(private val previewDir: File) {

    /** Runs every website at once. Each drawing is checked as soon as it is found and sent out right away. */
    fun search(terms: List<String>): Flow<Result> = channelFlow {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val gate = Semaphore(16)
        withTimeoutOrNull(SEARCH_TIME_LIMIT_MS) {
            coroutineScope {
                val emit: Emit = { drawing ->
                    if (seen.add(drawing.thumb)) {
                        launch(Dispatchers.IO) { gate.withPermit { check(drawing)?.let { send(it) } } }
                    }
                }
                for (source in Sources.all) for (term in terms) {
                    launch {
                        try {
                            source(term, emit)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("Crayo", "A website failed for '$term': $e")
                        }
                    }
                }
            }
        }
    }

    /** Downloads the preview, judges it and keeps it on disk. Returns null if it can't be used. */
    private suspend fun check(drawing: Drawing): Result? {
        val bytes = Http.image(drawing.thumb) ?: return null
        val checked = withContext(Dispatchers.Default) { ImageCheck.analyse(bytes) } ?: return null
        val id = MessageDigest.getInstance("SHA-1").digest(drawing.thumb.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)
        val file = File(previewDir, "$id.png")
        withContext(Dispatchers.IO) {
            file.outputStream().use { checked.preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        checked.preview.recycle()
        return Result(id, drawing.title, drawing.source, checked.looksOk, checked.width, checked.height, drawing.full, file)
    }

    /** The big version for printing. Falls back to the preview if no big one can be downloaded. */
    suspend fun printBitmap(result: Result): Bitmap? {
        for (url in result.full) {
            val bytes = Http.image(url) ?: continue
            val bitmap = withContext(Dispatchers.Default) { ImageCheck.decodeForPrint(bytes) }
            if (bitmap != null) return bitmap
        }
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.preview.path) }
    }

    private companion object {
        const val SEARCH_TIME_LIMIT_MS = 90_000L
    }
}
