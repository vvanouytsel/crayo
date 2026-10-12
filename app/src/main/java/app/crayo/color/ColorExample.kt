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

/** What [ColorExample.make] is busy with, in the order it happens. */
sealed interface ColorStep {
    data object Splitting : ColorStep
    /** Waiting for the model's first answer. [picture] is the numbered page it is looking at; [play] makes an animation to show meanwhile. */
    class Asking(val areas: Int, val picture: Bitmap, val play: ColorPlay) : ColorStep
    /** The model already answered for this page earlier, so nothing is asked or paid. */
    class Reusing(val areas: Int) : ColorStep
    data object Painting : ColorStep
}

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

    /** [onStep] is told what is happening, so the screen can show it. */
    suspend fun make(
        id: String, title: String, drawing: Bitmap, server: String, key: String, model: String, wash: Float,
        onStep: (ColorStep) -> Unit = {},
    ): Bitmap {
        onStep(ColorStep.Splitting)
        val page = withContext(Dispatchers.Default) { resize(drawing) }
        val regions = withContext(Dispatchers.Default) { Regions.split(grayOf(page), page.width, page.height) }
        if (regions.numberCount == 0) throw ColorError("This page has no closed areas to color.")

        val colors = load(id, regions.numberCount)?.also { onStep(ColorStep.Reusing(regions.numberCount)) } ?: run {
            val numbered = withContext(Dispatchers.Default) { numberedPicture(regions) }
            val play = withContext(Dispatchers.Default) { ColorPlay.of(page) }
            onStep(ColorStep.Asking(regions.numberCount, numbered, play))
            val pageJpeg = jpeg(page, 85)
            val numberedJpeg = jpeg(numbered, 92)
            val answer = OpenAi.colors(server, key, model, title, areasOf(regions), pageJpeg, numberedJpeg).colors
            save(id, regions.numberCount, answer)
            answer
        }
        onStep(ColorStep.Painting)
        return withContext(Dispatchers.Default) { painted(regions, colors, wash) }
    }

    private fun painted(regions: Regions, colors: Map<Int, Int>, wash: Float) =
        Bitmap.createBitmap(regions.paint(colors, wash), regions.width, regions.height, Bitmap.Config.ARGB_8888)

    /** Where every numbered area is, so the model can find it without reading the small printed number. */
    private fun areasOf(regions: Regions): List<OpenAi.Area> {
        val pixels = IntArray(regions.numberCount + 1)
        for (i in regions.label.indices) pixels[regions.numberOf[regions.label[i]]]++
        val total = regions.width * regions.height
        return (1..regions.numberCount).map { number ->
            OpenAi.Area(
                number, regions.spotX[number] * 100 / regions.width, regions.spotY[number] * 100 / regions.height,
                max(1, Math.round(pixels[number] * 100f / total)),
            )
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
        const val VERSION = 3        // raise when the way pages are split or colored changes, so old answers are dropped
    }
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

/**
 * A page that quietly colors itself in while the model thinks. A soft wash of pastel colors sweeps across the page from
 * the top left; every area fades in, then slowly drifts from one color to the next. It is only for show: the colors are
 * not the model's. It works on a small copy of the page so a frame is cheap to make.
 */
class ColorPlay private constructor(private val regions: Regions) {
    private val order = IntArray(regions.numberCount + 1) { number ->
        // When an area joins in: a diagonal sweep from the top left, with a little spread so it does not look mechanical.
        val sweep = (regions.spotX[number] * 100L / regions.width + regions.spotY[number] * 100L / regions.height).toInt()
        sweep / 2 + (number * 7) % 9
    }

    /** The picture at [tick] (eight a second). */
    fun frame(tick: Int): Bitmap {
        val colors = HashMap<Int, Int>()
        for (number in 1..regions.numberCount) {
            val age = tick - order[number]                              // ticks since this area joined in
            if (age < 0) { colors[number] = 0xFFFFFF; continue }
            val step = age / DRIFT                                      // which color it is on
            val from = PALETTE[(number * 5 + step) % PALETTE.size]
            val to = PALETTE[(number * 5 + step + 1) % PALETTE.size]
            val change = ((age % DRIFT) - (DRIFT - BLEND)).coerceAtLeast(0) / BLEND.toFloat()   // 0 most of the time, then 0..1
            val color = mix(from, to, ease(change))
            colors[number] = mix(0xFFFFFF, color, ease(minOf(1f, age / BLEND.toFloat())))       // new areas fade in from white
        }
        return Bitmap.createBitmap(regions.paint(colors, 0f), regions.width, regions.height, Bitmap.Config.ARGB_8888)
    }

    private fun ease(t: Float) = t * t * (3 - 2 * t)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object {
        private const val SIZE = 480
        private const val DRIFT = 28   // ticks an area stays on one color, about 3.5 seconds
        private const val BLEND = 12   // ticks it takes to fade into the next one
        // Soft, light colors that go well side by side.
        private val PALETTE = intArrayOf(
            0xF2B5AE, 0xF5CFA0, 0xF4E3A1, 0xB5DBAA, 0xA8D9D3, 0xA9CBEE, 0xC4BBEB, 0xEDB8D6, 0xDDC3AC,
        )

        fun of(page: Bitmap): ColorPlay {
            val gray = grayOf(page)
            // Shrink by keeping the darkest pixel of each block, so thin lines survive and areas stay closed.
            val step = max(1, Math.ceil(max(page.width, page.height) / SIZE.toDouble()).toInt())
            val w = max(1, page.width / step)
            val h = max(1, page.height / step)
            val small = IntArray(w * h) { i ->
                var darkest = 255
                val x0 = (i % w) * step
                val y0 = (i / w) * step
                for (y in y0 until minOf(y0 + step, page.height)) for (x in x0 until minOf(x0 + step, page.width)) darkest = minOf(darkest, gray[y * page.width + x])
                darkest
            }
            return ColorPlay(Regions.split(small, w, h))
        }
    }
}
