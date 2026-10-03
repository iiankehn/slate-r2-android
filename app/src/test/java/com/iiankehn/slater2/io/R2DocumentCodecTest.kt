package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class R2DocumentCodecTest {
    @Test fun `round trip preserves complete R2 structure`() {
        val document = WordProcessingDocument(
            id = "report", title = "Quarterly report",
            sections = listOf(DocumentSection(
                page = PageSetup(PageSize.A4, PageOrientation.Landscape, columns = 2),
                blocks = listOf(
                    ParagraphBlock("heading", listOf(TextRun("Results", CharacterStyle(bold = true))), ParagraphStyle(namedStyle = NamedParagraphStyle.Heading1, list = ListStyle(ListKind.Checklist, checked = true))),
                    TableBlock("table", listOf(TableRow(listOf(TableCell(), TableCell()))), 1),
                    ImageBlock("chart", "content://chart", "Sales chart", 180f, 120f),
                ),
                header = listOf(ParagraphBlock("header", listOf(TextRun("Confidential")))),
                footer = listOf(ParagraphBlock("footer", listOf(TextRun("Page")))),
                start = SectionStart.NextPage,
            )),
            metadata = DocumentMetadata("Author", "Subject", setOf("one", "two"), 10, 20),
        )
        assertEquals(document, R2DocumentCodec.decode(R2DocumentCodec.encode(document)))
    }

    @Test fun `invalid payload is rejected`() {
        try {
            R2DocumentCodec.decode("not-base64")
            fail("Invalid payload should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
