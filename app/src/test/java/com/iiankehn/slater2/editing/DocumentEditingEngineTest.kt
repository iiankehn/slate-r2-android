package com.iiankehn.slater2.editing

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.NamedParagraphStyle
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.PageSetup
import com.iiankehn.slater2.model.PageSize
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.ParagraphStyle
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.SectionStart
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
    fun listLevelsAdjustAcrossSelectedParagraphsAndClampAtBounds() {
        val listed = engine.execute(
            snapshot("One", "Two").select(position(0, 0), position(1, 3)),
            DocumentCommand.ToggleList(ListKind.Numbered),
        ).snapshot

        val nested = engine.execute(listed, DocumentCommand.AdjustListLevel(1)).snapshot
        nested.document.sections.single().blocks.filterIsInstance<ParagraphBlock>().forEach {
            assertEquals(1, it.style.list?.level)
        }

        val restored = engine.execute(nested, DocumentCommand.HandleTab(outdent = true)).snapshot
        restored.document.sections.single().blocks.filterIsInstance<ParagraphBlock>().forEach {
            assertEquals(0, it.style.list?.level)
        }
        assertFalse(engine.execute(restored, DocumentCommand.AdjustListLevel(-1)).changed)
    }

    @Test
    fun tabInPlainParagraphInsertsTextWhileListTabChangesNesting() {
        val plain = engine.execute(snapshot("Text").select(position(0, 0)), DocumentCommand.HandleTab(outdent = false))
        assertEquals(listOf("\tText"), plain.snapshot.paragraphTexts())

        val listed = engine.execute(
            snapshot("Item").select(position(0, 0), position(0, 4)),
            DocumentCommand.ToggleList(ListKind.Checklist),
        ).snapshot
        val nested = engine.execute(listed, DocumentCommand.HandleTab(outdent = false)).snapshot
        assertEquals(1, (nested.document.sections.single().blocks.single() as ParagraphBlock).style.list?.level)
        assertEquals(listOf("Item"), nested.paragraphTexts())
    }

    @Test
    fun checklistItemsToggleWithoutChangingTheirText() {
        val listed = engine.execute(
            snapshot("Task").select(position(0, 0), position(0, 4)),
            DocumentCommand.ToggleList(ListKind.Checklist),
        ).snapshot
        val paragraph = listed.document.sections.single().blocks.single() as ParagraphBlock

        val checked = engine.execute(listed, DocumentCommand.ToggleChecklistItem(paragraph.id)).snapshot

        val updated = checked.document.sections.single().blocks.single() as ParagraphBlock
        assertTrue(updated.style.list?.checked == true)
        assertEquals("Task", updated.runs.single().text)
    }

    @Test
    fun sectionBreakSplitsAtCaretAndHeaderFooterChangesAreUndoable() {
        val session = DocumentEditorSession(snapshot("AlphaBeta").select(position(0, 5)), engine)

        session.execute(DocumentCommand.InsertSectionBreak)
        assertEquals(2, session.current.document.sections.size)
        assertEquals("Alpha", (session.current.document.sections[0].blocks.single() as ParagraphBlock).runs.single().text)
        assertEquals("Beta", (session.current.document.sections[1].blocks.single() as ParagraphBlock).runs.single().text)
        assertEquals(SectionStart.NextPage, session.current.document.sections[1].start)
        assertEquals(DocumentPosition(1, 0, 0), session.current.selection.focus)

        session.execute(DocumentCommand.UpdateHeaderFooter("Report", "Confidential\nPage"))
        assertEquals("Report", session.current.document.sections[1].header.single().runs.single().text)
        assertEquals(2, session.current.document.sections[1].footer.size)
        session.undo()
        assertTrue(session.current.document.sections[1].header.isEmpty())
    }

    @Test
    fun deletingSectionBoundaryJoinsTextAndUsesFollowingSectionSetup() {
        val document = WordProcessingDocument(
            "document",
            "",
            listOf(
                DocumentSection(PageSetup(PageSize.Letter), listOf(ParagraphBlock("left", listOf(TextRun("Left"))))),
                DocumentSection(PageSetup(PageSize.A4), listOf(ParagraphBlock("right", listOf(TextRun("Right")))), start = SectionStart.NextPage),
            ),
        )
        val snapshot = EditorSnapshot(document, DocumentSelection(DocumentPosition(1, 0, 0)))

        val joined = engine.execute(snapshot, DocumentCommand.DeleteBackward).snapshot

        assertEquals(1, joined.document.sections.size)
        assertEquals(PageSize.A4, joined.document.sections.single().page.size)
        assertEquals("LeftRight", (joined.document.sections.single().blocks.single() as ParagraphBlock).runs.single().text)
        assertEquals(DocumentPosition(0, 0, 4), joined.selection.focus)
    }

    @Test
    fun replacementAcrossSectionsRemovesBoundariesAndKeepsFollowingSetup() {
        val document = WordProcessingDocument(
            "document", "", listOf(
                DocumentSection(PageSetup(PageSize.Letter), listOf(ParagraphBlock("left", listOf(TextRun("Alpha"))))),
                DocumentSection(PageSetup(PageSize.A4), listOf(ParagraphBlock("right", listOf(TextRun("Omega")))), start = SectionStart.NextPage),
            ),
        )
        val selected = EditorSnapshot(
            document,
            DocumentSelection(DocumentPosition(0, 0, 2), DocumentPosition(1, 0, 3)),
        )

        val replaced = engine.execute(selected, DocumentCommand.ReplaceSelection("X\nY")).snapshot

        assertEquals(1, replaced.document.sections.size)
        assertEquals(PageSize.A4, replaced.document.sections.single().page.size)
        assertEquals(listOf("AlX", "Yga"), replaced.paragraphTexts())
        assertEquals(DocumentPosition(0, 1, 1), replaced.selection.focus)
    }

    @Test
    fun tableRowsColumnsAndRepeatingHeadersAreEditable() {
        val inserted = engine.execute(snapshot("One"), DocumentCommand.InsertTable(3, 3)).snapshot
        val table = inserted.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        val withHeader = engine.execute(inserted, DocumentCommand.SetTableHeaderRows(table.id, 1)).snapshot
        val withoutRow = engine.execute(withHeader, DocumentCommand.DeleteTableRow(table.id, 1)).snapshot
        val withoutColumn = engine.execute(withoutRow, DocumentCommand.DeleteTableColumn(table.id, 2)).snapshot
        val updated = withoutColumn.document.sections.single().blocks.filterIsInstance<TableBlock>().single()

        assertEquals(2, updated.rows.size)
        assertEquals(2, updated.rows.first().cells.size)
        assertEquals(1, updated.headerRowCount)
    }

    @Test
    fun adjacentTableCellsMergeAndRemainUndoable() {
        val session = DocumentEditorSession(snapshot("One"), engine)
        session.execute(DocumentCommand.InsertTable(2, 3))
        val table = session.current.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        session.execute(DocumentCommand.UpdateTableCell(table.id, 0, 0, "Left"))
        session.execute(DocumentCommand.UpdateTableCell(table.id, 0, 1, "Right"))

        session.execute(DocumentCommand.MergeTableCells(table.id, 0, 0, 1))

        val merged = session.current.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        assertEquals(2, merged.rows[0].cells[0].columnSpan)
        assertEquals(0, merged.rows[0].cells[1].columnSpan)
        assertEquals(listOf("Left", "Right"), merged.rows[0].cells[0].blocks.map { it.runs.joinToString("") { run -> run.text } })
        session.undo()
        val restored = session.current.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        assertEquals(1, restored.rows[0].cells[0].columnSpan)
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

    @Test
    fun objectSelectionIsValidatedAndDoesNotCreateHistory() {
        val withTable = engine.execute(snapshot("One"), DocumentCommand.InsertTable()).snapshot
        val table = withTable.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        val session = DocumentEditorSession(withTable, engine)

        session.selectObject(table.id)

        assertEquals(table.id, session.current.selectedObjectId)
        assertFalse(session.canUndo)
        session.updateSelection(session.current.selection)
        assertEquals(null, session.current.selectedObjectId)
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
