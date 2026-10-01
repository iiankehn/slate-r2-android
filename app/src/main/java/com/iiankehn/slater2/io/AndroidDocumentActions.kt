package com.iiankehn.slater2.io

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

object AndroidDocumentActions {
    fun renderPdf(document: Document): ByteArray {
        val pdf = PdfDocument()
        val title = DocumentTitlePolicy.displayTitle(document.title, document.body.text)
        val lines = buildList {
            add(title)
            add("")
            document.body.text.lines().forEach(::add)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; isFakeBoldText = true }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f }
        var pageNumber = 1
        var index = 0
        while (index < lines.size) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNumber++).create())
            var y = 64f
            while (index < lines.size && y < 744f) {
                val line = lines[index]
                val paint = if (index == 0) titlePaint else bodyPaint
                wrap(line, if (index == 0) 38 else 72).forEach { segment ->
                    if (y < 744f) page.canvas.drawText(segment, 48f, y, paint)
                    y += if (index == 0) 38f else 23f
                }
                index += 1
            }
            pdf.finishPage(page)
        }
        val output = ByteArrayOutputStream()
        pdf.writeTo(output)
        pdf.close()
        return output.toByteArray()
    }

    fun share(context: Context, document: Document) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, DocumentTitlePolicy.displayTitle(document.title, document.body.text))
            putExtra(Intent.EXTRA_TEXT, document.body.text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Slate document"))
    }

    fun print(context: Context, document: Document) {
        val bytes = renderPdf(document)
        val title = DocumentTitlePolicy.displayTitle(document.title, document.body.text)
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder("$title.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                    true,
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                callback: WriteResultCallback,
            ) {
                runCatching { FileOutputStream(destination.fileDescriptor).use { it.write(bytes) } }
                    .onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message) }
            }
        }
        (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title, adapter, null)
    }

    private fun wrap(value: String, width: Int): List<String> {
        if (value.length <= width) return listOf(value)
        val lines = mutableListOf<String>()
        var remaining = value
        while (remaining.length > width) {
            val breakAt = remaining.lastIndexOf(' ', width).takeIf { it > 0 } ?: width
            lines += remaining.substring(0, breakAt)
            remaining = remaining.substring(breakAt).trimStart()
        }
        lines += remaining
        return lines
    }
}
