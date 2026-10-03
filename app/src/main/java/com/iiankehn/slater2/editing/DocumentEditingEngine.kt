package com.iiankehn.slater2.editing

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentBlock
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.ListStyle
import com.iiankehn.slater2.model.PageSetup
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.TableCell
import com.iiankehn.slater2.model.TableRow
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.model.ImageWrapping
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.ParagraphStyle
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import java.util.UUID

/** A stable logical caret location. Offsets are UTF-16 indices, matching Compose text APIs. */
data class DocumentPosition(
    val sectionIndex: Int,
    val blockIndex: Int,
    val offset: Int,
) : Comparable<DocumentPosition> {
    init {
        require(sectionIndex >= 0 && blockIndex >= 0 && offset >= 0) {
            "Document positions cannot contain negative indices."
        }
    }

    override fun compareTo(other: DocumentPosition): Int =
        compareValuesBy(this, other, DocumentPosition::sectionIndex, DocumentPosition::blockIndex, DocumentPosition::offset)
}

data class DocumentSelection(
    val anchor: DocumentPosition,
    val focus: DocumentPosition = anchor,
) {
    val start: DocumentPosition get() = minOf(anchor, focus)
    val end: DocumentPosition get() = maxOf(anchor, focus)
    val isCollapsed: Boolean get() = anchor == focus

    fun collapsedAt(position: DocumentPosition) = DocumentSelection(position)
}

enum class CharacterFormat { Bold, Italic, Underline, StrikeThrough }

sealed interface DocumentCommand {
    data class ReplaceSelection(val text: String) : DocumentCommand
    data object DeleteBackward : DocumentCommand
    data object DeleteForward : DocumentCommand
    data class ToggleCharacterFormat(val format: CharacterFormat) : DocumentCommand
    data class ApplyCharacterStyle(val style: CharacterStyle) : DocumentCommand
    data class ApplyParagraphStyle(val style: ParagraphStyle) : DocumentCommand
    data class ApplyNamedStyle(val style: NamedParagraphStyle) : DocumentCommand
    data class ToggleList(val kind: ListKind) : DocumentCommand
    data class AdjustListLevel(val delta: Int) : DocumentCommand
    data class HandleTab(val outdent: Boolean) : DocumentCommand
    data class UpdatePageSetup(val page: PageSetup) : DocumentCommand
    data object InsertPageBreak : DocumentCommand
    data object InsertSectionBreak : DocumentCommand
    data class UpdateHeaderFooter(val headerText: String, val footerText: String) : DocumentCommand
    data class InsertTable(val rows: Int = 2, val columns: Int = 2) : DocumentCommand
    data class InsertImage(val sourceUri: String, val description: String = "") : DocumentCommand
    data class UpdateTableCell(val tableId: String, val row: Int, val column: Int, val text: String) : DocumentCommand
    data class ResizeTable(val tableId: String, val rows: Int, val columns: Int) : DocumentCommand
    data class UpdateImage(
        val imageId: String,
        val description: String,
        val widthPoints: Float?,
        val heightPoints: Float?,
        val wrapping: ImageWrapping,
    ) : DocumentCommand
    data class DeleteObject(val objectId: String) : DocumentCommand
}

data class EditorSnapshot(
    val document: WordProcessingDocument,
    val selection: DocumentSelection,
    val selectedObjectId: String? = null,
)

data class EditResult(
    val snapshot: EditorSnapshot,
    val changed: Boolean,
)

/**
 * Immutable editing operations for the R2 document model.
 *
 * Text replacement spans any number of paragraphs in one section. Character commands preserve
 * unrelated runs and coalesce adjacent runs with identical formatting. Paragraph commands apply
 * across every selected paragraph, including selections that cross sections.
 */
class DocumentEditingEngine(
    private val paragraphIdFactory: () -> String = { "paragraph-${UUID.randomUUID()}" },
) {
    fun execute(snapshot: EditorSnapshot, command: DocumentCommand): EditResult {
        snapshot.document.requireValid(snapshot)
        val updated = when (command) {
            is DocumentCommand.ReplaceSelection -> replaceSelection(snapshot, command.text)
            DocumentCommand.DeleteBackward -> deleteBackward(snapshot)
            DocumentCommand.DeleteForward -> deleteForward(snapshot)
            is DocumentCommand.ToggleCharacterFormat -> toggleCharacterFormat(snapshot, command.format)
            is DocumentCommand.ApplyCharacterStyle -> applyCharacterStyle(snapshot, command.style)
            is DocumentCommand.ApplyParagraphStyle -> applyParagraphStyle(snapshot, command.style)
            is DocumentCommand.ApplyNamedStyle -> applyParagraphStyle(snapshot) {
                it.copy(namedStyle = command.style)
            }
            is DocumentCommand.ToggleList -> applyParagraphStyle(snapshot) {
                it.copy(list = if (it.list?.kind == command.kind) null else ListStyle(command.kind, it.list?.level ?: 0))
            }
            is DocumentCommand.AdjustListLevel -> adjustListLevel(snapshot, command.delta)
            is DocumentCommand.HandleTab -> handleTab(snapshot, command.outdent)
            is DocumentCommand.UpdatePageSetup -> updatePageSetup(snapshot, command.page)
            DocumentCommand.InsertPageBreak -> insertPageBreak(snapshot)
            DocumentCommand.InsertSectionBreak -> insertSectionBreak(snapshot)
            is DocumentCommand.UpdateHeaderFooter -> updateHeaderFooter(snapshot, command.headerText, command.footerText)
            is DocumentCommand.InsertTable -> insertBlock(snapshot, TableBlock(
                id = "table-${UUID.randomUUID()}",
                rows = List(command.rows.coerceIn(1, 100)) { TableRow(List(command.columns.coerceIn(1, 20)) { TableCell() }) },
            ))
            is DocumentCommand.InsertImage -> insertBlock(snapshot, ImageBlock(
                id = "image-${UUID.randomUUID()}", sourceUri = command.sourceUri, description = command.description,
            ))
            is DocumentCommand.UpdateTableCell -> updateTableCell(snapshot, command)
            is DocumentCommand.ResizeTable -> resizeTable(snapshot, command)
            is DocumentCommand.UpdateImage -> updateImage(snapshot, command)
            is DocumentCommand.DeleteObject -> deleteObject(snapshot, command.objectId)
        }
        return EditResult(updated, updated != snapshot)
    }

    private fun updatePageSetup(snapshot: EditorSnapshot, page: PageSetup): EditorSnapshot {
        val sectionIndex = snapshot.selection.focus.sectionIndex
        val sections = snapshot.document.sections.mapIndexed { index, section -> if (index == sectionIndex) section.copy(page = page) else section }
        return snapshot.copy(document = snapshot.document.copy(sections = sections).touch())
    }

    private fun insertPageBreak(snapshot: EditorSnapshot): EditorSnapshot {
        val inserted = replaceSelection(snapshot, "\n")
        return applyParagraphStyle(inserted) { it.copy(pageBreakBefore = true) }
    }

    private fun insertSectionBreak(snapshot: EditorSnapshot): EditorSnapshot {
        val working = if (snapshot.selection.isCollapsed) snapshot else replaceSelection(snapshot, "")
        val position = working.selection.focus
        val section = working.document.sections[position.sectionIndex]
        val paragraph = section.blocks[position.blockIndex] as ParagraphBlock
        val leading = paragraph.copy(runs = paragraph.runs.slice(0, position.offset).normalizedRuns())
        val trailing = paragraph.copy(id = paragraphIdFactory(), runs = paragraph.runs.slice(position.offset, paragraph.textLength).normalizedRuns())
        val first = section.copy(blocks = section.blocks.take(position.blockIndex) + leading)
        val second = section.copy(
            blocks = listOf(trailing) + section.blocks.drop(position.blockIndex + 1),
            startsOnNewPage = true,
        )
        val sections = buildList {
            addAll(working.document.sections.take(position.sectionIndex))
            add(first)
            add(second)
            addAll(working.document.sections.drop(position.sectionIndex + 1))
        }
        return EditorSnapshot(
            document = working.document.copy(sections = sections).touch(),
            selection = DocumentSelection(DocumentPosition(position.sectionIndex + 1, 0, 0)),
        )
    }

    private fun updateHeaderFooter(snapshot: EditorSnapshot, headerText: String, footerText: String): EditorSnapshot {
        val index = snapshot.selection.focus.sectionIndex
        val section = snapshot.document.sections[index]
        fun marginParagraphs(text: String, existing: List<ParagraphBlock>, prefix: String): List<ParagraphBlock> =
            if (text.isEmpty()) emptyList() else text.split('\n').mapIndexed { paragraphIndex, line ->
                val previous = existing.getOrNull(paragraphIndex)
                ParagraphBlock(
                    id = previous?.id ?: "$prefix-${UUID.randomUUID()}",
                    runs = listOf(TextRun(line, previous?.runs?.firstOrNull()?.style ?: CharacterStyle(fontSizePoints = 9f))),
                    style = previous?.style ?: ParagraphStyle(spaceAfterPoints = 0f),
                )
            }
        val updated = section.copy(
            header = marginParagraphs(headerText, section.header, "header"),
            footer = marginParagraphs(footerText, section.footer, "footer"),
        )
        if (updated == section) return snapshot
        return snapshot.copy(document = snapshot.document.replaceSection(index, updated))
    }

    private fun adjustListLevel(snapshot: EditorSnapshot, delta: Int): EditorSnapshot {
        if (delta == 0) return snapshot
        val canChange = snapshot.document.sections.withIndex().any { (sectionIndex, section) ->
            section.blocks.withIndex().any blockLoop@ { (blockIndex, block) ->
                val level = (block as? ParagraphBlock)?.style?.list?.level ?: return@blockLoop false
                snapshot.selection.includes(sectionIndex, blockIndex) && (level + delta).coerceIn(0, 8) != level
            }
        }
        if (!canChange) return snapshot
        return applyParagraphStyle(snapshot) { style ->
            style.list?.let { list -> style.copy(list = list.copy(level = (list.level + delta).coerceIn(0, 8))) } ?: style
        }
    }

    private fun handleTab(snapshot: EditorSnapshot, outdent: Boolean): EditorSnapshot {
        val hasSelectedList = snapshot.document.sections.withIndex().any { (sectionIndex, section) ->
            section.blocks.withIndex().any { (blockIndex, block) ->
                block is ParagraphBlock && snapshot.selection.includes(sectionIndex, blockIndex) && block.style.list != null
            }
        }
        return when {
            hasSelectedList -> adjustListLevel(snapshot, if (outdent) -1 else 1)
            outdent -> snapshot
            else -> replaceSelection(snapshot, "\t")
        }
    }

    private fun insertBlock(snapshot: EditorSnapshot, block: DocumentBlock): EditorSnapshot {
        val position = snapshot.selection.focus
        val section = snapshot.document.sections[position.sectionIndex]
        val trailingParagraph = ParagraphBlock(id = paragraphIdFactory())
        val insertionIndex = position.blockIndex + 1
        val blocks = section.blocks.toMutableList().apply {
            add(insertionIndex, block)
            add(insertionIndex + 1, trailingParagraph)
        }
        return EditorSnapshot(
            snapshot.document.replaceSection(position.sectionIndex, section.copy(blocks = blocks)),
            DocumentSelection(DocumentPosition(position.sectionIndex, insertionIndex + 1, 0)),
            selectedObjectId = block.id,
        )
    }

    private fun updateTableCell(
        snapshot: EditorSnapshot,
        command: DocumentCommand.UpdateTableCell,
    ): EditorSnapshot = snapshot.updateObject(command.tableId) { block ->
        val table = block as? TableBlock ?: return@updateObject block
        if (command.row !in table.rows.indices || command.column !in table.rows[command.row].cells.indices) return@updateObject block
        table.copy(rows = table.rows.mapIndexed { rowIndex, row ->
            if (rowIndex != command.row) row else row.copy(cells = row.cells.mapIndexed { columnIndex, cell ->
                if (columnIndex != command.column) cell else {
                    val paragraph = cell.blocks.first()
                    val style = paragraph.runs.first().style
                    cell.copy(blocks = command.text.split('\n').mapIndexed { index, text ->
                        paragraph.copy(
                            id = if (index == 0) paragraph.id else paragraphIdFactory(),
                            runs = listOf(TextRun(text, style)),
                        )
                    })
                }
            })
        })
    }

    private fun resizeTable(
        snapshot: EditorSnapshot,
        command: DocumentCommand.ResizeTable,
    ): EditorSnapshot = snapshot.updateObject(command.tableId) { block ->
        val table = block as? TableBlock ?: return@updateObject block
        val rows = command.rows.coerceIn(1, 100)
        val columns = command.columns.coerceIn(1, 20)
        table.copy(
            rows = List(rows) { rowIndex ->
                TableRow(List(columns) { columnIndex ->
                    table.rows.getOrNull(rowIndex)?.cells?.getOrNull(columnIndex) ?: TableCell()
                })
            },
            headerRowCount = table.headerRowCount.coerceAtMost(rows),
        )
    }

    private fun updateImage(
        snapshot: EditorSnapshot,
        command: DocumentCommand.UpdateImage,
    ): EditorSnapshot = snapshot.updateObject(command.imageId) { block ->
        val image = block as? ImageBlock ?: return@updateObject block
        image.copy(
            description = command.description,
            widthPoints = command.widthPoints?.coerceIn(24f, 1200f),
            heightPoints = command.heightPoints?.coerceIn(24f, 1200f),
            wrapping = command.wrapping,
        )
    }

    private fun deleteObject(snapshot: EditorSnapshot, objectId: String): EditorSnapshot {
        var removed = false
        val sections = snapshot.document.sections.map { section ->
            val filtered = section.blocks.filterNot {
                (it.id == objectId && it !is ParagraphBlock).also { match -> removed = removed || match }
            }
            section.copy(blocks = filtered.ifEmpty { listOf(ParagraphBlock(id = paragraphIdFactory())) })
        }
        return if (!removed) snapshot else snapshot.copy(
            document = snapshot.document.copy(sections = sections).touch(),
            selectedObjectId = snapshot.selectedObjectId.takeUnless { it == objectId },
        )
    }

    private fun replaceSelection(snapshot: EditorSnapshot, insertedText: String): EditorSnapshot {
        val selection = snapshot.selection
        require(selection.start.sectionIndex == selection.end.sectionIndex) {
            "Text replacement across section boundaries is not implemented yet."
        }
        val sectionIndex = selection.start.sectionIndex
        val section = snapshot.document.sections[sectionIndex]
        val startBlock = section.blocks[selection.start.blockIndex] as ParagraphBlock
        val endBlock = section.blocks[selection.end.blockIndex] as ParagraphBlock
        val prefix = startBlock.runs.slice(0, selection.start.offset)
        val suffix = endBlock.runs.slice(selection.end.offset, endBlock.textLength)
        val insertionStyle = prefix.lastOrNull()?.style
            ?: startBlock.runs.styleAt(selection.start.offset)
            ?: CharacterStyle()
        val lines = insertedText.split('\n')
        val replacement = lines.mapIndexed { index, line ->
            val runs = buildList {
                if (index == 0) addAll(prefix)
                if (line.isNotEmpty()) add(TextRun(line, insertionStyle))
                if (index == lines.lastIndex) addAll(suffix)
            }.normalizedRuns()
            ParagraphBlock(
                id = if (index == 0) startBlock.id else paragraphIdFactory(),
                runs = runs,
                style = if (index == lines.lastIndex) endBlock.style else startBlock.style,
            )
        }
        val updatedBlocks = buildList {
            addAll(section.blocks.take(selection.start.blockIndex))
            addAll(replacement)
            addAll(section.blocks.drop(selection.end.blockIndex + 1))
        }
        val caret = DocumentPosition(
            sectionIndex = sectionIndex,
            blockIndex = selection.start.blockIndex + lines.lastIndex,
            offset = if (lines.size == 1) selection.start.offset + insertedText.length else lines.last().length,
        )
        return EditorSnapshot(
            document = snapshot.document.replaceSection(sectionIndex, section.copy(blocks = updatedBlocks)),
            selection = DocumentSelection(caret),
        )
    }

    private fun deleteBackward(snapshot: EditorSnapshot): EditorSnapshot {
        if (!snapshot.selection.isCollapsed) return replaceSelection(snapshot, "")
        val position = snapshot.selection.focus
        if (position.offset > 0) {
            return replaceSelection(
                snapshot.copy(
                    selection = DocumentSelection(
                        position.copy(offset = position.offset - 1),
                        position,
                    ),
                ),
                "",
            )
        }
        val section = snapshot.document.sections[position.sectionIndex]
        val previousIndex = section.blocks.indexOfPreviousParagraph(position.blockIndex)
            ?: return if (position.sectionIndex > 0) joinAdjacentSections(snapshot, position.sectionIndex - 1) else snapshot
        val previous = section.blocks[previousIndex] as ParagraphBlock
        return replaceSelection(
            snapshot.copy(
                selection = DocumentSelection(
                    DocumentPosition(position.sectionIndex, previousIndex, previous.textLength),
                    position,
                ),
            ),
            "",
        )
    }

    private fun deleteForward(snapshot: EditorSnapshot): EditorSnapshot {
        if (!snapshot.selection.isCollapsed) return replaceSelection(snapshot, "")
        val position = snapshot.selection.focus
        val section = snapshot.document.sections[position.sectionIndex]
        val paragraph = section.blocks[position.blockIndex] as ParagraphBlock
        if (position.offset < paragraph.textLength) {
            return replaceSelection(
                snapshot.copy(selection = DocumentSelection(position, position.copy(offset = position.offset + 1))),
                "",
            )
        }
        val nextIndex = section.blocks.indexOfNextParagraph(position.blockIndex)
            ?: return if (position.sectionIndex < snapshot.document.sections.lastIndex) joinAdjacentSections(snapshot, position.sectionIndex) else snapshot
        return replaceSelection(
            snapshot.copy(
                selection = DocumentSelection(
                    position,
                    DocumentPosition(position.sectionIndex, nextIndex, 0),
                ),
            ),
            "",
        )
    }

    private fun joinAdjacentSections(snapshot: EditorSnapshot, leftIndex: Int): EditorSnapshot {
        if (leftIndex !in 0 until snapshot.document.sections.lastIndex) return snapshot
        val left = snapshot.document.sections[leftIndex]
        val right = snapshot.document.sections[leftIndex + 1]
        val leftParagraphIndex = left.blocks.indexOfLast { it is ParagraphBlock }
        val rightParagraphIndex = right.blocks.indexOfFirst { it is ParagraphBlock }
        if (leftParagraphIndex != left.blocks.lastIndex || rightParagraphIndex != 0) return snapshot
        val leftParagraph = left.blocks[leftParagraphIndex] as ParagraphBlock
        val rightParagraph = right.blocks[rightParagraphIndex] as ParagraphBlock
        val mergedParagraph = leftParagraph.copy(runs = (leftParagraph.runs + rightParagraph.runs).normalizedRuns())
        val combined = right.copy(
            blocks = left.blocks.dropLast(1) + mergedParagraph + right.blocks.drop(1),
            startsOnNewPage = left.startsOnNewPage,
        )
        val sections = buildList {
            addAll(snapshot.document.sections.take(leftIndex))
            add(combined)
            addAll(snapshot.document.sections.drop(leftIndex + 2))
        }
        return EditorSnapshot(
            document = snapshot.document.copy(sections = sections).touch(),
            selection = DocumentSelection(DocumentPosition(leftIndex, leftParagraphIndex, leftParagraph.textLength)),
        )
    }

    private fun toggleCharacterFormat(snapshot: EditorSnapshot, format: CharacterFormat): EditorSnapshot {
        if (snapshot.selection.isCollapsed) return snapshot
        val selectedRuns = snapshot.document.selectedRunSegments(snapshot.selection)
        if (selectedRuns.isEmpty()) return snapshot
        val enable = selectedRuns.any { !it.style.formatEnabled(format) }
        return applyCharacterStyle(snapshot) { style -> style.withFormat(format, enable) }
    }

    private fun applyCharacterStyle(snapshot: EditorSnapshot, style: CharacterStyle): EditorSnapshot =
        applyCharacterStyle(snapshot) { style }

    private fun applyCharacterStyle(
        snapshot: EditorSnapshot,
        transform: (CharacterStyle) -> CharacterStyle,
    ): EditorSnapshot {
        if (snapshot.selection.isCollapsed) return snapshot
        val start = snapshot.selection.start
        val end = snapshot.selection.end
        val sections = snapshot.document.sections.mapIndexed sectionLoop@ { sectionIndex, section ->
            if (sectionIndex !in start.sectionIndex..end.sectionIndex) return@sectionLoop section
            section.copy(
                blocks = section.blocks.mapIndexed blockLoop@ { blockIndex, block ->
                    if (block !is ParagraphBlock || !snapshot.selection.includes(sectionIndex, blockIndex)) return@blockLoop block
                    val from = if (sectionIndex == start.sectionIndex && blockIndex == start.blockIndex) start.offset else 0
                    val to = if (sectionIndex == end.sectionIndex && blockIndex == end.blockIndex) end.offset else block.textLength
                    block.copy(runs = block.runs.transformRange(from, to, transform))
                },
            )
        }
        return snapshot.copy(document = snapshot.document.copy(sections = sections).touch())
    }

    private fun applyParagraphStyle(snapshot: EditorSnapshot, style: ParagraphStyle): EditorSnapshot =
        applyParagraphStyle(snapshot) { style }

    private fun applyParagraphStyle(
        snapshot: EditorSnapshot,
        transform: (ParagraphStyle) -> ParagraphStyle,
    ): EditorSnapshot {
        val start = snapshot.selection.start
        val end = snapshot.selection.end
        val sections = snapshot.document.sections.mapIndexed sectionLoop@ { sectionIndex, section ->
            if (sectionIndex !in start.sectionIndex..end.sectionIndex) return@sectionLoop section
            section.copy(
                blocks = section.blocks.mapIndexed { blockIndex, block ->
                    if (block is ParagraphBlock && snapshot.selection.includes(sectionIndex, blockIndex)) {
                        block.copy(style = transform(block.style))
                    } else {
                        block
                    }
                },
            )
        }
        return snapshot.copy(document = snapshot.document.copy(sections = sections).touch())
    }
}

private fun EditorSnapshot.updateObject(
    objectId: String,
    transform: (DocumentBlock) -> DocumentBlock,
): EditorSnapshot {
    var changed = false
    val sections = document.sections.map { section ->
        section.copy(blocks = section.blocks.map { block ->
            if (block.id != objectId) block else transform(block).also { changed = changed || it != block }
        })
    }
    return if (!changed) this else copy(document = document.copy(sections = sections).touch())
}

/** Mutable session boundary; each command or transaction is a single durable undo step. */
class DocumentEditorSession(
    initial: EditorSnapshot,
    private val engine: DocumentEditingEngine = DocumentEditingEngine(),
    private val historyLimit: Int = 100,
) {
    init {
        require(historyLimit > 0) { "History must retain at least one edit." }
        initial.document.requireValid(initial)
    }

    var current: EditorSnapshot = initial
        private set

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(command: DocumentCommand): EditResult = transaction(listOf(command))

    fun transaction(commands: List<DocumentCommand>): EditResult {
        require(commands.isNotEmpty()) { "An edit transaction needs at least one command." }
        val before = current
        var working = before
        commands.forEach { command -> working = engine.execute(working, command).snapshot }
        if (working == before) return EditResult(current, changed = false)
        undoStack.addLast(before)
        while (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        current = working
        return EditResult(current, changed = true)
    }

    fun updateSelection(selection: DocumentSelection) {
        current.document.requireValid(current.copy(selection = selection, selectedObjectId = null))
        current = current.copy(selection = selection, selectedObjectId = null)
    }

    /** Object focus is transient editor state and deliberately does not create an undo step. */
    fun selectObject(objectId: String?) {
        val updated = current.copy(selectedObjectId = objectId)
        current.document.requireValid(updated)
        current = updated
    }

    fun undo(): EditorSnapshot {
        if (undoStack.isEmpty()) return current
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): EditorSnapshot {
        if (redoStack.isEmpty()) return current
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }
}

private fun WordProcessingDocument.requireValid(snapshot: EditorSnapshot) {
    val selection = snapshot.selection
    listOf(selection.anchor, selection.focus).forEach { position ->
        require(position.sectionIndex in sections.indices) { "Selection section does not exist." }
        val section = sections[position.sectionIndex]
        require(position.blockIndex in section.blocks.indices) { "Selection block does not exist." }
        val paragraph = section.blocks[position.blockIndex] as? ParagraphBlock
            ?: error("Text selections must resolve to paragraphs.")
        require(position.offset <= paragraph.textLength) { "Selection offset exceeds paragraph text." }
    }
    snapshot.selectedObjectId?.let { objectId ->
        require(sections.any { section -> section.blocks.any { it.id == objectId && it !is ParagraphBlock } }) {
            "Selected object does not exist."
        }
    }
}

private fun WordProcessingDocument.replaceSection(index: Int, section: DocumentSection): WordProcessingDocument =
    copy(sections = sections.mapIndexed { sectionIndex, current -> if (sectionIndex == index) section else current }).touch()

private fun WordProcessingDocument.touch(): WordProcessingDocument = copy(
    metadata = metadata.copy(updatedAtEpochMillis = maxOf(System.currentTimeMillis(), metadata.updatedAtEpochMillis + 1)),
)

private val ParagraphBlock.textLength: Int get() = runs.sumOf { it.text.length }

private fun List<DocumentBlock>.indexOfPreviousParagraph(before: Int): Int? =
    (before - 1 downTo 0).firstOrNull { this[it] is ParagraphBlock }

private fun List<DocumentBlock>.indexOfNextParagraph(after: Int): Int? =
    (after + 1..lastIndex).firstOrNull { this[it] is ParagraphBlock }

private fun DocumentSelection.includes(sectionIndex: Int, blockIndex: Int): Boolean {
    val blockStart = DocumentPosition(sectionIndex, blockIndex, 0)
    val blockEnd = DocumentPosition(sectionIndex, blockIndex, Int.MAX_VALUE)
    return blockEnd >= start && blockStart <= end
}

private fun WordProcessingDocument.selectedRunSegments(selection: DocumentSelection): List<TextRun> = buildList {
    sections.forEachIndexed { sectionIndex, section ->
        section.blocks.forEachIndexed { blockIndex, block ->
            if (block is ParagraphBlock && selection.includes(sectionIndex, blockIndex)) {
                val from = if (sectionIndex == selection.start.sectionIndex && blockIndex == selection.start.blockIndex) selection.start.offset else 0
                val to = if (sectionIndex == selection.end.sectionIndex && blockIndex == selection.end.blockIndex) selection.end.offset else block.textLength
                addAll(block.runs.slice(from, to))
            }
        }
    }
}

private fun List<TextRun>.styleAt(offset: Int): CharacterStyle? {
    var cursor = 0
    forEach { run ->
        val end = cursor + run.text.length
        if (offset in cursor..end) return run.style
        cursor = end
    }
    return lastOrNull()?.style
}

private fun List<TextRun>.slice(from: Int, to: Int): List<TextRun> {
    if (from >= to) return emptyList()
    var cursor = 0
    return mapNotNull { run ->
        val runStart = cursor
        val runEnd = cursor + run.text.length
        cursor = runEnd
        val overlapStart = maxOf(from, runStart)
        val overlapEnd = minOf(to, runEnd)
        if (overlapStart >= overlapEnd) null
        else run.copy(text = run.text.substring(overlapStart - runStart, overlapEnd - runStart))
    }.normalizedRuns()
}

private fun List<TextRun>.transformRange(
    from: Int,
    to: Int,
    transform: (CharacterStyle) -> CharacterStyle,
): List<TextRun> {
    if (from >= to) return this
    var cursor = 0
    return buildList {
        this@transformRange.forEach { run ->
            val runStart = cursor
            val runEnd = cursor + run.text.length
            cursor = runEnd
            if (runEnd <= from || runStart >= to) {
                add(run)
                return@forEach
            }
            val selectedStart = maxOf(from, runStart)
            val selectedEnd = minOf(to, runEnd)
            if (runStart < selectedStart) add(run.copy(text = run.text.substring(0, selectedStart - runStart)))
            if (selectedStart < selectedEnd) add(run.copy(text = run.text.substring(selectedStart - runStart, selectedEnd - runStart), style = transform(run.style)))
            if (selectedEnd < runEnd) add(run.copy(text = run.text.substring(selectedEnd - runStart)))
        }
    }.normalizedRuns()
}

private fun List<TextRun>.normalizedRuns(): List<TextRun> {
    val result = mutableListOf<TextRun>()
    filter { it.text.isNotEmpty() }.forEach { run ->
        val previous = result.lastOrNull()
        if (previous != null && previous.style == run.style) result[result.lastIndex] = previous.copy(text = previous.text + run.text)
        else result += run
    }
    return result.ifEmpty { listOf(TextRun()) }
}

private fun CharacterStyle.formatEnabled(format: CharacterFormat): Boolean = when (format) {
    CharacterFormat.Bold -> bold
    CharacterFormat.Italic -> italic
    CharacterFormat.Underline -> underline
    CharacterFormat.StrikeThrough -> strikeThrough
}

private fun CharacterStyle.withFormat(format: CharacterFormat, enabled: Boolean): CharacterStyle = when (format) {
    CharacterFormat.Bold -> copy(bold = enabled)
    CharacterFormat.Italic -> copy(italic = enabled)
    CharacterFormat.Underline -> copy(underline = enabled)
    CharacterFormat.StrikeThrough -> copy(strikeThrough = enabled)
}
