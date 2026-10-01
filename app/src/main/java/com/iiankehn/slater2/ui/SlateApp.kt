package com.iiankehn.slater2.ui

import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iiankehn.slater2.SlateViewModel
import com.iiankehn.slater2.io.AndroidDocumentActions
import com.iiankehn.slater2.io.DocumentFormats
import com.iiankehn.slater2.io.R2DocumentBridge
import com.iiankehn.slater2.editing.CharacterFormat
import com.iiankehn.slater2.editing.FlatTextEditorAdapter
import com.iiankehn.slater2.layout.DocumentLayoutEngine
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextStyle
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.PageMargins
import com.iiankehn.slater2.model.PageOrientation
import com.iiankehn.slater2.model.PageSize
import com.iiankehn.slater2.ui.theme.CanvasBackground
import com.iiankehn.slater2.ui.theme.CoreBlue
import com.iiankehn.slater2.ui.theme.Paper
import com.iiankehn.slater2.ui.theme.PaperText
import com.iiankehn.slater2.update.SlateUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RibbonTab { File, Home, Insert, Layout, Review, View }
private enum class LayoutAction { Margins, Orientation, Size, Columns }
private enum class ExportFormat(val extension: String, val mime: String) {
    Text("txt", "text/plain"),
    Markdown("md", "text/markdown"),
    Docx("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Pdf("pdf", "application/pdf"),
}
private enum class TemplateKind(val title: String, val description: String, val content: String) {
    Report("Report", "Structured sections", "Executive summary\n\nStart writing your summary here.\n\nBackground\n\nAdd the context for your report.\n\nFindings\n\nDescribe your findings."),
    Letter("Letter", "Professional correspondence", "Date\n\nRecipient name\nRecipient address\n\nDear recipient,\n\nStart your letter here.\n\nSincerely,\nYour name"),
    Resume("Resume", "Clear, modern résumé", "YOUR NAME\nRole or specialty\n\nPROFILE\nWrite a short professional summary.\n\nEXPERIENCE\nRole — Organization\nAdd accomplishments and responsibilities.\n\nEDUCATION\nQualification — Institution"),
}
private data class ExportRequest(val document: Document, val format: ExportFormat)

@Composable
fun SlateR2App(viewModel: SlateViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    val selected = uiState.documents.firstOrNull { it.id == selectedId }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else "Imported document"
                    } ?: "Imported document"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read the selected document.")
                    val title = name.substringBeforeLast('.').ifBlank { "Imported document" }
                    when (name.substringAfterLast('.', "").lowercase()) {
                        "md", "markdown" -> DocumentFormats.importMarkdown(bytes, title)
                        "docx" -> DocumentFormats.importDocx(bytes, title)
                        else -> DocumentFormats.importText(bytes, title)
                    }
                }
            }.onSuccess { imported ->
                selectedId = viewModel.importDocument(imported).id
                if (imported.warnings.isNotEmpty()) Toast.makeText(context, imported.warnings.joinToString(" "), Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, it.message ?: "Import failed", Toast.LENGTH_LONG).show() }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = pendingExport
        val uri = result.data?.data
        pendingExport = null
        if (request != null && uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = when (request.format) {
                        ExportFormat.Text -> DocumentFormats.exportText(request.document.body)
                        ExportFormat.Markdown -> DocumentFormats.exportMarkdown(request.document.body)
                        ExportFormat.Docx -> DocumentFormats.exportDocx(request.document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(request.document))
                        ExportFormat.Pdf -> AndroidDocumentActions.renderPdf(request.document)
                    }
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: error("Unable to write the selected file.")
                }
            }.onSuccess { Toast.makeText(context, "Export complete", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_LONG).show() }
        }
    }

    fun export(document: Document, format: ExportFormat) {
        pendingExport = ExportRequest(document, format)
        val title = DocumentTitlePolicy.displayTitle(document.title, document.body.text)
        exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = format.mime
            putExtra(Intent.EXTRA_TITLE, "$title.${format.extension}")
            addCategory(Intent.CATEGORY_OPENABLE)
        })
    }
    fun createBlank() { selectedId = viewModel.createDocument().id }
    fun createFromTemplate(template: TemplateKind) {
        val document = viewModel.createDocument().copy(title = template.title, body = RichTextDocument.plain(template.content))
        viewModel.updateDocument(document)
        selectedId = document.id
    }

    BackHandler(enabled = selected != null) { selectedId = null }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            selected == null -> StartCenter(
                documents = uiState.documents.filterNot { it.isDeleted || it.isArchived },
                onNew = ::createBlank,
                onTemplate = ::createFromTemplate,
                onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                onOpen = { selectedId = it.id },
            )
            else -> WordProcessorWorkspace(
                document = selected,
                saving = selected.id in uiState.savingDocumentIds,
                onClose = { selectedId = null },
                onChange = viewModel::updateDocument,
                onNew = ::createBlank,
                onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                onExport = { export(selected, it) },
                onShare = { AndroidDocumentActions.share(context, selected) },
                onPrint = { AndroidDocumentActions.print(context, selected) },
                onCheckUpdates = {
                    scope.launch {
                        val message = runCatching { SlateUpdater.checkForUpdate(context) }.fold(
                            onSuccess = { if (it == null) "Slate R2 is up to date." else "${it.versionName} is available from GitHub." },
                            onFailure = { it.message ?: "Unable to check for updates." },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun StartCenter(
    documents: List<Document>,
    onNew: () -> Unit,
    onTemplate: (TemplateKind) -> Unit,
    onImport: () -> Unit,
    onOpen: (Document) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Slate R2", style = MaterialTheme.typography.headlineMedium)
                        Text("Word processor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onImport) { Text("Open document") }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = if (wide) 48.dp else 20.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Create a document", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NewDocumentCard("Blank document", "Start with a clean page", true, onNew)
                            TemplateKind.entries.forEach { NewDocumentCard(it.title, it.description, onClick = { onTemplate(it) }) }
                        }
                    }
                }
                item { Text("Recent documents", style = MaterialTheme.typography.titleLarge) }
                if (documents.isEmpty()) item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your workspace is ready", style = MaterialTheme.typography.titleMedium)
                            Text("Create a blank document, choose a template, or open a DOCX, Markdown, or text file.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else items(documents, key = Document::id) { RecentDocument(it) { onOpen(it) } }
            }
        }
    }
}

@Composable
private fun NewDocumentCard(title: String, description: String, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(190.dp).height(150.dp).clickable(onClick = onClick),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (primary) "+" else "▤", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentDocument(document: Document, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text("DOC", modifier = Modifier.padding(horizontal = 11.dp, vertical = 14.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(DocumentTitlePolicy.displayTitle(document.title, document.body.text), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(document.body.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "Blank document", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(document.updatedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordProcessorWorkspace(
    document: Document,
    saving: Boolean,
    onClose: () -> Unit,
    onChange: (Document) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(RibbonTab.Home) }
    var showNavigation by remember { mutableStateOf(true) }
    var showInspector by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(100) }
    val editor = remember(document.id) { FlatTextEditorAdapter(document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)) }
    var editorValue by remember(document.id) { mutableStateOf(TextFieldValue(annotatedBody(editor.legacyBody()))) }
    val focusRequester = remember { FocusRequester() }
    fun publish(state: com.iiankehn.slater2.editing.FlatEditorState) {
        val body = editor.legacyBody()
        val selection = TextRange(state.selectionStart, state.selectionEnd)
        editorValue = TextFieldValue(annotatedBody(body), selection.coerceIn(0, body.text.length))
        onChange(document.copy(body = body, wordProcessingDocument = editor.document.copy(title = document.title)))
    }
    fun toggle(style: RichTextStyle) {
        val range = selectionOrWordRange(editorValue.text, editorValue.selection)
        val state = when (style) {
            RichTextStyle.Bold -> editor.toggle(CharacterFormat.Bold, range.start, range.end)
            RichTextStyle.Italic -> editor.toggle(CharacterFormat.Italic, range.start, range.end)
            RichTextStyle.Underline, RichTextStyle.Link -> editor.toggle(CharacterFormat.Underline, range.start, range.end)
            RichTextStyle.HeadingOne -> editor.applyNamedStyle(NamedParagraphStyle.Heading1, range.start, range.end)
            RichTextStyle.Quote -> editor.applyNamedStyle(NamedParagraphStyle.Quote, range.start, range.end)
            else -> return
        }
        publish(state)
    }
    fun insert(text: String) {
        val selection = editorValue.selection.coerceIn(0, document.body.text.length)
        val nextText = editorValue.text.replaceRange(selection.min, selection.max, text)
        publish(editor.replace(nextText, selection.min + text.length, selection.min + text.length))
        focusRequester.requestFocus()
    }
    fun toggleList(kind: ListKind) {
        val range = selectionOrWordRange(editorValue.text, editorValue.selection)
        publish(editor.toggleList(kind, range.start, range.end))
    }
    fun updateLayout(action: LayoutAction) {
        val page = editor.document.sections.first().page
        val updated = when (action) {
            LayoutAction.Margins -> page.copy(margins = if (page.margins.topPoints == 72f) PageMargins(36f, 36f, 36f, 36f) else PageMargins())
            LayoutAction.Orientation -> page.copy(orientation = if (page.orientation == PageOrientation.Portrait) PageOrientation.Landscape else PageOrientation.Portrait)
            LayoutAction.Size -> page.copy(size = PageSize.entries[(page.size.ordinal + 1) % PageSize.entries.size])
            LayoutAction.Columns -> page.copy(columns = page.columns % 4 + 1)
        }
        publish(editor.updatePageSetup(updated))
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        val tablet = maxWidth >= 840.dp
        val desktop = maxWidth >= 1200.dp
        Column(Modifier.fillMaxSize()) {
            DocumentTitleBar(document, saving, onClose, onChange)
            RibbonTabs(activeTab) { activeTab = it }
            Ribbon(
                tab = activeTab, compact = !tablet, document = document, selection = editorValue.selection,
                onToggle = ::toggle, onInsert = ::insert, onNew = onNew, onOpen = onImport,
                onToggleList = ::toggleList,
                onPageBreak = { publish(editor.insertPageBreak(editorValue.selection.min, editorValue.selection.max)) },
                onLayout = ::updateLayout,
                onExport = onExport, onShare = onShare, onPrint = onPrint, onCheckUpdates = onCheckUpdates,
                showNavigation = showNavigation, showInspector = showInspector,
                onToggleNavigation = { showNavigation = !showNavigation }, onToggleInspector = { showInspector = !showInspector },
                onZoom = { zoom = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (tablet && showNavigation) NavigationPane(document, Modifier.width(230.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().background(CanvasBackground)) {
                    Ruler(zoom)
                    DocumentCanvas(
                        value = editorValue, zoom = zoom, focusRequester = focusRequester,
                        onValueChange = { value ->
                            publish(editor.replace(value.text, value.selection.start, value.selection.end))
                        },
                        onToggle = ::toggle,
                        onUndo = { publish(editor.undo()) },
                        onRedo = { publish(editor.redo()) },
                    )
                }
                if (desktop && showInspector) InspectorPane(document, Modifier.width(280.dp).fillMaxHeight())
            }
            StatusBar(document, saving, zoom) { zoom = it }
        }
    }
}

@Composable
private fun DocumentTitleBar(document: Document, saving: Boolean, onClose: () -> Unit, onChange: (Document) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Start") }
            Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 7.dp))
            BasicTextField(
                value = document.title,
                onValueChange = { onChange(document.copy(title = it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                decorationBox = { inner -> if (document.title.isBlank()) Text("Untitled document", color = MaterialTheme.colorScheme.onSurfaceVariant) else inner() },
            )
            Text(if (saving) "Saving…" else "Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RibbonTabs(active: RibbonTab, onSelect: (RibbonTab) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        RibbonTab.entries.forEach { tab ->
            val selected = tab == active
            Column(Modifier.clickable { onSelect(tab) }.padding(horizontal = 18.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tab.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected) Spacer(Modifier.padding(top = 4.dp).width(28.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun Ribbon(
    tab: RibbonTab,
    compact: Boolean,
    document: Document,
    selection: TextRange,
    onToggle: (RichTextStyle) -> Unit,
    onInsert: (String) -> Unit,
    onToggleList: (ListKind) -> Unit,
    onPageBreak: () -> Unit,
    onLayout: (LayoutAction) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onCheckUpdates: () -> Unit,
    showNavigation: Boolean,
    showInspector: Boolean,
    onToggleNavigation: () -> Unit,
    onToggleInspector: () -> Unit,
    onZoom: (Int) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().height(if (compact) 70.dp else 88.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                RibbonTab.File -> {
                    RibbonGroup("Document") { RibbonCommand("New", onNew); RibbonCommand("Open", onOpen) }
                    RibbonGroup("Export") { ExportMenu(onExport); RibbonCommand("Share", onShare); RibbonCommand("Print", onPrint) }
                    RibbonGroup("Slate") { RibbonCommand("Updates", onCheckUpdates) }
                }
                RibbonTab.Home -> {
                    RibbonGroup("Font") {
                        RibbonCommand("B", { onToggle(RichTextStyle.Bold) }, document.body.hasStyle(RichTextStyle.Bold, selection.min, selection.max), FontWeight.Black)
                        RibbonCommand("I", { onToggle(RichTextStyle.Italic) }, document.body.hasStyle(RichTextStyle.Italic, selection.min, selection.max), italic = true)
                        RibbonCommand("U", { onToggle(RichTextStyle.Underline) }, document.body.hasStyle(RichTextStyle.Underline, selection.min, selection.max), underline = true)
                    }
                    RibbonGroup("Paragraph") { RibbonCommand("Bullets", { onToggleList(ListKind.Bulleted) }); RibbonCommand("Numbering", { onToggleList(ListKind.Numbered) }); RibbonCommand("Quote", { onToggle(RichTextStyle.Quote) }) }
                    RibbonGroup("Styles") { RibbonCommand("Title", { onToggle(RichTextStyle.HeadingOne) }); RibbonCommand("Normal", {}) }
                }
                RibbonTab.Insert -> {
                    RibbonGroup("Pages") { RibbonCommand("Page break", onPageBreak) }
                    RibbonGroup("Content") { RibbonCommand("Table", { onInsert("\n| Column | Column |\n| — | — |\n|  |  |\n") }); RibbonCommand("Picture", { onInsert("[Image]\n") }); RibbonCommand("Link", { onToggle(RichTextStyle.Link) }) }
                }
                RibbonTab.Layout -> RibbonGroup("Page setup") { RibbonCommand("Margins", { onLayout(LayoutAction.Margins) }); RibbonCommand("Orientation", { onLayout(LayoutAction.Orientation) }); RibbonCommand("Size", { onLayout(LayoutAction.Size) }); RibbonCommand("Columns", { onLayout(LayoutAction.Columns) }) }
                RibbonTab.Review -> {
                    RibbonGroup("Proofing") { RibbonCommand("Spelling", {}); RibbonCommand("Word count", {}) }
                    RibbonGroup("Changes") { RibbonCommand("Comment", { onInsert("[Comment] ") }); RibbonCommand("Track", {}) }
                }
                RibbonTab.View -> {
                    RibbonGroup("Show") { RibbonCommand("Navigation", onToggleNavigation, showNavigation); RibbonCommand("Inspector", onToggleInspector, showInspector) }
                    RibbonGroup("Zoom") { RibbonCommand("75%", { onZoom(75) }); RibbonCommand("100%", { onZoom(100) }); RibbonCommand("125%", { onZoom(125) }) }
                }
            }
        }
    }
}

@Composable
private fun RibbonGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
    }
    Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun RibbonCommand(label: String, onClick: () -> Unit, active: Boolean = false, weight: FontWeight = FontWeight.Medium, italic: Boolean = false, underline: Boolean = false) {
    Surface(Modifier.clickable(onClick = onClick), color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        Text(label, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (underline) TextDecoration.Underline else null, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1)
    }
}

@Composable
private fun ExportMenu(onExport: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonCommand("Export", { open = true })
        DropdownMenu(open, { open = false }) {
            ExportFormat.entries.forEach { format -> DropdownMenuItem(text = { Text(format.name) }, onClick = { open = false; onExport(format) }) }
        }
    }
}

@Composable
private fun NavigationPane(document: Document, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Text("Navigation", style = MaterialTheme.typography.titleMedium)
            Text("HEADINGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            val headings = document.body.text.lines().filter { it.isNotBlank() }.take(8)
            if (headings.isEmpty()) Text("Add headings to build an outline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            headings.forEachIndexed { index, line -> Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun InspectorPane(document: Document, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Format", style = MaterialTheme.typography.titleMedium)
            InspectorSection("Text", listOf("Typeface" to "Serif", "Size" to "11 pt", "Color" to "Automatic"))
            InspectorSection("Paragraph", listOf("Alignment" to "Left", "Line spacing" to "1.15", "After" to "8 pt"))
            InspectorSection("Document", listOf("Page" to "A4", "Margins" to "Normal", "Words" to wordCount(document.body.text).toString()))
        }
    }
}

@Composable
private fun InspectorSection(title: String, values: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        values.forEach { (label, value) -> Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Ruler(zoom: Int) {
    Row(Modifier.fillMaxWidth().height(30.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("0     1     2     3     4     5     6     7     8", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("$zoom%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocumentCanvas(value: TextFieldValue, zoom: Int, focusRequester: FocusRequester, onValueChange: (TextFieldValue) -> Unit, onToggle: (RichTextStyle) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 28.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.widthIn(max = (760 * zoom / 100).dp).fillMaxWidth(),
            color = Paper, contentColor = PaperText, shape = RoundedCornerShape(3.dp), shadowElevation = 4.dp,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = PaperText, fontSize = (17 * zoom / 100f).sp, lineHeight = (28 * zoom / 100f).sp, fontFamily = FontFamily.Serif),
                cursorBrush = SolidColor(CoreBlue),
                modifier = Modifier.fillMaxWidth().height((980 * zoom / 100).dp)
                    .padding(horizontal = (72 * zoom / 100).dp, vertical = (70 * zoom / 100).dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.B -> { onToggle(RichTextStyle.Bold); true }
                            Key.I -> { onToggle(RichTextStyle.Italic); true }
                            Key.U -> { onToggle(RichTextStyle.Underline); true }
                            Key.Z -> { onUndo(); true }
                            Key.Y -> { onRedo(); true }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    if (value.text.isEmpty()) Text("Start writing…", color = PaperText.copy(alpha = 0.42f), fontFamily = FontFamily.Serif, fontSize = 17.sp)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun StatusBar(document: Document, saving: Boolean, zoom: Int, onZoom: (Int) -> Unit) {
    val words = wordCount(document.body.text)
    val pages = remember(document.wordProcessingDocument, document.body) {
        DocumentLayoutEngine().layout(document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)).pageCount
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (saving) "Saving…" else "Saved locally", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  •  Page 1 of $pages  •  $words words  •  ${document.body.text.length} characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onZoom((zoom - 25).coerceAtLeast(50)) }) { Text("−") }
            Text("$zoom%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { onZoom((zoom + 25).coerceAtMost(175)) }) { Text("+") }
        }
    }
}

private fun wordCount(text: String): Int = text.trim().takeIf(String::isNotEmpty)?.split(Regex("\\s+"))?.size ?: 0

private fun annotatedBody(body: RichTextDocument): AnnotatedString = AnnotatedString.Builder(body.text).apply {
    body.normalized().ranges.forEach { range ->
        val style = when (range.style) {
            RichTextStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            RichTextStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            RichTextStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
            RichTextStyle.HeadingOne -> SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)
            RichTextStyle.Link -> SpanStyle(color = CoreBlue, textDecoration = TextDecoration.Underline)
            RichTextStyle.Quote -> SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF536273))
            RichTextStyle.Image -> SpanStyle(color = CoreBlue, fontWeight = FontWeight.SemiBold)
            RichTextStyle.Table -> SpanStyle(fontWeight = FontWeight.Medium)
        }
        addStyle(style, range.start, range.end)
    }
}.toAnnotatedString()

private fun TextRange.coerceIn(minimum: Int, maximum: Int): TextRange = TextRange(start.coerceIn(minimum, maximum), end.coerceIn(minimum, maximum))

private fun selectionOrWordRange(text: String, selection: TextRange): TextRange {
    if (!selection.collapsed) return selection.coerceIn(0, text.length)
    if (text.isEmpty()) return TextRange.Zero
    val cursor = selection.start.coerceIn(0, text.length)
    var start = cursor
    var end = cursor
    while (start > 0 && !text[start - 1].isWhitespace()) start -= 1
    while (end < text.length && !text[end].isWhitespace()) end += 1
    return TextRange(start, end)
}
