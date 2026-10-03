package com.iiankehn.slater2.ui

import com.iiankehn.slater2.layout.DocumentLayoutEngine
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.ParagraphStyle
import com.iiankehn.slater2.model.ListKind
import com.iiankehn.slater2.model.ListStyle
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextSlicesTest {
    @Test
    fun longParagraphProducesContiguousEditablePageRanges() {
        val text = "Slate word processor ".repeat(900)
        val document = WordProcessingDocument(
            "document",
            "",
            listOf(DocumentSection(blocks = listOf(ParagraphBlock("long", listOf(TextRun(text)))))),
        )
        val layout = DocumentLayoutEngine().layout(document)

        val slices = pageTextSlices(document, layout, text.length)

        assertTrue(slices.size > 1)
        assertEquals(0, slices.first().start)
        assertEquals(text.length, slices.last().end)
        slices.zipWithNext().forEach { (current, next) -> assertEquals(current.end, next.start) }
        assertTrue(slices.all { it.length > 0 })
    }

    @Test
    fun explicitPageBreakStartsTheSecondEditableRangeAtTheNextParagraph() {
        val first = "First page"
        val second = "Second page"
        val document = WordProcessingDocument(
            "document",
            "",
            listOf(DocumentSection(blocks = listOf(
                ParagraphBlock("first", listOf(TextRun(first))),
                ParagraphBlock("second", listOf(TextRun(second)), ParagraphStyle(pageBreakBefore = true)),
            ))),
        )
        val layout = DocumentLayoutEngine().layout(document)

        val slices = pageTextSlices(document, layout, first.length + 1 + second.length)

        assertEquals(2, slices.size)
        assertEquals(PageTextSlice(0, first.length + 1), slices[0])
        assertEquals(PageTextSlice(first.length + 1, first.length + 1 + second.length), slices[1])
    }

    @Test
    fun listMarkersRespectKindsLevelsAndNumberRestarts() {
        val document = WordProcessingDocument(
            "document",
            "",
            listOf(DocumentSection(blocks = listOf(
                paragraph("n1", ListStyle(ListKind.Numbered, startAt = 3)),
                paragraph("n2", ListStyle(ListKind.Numbered)),
                paragraph("bullet", ListStyle(ListKind.Bulleted, level = 1)),
                paragraph("plain", null),
                paragraph("check", ListStyle(ListKind.Checklist)),
            ))),
        )

        val markers = paragraphListMarkers(document)

        assertEquals(ParagraphListMarker("3.", 0), markers["n1"])
        assertEquals(ParagraphListMarker("4.", 0), markers["n2"])
        assertEquals(ParagraphListMarker("◦", 1), markers["bullet"])
        assertEquals(null, markers["plain"])
        assertEquals(ParagraphListMarker("☐", 0), markers["check"])
    }

    private fun paragraph(id: String, list: ListStyle?) = ParagraphBlock(
        id,
        listOf(TextRun(id)),
        ParagraphStyle(list = list),
    )
}
