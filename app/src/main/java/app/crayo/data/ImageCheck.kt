package app.crayo.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** What we learned about a picture: a small preview and whether it looks like a coloring page. */
class Checked(val looksOk: Boolean, val width: Int, val height: Int, val preview: Bitmap)

/**
 * Looks at a picture and decides whether it looks like a coloring page.
 * Deliberately forgiving: a coloring page is mostly white paper with dark lines,
 * but shading, small watermarks and thin lines are allowed through.
 */
object ImageCheck {
    private const val MIN_SIDE = 250        // pixels on the short side
    private const val MIN_WHITE = 0.55      // share of near-white pixels
    private const val MAX_COLOR = 0.12      // share of clearly colored pixels
    private const val MAX_INK = 0.45        // share of dark pixels (solid black blobs are not for coloring)
    private const val PREVIEW_SIZE = 420

    fun analyse(data: ByteArray): Checked? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        while (max(width, height) / sample > PREVIEW_SIZE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val preview = onWhite(decoded, PREVIEW_SIZE)

        val pixels = IntArray(preview.width * preview.height)
        preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
        var white = 0
        var ink = 0
        var color = 0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            if (min(r, min(g, b)) > 225) white++
            if (max(r, max(g, b)) < 130) ink++
            if (max(r, max(g, b)) - min(r, min(g, b)) > 50) color++
        }
        val total = pixels.size.toFloat()
        val ok = min(width, height) >= MIN_SIDE && white / total >= MIN_WHITE &&
            color / total <= MAX_COLOR && ink / total <= MAX_INK
        return Checked(ok, width, height, preview)
    }

    /** Draws a picture on white paper (transparent pictures would otherwise turn black) at most `limit` pixels big. */
    fun onWhite(source: Bitmap, limit: Int): Bitmap {
        val scale = min(1f, limit.toFloat() / max(source.width, source.height))
        val w = max(1, (source.width * scale).toInt())
        val h = max(1, (source.height * scale).toInt())
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(Bitmap.createScaledBitmap(source, w, h, true), 0f, 0f, null)
        return result
    }

    /** The full-size picture for printing, on white paper, at most `limit` pixels on the long side. */
    fun decodeForPrint(data: ByteArray, limit: Int = 2600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return onWhite(decoded, Int.MAX_VALUE)
    }
}
