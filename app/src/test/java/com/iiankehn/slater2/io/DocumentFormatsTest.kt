package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextRange
import com.iiankehn.slater2.model.RichTextStyle
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
