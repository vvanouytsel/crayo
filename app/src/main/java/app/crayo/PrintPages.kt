package app.crayo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import kotlin.math.min

/** One coloring page to print, with an optional colored example. */
class Sheet(
    val drawing: Bitmap,
    val example: Bitmap? = null,
    val exampleOnSameSheet: Boolean = false,   // true: small, next to the drawing; false: on its own sheet
)

/** Sends coloring pages to Android's print screen on A4 paper. */
object PrintPages {
    private const val MARGIN = 18f

    fun print(activity: Activity, sheets: List<Sheet>) {
        val pages = ArrayList<(Canvas, RectF) -> Unit>()
        for (sheet in sheets) {
            val example = sheet.example
            when {
                example == null -> pages += { canvas, page -> drawAlone(canvas, sheet.drawing, page) }
                sheet.exampleOnSameSheet -> pages += { canvas, page -> drawTogether(canvas, sheet.drawing, example, page) }
                else -> {
                    pages += { canvas, page -> drawAlone(canvas, sheet.drawing, page) }
                    pages += { canvas, page -> drawAlone(canvas, example, page) }
                }
            }
        }
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print("Crayo coloring pages", PagesAdapter(activity, pages), attributes)
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun isWide(bitmap: Bitmap) = bitmap.width > bitmap.height * 1.15f

    /** Moves the origin to the middle of the page and turns it sideways for wide drawings. Returns the usable width and height. */
    private fun frame(canvas: Canvas, page: RectF, sideways: Boolean): Pair<Float, Float> {
        canvas.translate(page.centerX(), page.centerY())
        if (sideways) canvas.rotate(90f)
        val width = (if (sideways) page.height() else page.width()) - 2 * MARGIN
        val height = (if (sideways) page.width() else page.height()) - 2 * MARGIN
        return width to height
    }

    private fun fit(canvas: Canvas, bitmap: Bitmap, area: RectF) {
        val scale = min(area.width() / bitmap.width, area.height() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        canvas.drawBitmap(bitmap, null, RectF(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2), paint)
    }

    /** One picture filling the page. Wide pictures are turned sideways so they fill it. */
    private fun drawAlone(canvas: Canvas, bitmap: Bitmap, page: RectF) {
        canvas.save()
        val (width, height) = frame(canvas, page, isWide(bitmap))
        fit(canvas, bitmap, RectF(-width / 2, -height / 2, width / 2, height / 2))
        canvas.restore()
    }

    /** The drawing takes most of the sheet, with the small example below it (or beside it for wide drawings). */
    private fun drawTogether(canvas: Canvas, drawing: Bitmap, example: Bitmap, page: RectF) {
        canvas.save()
        val wide = isWide(drawing)
        val (width, height) = frame(canvas, page, wide)
        val left = -width / 2
        val top = -height / 2
        if (wide) {
            fit(canvas, drawing, RectF(left, top, left + width * 0.74f, top + height))
            fit(canvas, example, RectF(left + width * 0.76f, top, left + width, top + height))
        } else {
            fit(canvas, drawing, RectF(left, top, left + width, top + height * 0.76f))
            fit(canvas, example, RectF(left, top + height * 0.78f, left + width, top + height))
        }
        canvas.restore()
    }

    private class PagesAdapter(private val context: Context, private val pages: List<(Canvas, RectF) -> Unit>) : PrintDocumentAdapter() {
        private var document: PrintedPdfDocument? = null

        override fun onLayout(
            old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?,
            callback: LayoutResultCallback, extras: Bundle?,
        ) {
            document = PrintedPdfDocument(context, new)
            if (cancel?.isCanceled == true) return callback.onLayoutCancelled()
            val info = PrintDocumentInfo.Builder("crayo.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages.size)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            ranges: Array<PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            val pdf = document ?: return callback.onWriteFailed("Nothing to print")
            try {
                pages.forEachIndexed { index, draw ->
                    val page = pdf.startPage(index)
                    draw(page.canvas, RectF(page.info.contentRect))
                    pdf.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                pdf.close()
                document = null
            }
        }
    }
}
