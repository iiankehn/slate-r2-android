package com.iiankehn.slater2.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextDocumentTest {
    @Test
    fun toggleAddsAndRemovesInlineStyle() {
        val bold = RichTextDocument.plain("Slate").toggle(RichTextStyle.Bold, 0, 5)
        assertTrue(bold.hasStyle(RichTextStyle.Bold, 0, 5))

        val plain = bold.toggle(RichTextStyle.Bold, 0, 5)
        assertFalse(plain.hasStyle(RichTextStyle.Bold, 0, 5))
    }

    @Test
    fun insertionInsideStyleExtendsRange() {
        val original = RichTextDocument(
            text = "Slate",
            ranges = listOf(RichTextRange(RichTextStyle.Italic, 0, 5)),
        )

        val changed = original.updateText("Sla-te")

        assertEquals(RichTextRange(RichTextStyle.Italic, 0, 6), changed.ranges.single())
    }

    @Test
    fun normalizationClampsAndDropsEmptyRanges() {
        val normalized = RichTextDocument(
            text = "Hi",
            ranges = listOf(
                RichTextRange(RichTextStyle.Bold, 0, 8),
                RichTextRange(RichTextStyle.Italic, 1, 1),
            ),
        ).normalized()

        assertEquals(listOf(RichTextRange(RichTextStyle.Bold, 0, 2)), normalized.ranges)
    }
}
