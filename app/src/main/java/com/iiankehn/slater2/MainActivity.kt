package com.iiankehn.slater2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.iiankehn.slater2.data.DocumentRepository
import com.iiankehn.slater2.data.SlateDatabase
import com.iiankehn.slater2.io.DocumentFormats
import com.iiankehn.slater2.io.ImportedDocument
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.ui.SlateR2App
import com.iiankehn.slater2.ui.theme.SlateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val slateViewModel: SlateViewModel by viewModels {
        SlateViewModel.Factory(
            DocumentRepository(SlateDatabase.getInstance(applicationContext).slateDao()),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            SlateTheme {
                SlateR2App(slateViewModel)
            }
        }
        handleDocumentIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDocumentIntent(intent)
    }

    private fun handleDocumentIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return
        lifecycleScope.launch {
            val imported = runCatching {
                withContext(Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else "Imported document"
                    } ?: "Imported document"
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read document")
                    val title = name.substringBeforeLast('.').ifBlank { "Imported document" }
                    val imported = when (name.substringAfterLast('.', "").lowercase()) {
                        "slx" -> DocumentFormats.importSlx(bytes)
                        "slxf" -> DocumentFormats.importSlxf(bytes)
                        "md", "markdown" -> DocumentFormats.importMarkdown(bytes, title)
                        "docx" -> DocumentFormats.importDocx(bytes, title)
                        else -> DocumentFormats.importText(bytes, title)
                    }
                    materializeSlateAssets(imported)
                }
            }.getOrNull()
            if (imported != null) slateViewModel.importDocument(imported)
        }
    }

    private fun materializeSlateAssets(imported: ImportedDocument): ImportedDocument {
        if (imported.embeddedImages.isEmpty() && imported.slxAssets.isEmpty()) return imported
        val directory = File(filesDir, "imported-slate-media").apply { mkdirs() }
        fun write(id: String, extension: String, bytes: ByteArray): String {
            val safeExtension = extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
            val file = File(directory, "$id-${bytes.contentHashCode()}.$safeExtension")
            file.outputStream().use { it.write(bytes) }
            return Uri.fromFile(file).toString()
        }
        val forgeUris = imported.embeddedImages.associate { it.blockId to write(it.blockId, it.extension, it.bytes) }
        val sharedUris = imported.slxAssets.associate { it.id to write(it.id, it.extension, it.bytes) }
        val body = imported.body.copy(ranges = imported.body.ranges.map { range ->
            val id = range.data?.takeIf { it.startsWith("asset:") }?.removePrefix("asset:")
            if (id != null && id in sharedUris) range.copy(data = sharedUris.getValue(id)) else range
        }).normalized()
        val forge = imported.wordProcessingDocument?.let { document ->
            document.copy(sections = document.sections.map { section ->
                section.copy(blocks = section.blocks.map { block ->
                    if (block is ImageBlock && block.id in forgeUris) block.copy(sourceUri = forgeUris.getValue(block.id)) else block
                })
            })
        }
        return imported.copy(body = body, wordProcessingDocument = forge, embeddedImages = emptyList(), slxAssets = emptyList())
    }
}
