package app.crayo.color

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Makes a colored example of a coloring page.
 *
 * The phone splits the page into numbered areas, a vision model names a color for each number,
 * and the phone paints the original drawing with those colors. The model never draws anything.
 * The model's answer is kept on the phone, so the same page is only ever paid for once.
 */
class ColorExample(private val dir: File) {

    init {
        dir.mkdirs()
    }

    private fun answerFile(id: String) = File(dir, "$id.json")

    fun isKnown(id: String) = answerFile(id).exists()

    suspend fun make(id: String, title: String, drawing: Bitmap, server: String, key: String, model: String, wash: Float): Bitmap {
        val page = withContext(Dispatchers.Default) { resize(drawing) }
        val regions = withContext(Dispatchers.Default) { Regions.split(grayOf(page), page.width, page.height) }
        if (regions.numberCount == 0) throw ColorError("This page has no closed areas to color.")

        val colors = load(id, regions.numberCount) ?: run {
            val numbered = withContext(Dispatchers.Default) { numberedPicture(regions) }
            val answer = OpenAi.colors(server, key, model, title, regions.numberCount, jpeg(page, 85), jpeg(numbered, 92))
            save(id, regions.numberCount, answer)
            answer
        }
        return withContext(Dispatchers.Default) {
            Bitmap.createBitmap(regions.paint(colors, wash), regions.width, regions.height, Bitmap.Config.ARGB_8888)
        }
    }

    // The saved answer only fits if the page splits into the same number of areas as when it was asked.
    private suspend fun load(id: String, numbers: Int): Map<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val saved = JSONObject(answerFile(id).readText())
            if (saved.getInt("numbers") != numbers || saved.getInt("version") != VERSION) return@withContext null
            val colors = saved.getJSONObject("colors")
            colors.keys().asSequence().associate { it.toInt() to colors.getInt(it) }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun save(id: String, numbers: Int, colors: Map<Int, Int>) = withContext(Dispatchers.IO) {
        val list = JSONObject()
        colors.forEach { (number, color) -> list.put(number.toString(), color) }
        answerFile(id).writeText(JSONObject().put("version", VERSION).put("numbers", numbers).put("colors", list).toString())
    }

    private fun resize(source: Bitmap): Bitmap {
        val scale = WORK_SIZE.toFloat() / max(source.width, source.height)
        val w = max(1, (source.width * scale).toInt())
        val h = max(1, (source.height * scale).toInt())
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(Bitmap.createScaledBitmap(source, w, h, true), 0f, 0f, null)
        return result
    }

    private fun grayOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
        return pixels
    }

    /** The page with every numbered area lightly tinted and its number written on it. */
    private fun numberedPicture(regions: Regions): Bitmap {
        val tints = IntArray(regions.numberCount + 1) { Color.HSVToColor(floatArrayOf((it * 222.5f) % 360f, 0.30f, 1f)) }
        val pixels = IntArray(regions.width * regions.height)
        for (i in pixels.indices) {
            val gray = regions.gray[i]
            val number = regions.numberOf[regions.label[i]]
            pixels[i] = if (number == 0) Color.rgb(gray, gray, gray) else {
                val tint = tints[number]
                Color.rgb((gray + 3 * Color.red(tint)) / 4, (gray + 3 * Color.green(tint)) / 4, (gray + 3 * Color.blue(tint)) / 4)
            }
        }
        val picture = Bitmap.createBitmap(pixels, regions.width, regions.height, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(picture)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val box = Paint().apply { color = Color.WHITE }
        val edge = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        for (number in 1..regions.numberCount) {
            val size = (regions.spotRoom[number] * 0.9f).coerceIn(15f, 30f)
            text.textSize = size
            val label = number.toString()
            val half = text.measureText(label) / 2 + 3
            val x = regions.spotX[number].toFloat()
            val y = regions.spotY[number].toFloat()
            canvas.drawRect(x - half, y - size / 2 - 2, x + half, y + size / 2 + 2, box)
            canvas.drawRect(x - half, y - size / 2 - 2, x + half, y + size / 2 + 2, edge)
            canvas.drawText(label, x, y + size * 0.36f, text)
        }
        return picture
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val WORK_SIZE = 1300   // pixels on the long side while splitting and painting
        const val VERSION = 1        // raise when the way pages are split changes, so old answers are dropped
    }
}
