package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DocumentBridgeTest {
    @Test fun `legacy paragraphs and inline formatting migrate to R2`() {
        val body = RichTextDocument("Hello\nWorld", listOf(RichTextRange(RichTextStyle.Bold, 0, 5)))
        val legacy = Document("id", "Title", body, "Now", updatedAtEpochMillis = 100)
        val r2 = R2DocumentBridge.fromLegacy(legacy)
        assertEquals(2, r2.sections.single().blocks.size)
        assertTrue((r2.sections.single().blocks.first() as ParagraphBlock).runs.first().style.bold)
        assertEquals(body.normalized(), R2DocumentBridge.toLegacyBody(r2))
    }
}
