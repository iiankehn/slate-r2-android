package com.iiankehn.slater2.editing

import com.iiankehn.slater2.io.R2DocumentBridge
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.WordProcessingDocument
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.PageSetup
import com.iiankehn.slater2.model.ImageWrapping

data class FlatEditorState(val text: String, val selectionStart: Int, val selectionEnd: Int)

/** Routes IME/Compose text diffs through the R2 command engine without making UI state authoritative. */
class FlatTextEditorAdapter(initial: WordProcessingDocument) {
    private var index = FlatDocumentIndex(initial)
    private val session = DocumentEditorSession(EditorSnapshot(initial, DocumentSelection(index.position(0))))

    val document: WordProcessingDocument get() = session.current.document
    val canUndo: Boolean get() = session.canUndo
    val canRedo: Boolean get() = session.canRedo

    fun state(): FlatEditorState {
        index = FlatDocumentIndex(document)
        return FlatEditorState(index.text, index.offset(session.current.selection.anchor), index.offset(session.current.selection.focus))
    }

    fun replace(newText: String, selectionStart: Int, selectionEnd: Int): FlatEditorState {
        val old = index.text
        if (newText != old) {
            val prefix = old.commonPrefixWith(newText).length
            val suffix = old.substring(prefix).commonSuffixWith(newText.substring(prefix)).length
                .coerceAtMost(old.length - prefix).coerceAtMost(newText.length - prefix)
            val selection = DocumentSelection(index.position(prefix), index.position(old.length - suffix))
            session.updateSelection(selection)
            session.execute(DocumentCommand.ReplaceSelection(newText.substring(prefix, newText.length - suffix)))
            index = FlatDocumentIndex(document)
        }
        session.updateSelection(DocumentSelection(index.position(selectionStart), index.position(selectionEnd)))
        return state()
    }

    fun toggle(format: CharacterFormat, start: Int, end: Int): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.ToggleCharacterFormat(format))
        return state()
    }

    fun applyNamedStyle(style: NamedParagraphStyle, start: Int, end: Int): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.ApplyNamedStyle(style))
        return state()
    }

    fun toggleList(kind: ListKind, start: Int, end: Int): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.ToggleList(kind))
        return state()
    }

    fun updatePageSetup(page: PageSetup): FlatEditorState {
        session.execute(DocumentCommand.UpdatePageSetup(page))
        return state()
    }

    fun insertPageBreak(start: Int, end: Int): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.InsertPageBreak)
        return state()
    }

    fun insertTable(start: Int, end: Int, rows: Int = 2, columns: Int = 2): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.InsertTable(rows, columns))
        return state()
    }

    fun insertImage(start: Int, end: Int, sourceUri: String, description: String = ""): FlatEditorState {
        session.updateSelection(DocumentSelection(index.position(start), index.position(end)))
        session.execute(DocumentCommand.InsertImage(sourceUri, description))
        return state()
    }

    fun updateTableCell(tableId: String, row: Int, column: Int, text: String): FlatEditorState {
        session.execute(DocumentCommand.UpdateTableCell(tableId, row, column, text))
        return state()
    }

    fun resizeTable(tableId: String, rows: Int, columns: Int): FlatEditorState {
        session.execute(DocumentCommand.ResizeTable(tableId, rows, columns))
        return state()
    }

    fun updateImage(
        imageId: String,
        description: String,
        widthPoints: Float?,
        heightPoints: Float?,
        wrapping: ImageWrapping,
    ): FlatEditorState {
        session.execute(DocumentCommand.UpdateImage(imageId, description, widthPoints, heightPoints, wrapping))
        return state()
    }

    fun deleteObject(objectId: String): FlatEditorState {
        session.execute(DocumentCommand.DeleteObject(objectId))
        return state()
    }

    fun undo(): FlatEditorState { session.undo(); return state() }
    fun redo(): FlatEditorState { session.redo(); return state() }

    fun legacyBody() = R2DocumentBridge.toLegacyBody(document)
}

private class FlatDocumentIndex(document: WordProcessingDocument) {
    private data class Entry(val section: Int, val block: Int, val start: Int, val end: Int)
    private val entries: List<Entry>
    val text: String

    init {
        val output = StringBuilder()
        val built = mutableListOf<Entry>()
        document.sections.forEachIndexed { sectionIndex, section ->
            section.blocks.forEachIndexed { blockIndex, block ->
                if (block !is ParagraphBlock) return@forEachIndexed
                if (built.isNotEmpty()) output.append('\n')
                val start = output.length
                block.runs.forEach { output.append(it.text) }
                built += Entry(sectionIndex, blockIndex, start, output.length)
            }
        }
        require(built.isNotEmpty()) { "The flat editor requires at least one paragraph." }
        entries = built
        text = output.toString()
    }

    fun position(rawOffset: Int): DocumentPosition {
        val offset = rawOffset.coerceIn(0, text.length)
        val entry = entries.firstOrNull { offset <= it.end } ?: entries.last()
        return DocumentPosition(entry.section, entry.block, (offset - entry.start).coerceIn(0, entry.end - entry.start))
    }

    fun offset(position: DocumentPosition): Int {
        val entry = entries.firstOrNull { it.section == position.sectionIndex && it.block == position.blockIndex }
            ?: return text.length
        return (entry.start + position.offset).coerceIn(entry.start, entry.end)
    }
}
