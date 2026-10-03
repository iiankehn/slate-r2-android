package com.iiankehn.slater2.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iiankehn.slater2.SlateViewModel
import com.iiankehn.slater2.io.AndroidDocumentActions
import com.iiankehn.slater2.io.DocumentFormats
import com.iiankehn.slater2.io.DocxEmbeddedImage
import com.iiankehn.slater2.io.R2DocumentBridge
import com.iiankehn.slater2.editing.CharacterFormat
import com.iiankehn.slater2.editing.FlatTextEditorAdapter
import com.iiankehn.slater2.layout.DocumentLayoutEngine
import com.iiankehn.slater2.layout.DocumentLayout
import com.iiankehn.slater2.layout.FragmentKind
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextStyle
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.PageMargins
import com.iiankehn.slater2.model.PageOrientation
import com.iiankehn.slater2.model.PageSize
import com.iiankehn.slater2.model.ParagraphAlignment
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.model.ImageWrapping
import com.iiankehn.slater2.model.WordProcessingDocument
import com.iiankehn.slater2.model.SectionStart
import com.iiankehn.slater2.ui.theme.CanvasBackground
import com.iiankehn.slater2.ui.theme.CoreBlue
import com.iiankehn.slater2.ui.theme.Paper
import com.iiankehn.slater2.ui.theme.PaperText
import com.iiankehn.slater2.update.SlateUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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
    var pendingImageUri by remember { mutableStateOf<String?>(null) }
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
                        ExportFormat.Docx -> {
                            val r2 = request.document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(request.document)
                            val images = r2.sections.flatMap { it.blocks }.filterIsInstance<ImageBlock>().mapNotNull { image ->
                                runCatching {
                                    val uri = Uri.parse(image.sourceUri)
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                                    val contentType = context.contentResolver.getType(uri) ?: "image/png"
                                    val extension = when (contentType) {
                                        "image/jpeg" -> "jpg"
                                        "image/gif" -> "gif"
                                        "image/webp" -> "webp"
                                        else -> "png"
                                    }
                                    image.id to DocxEmbeddedImage(bytes, extension, contentType)
                                }.getOrNull()
                            }.toMap()
                            DocumentFormats.exportDocx(r2, images)
                        }
                        ExportFormat.Pdf -> AndroidDocumentActions.renderPdf(context, request.document)
                    }
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: error("Unable to write the selected file.")
                }
            }.onSuccess { Toast.makeText(context, "Export complete", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_LONG).show() }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingImageUri = uri.toString()
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
                pendingImageUri = pendingImageUri,
                onChooseImage = { imageLauncher.launch(arrayOf("image/*")) },
                onImageConsumed = { pendingImageUri = null },
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
    pendingImageUri: String?,
    onChooseImage: () -> Unit,
    onImageConsumed: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(RibbonTab.Home) }
    var showNavigation by remember { mutableStateOf(true) }
    var showInspector by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(100) }
    var activePage by remember(document.id) { mutableStateOf(0) }
    var showHeaderFooterEditor by remember(document.id) { mutableStateOf(false) }
    var showCustomPageSizeEditor by remember(document.id) { mutableStateOf(false) }
    val editor = remember(document.id) { FlatTextEditorAdapter(document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)) }
    var editorValue by remember(document.id) { mutableStateOf(TextFieldValue(annotatedBody(editor.legacyBody(), editor.document))) }
    var selectedObjectId by remember(document.id) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    fun publish(state: com.iiankehn.slater2.editing.FlatEditorState) {
        val body = editor.legacyBody()
        val selection = TextRange(state.selectionStart, state.selectionEnd)
        editorValue = TextFieldValue(annotatedBody(body, editor.document), selection.coerceIn(0, body.text.length))
        selectedObjectId = editor.selectedObjectId
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
    fun adjustListLevel(delta: Int) {
        val range = selectionOrWordRange(editorValue.text, editorValue.selection)
        publish(editor.adjustListLevel(delta, range.start, range.end))
    }
    fun handleTab(outdent: Boolean) {
        publish(editor.handleTab(editorValue.selection.min, editorValue.selection.max, outdent))
    }
    fun updateLayout(action: LayoutAction) {
        val page = editor.document.sections[editor.activeSectionIndex].page
        val updated = when (action) {
            LayoutAction.Margins -> page.copy(margins = if (page.margins.topPoints == 72f) PageMargins(36f, 36f, 36f, 36f) else PageMargins())
            LayoutAction.Orientation -> page.copy(orientation = if (page.orientation == PageOrientation.Portrait) PageOrientation.Landscape else PageOrientation.Portrait)
            LayoutAction.Size -> page.copy(
                size = PageSize.entries[(page.size.ordinal + 1) % PageSize.entries.size],
                customWidthPoints = null,
                customHeightPoints = null,
            )
            LayoutAction.Columns -> page.copy(columns = page.columns % 4 + 1)
        }
        publish(editor.updatePageSetup(updated))
    }
    LaunchedEffect(pendingImageUri) {
        pendingImageUri?.let { uri ->
            publish(editor.insertImage(editorValue.selection.min, editorValue.selection.max, uri, "Imported picture"))
            onImageConsumed()
        }
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
                onAdjustListLevel = ::adjustListLevel,
                onPageBreak = { publish(editor.insertPageBreak(editorValue.selection.min, editorValue.selection.max)) },
                onSectionBreak = { start -> publish(editor.insertSectionBreak(editorValue.selection.min, editorValue.selection.max, start)) },
                onEditHeaderFooter = { showHeaderFooterEditor = true },
                onInsertTable = { publish(editor.insertTable(editorValue.selection.min, editorValue.selection.max)) },
                onInsertImage = onChooseImage,
                onLayout = ::updateLayout,
                onCustomPageSize = { showCustomPageSizeEditor = true },
                onExport = onExport, onShare = onShare, onPrint = onPrint, onCheckUpdates = onCheckUpdates,
                showNavigation = showNavigation, showInspector = showInspector,
                onToggleNavigation = { showNavigation = !showNavigation }, onToggleInspector = { showInspector = !showInspector },
                onZoom = { zoom = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (tablet && showNavigation) NavigationPane(
                    document = document,
                    activePage = activePage,
                    onPageSelected = { activePage = it },
                    modifier = Modifier.width(230.dp).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight().background(CanvasBackground)) {
                    Ruler(zoom)
                    DocumentCanvas(
                        value = editorValue, zoom = zoom, focusRequester = focusRequester,
                        document = editor.document,
                        onValueChange = { value ->
                            publish(editor.replace(value.text, value.selection.start, value.selection.end))
                        },
                        onToggle = ::toggle,
                        onUndo = { publish(editor.undo()) },
                        onRedo = { publish(editor.redo()) },
                        onTab = ::handleTab,
                        onToggleChecklistItem = { id -> publish(editor.toggleChecklistItem(id)) },
                        onUpdateTableCell = { id, row, column, text -> publish(editor.updateTableCell(id, row, column, text)) },
                        onResizeTable = { id, rows, columns -> publish(editor.resizeTable(id, rows, columns)) },
                        onDeleteTableRow = { id, row -> publish(editor.deleteTableRow(id, row)) },
                        onDeleteTableColumn = { id, column -> publish(editor.deleteTableColumn(id, column)) },
                        onSetTableHeaderRows = { id, count -> publish(editor.setTableHeaderRows(id, count)) },
                        onUpdateImage = { id, description, width, height, wrapping ->
                            publish(editor.updateImage(id, description, width, height, wrapping))
                        },
                        onDeleteObject = { id -> publish(editor.deleteObject(id)) },
                        selectedObjectId = selectedObjectId,
                        onSelectObject = { id ->
                            editor.selectObject(id)
                            selectedObjectId = editor.selectedObjectId
                        },
                        activePage = activePage,
                        onActivePageChange = { activePage = it },
                    )
                }
                if (desktop && showInspector) InspectorPane(
                    document = document,
                    r2 = editor.document,
                    activeSectionIndex = editor.activeSectionIndex,
                    onHeaderFooterChange = { header, footer -> publish(editor.updateHeaderFooter(header, footer)) },
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                )
            }
            StatusBar(document, saving, zoom, activePage) { zoom = it }
        }
    }
    if (showHeaderFooterEditor) {
        val section = editor.document.sections[editor.activeSectionIndex]
        HeaderFooterEditorDialog(
            initialHeader = sectionMarginText(section.header),
            initialFooter = sectionMarginText(section.footer),
            onDismiss = { showHeaderFooterEditor = false },
            onSave = { header, footer ->
                publish(editor.updateHeaderFooter(header, footer))
                showHeaderFooterEditor = false
            },
        )
    }
    if (showCustomPageSizeEditor) {
        val page = editor.document.sections[editor.activeSectionIndex].page
        CustomPageSizeDialog(
            initialWidth = page.widthPoints,
            initialHeight = page.heightPoints,
            onDismiss = { showCustomPageSizeEditor = false },
            onSave = { width, height ->
                publish(editor.updatePageSetup(page.copy(
                    orientation = PageOrientation.Portrait,
                    customWidthPoints = width,
                    customHeightPoints = height,
                )))
                showCustomPageSizeEditor = false
            },
        )
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
    onAdjustListLevel: (Int) -> Unit,
    onPageBreak: () -> Unit,
    onSectionBreak: (SectionStart) -> Unit,
    onEditHeaderFooter: () -> Unit,
    onInsertTable: () -> Unit,
    onInsertImage: () -> Unit,
    onLayout: (LayoutAction) -> Unit,
    onCustomPageSize: () -> Unit,
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
                    RibbonGroup("Paragraph") {
                        RibbonCommand("Bullets", { onToggleList(ListKind.Bulleted) })
                        RibbonCommand("Numbering", { onToggleList(ListKind.Numbered) })
                        RibbonCommand("Checklist", { onToggleList(ListKind.Checklist) })
                        RibbonCommand("Outdent", { onAdjustListLevel(-1) })
                        RibbonCommand("Indent", { onAdjustListLevel(1) })
                        RibbonCommand("Quote", { onToggle(RichTextStyle.Quote) })
                    }
                    RibbonGroup("Styles") { RibbonCommand("Title", { onToggle(RichTextStyle.HeadingOne) }); RibbonCommand("Normal", {}) }
                }
                RibbonTab.Insert -> {
                    RibbonGroup("Pages") { RibbonCommand("Page break", onPageBreak); SectionBreakMenu(onSectionBreak) }
                    RibbonGroup("Content") { RibbonCommand("Table", onInsertTable); RibbonCommand("Picture", onInsertImage); RibbonCommand("Link", { onToggle(RichTextStyle.Link) }) }
                    RibbonGroup("Page elements") { RibbonCommand("Header / footer", onEditHeaderFooter) }
                }
                RibbonTab.Layout -> RibbonGroup("Page setup") { RibbonCommand("Margins", { onLayout(LayoutAction.Margins) }); RibbonCommand("Orientation", { onLayout(LayoutAction.Orientation) }); RibbonCommand("Size", { onLayout(LayoutAction.Size) }); RibbonCommand("Custom size", onCustomPageSize); RibbonCommand("Columns", { onLayout(LayoutAction.Columns) }) }
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
private fun SectionBreakMenu(onInsert: (SectionStart) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonCommand("Section break", { open = true })
        DropdownMenu(open, { open = false }) {
            listOf(
                SectionStart.Continuous to "Continuous",
                SectionStart.NextPage to "Next page",
                SectionStart.OddPage to "Odd page",
                SectionStart.EvenPage to "Even page",
            ).forEach { (start, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onInsert(start) })
            }
        }
    }
}

@Composable
private fun NavigationPane(
    document: Document,
    activePage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r2 = document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)
    val layout = remember(r2) { DocumentLayoutEngine().layout(r2) }
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Navigation", style = MaterialTheme.typography.titleMedium)
            Text("HEADINGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            val headings = document.body.text.lines().filter { it.isNotBlank() }.take(8)
            if (headings.isEmpty()) Text("Add headings to build an outline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            headings.forEachIndexed { index, line -> Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal) }
            Text("PAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
            layout.pages.take(50).forEach { page ->
                val selected = page.index == activePage
                Row(
                    Modifier.fillMaxWidth().clickable { onPageSelected(page.index) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        Modifier.width(42.dp).aspectRatio(page.setup.widthPoints / page.setup.heightPoints),
                        color = Paper,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) CoreBlue else MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp,
                    ) {}
                    Text(
                        "Page ${page.index + 1}",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorPane(
    document: Document,
    r2: WordProcessingDocument,
    activeSectionIndex: Int,
    onHeaderFooterChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val section = r2.sections[activeSectionIndex.coerceIn(r2.sections.indices)]
    val page = section.page
    val blocks = r2.sections.flatMap { it.blocks }
    val header = sectionMarginText(section.header)
    val footer = sectionMarginText(section.footer)
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Format", style = MaterialTheme.typography.titleMedium)
            InspectorSection("Text", listOf("Typeface" to "Serif", "Size" to "11 pt", "Color" to "Automatic"))
            InspectorSection("Paragraph", listOf("Alignment" to "Left", "Line spacing" to "1.15", "After" to "8 pt"))
            InspectorSection("Document", listOf(
                "Page" to page.size.name,
                "Orientation" to page.orientation.name,
                "Columns" to page.columns.toString(),
                "Tables" to blocks.count { it is TableBlock }.toString(),
                "Pictures" to blocks.count { it is ImageBlock }.toString(),
                "Words" to wordCount(document.body.text).toString(),
            ))
            Text("Section ${activeSectionIndex + 1} of ${r2.sections.size}", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = header,
                onValueChange = { onHeaderFooterChange(it, footer) },
                label = { Text("Header") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = footer,
                onValueChange = { onHeaderFooterChange(header, it) },
                label = { Text("Footer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

@Composable
private fun HeaderFooterEditorDialog(
    initialHeader: String,
    initialFooter: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var header by remember(initialHeader) { mutableStateOf(initialHeader) }
    var footer by remember(initialFooter) { mutableStateOf(initialFooter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Header and footer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(header, { header = it }, label = { Text("Header") }, minLines = 2)
                OutlinedTextField(footer, { footer = it }, label = { Text("Footer") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(header, footer) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomPageSizeDialog(
    initialWidth: Float,
    initialHeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit,
) {
    var widthInches by remember(initialWidth) { mutableStateOf("%.2f".format(initialWidth / 72f)) }
    var heightInches by remember(initialHeight) { mutableStateOf("%.2f".format(initialHeight / 72f)) }
    val widthPoints = widthInches.toFloatOrNull()?.times(72f)
    val heightPoints = heightInches.toFloatOrNull()?.times(72f)
    val valid = widthPoints != null && heightPoints != null && widthPoints in 144f..1440f && heightPoints in 144f..1440f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom page size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter dimensions from 2 to 20 inches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(widthInches, { widthInches = it }, label = { Text("Width (inches)") }, singleLine = true)
                OutlinedTextField(heightInches, { heightInches = it }, label = { Text("Height (inches)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(requireNotNull(widthPoints), requireNotNull(heightPoints)) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun DocumentCanvas(
    value: TextFieldValue,
    document: WordProcessingDocument,
    zoom: Int,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onToggle: (RichTextStyle) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTab: (Boolean) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onUpdateTableCell: (String, Int, Int, String) -> Unit,
    onResizeTable: (String, Int, Int) -> Unit,
    onDeleteTableRow: (String, Int) -> Unit,
    onDeleteTableColumn: (String, Int) -> Unit,
    onSetTableHeaderRows: (String, Int) -> Unit,
    onUpdateImage: (String, String, Float?, Float?, ImageWrapping) -> Unit,
    onDeleteObject: (String) -> Unit,
    selectedObjectId: String?,
    onSelectObject: (String?) -> Unit,
    activePage: Int,
    onActivePageChange: (Int) -> Unit,
) {
    val layout = remember(document) { DocumentLayoutEngine().layout(document) }
    val slices = remember(document, layout, value.text.length) { pageTextSlices(document, layout, value.text.length) }
    val listState = rememberLazyListState()
    LaunchedEffect(activePage, layout.pageCount) {
        val target = activePage.coerceIn(0, layout.pages.lastIndex)
        if (target != activePage) onActivePageChange(target)
        if (target != listState.firstVisibleItemIndex) listState.animateScrollToItem(target)
    }
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.isScrollInProgress) null else listState.firstVisibleItemIndex }
            .collect { visiblePage -> visiblePage?.let(onActivePageChange) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(layout.pages, key = { _, page -> "page-${page.index}" }) { pageIndex, page ->
            val slice = slices[pageIndex]
            val selected = pageIndex == activePage
            val localSelection = TextRange(
                (value.selection.start - slice.start).coerceIn(0, slice.length),
                (value.selection.end - slice.start).coerceIn(0, slice.length),
            )
            val pageValue = TextFieldValue(
                value.annotatedString.subSequence(slice.start, slice.end),
                if (selected) localSelection else TextRange.Zero,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.widthIn(max = (760 * zoom / 100).dp).fillMaxWidth()
                        .aspectRatio(page.setup.widthPoints / page.setup.heightPoints)
                        .clickable { onActivePageChange(pageIndex) }
                        .semantics { contentDescription = "Document page ${pageIndex + 1} of ${layout.pageCount}" },
                    color = Paper,
                    contentColor = PaperText,
                    shape = RoundedCornerShape(3.dp),
                    border = if (selected) BorderStroke(2.dp, CoreBlue) else null,
                    shadowElevation = if (selected) 7.dp else 4.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            StructuredObjects(
                                document = document,
                                layout = layout,
                                pageIndex = pageIndex,
                                onUpdateTableCell = onUpdateTableCell,
                                onResizeTable = onResizeTable,
                                onDeleteTableRow = onDeleteTableRow,
                                onDeleteTableColumn = onDeleteTableColumn,
                                onSetTableHeaderRows = onSetTableHeaderRows,
                                onUpdateImage = onUpdateImage,
                                onDeleteObject = onDeleteObject,
                                selectedObjectId = selectedObjectId,
                                onSelectObject = onSelectObject,
                            )
                            BasicTextField(
                            value = pageValue,
                            onValueChange = { changed ->
                                val combined = value.text.replaceRange(slice.start, slice.end, changed.text)
                                onValueChange(TextFieldValue(
                                    combined,
                                    TextRange(slice.start + changed.selection.start, slice.start + changed.selection.end),
                                ))
                                onActivePageChange(pageIndex)
                            },
                            textStyle = TextStyle(
                                color = PaperText,
                                fontSize = (17 * zoom / 100f).sp,
                                lineHeight = (28 * zoom / 100f).sp,
                                fontFamily = FontFamily.Serif,
                            ),
                            cursorBrush = SolidColor(CoreBlue),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .padding(horizontal = (72 * zoom / 100).dp, vertical = (34 * zoom / 100).dp)
                                .then(if (selected) Modifier.focusRequester(focusRequester) else Modifier)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    if (event.key == Key.Tab) {
                                        onTab(event.isShiftPressed)
                                        return@onPreviewKeyEvent true
                                    }
                                    if (!event.isCtrlPressed) return@onPreviewKeyEvent false
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
                                if (pageValue.text.isEmpty()) Text(
                                    if (pageIndex == 0) "Start writing…" else "Continue writing…",
                                    color = PaperText.copy(alpha = 0.42f),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 17.sp,
                                )
                                inner()
                            },
                            )
                        }
                        ListMarkerOverlay(document, layout, pageIndex, onToggleChecklistItem)
                    }
                }
                Text(
                    "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StructuredObjects(
    document: WordProcessingDocument,
    layout: DocumentLayout,
    pageIndex: Int,
    onUpdateTableCell: (String, Int, Int, String) -> Unit,
    onResizeTable: (String, Int, Int) -> Unit,
    onDeleteTableRow: (String, Int) -> Unit,
    onDeleteTableColumn: (String, Int) -> Unit,
    onSetTableHeaderRows: (String, Int) -> Unit,
    onUpdateImage: (String, String, Float?, Float?, ImageWrapping) -> Unit,
    onDeleteObject: (String) -> Unit,
    selectedObjectId: String?,
    onSelectObject: (String?) -> Unit,
) {
    val objectIds = layout.pages[pageIndex].columns.flatMap { it.fragments }
        .filter { it.kind == FragmentKind.Table || it.kind == FragmentKind.Image }
        .mapTo(linkedSetOf()) { it.blockId }
    val objects = document.sections.flatMap { it.blocks }.filter { it.id in objectIds }
    if (objects.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 72.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        objects.forEach { block -> when (block) {
            is TableBlock -> EditableTable(
                block, block.id == selectedObjectId, onSelectObject,
                onUpdateTableCell, onResizeTable, onDeleteTableRow, onDeleteTableColumn,
                onSetTableHeaderRows, onDeleteObject,
            )
            is ImageBlock -> EditableImage(
                block, block.id == selectedObjectId, onSelectObject, onUpdateImage, onDeleteObject,
            )
            else -> Unit
        }
        }
    }
}

internal data class ParagraphListMarker(val text: String, val level: Int)

internal fun paragraphListMarkers(document: WordProcessingDocument): Map<String, ParagraphListMarker> = buildMap {
    val counters = IntArray(9)
    val kinds = arrayOfNulls<ListKind>(9)
    document.sections.forEach { section ->
        section.blocks.filterIsInstance<ParagraphBlock>().forEach paragraphLoop@ { paragraph ->
            val list = paragraph.style.list
            if (list == null) {
                counters.fill(0)
                kinds.fill(null)
                return@paragraphLoop
            }
            ((list.level + 1)..8).forEach { level -> counters[level] = 0; kinds[level] = null }
            val marker = when (list.kind) {
                ListKind.Bulleted -> listOf("•", "◦", "▪")[list.level % 3]
                ListKind.Checklist -> if (list.checked) "☑" else "☐"
                ListKind.Numbered -> {
                    counters[list.level] = if (kinds[list.level] == ListKind.Numbered) counters[list.level] + 1 else list.startAt
                    "${counters[list.level]}."
                }
            }
            kinds[list.level] = list.kind
            put(paragraph.id, ParagraphListMarker(marker, list.level))
        }
    }
}

@Composable
private fun ListMarkerOverlay(
    document: WordProcessingDocument,
    layout: DocumentLayout,
    pageIndex: Int,
    onToggleChecklistItem: (String) -> Unit,
) {
    val markers = remember(document) { paragraphListMarkers(document) }
    if (markers.isEmpty()) return
    val page = layout.pages[pageIndex]
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth.value / page.setup.widthPoints
        page.columns.flatMap { it.fragments }
            .filter { it.kind == FragmentKind.Paragraph && !it.continuedFromPrevious && it.blockId in markers }
            .distinctBy { it.blockId }
            .forEach { fragment ->
                val marker = markers.getValue(fragment.blockId)
                val x = ((fragment.bounds.left + marker.level * 18f - 22f) * scale).coerceAtLeast(4f)
                val y = (fragment.bounds.top * scale).coerceAtLeast(0f)
                Text(
                    marker.text,
                    modifier = Modifier.offset(x.dp, y.dp).clickable {
                        if (marker.text == "☐" || marker.text == "☑") onToggleChecklistItem(fragment.blockId)
                    }.semantics { contentDescription = if (marker.text == "☑") "Checked checklist item" else "Checklist item" },
                    color = PaperText,
                    fontSize = (11f * scale).coerceIn(9f, 18f).sp,
                    fontWeight = FontWeight.Medium,
                )
            }
    }
}

@Composable
private fun EditableTable(
    table: TableBlock,
    selected: Boolean,
    onSelect: (String?) -> Unit,
    onUpdateCell: (String, Int, Int, String) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onDeleteRow: (String, Int) -> Unit,
    onDeleteColumn: (String, Int) -> Unit,
    onSetHeaderRows: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
) {
    val columnCount = table.rows.first().cells.size
    val focusRequester = remember { FocusRequester() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onSelect(table.id); focusRequester.requestFocus() }
            .onKeyEvent { event ->
                if (selected && event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace)) {
                    onDelete(table.id)
                    true
                } else false
            },
        color = Color(0xFFF7F9FC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) CoreBlue else Color(0xFFCAD3DF)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Table · ${table.rows.size} × $columnCount", color = PaperText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onResize(table.id, table.rows.size + 1, columnCount) }) { Text("+ Row") }
                TextButton(onClick = { onResize(table.id, table.rows.size, columnCount + 1) }) { Text("+ Column") }
                TextButton(onClick = { onDelete(table.id) }) { Text("Delete table") }
            }
            if (selected) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = table.rows.size > 1,
                        onClick = { onDeleteRow(table.id, table.rows.lastIndex) },
                    ) { Text("− Last row") }
                    TextButton(
                        enabled = columnCount > 1,
                        onClick = { onDeleteColumn(table.id, columnCount - 1) },
                    ) { Text("− Last column") }
                    TextButton(onClick = { onSetHeaderRows(table.id, if (table.headerRowCount == 0) 1 else 0) }) {
                        Text(if (table.headerRowCount == 0) "Repeat first row" else "Stop repeating header")
                    }
                }
            }
            table.rows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.cells.forEachIndexed { columnIndex, cell ->
                        val text = cell.blocks.joinToString("\n") { paragraph -> paragraph.runs.joinToString("") { it.text } }
                        Surface(Modifier.weight(1f), color = Paper, border = BorderStroke(1.dp, Color(0xFFCAD3DF))) {
                            BasicTextField(
                                value = text,
                                onValueChange = { onUpdateCell(table.id, rowIndex, columnIndex, it) },
                                textStyle = TextStyle(color = PaperText, fontSize = 14.sp),
                                cursorBrush = SolidColor(CoreBlue),
                                modifier = Modifier.fillMaxWidth().padding(9.dp),
                                decorationBox = { inner ->
                                    if (text.isEmpty()) Text("Cell", color = PaperText.copy(alpha = 0.38f), fontSize = 14.sp)
                                    inner()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableImage(
    image: ImageBlock,
    selected: Boolean,
    onSelect: (String?) -> Unit,
    onUpdate: (String, String, Float?, Float?, ImageWrapping) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.sourceUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(image.sourceUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val width = image.widthPoints ?: 300f
    val height = image.heightPoints ?: 200f
    var previewWidth by remember(image.id, width) { mutableStateOf(width) }
    var previewHeight by remember(image.id, height) { mutableStateOf(height) }
    val focusRequester = remember { FocusRequester() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onSelect(image.id); focusRequester.requestFocus() }
            .onKeyEvent { event ->
                if (selected && event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace)) {
                    onDelete(image.id)
                    true
                } else false
            },
        color = Color(0xFFF7F9FC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) CoreBlue else Color(0xFFCAD3DF)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = image.description.ifBlank { "Inserted picture" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = width.coerceIn(120f, 720f).dp).fillMaxWidth().heightIn(max = height.coerceIn(120f, 420f).dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFE8EDF5), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("Picture preview unavailable", color = PaperText.copy(alpha = 0.62f))
                }
            }
            BasicTextField(
                value = image.description,
                onValueChange = { onUpdate(image.id, it, image.widthPoints, image.heightPoints, image.wrapping) },
                textStyle = TextStyle(color = PaperText, fontSize = 14.sp),
                cursorBrush = SolidColor(CoreBlue),
                modifier = Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(6.dp)).padding(9.dp),
                decorationBox = { inner ->
                    if (image.description.isBlank()) Text("Describe this picture for accessibility", color = PaperText.copy(alpha = 0.42f), fontSize = 14.sp)
                    inner()
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onUpdate(image.id, image.description, (width - 36f).coerceAtLeast(72f), (height - 24f).coerceAtLeast(48f), image.wrapping) }) { Text("Smaller") }
                TextButton(onClick = { onUpdate(image.id, image.description, width + 36f, height + 24f, image.wrapping) }) { Text("Larger") }
                TextButton(onClick = {
                    val next = ImageWrapping.entries[(image.wrapping.ordinal + 1) % ImageWrapping.entries.size]
                    onUpdate(image.id, image.description, image.widthPoints, image.heightPoints, next)
                }) { Text(image.wrapping.name) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDelete(image.id) }) { Text("Delete") }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.End)
                        .background(CoreBlue, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "Drag to resize picture" }
                        .pointerInput(image.id, width, height) {
                            detectDragGestures(
                                onDragStart = { previewWidth = width; previewHeight = height },
                                onDragEnd = {
                                    onUpdate(image.id, image.description, previewWidth, previewHeight, image.wrapping)
                                },
                                onDragCancel = { previewWidth = width; previewHeight = height },
                            ) { change, dragAmount ->
                                change.consume()
                                previewWidth = (previewWidth + dragAmount.x).coerceIn(72f, 1200f)
                                previewHeight = (previewHeight + dragAmount.y).coerceIn(48f, 1200f)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("↘  ${previewWidth.toInt()} × ${previewHeight.toInt()} pt", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

internal data class PageTextSlice(val start: Int, val end: Int) {
    init { require(start >= 0 && end >= start) }
    val length: Int get() = end - start
}

internal fun pageTextSlices(
    document: WordProcessingDocument,
    layout: DocumentLayout,
    textLength: Int,
): List<PageTextSlice> {
    val paragraphRanges = buildMap<String, IntRange> {
        var cursor = 0
        var hasParagraph = false
        document.sections.forEach { section ->
            section.blocks.forEach blockLoop@ { block ->
                if (block !is ParagraphBlock) return@blockLoop
                if (hasParagraph) cursor += 1
                val start = cursor
                cursor += block.runs.sumOf { it.text.length }
                put(block.id, start..cursor)
                hasParagraph = true
            }
        }
    }
    val rawStarts = layout.pages.map { page ->
        page.columns.asSequence()
            .flatMap { it.fragments.asSequence() }
            .filter { it.kind == FragmentKind.Paragraph }
            .mapNotNull { fragment ->
                val range = paragraphRanges[fragment.blockId] ?: return@mapNotNull null
                range.first + (fragment.lines.minOfOrNull { it.sourceStart } ?: 0)
            }
            .minOrNull()
    }
    val starts = MutableList(layout.pageCount) { 0 }
    rawStarts.indices.forEach { index ->
        val previous = starts.getOrElse(index - 1) { 0 }
        val candidate = if (index == 0) 0 else rawStarts[index] ?: previous
        starts[index] = candidate.coerceIn(previous, textLength)
    }
    return starts.mapIndexed { index, start ->
        val end = starts.getOrNull(index + 1) ?: textLength
        PageTextSlice(start, end.coerceIn(start, textLength))
    }
}

@Composable
private fun StatusBar(document: Document, saving: Boolean, zoom: Int, activePage: Int, onZoom: (Int) -> Unit) {
    val words = wordCount(document.body.text)
    val pages = remember(document.wordProcessingDocument, document.body) {
        DocumentLayoutEngine().layout(document.wordProcessingDocument ?: R2DocumentBridge.fromLegacy(document)).pageCount
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (saving) "Saving…" else "Saved locally", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  •  Page ${(activePage + 1).coerceAtMost(pages)} of $pages  •  $words words  •  ${document.body.text.length} characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onZoom((zoom - 25).coerceAtLeast(50)) }) { Text("−") }
            Text("$zoom%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { onZoom((zoom + 25).coerceAtMost(175)) }) { Text("+") }
        }
    }
}

private fun sectionMarginText(paragraphs: List<ParagraphBlock>): String =
    paragraphs.joinToString("\n") { paragraph -> paragraph.runs.joinToString("") { it.text } }

private fun wordCount(text: String): Int = text.trim().takeIf(String::isNotEmpty)?.split(Regex("\\s+"))?.size ?: 0

private fun annotatedBody(body: RichTextDocument, document: WordProcessingDocument): AnnotatedString = AnnotatedString.Builder(body.text).apply {
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
    var cursor = 0
    var hasParagraph = false
    document.sections.forEach { section ->
        section.blocks.filterIsInstance<ParagraphBlock>().forEach { paragraph ->
            if (hasParagraph) cursor += 1
            val start = cursor
            cursor = (cursor + paragraph.runs.sumOf { it.text.length }).coerceAtMost(body.text.length)
            val listIndent = paragraph.style.list?.let { (it.level + 1) * 18f } ?: 0f
            val restIndent = paragraph.style.startIndentPoints + listIndent
            addStyle(
                androidx.compose.ui.text.ParagraphStyle(
                    textAlign = when (paragraph.style.alignment) {
                        ParagraphAlignment.Start -> TextAlign.Start
                        ParagraphAlignment.Center -> TextAlign.Center
                        ParagraphAlignment.End -> TextAlign.End
                        ParagraphAlignment.Justify -> TextAlign.Justify
                    },
                    textIndent = TextIndent(
                        firstLine = (restIndent + paragraph.style.firstLineIndentPoints).sp,
                        restLine = restIndent.sp,
                    ),
                ),
                start,
                cursor,
            )
            hasParagraph = true
        }
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
