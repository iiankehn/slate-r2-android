package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFormatsTest {
    @Test
    fun markdownLinkRoundTripKeepsVisibleLabelAndUrl() {
        val imported = DocumentFormats.importMarkdown("Read [Slate](https://example.com/slate)".toByteArray(), "Imported")
        assertEquals("Read Slate", imported.body.text)
        assertEquals("https://example.com/slate", imported.body.ranges.single().data)
        assertEquals("Read [Slate](https://example.com/slate)", DocumentFormats.exportMarkdown(imported.body).toString(Charsets.UTF_8))
    }

    @Test
    fun docxRoundTripKeepsTitleAndParagraphs() {
        val bytes = DocumentFormats.exportDocx("Draft", RichTextDocument.plain("First\nSecond"))
        val imported = DocumentFormats.importDocx(bytes, "Fallback")
        assertEquals("Draft", imported.title)
        assertTrue(imported.body.text.contains("First"))
        assertTrue(imported.body.text.contains("Second"))
        assertTrue(imported.wordProcessingDocument != null)
    }

    @Test
    fun structuredDocxRoundTripKeepsStylesTablesAndPageSetup() {
        val source = WordProcessingDocument(
            "source", "Report",
            sections = listOf(DocumentSection(
                page = PageSetup(PageSize.A4, PageOrientation.Landscape, PageMargins(36f, 40f, 44f, 48f), columns = 2),
                blocks = listOf(
                    ParagraphBlock("heading", listOf(TextRun("Report", CharacterStyle(bold = true, italic = true, fontSizePoints = 18f))), ParagraphStyle(namedStyle = NamedParagraphStyle.Heading1, alignment = ParagraphAlignment.Center, pageBreakBefore = true)),
                    TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(ParagraphBlock("cell", listOf(TextRun("Value")))))), TableCell())))),
                ),
            )),
        )
        val imported = DocumentFormats.importDocx(DocumentFormats.exportDocx(source), "Fallback")
        val r2 = requireNotNull(imported.wordProcessingDocument)
        val section = r2.sections.single()
        val heading = section.blocks.first() as ParagraphBlock
        assertEquals(PageSize.A4, section.page.size)
        assertEquals(PageOrientation.Landscape, section.page.orientation)
        assertEquals(2, section.page.columns)
        assertEquals(NamedParagraphStyle.Heading1, heading.style.namedStyle)
        assertTrue(heading.runs.first().style.bold)
        assertTrue(section.blocks[1] is TableBlock)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedDocxIsRejected() {
        DocumentFormats.importDocx("not a zip".toByteArray(), "Broken")
    }

    @Test
    fun markdownExportWritesFormatting() {
        val body = RichTextDocument("Slate", listOf(RichTextRange(RichTextStyle.Bold, 0, 5)))
        assertEquals("**Slate**", DocumentFormats.exportMarkdown(body).toString(Charsets.UTF_8))
    }
}
