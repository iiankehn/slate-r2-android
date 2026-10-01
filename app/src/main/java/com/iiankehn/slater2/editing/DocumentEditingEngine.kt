package com.iiankehn.slater2.editing

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentBlock
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.NamedParagraphStyle
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
}

data class EditorSnapshot(
    val document: WordProcessingDocument,
    val selection: DocumentSelection,
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
        snapshot.document.requireValid(snapshot.selection)
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
        }
        return EditResult(updated, updated != snapshot)
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
        if (position.blockIndex == 0) return snapshot
        val section = snapshot.document.sections[position.sectionIndex]
        val previousIndex = section.blocks.indexOfPreviousParagraph(position.blockIndex) ?: return snapshot
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
        val nextIndex = section.blocks.indexOfNextParagraph(position.blockIndex) ?: return snapshot
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

/** Mutable session boundary; each command or transaction is a single durable undo step. */
class DocumentEditorSession(
    initial: EditorSnapshot,
    private val engine: DocumentEditingEngine = DocumentEditingEngine(),
    private val historyLimit: Int = 100,
) {
    init {
        require(historyLimit > 0) { "History must retain at least one edit." }
        initial.document.requireValid(initial.selection)
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
        current.document.requireValid(selection)
        current = current.copy(selection = selection)
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

private fun WordProcessingDocument.requireValid(selection: DocumentSelection) {
    listOf(selection.anchor, selection.focus).forEach { position ->
        require(position.sectionIndex in sections.indices) { "Selection section does not exist." }
        val section = sections[position.sectionIndex]
        require(position.blockIndex in section.blocks.indices) { "Selection block does not exist." }
        val paragraph = section.blocks[position.blockIndex] as? ParagraphBlock
            ?: error("Text selections must resolve to paragraphs.")
        require(position.offset <= paragraph.textLength) { "Selection offset exceeds paragraph text." }
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
