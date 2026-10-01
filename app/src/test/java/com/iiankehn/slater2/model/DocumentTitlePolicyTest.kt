package com.iiankehn.slater2.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTitlePolicyTest {
    @Test
    fun explicitTitleWins() {
        assertEquals("Plan", DocumentTitlePolicy.displayTitle(" Plan ", "Body"))
    }

    @Test
    fun firstBodyLineBecomesFallbackTitle() {
        assertEquals("First thought", DocumentTitlePolicy.displayTitle("", "\nFirst thought\nMore"))
    }

    @Test
    fun blankDocumentIsUntitled() {
        assertEquals("Untitled", DocumentTitlePolicy.displayTitle("", ""))
    }
}
