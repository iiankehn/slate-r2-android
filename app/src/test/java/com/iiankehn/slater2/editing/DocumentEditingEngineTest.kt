package com.iiankehn.slater2.editing

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.ParagraphStyle
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentEditingEngineTest {
    private var nextId = 1
    private val engine = DocumentEditingEngine { "generated-${nextId++}" }

    @Test
    fun replacementAcrossParagraphsPreservesOuterTextAndCollapsesSelection() {
        val snapshot = snapshot("Hello world", "Second paragraph")
            .select(position(0, 6), position(1, 6))

        val result = engine.execute(snapshot, DocumentCommand.ReplaceSelection("R2"))

        assertTrue(result.changed)
        assertEquals(listOf("Hello R2 paragraph"), result.snapshot.paragraphTexts())
        assertEquals(position(0, 8), result.snapshot.selection.focus)
        assertTrue(result.snapshot.selection.isCollapsed)
    }

    @Test
    fun reverseSelectionUsesTheSameNormalizedRange() {
        val result = engine.execute(
            snapshot("Alpha", "Bravo").select(position(1, 3), position(0, 2)),
            DocumentCommand.ReplaceSelection("X"),
        )

        assertEquals(listOf("AlXvo"), result.snapshot.paragraphTexts())
        assertEquals(position(0, 3), result.snapshot.selection.focus)
    }

    @Test
    fun multilineInsertionCreatesParagraphsAndRetainsFollowingText() {
        val result = engine.execute(
            snapshot("Alpha omega").select(position(0, 6)),
            DocumentCommand.ReplaceSelection("one\ntwo\n"),
        )

        assertEquals(listOf("Alpha one", "two", "omega"), result.snapshot.paragraphTexts())
        assertEquals(position(2, 0), result.snapshot.selection.focus)
    }

    @Test
    fun characterFormattingSplitsRunsAcrossMultipleParagraphs() {
        val snapshot = snapshot("Alpha", "Bravo")
            .select(position(0, 2), position(1, 3))

        val result = engine.execute(snapshot, DocumentCommand.ToggleCharacterFormat(CharacterFormat.Bold))
        val paragraphs = result.snapshot.document.sections.single().blocks.filterIsInstance<ParagraphBlock>()

        assertEquals(listOf("Al", "pha"), paragraphs[0].runs.map(TextRun::text))
        assertFalse(paragraphs[0].runs[0].style.bold)
        assertTrue(paragraphs[0].runs[1].style.bold)
        assertEquals(listOf("Bra", "vo"), paragraphs[1].runs.map(TextRun::text))
        assertTrue(paragraphs[1].runs[0].style.bold)
        assertFalse(paragraphs[1].runs[1].style.bold)
    }

    @Test
    fun togglingFullyFormattedSelectionTurnsFormattingOff() {
        val bold = CharacterStyle(bold = true)
        val document = WordProcessingDocument(
            id = "document",
            title = "",
            sections = listOf(DocumentSection(blocks = listOf(ParagraphBlock(runs = listOf(TextRun("Bold", bold)))))),
        )
        val snapshot = EditorSnapshot(document, DocumentSelection(position(0, 0), position(0, 4)))

        val result = engine.execute(snapshot, DocumentCommand.ToggleCharacterFormat(CharacterFormat.Bold))

        assertFalse((result.snapshot.document.sections[0].blocks[0] as ParagraphBlock).runs.single().style.bold)
    }

    @Test
    fun mixedFormattingBecomesFullyEnabled() {
        val document = WordProcessingDocument(
            id = "document",
            title = "",
            sections = listOf(
                DocumentSection(
                    blocks = listOf(
                        ParagraphBlock(
                            runs = listOf(
                                TextRun("A", CharacterStyle(bold = true)),
                                TextRun("B"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.execute(
            EditorSnapshot(document, DocumentSelection(position(0, 0), position(0, 2))),
            DocumentCommand.ToggleCharacterFormat(CharacterFormat.Bold),
        )

        assertTrue((result.snapshot.document.sections[0].blocks[0] as ParagraphBlock).runs.single().style.bold)
    }

    @Test
    fun paragraphStyleAppliesAcrossSelectedBlocks() {
        val result = engine.execute(
            snapshot("One", "Two", "Three").select(position(0, 1), position(2, 2)),
            DocumentCommand.ApplyNamedStyle(NamedParagraphStyle.Heading2),
        )

        result.snapshot.document.sections.single().blocks.filterIsInstance<ParagraphBlock>().forEach {
            assertEquals(NamedParagraphStyle.Heading2, it.style.namedStyle)
        }
    }

    @Test
    fun backspaceAtParagraphStartJoinsParagraphs() {
        val result = engine.execute(
            snapshot("First", "Second").select(position(1, 0)),
            DocumentCommand.DeleteBackward,
        )

        assertEquals(listOf("FirstSecond"), result.snapshot.paragraphTexts())
        assertEquals(position(0, 5), result.snapshot.selection.focus)
    }

    @Test
    fun forwardDeleteAtParagraphEndJoinsParagraphs() {
        val result = engine.execute(
            snapshot("First", "Second").select(position(0, 5)),
            DocumentCommand.DeleteForward,
        )

        assertEquals(listOf("FirstSecond"), result.snapshot.paragraphTexts())
        assertEquals(position(0, 5), result.snapshot.selection.focus)
    }

    @Test
    fun transactionIsOneUndoStepAndClearsRedoOnNewEdit() {
        val session = DocumentEditorSession(snapshot(""), engine)

        session.transaction(
            listOf(
                DocumentCommand.ReplaceSelection("Slate"),
                DocumentCommand.ReplaceSelection(" R2"),
            ),
        )
        assertEquals(listOf("Slate R2"), session.current.paragraphTexts())
        assertTrue(session.canUndo)

        session.undo()
        assertEquals(listOf(""), session.current.paragraphTexts())
        assertTrue(session.canRedo)

        session.redo()
        assertEquals(listOf("Slate R2"), session.current.paragraphTexts())
        session.execute(DocumentCommand.DeleteBackward)
        assertFalse(session.canRedo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPositionBeyondParagraphText() {
        engine.execute(snapshot("Short").select(position(0, 10)), DocumentCommand.ReplaceSelection("x"))
    }

    private fun snapshot(vararg paragraphs: String): EditorSnapshot {
        val document = WordProcessingDocument(
            id = "document",
            title = "Test",
            sections = listOf(
                DocumentSection(
                    blocks = paragraphs.mapIndexed { index, text ->
                        ParagraphBlock(id = "paragraph-$index", runs = listOf(TextRun(text)), style = ParagraphStyle())
                    },
                ),
            ),
        )
        return EditorSnapshot(document, DocumentSelection(position(0, 0)))
    }

    private fun EditorSnapshot.select(anchor: DocumentPosition, focus: DocumentPosition = anchor) =
        copy(selection = DocumentSelection(anchor, focus))

    private fun EditorSnapshot.paragraphTexts(): List<String> = document.sections.single().blocks
        .filterIsInstance<ParagraphBlock>()
        .map { paragraph -> paragraph.runs.joinToString("") { it.text } }

    private fun position(block: Int, offset: Int) = DocumentPosition(0, block, offset)
}
