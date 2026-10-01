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

    private fun document(text: String) = WordProcessingDocument(
        "document", "", listOf(DocumentSection(blocks = listOf(ParagraphBlock("p", listOf(TextRun(text))))))
    )
}
