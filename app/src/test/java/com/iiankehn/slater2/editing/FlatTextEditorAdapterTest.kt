package com.iiankehn.slater2.editing

import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.model.ImageWrapping

class FlatTextEditorAdapterTest {
    @Test fun `ime replacement becomes an undoable R2 transaction`() {
        val adapter = FlatTextEditorAdapter(document("Hello"))
        adapter.replace("Hello world", 11, 11)
        assertEquals("Hello world", adapter.state().text)
        assertTrue(adapter.canUndo)
        assertEquals("Hello", adapter.undo().text)
        assertEquals("Hello world", adapter.redo().text)
    }

    @Test fun `newlines create real paragraphs`() {
        val adapter = FlatTextEditorAdapter(document("One"))
        adapter.replace("One\nTwo", 7, 7)
        assertEquals(2, adapter.document.sections.single().blocks.size)
        assertEquals("One\nTwo", adapter.state().text)
    }

    @Test fun `structured blocks are inserted into the R2 model and undoable`() {
        val adapter = FlatTextEditorAdapter(document("One"))
        adapter.insertTable(3, 3)
        assertTrue(adapter.document.sections.single().blocks.any { it is TableBlock })
        adapter.insertImage(4, 4, "content://picture", "Chart")
        assertTrue(adapter.document.sections.single().blocks.any { it is ImageBlock })
        adapter.undo()
        assertTrue(adapter.document.sections.single().blocks.none { it is ImageBlock })
    }

    @Test fun `table cells and dimensions are editable and undoable`() {
        val adapter = FlatTextEditorAdapter(document("One"))
        adapter.insertTable(3, 3)
        val table = adapter.document.sections.single().blocks.filterIsInstance<TableBlock>().single()

        adapter.updateTableCell(table.id, 0, 0, "Quarter\nRevenue")
        adapter.resizeTable(table.id, 3, 4)

        val updated = adapter.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        assertEquals(3, updated.rows.size)
        assertEquals(4, updated.rows.first().cells.size)
        assertEquals(listOf("Quarter", "Revenue"), updated.rows.first().cells.first().blocks.map { paragraph ->
            paragraph.runs.joinToString("") { it.text }
        })
        adapter.undo()
        assertEquals(2, adapter.document.sections.single().blocks.filterIsInstance<TableBlock>().single().rows.size)
    }

    @Test fun `image properties and object deletion are undoable`() {
        val adapter = FlatTextEditorAdapter(document("One"))
        adapter.insertImage(3, 3, "content://picture", "Chart")
        val image = adapter.document.sections.single().blocks.filterIsInstance<ImageBlock>().single()

        adapter.updateImage(image.id, "Quarterly chart", 360f, 240f, ImageWrapping.Square)
        val updated = adapter.document.sections.single().blocks.filterIsInstance<ImageBlock>().single()
        assertEquals("Quarterly chart", updated.description)
        assertEquals(ImageWrapping.Square, updated.wrapping)

        adapter.deleteObject(image.id)
        assertTrue(adapter.document.sections.single().blocks.none { it is ImageBlock })
        adapter.undo()
        assertTrue(adapter.document.sections.single().blocks.any { it is ImageBlock })
    }

    @Test fun `inserted objects are selected and selected deletion is undoable`() {
        val adapter = FlatTextEditorAdapter(document("One"))

        adapter.insertTable(3, 3)
        val table = adapter.document.sections.single().blocks.filterIsInstance<TableBlock>().single()
        assertEquals(table.id, adapter.selectedObjectId)

        adapter.deleteSelectedObject()
        assertTrue(adapter.document.sections.single().blocks.none { it is TableBlock })
        assertEquals(null, adapter.selectedObjectId)

        adapter.undo()
        assertTrue(adapter.document.sections.single().blocks.any { it is TableBlock })
        assertEquals(table.id, adapter.selectedObjectId)
    }

    @Test fun `moving the text selection clears object selection`() {
        val adapter = FlatTextEditorAdapter(document("One"))
        adapter.insertImage(3, 3, "content://picture")
        assertTrue(adapter.selectedObjectId != null)

        adapter.replace(adapter.state().text, 0, 0)

        assertEquals(null, adapter.selectedObjectId)
    }

    private fun document(text: String) = WordProcessingDocument(
        "document", "", listOf(DocumentSection(blocks = listOf(ParagraphBlock("p", listOf(TextRun(text))))))
    )
}
