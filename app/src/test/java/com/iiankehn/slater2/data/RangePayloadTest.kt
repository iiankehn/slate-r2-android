package com.iiankehn.slater2.data

import com.iiankehn.slater2.model.RichTextRange
import com.iiankehn.slater2.model.RichTextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class RangePayloadTest {
    @Test
    fun rangePayloadRoundTrips() {
        val ranges = listOf(
            RichTextRange(RichTextStyle.Bold, 0, 5),
            RichTextRange(RichTextStyle.HeadingOne, 8, 15),
        )

        assertEquals(ranges, RangePayload.decode(RangePayload.encode(ranges)))
    }

    @Test
    fun malformedRangesAreIgnored() {
        assertEquals(emptyList<RichTextRange>(), RangePayload.decode("not,a,range;Bold,x,4"))
    }

    @Test
    fun rangeMetadataRoundTrips() {
        val ranges = listOf(RichTextRange(RichTextStyle.Link, 0, 5, "https://example.com/a,b?q=1"))
        assertEquals(ranges, RangePayload.decode(RangePayload.encode(ranges)))
    }
}
