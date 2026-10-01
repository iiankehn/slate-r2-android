package com.iiankehn.slater2.model

enum class RichTextStyle {
    Bold,
    Italic,
    Underline,
    HeadingOne,
    Link,
    Quote,
    Image,
    Table,
}

data class RichTextRange(
    val style: RichTextStyle,
    val start: Int,
    val end: Int,
    val data: String? = null,
) {
    init {
        require(start >= 0) { "A rich-text range cannot start before zero." }
        require(end >= start) { "A rich-text range cannot end before it starts." }
    }
}

data class RichTextDocument(
    val text: String = "",
    val ranges: List<RichTextRange> = emptyList(),
) {
    fun normalized(): RichTextDocument = copy(
        ranges = ranges
            .mapNotNull { range ->
                val start = range.start.coerceIn(0, text.length)
                val end = range.end.coerceIn(start, text.length)
                if (start == end) null else range.copy(start = start, end = end)
            }
            .distinct()
            .sortedWith(compareBy(RichTextRange::start, RichTextRange::end, RichTextRange::style)),
    )

    fun updateText(newText: String): RichTextDocument {
        if (newText == text) return this

        val sharedPrefix = text.zip(newText).takeWhile { (old, new) -> old == new }.size
        val maximumSuffix = minOf(text.length - sharedPrefix, newText.length - sharedPrefix)
        var sharedSuffix = 0
        while (
            sharedSuffix < maximumSuffix &&
            text[text.lastIndex - sharedSuffix] == newText[newText.lastIndex - sharedSuffix]
        ) {
            sharedSuffix += 1
        }

        val oldChangedEnd = text.length - sharedSuffix
        val newChangedEnd = newText.length - sharedSuffix
        val delta = newText.length - text.length

        fun mapStart(position: Int): Int = when {
            position <= sharedPrefix -> position
            position >= oldChangedEnd -> position + delta
            else -> sharedPrefix
        }

        fun mapEnd(position: Int): Int = when {
            position <= sharedPrefix -> position
            position >= oldChangedEnd -> position + delta
            else -> newChangedEnd
        }

        return RichTextDocument(
            text = newText,
            ranges = ranges.mapNotNull { range ->
                val start = mapStart(range.start).coerceIn(0, newText.length)
                val end = mapEnd(range.end).coerceIn(start, newText.length)
                if (start == end) null else range.copy(start = start, end = end)
            },
        ).normalized()
    }

    fun toggle(style: RichTextStyle, start: Int, end: Int): RichTextDocument {
        val lower = minOf(start, end).coerceIn(0, text.length)
        val upper = maxOf(start, end).coerceIn(lower, text.length)
        if (lower == upper) return this

        val matching = ranges.filter { it.style == style && it.start < upper && it.end > lower }
        val fullyCovered = matching.any { it.start <= lower && it.end >= upper }
        val remaining = ranges.filterNot { it.style == style && it.start < upper && it.end > lower }.toMutableList()

        matching.forEach { range ->
            if (range.start < lower) remaining += range.copy(end = lower)
            if (range.end > upper) remaining += range.copy(start = upper)
        }

        if (!fullyCovered) {
            val mergedStart = matching.minOfOrNull { it.start }?.let { minOf(it, lower) } ?: lower
            val mergedEnd = matching.maxOfOrNull { it.end }?.let { maxOf(it, upper) } ?: upper
            remaining += RichTextRange(style, mergedStart, mergedEnd)
        }

        return copy(ranges = remaining).normalized()
    }

    fun hasStyle(style: RichTextStyle, start: Int, end: Int): Boolean {
        val lower = minOf(start, end).coerceIn(0, text.length)
        val upper = maxOf(start, end).coerceIn(lower, text.length)
        if (lower == upper) return ranges.any { it.style == style && lower in it.start..it.end }
        return ranges.any { it.style == style && it.start <= lower && it.end >= upper }
    }

    companion object {
        fun plain(text: String) = RichTextDocument(text = text)
    }
}
