package com.iiankehn.slater2.io

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
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
import com.iiankehn.slater2.model.WordProcessingDocument
import com.iiankehn.slater2.layout.DocumentLayoutEngine
import com.iiankehn.slater2.layout.FragmentKind
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

object AndroidDocumentActions {
    fun renderPdf(document: Document): ByteArray {
        val r2 = document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)
        return renderPdf(r2)
    }

    fun renderPdf(document: WordProcessingDocument): ByteArray {
        val pdf = PdfDocument()
        val layout = DocumentLayoutEngine().layout(document)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.rgb(17, 19, 24) }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.75f; color = android.graphics.Color.rgb(130, 134, 142)
        }
        layout.pages.forEach { laidOutPage ->
            val setup = laidOutPage.setup
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(setup.widthPoints.toInt(), setup.heightPoints.toInt(), laidOutPage.index + 1).create())
            val fragments = laidOutPage.header + laidOutPage.columns.flatMap { it.fragments } + laidOutPage.footer
            fragments.forEach { fragment ->
                when (fragment.kind) {
                    FragmentKind.Paragraph, FragmentKind.Header, FragmentKind.Footer -> fragment.lines.forEach { line ->
                        page.canvas.drawText(line.text, line.bounds.left, line.bounds.bottom - 2f, textPaint)
                    }
                    FragmentKind.Table -> page.canvas.drawRect(RectF(fragment.bounds.left, fragment.bounds.top, fragment.bounds.right, fragment.bounds.bottom), rulePaint)
                    FragmentKind.Image -> {
                        page.canvas.drawRect(RectF(fragment.bounds.left, fragment.bounds.top, fragment.bounds.right, fragment.bounds.bottom), rulePaint)
                        page.canvas.drawText("Image", fragment.bounds.left + 8f, fragment.bounds.top + 18f, textPaint)
                    }
                }
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

}
