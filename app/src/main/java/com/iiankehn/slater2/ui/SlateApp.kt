package com.iiankehn.slater2.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
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
import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.DocumentTitlePolicy
import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextRange
import com.iiankehn.slater2.model.RichTextStyle
import com.iiankehn.slater2.update.SlateUpdate
import com.iiankehn.slater2.update.SlateUpdater
import com.iiankehn.slater2.ui.theme.CoreBlue
import com.iiankehn.slater2.ui.theme.Midnight
import com.iiankehn.slater2.ui.theme.SlateBorder
import com.iiankehn.slater2.ui.theme.SlateSurfaceSoft
import com.iiankehn.slater2.ui.theme.SlateSurfaceRaised
import com.iiankehn.slater2.ui.theme.SlateTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CompactDestination { Library, Editor }
private enum class LibraryFilter { Documents, Favorites, Archive, Trash }
private enum class ExportFormat(val extension: String, val mime: String) {
    Text("txt", "text/plain"), Markdown("md", "text/markdown"), Docx("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), Pdf("pdf", "application/pdf")
}
private data class ExportRequest(val document: Document, val format: ExportFormat)
private sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data object Downloading : UpdateUiState
    data class Available(val update: SlateUpdate) : UpdateUiState
    data class Message(val title: String, val message: String) : UpdateUiState
}

@Composable
fun SlateR2App(viewModel: SlateViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val documents = uiState.documents
    var selectedId by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf(CompactDestination.Library) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Hidden) }
    var pendingInstallPermission by remember { mutableStateOf<SlateUpdate?>(null) }

    fun downloadUpdate(update: SlateUpdate) {
        updateUiState = UpdateUiState.Downloading
        scope.launch {
            runCatching {
                val apk = SlateUpdater.download(context, update)
                SlateUpdater.launchInstaller(context, apk)
            }
                .onSuccess {
                    updateUiState = UpdateUiState.Hidden
                }
                .onFailure { error ->
                    updateUiState = UpdateUiState.Message(
                        title = "Update failed",
                        message = error.message ?: "Slate could not download the update.",
                    )
                }
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val update = pendingInstallPermission
        pendingInstallPermission = null
        if (update != null && SlateUpdater.canRequestInstall(context)) {
            downloadUpdate(update)
        } else if (update != null) {
            updateUiState = UpdateUiState.Message(
                title = "Permission required",
                message = "Allow Slate to install updates, then try again.",
            )
        }
    }

    fun installUpdate(update: SlateUpdate) {
        if (SlateUpdater.canRequestInstall(context)) {
            downloadUpdate(update)
        } else {
            pendingInstallPermission = update
            installPermissionLauncher.launch(SlateUpdater.installPermissionIntent(context))
        }
    }

    fun checkForUpdates() {
        updateUiState = UpdateUiState.Checking
        scope.launch {
            runCatching { SlateUpdater.checkForUpdate(context) }
                .onSuccess { update ->
                    updateUiState = if (update == null) {
                        UpdateUiState.Message("Slate is up to date", "No newer published R2 build is available.")
                    } else {
                        UpdateUiState.Available(update)
                    }
                }
                .onFailure { error ->
                    updateUiState = UpdateUiState.Message(
                        title = "Unable to check",
                        message = error.message ?: "Slate could not contact GitHub.",
                    )
                }
        }
    }

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
                destination = CompactDestination.Editor
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
                        ExportFormat.Docx -> DocumentFormats.exportDocx(request.document.title, request.document.body)
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
        val base = DocumentTitlePolicy.displayTitle(document.title, document.body.text)
            .replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "Slate-document" }
        exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = format.mime
            putExtra(Intent.EXTRA_TITLE, "$base.${format.extension}")
            addCategory(Intent.CATEGORY_OPENABLE)
        })
    }

    LaunchedEffect(documents) {
        if (documents.none { it.id == selectedId }) {
            selectedId = documents.firstOrNull { !it.isArchived && !it.isDeleted }?.id ?: documents.firstOrNull()?.id
        }
    }

    if (uiState.loading || documents.isEmpty()) {
        LoadingSlate()
        return
    }

    val selected = documents.firstOrNull { it.id == selectedId }
        ?: documents.firstOrNull { !it.isArchived && !it.isDeleted }
        ?: documents.first()

    fun newDocument() {
        selectedId = viewModel.createDocument().id
    }

    fun selectAfterRemoval(documentId: String) {
        val replacement = documents.firstOrNull { it.id != documentId && !it.isArchived && !it.isDeleted }
        selectedId = replacement?.id ?: viewModel.createDocument().id
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val expanded = maxWidth >= 840.dp
            BackHandler(enabled = !expanded && destination == CompactDestination.Editor) {
                destination = CompactDestination.Library
            }
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    DocumentLibrary(
                        documents = documents,
                        selectedId = selected.id,
                        onDocumentSelected = { selectedId = it.id },
                        onNewDocument = { newDocument() },
                        onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )
                    Editor(
                        document = selected,
                        saving = selected.id in uiState.savingDocumentIds,
                        onDocumentChange = viewModel::updateDocument,
                        onDuplicate = { selectedId = viewModel.duplicateDocument(it).id },
                        onTogglePin = { viewModel.togglePin(it) },
                        onArchive = {
                            val changed = viewModel.toggleArchive(it)
                            if (changed.isArchived) selectAfterRemoval(it.id) else selectedId = changed.id
                        },
                        onDelete = {
                            viewModel.moveToTrash(it)
                            selectAfterRemoval(it.id)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onOrganize = { document, folder, tags -> viewModel.updateOrganization(document, folder, tags) },
                        onRestore = { viewModel.restoreFromTrash(it) },
                        onPermanentlyDelete = { viewModel.permanentlyDelete(it); selectAfterRemoval(it.id) },
                        onLoadHistory = viewModel::loadHistory,
                        history = uiState.history[selected.id].orEmpty(),
                        onRestoreVersion = { version -> viewModel.restoreVersion(selected, version) },
                        onExport = { format -> export(selected, format) },
                        onCheckForUpdates = ::checkForUpdates,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (destination == CompactDestination.Library) {
                DocumentLibrary(
                    documents = documents,
                    selectedId = selected.id,
                    onDocumentSelected = {
                        selectedId = it.id
                        destination = CompactDestination.Editor
                    },
                    onNewDocument = {
                        newDocument()
                        destination = CompactDestination.Editor
                    },
                    onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Editor(
                    document = selected,
                    saving = selected.id in uiState.savingDocumentIds,
                    onDocumentChange = viewModel::updateDocument,
                    onDuplicate = { selectedId = viewModel.duplicateDocument(it).id },
                    onTogglePin = { viewModel.togglePin(it) },
                    onArchive = {
                        val changed = viewModel.toggleArchive(it)
                        if (changed.isArchived) selectAfterRemoval(it.id) else selectedId = changed.id
                        destination = CompactDestination.Library
                    },
                    onDelete = {
                        viewModel.moveToTrash(it)
                        selectAfterRemoval(it.id)
                        destination = CompactDestination.Library
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onOrganize = { document, folder, tags -> viewModel.updateOrganization(document, folder, tags) },
                    onRestore = { viewModel.restoreFromTrash(it) },
                    onPermanentlyDelete = { viewModel.permanentlyDelete(it); selectAfterRemoval(it.id) },
                    onLoadHistory = viewModel::loadHistory,
                    history = uiState.history[selected.id].orEmpty(),
                    onRestoreVersion = { version -> viewModel.restoreVersion(selected, version) },
                    onExport = { format -> export(selected, format) },
                    onCheckForUpdates = ::checkForUpdates,
                    onBack = { destination = CompactDestination.Library },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    when (val state = updateUiState) {
        UpdateUiState.Hidden -> Unit
        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Checking for updates") },
            text = { Text("Slate is checking the official GitHub release channel.") },
            confirmButton = {},
        )
        UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = { Text("The APK will be verified before Android opens the installer.") },
            confirmButton = {},
        )
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { updateUiState = UpdateUiState.Hidden },
            title = { Text("Slate ${state.update.versionName} update") },
            text = { Text("Download the verified update and install it over this copy? Your documents stay on this device.") },
            confirmButton = {
                TextButton(onClick = { installUpdate(state.update) }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { updateUiState = UpdateUiState.Hidden }) { Text("Later") }
            },
        )
        is UpdateUiState.Message -> AlertDialog(
            onDismissRequest = { updateUiState = UpdateUiState.Hidden },
            title = { Text(state.title) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { updateUiState = UpdateUiState.Hidden }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun LoadingSlate() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Midnight),
        contentAlignment = Alignment.Center,
    ) {
        Text("Loading Slate…", color = SlateTextMuted)
    }
}

@Composable
private fun DocumentLibrary(
    documents: List<Document>,
    selectedId: String,
    onDocumentSelected: (Document) -> Unit,
    onNewDocument: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.Documents) }
    val visibleDocuments = documents.filter { document ->
        val inFilter = when (filter) {
            LibraryFilter.Documents -> !document.isArchived && !document.isDeleted
            LibraryFilter.Favorites -> document.isFavorite && !document.isDeleted
            LibraryFilter.Archive -> document.isArchived && !document.isDeleted
            LibraryFilter.Trash -> document.isDeleted
        }
        val matches = query.isBlank() || listOf(document.title, document.body.text, document.folder, document.tags.joinToString(" "))
            .any { it.contains(query, ignoreCase = true) }
        inFilter && matches
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SLATE R2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp)
                    Text("Documents", style = MaterialTheme.typography.headlineMedium)
                }
                Button(
                    onClick = onNewDocument,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                ) { Text("＋  New") }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search documents") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                LibraryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.name) },
                    )
                }
                AssistChip(onClick = onImport, label = { Text("Import") })
            }

            Text(
                "${visibleDocuments.size} on this device",
                color = SlateTextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )

            if (visibleDocuments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "Nothing here yet" else "No matching documents", color = SlateTextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleDocuments, key = { it.id }) { document ->
                        DocumentRow(document, selectedId == document.id) { onDocumentSelected(document) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else SlateSurfaceSoft,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (document.isPinned && !document.isArchived) {
                    Box(Modifier.size(7.dp).background(CoreBlue, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    DocumentTitlePolicy.displayTitle(document.title, document.body.text),
                    color = if (document.isArchived) SlateTextMuted else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (document.isFavorite) Text("★", color = MaterialTheme.colorScheme.primary)
            }
            if (document.body.text.isNotBlank()) {
                Text(
                    document.body.text.replace('\n', ' ').trim(),
                    color = SlateTextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Text(
                buildList {
                    add(document.updatedLabel)
                    if (document.folder.isNotBlank()) add(document.folder)
                    if (document.tags.isNotEmpty()) add(document.tags.joinToString(" · ") { "#$it" })
                }.joinToString("  •  "),
                color = SlateTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private data class EditorSnapshot(
    val title: String,
    val body: RichTextDocument,
)

@Composable
private fun Editor(
    document: Document,
    saving: Boolean,
    onDocumentChange: (Document) -> Unit,
    onDuplicate: (Document) -> Unit,
    onTogglePin: (Document) -> Unit,
    onArchive: (Document) -> Unit,
    onDelete: (Document) -> Unit,
    onToggleFavorite: (Document) -> Unit,
    onOrganize: (Document, String, Set<String>) -> Unit,
    onRestore: (Document) -> Unit,
    onPermanentlyDelete: (Document) -> Unit,
    onLoadHistory: (String) -> Unit,
    history: List<Document>,
    onRestoreVersion: (Document) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val undoStack = remember(document.id) { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember(document.id) { mutableStateListOf<EditorSnapshot>() }
    val titleFocusRequester = remember(document.id) { FocusRequester() }
    var renameRequest by remember(document.id) { mutableStateOf(0) }
    var menuExpanded by remember(document.id) { mutableStateOf(false) }
    var confirmDelete by remember(document.id) { mutableStateOf(false) }
    var showOrganize by remember(document.id) { mutableStateOf(false) }
    var showHistory by remember(document.id) { mutableStateOf(false) }
    var showFind by remember(document.id) { mutableStateOf(false) }
    var showLink by remember(document.id) { mutableStateOf(false) }
    var folderDraft by remember(document.id) { mutableStateOf(document.folder) }
    var tagsDraft by remember(document.id) { mutableStateOf(document.tags.joinToString(", ")) }
    var findDraft by remember(document.id) { mutableStateOf("") }
    var linkDraft by remember(document.id) { mutableStateOf("https://") }
    var bodyValue by remember(document.id) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = annotatedBody(document.body),
                selection = TextRange(document.body.text.length),
            ),
        )
    }

    LaunchedEffect(document.body) {
        val annotated = annotatedBody(document.body)
        if (bodyValue.text != document.body.text || bodyValue.annotatedString != annotated) {
            bodyValue = TextFieldValue(
                annotatedString = annotated,
                selection = bodyValue.selection.coerceIn(0, document.body.text.length),
            )
        }
    }

    LaunchedEffect(renameRequest) {
        if (renameRequest > 0) titleFocusRequester.requestFocus()
    }

    fun commit(changed: Document) {
        val before = EditorSnapshot(document.title, document.body)
        val after = EditorSnapshot(changed.title, changed.body)
        if (before == after) return
        if (undoStack.size == 100) undoStack.removeAt(0)
        undoStack += before
        redoStack.clear()
        onDocumentChange(changed.copy(updatedLabel = "Just now"))
    }

    fun applyStyle(style: RichTextStyle, blockStyle: Boolean = false) {
        val target = if (blockStyle) {
            paragraphRange(document.body.text, bodyValue.selection)
        } else {
            selectionOrWordRange(document.body.text, bodyValue.selection)
        }
        if (target.start == target.end) return
        val body = document.body.toggle(style, target.start, target.end)
        bodyValue = TextFieldValue(annotatedBody(body), bodyValue.selection)
        commit(document.copy(body = body))
    }

    fun applyPrefix(prefix: String) {
        val (body, selection) = toggleLinePrefix(document.body, bodyValue.selection, prefix)
        if (body == document.body) return
        bodyValue = TextFieldValue(annotatedBody(body), selection)
        commit(document.copy(body = body))
    }

    fun insertText(text: String, style: RichTextStyle? = null, data: String? = null) {
        val start = bodyValue.selection.min.coerceIn(0, document.body.text.length)
        val end = bodyValue.selection.max.coerceIn(start, document.body.text.length)
        val updatedText = document.body.text.replaceRange(start, end, text)
        var body = document.body.updateText(updatedText)
        if (style != null && text.isNotEmpty()) {
            body = body.copy(ranges = body.ranges + RichTextRange(style, start, start + text.length, data)).normalized()
        }
        bodyValue = TextFieldValue(annotatedBody(body), TextRange(start + text.length))
        commit(document.copy(body = body))
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            insertText("🖼 Image", RichTextStyle.Image, uri.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(backIcon, contentDescription = "Back")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (saving) MaterialTheme.colorScheme.primary else Color(0xFF63D39B), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (saving) "Saving…" else "Saved locally",
                    color = SlateTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            renameRequest += 1
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (document.isFavorite) "Remove favorite" else "Favorite") },
                        onClick = { menuExpanded = false; onToggleFavorite(document) },
                    )
                    DropdownMenuItem(
                        text = { Text("Folder & tags") },
                        onClick = { menuExpanded = false; showOrganize = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Version history") },
                        onClick = { menuExpanded = false; onLoadHistory(document.id); showHistory = true },
                    )
                    DropdownMenuItem(text = { Text("Export text") }, onClick = { menuExpanded = false; onExport(ExportFormat.Text) })
                    DropdownMenuItem(text = { Text("Export Markdown") }, onClick = { menuExpanded = false; onExport(ExportFormat.Markdown) })
                    DropdownMenuItem(text = { Text("Export DOCX") }, onClick = { menuExpanded = false; onExport(ExportFormat.Docx) })
                    DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menuExpanded = false; onExport(ExportFormat.Pdf) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; AndroidDocumentActions.share(context, document) })
                    DropdownMenuItem(text = { Text("Print") }, onClick = { menuExpanded = false; AndroidDocumentActions.print(context, document) })
                    DropdownMenuItem(text = { Text("Check for updates") }, onClick = { menuExpanded = false; onCheckForUpdates() })
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuExpanded = false
                            onDuplicate(document)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (document.isPinned) "Unpin" else "Pin") },
                        onClick = {
                            menuExpanded = false
                            onTogglePin(document)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (document.isArchived) "Restore" else "Archive") },
                        onClick = {
                            menuExpanded = false
                            onArchive(document)
                        },
                    )
                    if (document.isDeleted) {
                        DropdownMenuItem(text = { Text("Restore from trash") }, onClick = { menuExpanded = false; onRestore(document) })
                        DropdownMenuItem(text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onPermanentlyDelete(document) })
                    } else {
                        DropdownMenuItem(text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; confirmDelete = true })
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                BasicTextField(
                    value = document.title,
                    onValueChange = { commit(document.copy(title = it)) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CoreBlue),
                    decorationBox = { field ->
                        Box {
                            if (document.title.isBlank()) Text("Untitled", color = SlateTextMuted, style = MaterialTheme.typography.headlineMedium)
                            field()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                )

                HorizontalDivider(
                    color = SlateBorder,
                    modifier = Modifier.padding(top = 14.dp),
                )

                BasicTextField(
                    value = bodyValue,
                    onValueChange = { changed ->
                        val body = document.body.updateText(changed.text)
                        bodyValue = TextFieldValue(annotatedBody(body), changed.selection)
                        commit(document.copy(body = body))
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CoreBlue),
                    decorationBox = { field ->
                        Box(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                            if (document.body.text.isBlank()) Text("Start writing…", color = SlateTextMuted, fontSize = 18.sp)
                            field()
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.B -> { applyStyle(RichTextStyle.Bold); true }
                            Key.I -> { applyStyle(RichTextStyle.Italic); true }
                            Key.U -> { applyStyle(RichTextStyle.Underline); true }
                            Key.F -> { showFind = true; true }
                            Key.Z -> {
                                if (undoStack.isNotEmpty()) {
                                    val previous = undoStack.removeAt(undoStack.lastIndex)
                                    redoStack += EditorSnapshot(document.title, document.body)
                                    onDocumentChange(document.copy(title = previous.title, body = previous.body))
                                }
                                true
                            }
                            Key.Y -> {
                                if (redoStack.isNotEmpty()) {
                                    val next = redoStack.removeAt(redoStack.lastIndex)
                                    undoStack += EditorSnapshot(document.title, document.body)
                                    onDocumentChange(document.copy(title = next.title, body = next.body))
                                }
                                true
                            }
                            else -> false
                        }
                    },
                )
                document.body.ranges.filter { it.style == RichTextStyle.Image && it.data != null }.take(1).forEach { range ->
                    ImagePreview(range.data!!)
                }

                Surface(
                    color = SlateSurfaceSoft,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        FormattingButton("B", active = document.body.hasStyle(RichTextStyle.Bold, bodyValue.selection.start, bodyValue.selection.end)) { applyStyle(RichTextStyle.Bold) }
                        FormattingButton("I", active = document.body.hasStyle(RichTextStyle.Italic, bodyValue.selection.start, bodyValue.selection.end)) { applyStyle(RichTextStyle.Italic) }
                        FormattingButton("U", active = document.body.hasStyle(RichTextStyle.Underline, bodyValue.selection.start, bodyValue.selection.end)) { applyStyle(RichTextStyle.Underline) }
                        FormattingButton("H1", active = document.body.hasStyle(RichTextStyle.HeadingOne, bodyValue.selection.start, bodyValue.selection.end)) { applyStyle(RichTextStyle.HeadingOne, blockStyle = true) }
                        FormattingButton("List") { applyPrefix("• ") }
                        FormattingButton("Check") { applyPrefix("☐ ") }
                        FormattingButton("Quote") { applyStyle(RichTextStyle.Quote, blockStyle = true) }
                        FormattingButton("Link") { showLink = true }
                        FormattingButton("Image") { imageLauncher.launch(arrayOf("image/*")) }
                        FormattingButton("Table") { insertText("| Column 1 | Column 2 |\n| --- | --- |\n| Value | Value |", RichTextStyle.Table) }
                        FormattingButton("Find") { showFind = true }
                        FormattingButton("↶", enabled = undoStack.isNotEmpty()) {
                            val previous = undoStack.removeAt(undoStack.lastIndex)
                            redoStack += EditorSnapshot(document.title, document.body)
                            onDocumentChange(document.copy(title = previous.title, body = previous.body, updatedLabel = "Just now"))
                        }
                        FormattingButton("↷", enabled = redoStack.isNotEmpty()) {
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            undoStack += EditorSnapshot(document.title, document.body)
                            onDocumentChange(document.copy(title = next.title, body = next.body, updatedLabel = "Just now"))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete document?") },
            text = { Text("The document will move to Trash and can be restored later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(document)
                    },
                ) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (showOrganize) {
        AlertDialog(
            onDismissRequest = { showOrganize = false },
            title = { Text("Folder and tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicTextField(folderDraft, { folderDraft = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface), decorationBox = { field -> FieldShell("Folder", folderDraft, field) })
                    BasicTextField(tagsDraft, { tagsDraft = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface), decorationBox = { field -> FieldShell("Tags, separated by commas", tagsDraft, field) })
                }
            },
            confirmButton = { TextButton(onClick = { onOrganize(document, folderDraft, tagsDraft.split(',').map(String::trim).filter(String::isNotEmpty).toSet()); showOrganize = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showOrganize = false }) { Text("Cancel") } },
        )
    }

    if (showLink) {
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text("Insert link") },
            text = { BasicTextField(linkDraft, { linkDraft = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface), decorationBox = { field -> FieldShell("https://example.com", linkDraft, field) }) },
            confirmButton = { TextButton(onClick = {
                val selection = selectionOrWordRange(document.body.text, bodyValue.selection)
                val label = document.body.text.substring(selection.min, selection.max).ifBlank { linkDraft }
                bodyValue = bodyValue.copy(selection = selection)
                insertText(label, RichTextStyle.Link, linkDraft)
                showLink = false
            }) { Text("Insert") } },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancel") } },
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find in document") },
            text = { BasicTextField(findDraft, { findDraft = it }, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface), decorationBox = { field -> FieldShell("Search", findDraft, field) }) },
            confirmButton = { TextButton(onClick = {
                val start = document.body.text.indexOf(findDraft, bodyValue.selection.max.coerceAtMost(document.body.text.length), ignoreCase = true)
                    .takeIf { it >= 0 } ?: document.body.text.indexOf(findDraft, ignoreCase = true)
                if (start >= 0 && findDraft.isNotEmpty()) bodyValue = bodyValue.copy(selection = TextRange(start, start + findDraft.length))
                else Toast.makeText(context, "No match", Toast.LENGTH_SHORT).show()
            }) { Text("Find next") } },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("Close") } },
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Version history") },
            text = {
                LazyColumn {
                    if (history.isEmpty()) item { Text("Loading history…", color = SlateTextMuted) }
                    items(history.take(30), key = { it.updatedAtEpochMillis }) { version ->
                        TextButton(onClick = { onRestoreVersion(version); showHistory = false }) {
                            Text("Restore ${version.updatedLabel} · ${version.body.text.take(48)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ImagePreview(uri: String) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Document image",
            modifier = Modifier.fillMaxWidth().heightIn(max = 112.dp).clip(RoundedCornerShape(14.dp)),
        )
    }
}

@Composable
private fun FieldShell(placeholder: String, value: String, field: @Composable () -> Unit) {
    Surface(
        color = SlateSurfaceSoft,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SlateBorder),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
            if (value.isBlank()) Text(placeholder, color = SlateTextMuted)
            field()
        }
    }
}

@Composable
private fun FormattingButton(
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer else SlateSurfaceRaised,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else SlateTextMuted.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else SlateBorder,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        )
    }
}

private fun annotatedBody(body: RichTextDocument): AnnotatedString = AnnotatedString.Builder(body.text).apply {
    body.normalized().ranges.forEach { range ->
        val style = when (range.style) {
            RichTextStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            RichTextStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            RichTextStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
            RichTextStyle.HeadingOne -> SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)
            RichTextStyle.Link -> SpanStyle(color = CoreBlue, textDecoration = TextDecoration.Underline)
            RichTextStyle.Quote -> SpanStyle(fontStyle = FontStyle.Italic, color = SlateTextMuted)
            RichTextStyle.Image -> SpanStyle(color = CoreBlue, fontWeight = FontWeight.SemiBold)
            RichTextStyle.Table -> SpanStyle(fontWeight = FontWeight.Medium)
        }
        addStyle(style, range.start, range.end)
    }
}.toAnnotatedString()

private fun TextRange.coerceIn(minimum: Int, maximum: Int): TextRange = TextRange(
    start.coerceIn(minimum, maximum),
    end.coerceIn(minimum, maximum),
)

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

private fun paragraphRange(text: String, selection: TextRange): TextRange {
    if (text.isEmpty()) return TextRange.Zero
    val safe = selection.coerceIn(0, text.length)
    val searchStart = (safe.min - 1).coerceAtLeast(0)
    val start = text.lastIndexOf('\n', searchStart).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', safe.max).let { if (it < 0) text.length else it }
    return TextRange(start, end)
}

private fun toggleLinePrefix(
    body: RichTextDocument,
    selection: TextRange,
    prefix: String,
): Pair<RichTextDocument, TextRange> {
    val range = paragraphRange(body.text, selection)
    if (range.start == range.end && body.text.isEmpty()) return body to selection

    val original = body.text.substring(range.start, range.end)
    val lines = original.split('\n')
    val removePrefix = lines.all { it.startsWith(prefix) }
    val replacement = lines.joinToString("\n") { line ->
        if (removePrefix) line.removePrefix(prefix) else prefix + line
    }
    val updatedText = body.text.replaceRange(range.start, range.end, replacement)
    val updatedBody = body.updateText(updatedText)
    return updatedBody to TextRange(range.start, range.start + replacement.length)
}

private val backIcon: ImageVector = ImageVector.Builder(
    name = "Back",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.42f, 18.59f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        close()
    }
}.build()
