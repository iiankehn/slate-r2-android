package com.iiankehn.slater2

import android.content.Intent
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
import com.iiankehn.slater2.ui.SlateR2App
import com.iiankehn.slater2.ui.theme.SlateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    when (name.substringAfterLast('.', "").lowercase()) {
                        "md", "markdown" -> DocumentFormats.importMarkdown(bytes, title)
                        "docx" -> DocumentFormats.importDocx(bytes, title)
                        else -> DocumentFormats.importText(bytes, title)
                    }
                }
            }.getOrNull()
            if (imported != null) slateViewModel.importDocument(imported)
        }
    }
}
