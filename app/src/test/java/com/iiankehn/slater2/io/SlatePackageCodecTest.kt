package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SlatePackageCodecTest {
    @Test fun slxRoundTripPreservesSharedRichText() {
        val source = SlxDocument(
            title = "Notes handoff",
            body = RichTextDocument("Write boldly", listOf(RichTextRange(RichTextStyle.Bold, 6, 12))),
            sourceDocumentId = "note-1",
            sourceRevision = 7,
            sourceProduct = "notes",
        )
        val decoded = SlxCodec.decode(SlxCodec.encode(source))
        assertEquals(source.body.normalized(), decoded.body)
        assertEquals("notes", decoded.sourceProduct)
    }

    @Test fun slxfRoundTripPreservesForgeLayoutAndAssets() {
        val forge = WordProcessingDocument(
            id = "forge-1",
            title = "Designed report",
            sections = listOf(DocumentSection(
                page = PageSetup(size = PageSize.A4, orientation = PageOrientation.Landscape, columns = 2),
                blocks = listOf(ParagraphBlock("p1", listOf(TextRun("Heading", CharacterStyle(bold = true))), ParagraphStyle(namedStyle = NamedParagraphStyle.Heading1))),
                header = listOf(ParagraphBlock("h1", listOf(TextRun("Slate Forge")))),
                start = SectionStart.NextPage,
            )),
        )
        val asset = SlxAsset("image-1", "webp", "image/webp", byteArrayOf(9, 8, 7))
        val decoded = SlxfCodec.decode(SlxfCodec.encode(SlxfDocument(forge, 99, mapOf("image-1" to asset))))
        assertEquals(forge, decoded.document)
        assertEquals(99, decoded.sourceRevision)
        assertArrayEquals(asset.bytes, decoded.assets.getValue("image-1").bytes)
    }
}
