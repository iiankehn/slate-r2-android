package com.iiankehn.slater2.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordProcessingDocumentTest {
    @Test
    fun landscapeSwapsPageDimensions() {
        val setup = PageSetup(size = PageSize.A4, orientation = PageOrientation.Landscape)

        assertEquals(PageSize.A4.heightPoints, setup.widthPoints)
        assertEquals(PageSize.A4.widthPoints, setup.heightPoints)
    }

    @Test
    fun defaultLetterPageProvidesPrintableContentArea() {
        val setup = PageSetup()

        assertEquals(468f, setup.contentWidthPoints)
        assertEquals(648f, setup.contentHeightPoints)
        assertTrue(setup.contentWidthPoints > 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tableRejectsMismatchedColumnCounts() {
        TableBlock(
            id = "table-1",
            rows = listOf(
                TableRow(listOf(TableCell(), TableCell())),
                TableRow(listOf(TableCell())),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun documentRequiresASection() {
        WordProcessingDocument(id = "document-1", title = "Empty", sections = emptyList())
    }
}
