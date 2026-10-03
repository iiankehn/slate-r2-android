package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

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
                    TableBlock(
                        "table",
                        listOf(
                            TableRow(
                                listOf(
                                    TableCell(listOf(ParagraphBlock("cell", listOf(TextRun("Value"))))),
                                    TableCell(),
                                ),
                            ),
                        ),
                    ),
                ),
                header = listOf(ParagraphBlock("header", listOf(TextRun("Report header")))),
                footer = listOf(ParagraphBlock("footer", listOf(TextRun("Report footer")))),
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
        assertEquals("Report header", section.header.single().runs.single().text)
        assertEquals("Report footer", section.footer.single().runs.single().text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedDocxIsRejected() {
        DocumentFormats.importDocx("not a zip".toByteArray(), "Broken")
    }

    @Test
    fun structuredDocxWritesNumberingHeadersFootersAndRepeatingTableHeaders() {
        val listed = ParagraphBlock(
            "item", listOf(TextRun("Item")),
            ParagraphStyle(list = ListStyle(ListKind.Numbered, level = 1)),
        )
        val table = TableBlock("table", listOf(TableRow(listOf(TableCell()))), headerRowCount = 1)
        val source = WordProcessingDocument(
            "source", "Report", listOf(DocumentSection(
                blocks = listOf(listed, table),
                header = listOf(ParagraphBlock("header", listOf(TextRun("Header")))),
                footer = listOf(ParagraphBlock("footer", listOf(TextRun("Footer")))),
            )),
        )

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(DocumentFormats.exportDocx(source))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertTrue("word/numbering.xml" in entries)
        assertTrue("word/header1.xml" in entries)
        assertTrue("word/footer1.xml" in entries)
        assertTrue(entries.getValue("word/document.xml").contains("<w:numPr>"))
        assertTrue(entries.getValue("word/document.xml").contains("<w:tblHeader/>"))
        assertTrue(entries.getValue("word/document.xml").contains("rIdHeader1"))
    }

    @Test
    fun structuredDocxEmbedsProvidedImageRelationships() {
        val image = ImageBlock("image", "content://picture", "Chart", 240f, 160f)
        val source = WordProcessingDocument(
            "source", "Pictures", listOf(DocumentSection(blocks = listOf(ParagraphBlock("p"), image))),
        )
        val bytes = DocumentFormats.exportDocx(
            source,
            mapOf(image.id to DocxEmbeddedImage(byteArrayOf(1, 2, 3), "png", "image/png")),
        )
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }

        assertTrue("word/media/image1.png" in entries)
        assertTrue(entries.getValue("word/document.xml").toString(Charsets.UTF_8).contains("r:embed=\"rIdImage1\""))
        assertTrue(entries.getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8).contains("relationships/image"))
    }

    @Test
    fun markdownExportWritesFormatting() {
        val body = RichTextDocument("Slate", listOf(RichTextRange(RichTextStyle.Bold, 0, 5)))
        assertEquals("**Slate**", DocumentFormats.exportMarkdown(body).toString(Charsets.UTF_8))
    }
}
