package com.iiankehn.slater2.io

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.FileProvider
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import com.iiankehn.slater2.model.WordProcessingDocument
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.layout.DocumentLayoutEngine
import com.iiankehn.slater2.layout.FragmentKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object AndroidDocumentActions {
    fun sendToNotes(context: Context, document: Document, assets: List<SlxAsset> = emptyList()) {
        requireTrustedTarget(context, "com.iiankehn.slate")
        val directory = File(context.cacheDir, "handoff").apply { mkdirs() }
        val file = File(directory, "${document.id}.slx")
        file.writeBytes(DocumentFormats.exportSlx(document, assets))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, SlxCodec.MIME_TYPE)
            setPackage("com.iiankehn.slate")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun requireTrustedTarget(context: Context, targetPackage: String) {
        require(context.packageManager.checkSignatures(context.packageName, targetPackage) == PackageManager.SIGNATURE_MATCH) {
            "The installed Slate Notes build is missing or is not signed by the trusted Slate key."
        }
    }

    fun renderPdf(document: Document): ByteArray {
        val r2 = document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)
        return renderPdf(r2, null)
    }

    fun renderPdf(context: Context, document: Document): ByteArray {
        val r2 = document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)
        return renderPdf(r2, context)
    }

    fun renderPdf(document: WordProcessingDocument): ByteArray = renderPdf(document, null)

    private fun renderPdf(document: WordProcessingDocument, context: Context?): ByteArray {
        val pdf = PdfDocument()
        val layout = DocumentLayoutEngine().layout(document)
        val images = document.sections.flatMap { it.blocks }.filterIsInstance<ImageBlock>().associateBy(ImageBlock::id)
        val bitmaps = mutableMapOf<String, android.graphics.Bitmap?>()
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
                        val bitmap = bitmaps.getOrPut(fragment.blockId) {
                            val image = images[fragment.blockId]
                            if (context == null || image == null) null else runCatching {
                                context.contentResolver.openInputStream(Uri.parse(image.sourceUri))?.use(BitmapFactory::decodeStream)
                            }.getOrNull()
                        }
                        val destination = RectF(fragment.bounds.left, fragment.bounds.top, fragment.bounds.right, fragment.bounds.bottom)
                        if (bitmap != null) page.canvas.drawBitmap(bitmap, null, destination, null)
                        else {
                            page.canvas.drawRect(destination, rulePaint)
                            page.canvas.drawText("Image", fragment.bounds.left + 8f, fragment.bounds.top + 18f, textPaint)
                        }
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
        val bytes = renderPdf(context, document)
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
