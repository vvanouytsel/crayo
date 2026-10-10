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

/** Sends coloring pages to Android's print screen, one A4 page per drawing. */
object PrintPages {
    fun print(activity: Activity, pages: List<Bitmap>) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print("Crayo coloring pages", PagesAdapter(activity, pages), attributes)
    }

    private class PagesAdapter(private val context: Context, private val pages: List<Bitmap>) : PrintDocumentAdapter() {
        private var document: PrintedPdfDocument? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

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
                pages.forEachIndexed { index, bitmap ->
                    val page = pdf.startPage(index)
                    draw(page.canvas, bitmap, RectF(page.info.contentRect))
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

        /** Fits the drawing on the page with a small margin. Wide drawings are turned sideways so they fill it. */
        private fun draw(canvas: Canvas, bitmap: Bitmap, page: RectF) {
            val margin = 18f
            val wide = bitmap.width > bitmap.height * 1.15f
            val areaW = page.width() - 2 * margin
            val areaH = page.height() - 2 * margin
            val (fitW, fitH) = if (wide) areaH to areaW else areaW to areaH
            val scale = min(fitW / bitmap.width, fitH / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            canvas.save()
            canvas.translate(page.centerX(), page.centerY())
            if (wide) canvas.rotate(90f)
            canvas.drawBitmap(bitmap, null, RectF(-w / 2, -h / 2, w / 2, h / 2), paint)
            canvas.restore()
        }
    }
}
