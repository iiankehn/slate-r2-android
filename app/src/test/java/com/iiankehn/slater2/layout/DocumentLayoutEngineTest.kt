package com.iiankehn.slater2.layout

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.model.PageMargins
import com.iiankehn.slater2.model.PageSetup
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.ParagraphStyle
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.TableCell
import com.iiankehn.slater2.model.TableRow
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLayoutEngineTest {
    private val engine = DocumentLayoutEngine()

    @Test
    fun longParagraphPaginatesDeterministically() {
        val paragraph = ParagraphBlock(id = "long", runs = listOf(TextRun("Slate word processor ".repeat(900))))
        val document = WordProcessingDocument("doc", "", listOf(DocumentSection(blocks = listOf(paragraph))))

        val first = engine.layout(document)
        val second = engine.layout(document)

        assertTrue(first.pageCount > 1)
        assertEquals(first, second)
        assertTrue(first.blockLocations.getValue("long").size > 1)
    }

    @Test
    fun columnsFlowLeftToRightBeforeCreatingPage() {
        val setup = PageSetup(columns = 2, margins = PageMargins(36f, 36f, 36f, 36f))
        val paragraph = ParagraphBlock(id = "columns", runs = listOf(TextRun("column text ".repeat(350))))
        val layout = engine.layout(WordProcessingDocument("doc", "", listOf(DocumentSection(setup, listOf(paragraph)))))

        assertTrue(layout.pages.first().columns[0].fragments.isNotEmpty())
        assertTrue(layout.pages.first().columns[1].fragments.isNotEmpty())
        assertTrue(layout.pages.first().columns[0].bounds.right < layout.pages.first().columns[1].bounds.left)
    }

    @Test
    fun pageBreakBeforeStartsParagraphOnNewPage() {
        val blocks = listOf(
            ParagraphBlock(id = "one", runs = listOf(TextRun("First"))),
            ParagraphBlock(id = "two", runs = listOf(TextRun("Second")), style = ParagraphStyle(pageBreakBefore = true)),
        )
        val layout = engine.layout(WordProcessingDocument("doc", "", listOf(DocumentSection(blocks = blocks))))

        assertEquals(2, layout.pageCount)
        assertEquals(0, layout.blockLocations.getValue("one").single().first)
        assertEquals(1, layout.blockLocations.getValue("two").single().first)
    }

    @Test
    fun tableAndImageUseSharedPointGeometry() {
        val table = TableBlock("table", List(6) { TableRow(listOf(TableCell(), TableCell())) }, headerRowCount = 1)
        val image = ImageBlock("image", "content://image", widthPoints = 144f, heightPoints = 90f)
        val layout = engine.layout(WordProcessingDocument("doc", "", listOf(DocumentSection(blocks = listOf(table, image)))))

        val tableBounds = layout.blockLocations.getValue("table").first().second
        val imageBounds = layout.blockLocations.getValue("image").first().second
        assertEquals(144f, imageBounds.width)
        assertEquals(90f, imageBounds.height)
        assertTrue(imageBounds.top >= tableBounds.bottom)
    }

    @Test
    fun headersAndFootersRepeatOnEveryPage() {
        val section = DocumentSection(
            blocks = listOf(ParagraphBlock(id = "body", runs = listOf(TextRun("body ".repeat(4000), CharacterStyle(fontSizePoints = 14f))))),
            header = listOf(ParagraphBlock(id = "header", runs = listOf(TextRun("Header")))),
            footer = listOf(ParagraphBlock(id = "footer", runs = listOf(TextRun("Footer")))),
        )
        val layout = engine.layout(WordProcessingDocument("doc", "", listOf(section)))

        assertTrue(layout.pageCount > 1)
        assertTrue(layout.pages.all { it.header.single().blockId == "header" })
        assertTrue(layout.pages.all { it.footer.single().blockId == "footer" })
    }

    @Test
    fun largeDocumentPaginationRemainsDeterministic() {
        val paragraphs = List(1_000) { index ->
            ParagraphBlock("p$index", listOf(TextRun("Paragraph $index contains enough text to exercise measurement and pagination.")))
        }
        val document = WordProcessingDocument("large", "", listOf(DocumentSection(blocks = paragraphs)))
        val first = engine.layout(document)
        val second = engine.layout(document)
        assertEquals(first.pageCount, second.pageCount)
        assertEquals(first.blockLocations, second.blockLocations)
    }
}
