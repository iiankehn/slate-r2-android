package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*

/** Lossless bridge for content supported by the R1 compatibility editor. */
object R2DocumentBridge {
    fun fromLegacy(document: Document): WordProcessingDocument {
        val body = document.body.normalized()
        val paragraphs = splitParagraphs(body).ifEmpty { listOf(ParagraphBlock(id = "${document.id}-p0")) }
        val images = body.ranges.filter { it.style == RichTextStyle.Image && !it.data.isNullOrBlank() }.mapIndexed { index, range ->
            ImageBlock(
                id = "${document.id}-image-$index",
                sourceUri = requireNotNull(range.data),
                description = body.text.substring(range.start.coerceAtMost(body.text.length), range.end.coerceAtMost(body.text.length)),
            )
        }
        return WordProcessingDocument(
            id = document.id,
            title = document.title,
            sections = listOf(DocumentSection(blocks = paragraphs + images)),
            metadata = DocumentMetadata(
                createdAtEpochMillis = document.updatedAtEpochMillis,
                updatedAtEpochMillis = document.updatedAtEpochMillis,
            ),
        )
    }

    fun toLegacyBody(document: WordProcessingDocument): RichTextDocument {
        val text = StringBuilder()
        val ranges = mutableListOf<RichTextRange>()
        document.sections.flatMap { it.blocks }.forEachIndexed { blockIndex, block ->
            if (blockIndex > 0) text.append('\n')
            when (block) {
                is ParagraphBlock -> {
                    val paragraphStart = text.length
                    block.runs.forEach { run ->
                        val start = text.length; text.append(run.text); val end = text.length
                        if (end > start) ranges += run.style.toLegacyRanges(start, end)
                    }
                    val end = text.length
                    if (end > paragraphStart) block.style.toLegacyStyle()?.let { ranges += RichTextRange(it, paragraphStart, end) }
                }
                is ImageBlock -> {
                    val start = text.length; text.append(block.description.ifBlank { "🖼 Image" })
                    ranges += RichTextRange(RichTextStyle.Image, start, text.length, block.sourceUri)
                }
                is TableBlock -> {
                    val start = text.length
                    text.append(block.rows.joinToString("\n") { row -> row.cells.joinToString(" | ") { cell -> cell.blocks.joinToString(" ") { paragraph -> paragraph.runs.joinToString("") { it.text } } } })
                    if (text.length > start) ranges += RichTextRange(RichTextStyle.Table, start, text.length)
                }
            }
        }
        return RichTextDocument(text.toString(), ranges).normalized()
    }

    private fun splitParagraphs(body: RichTextDocument): List<ParagraphBlock> {
        val starts = mutableListOf(0)
        body.text.forEachIndexed { index, character -> if (character == '\n') starts += index + 1 }
        return starts.mapIndexed { paragraphIndex, start ->
            val end = body.text.indexOf('\n', start).takeIf { it >= 0 } ?: body.text.length
            val cuts = sortedSetOf(start, end)
            body.ranges.filter { it.end > start && it.start < end }.forEach { range ->
                cuts += range.start.coerceIn(start, end); cuts += range.end.coerceIn(start, end)
            }
            val runs = cuts.zipWithNext().map { (runStart, runEnd) ->
                val active = body.ranges.filter { it.start <= runStart && it.end >= runEnd }
                TextRun(body.text.substring(runStart, runEnd), active.toCharacterStyle())
            }.ifEmpty { listOf(TextRun()) }
            val paragraphRanges = body.ranges.filter { it.start <= start && it.end >= end }
            ParagraphBlock("legacy-p$paragraphIndex", runs, paragraphRanges.toParagraphStyle())
        }
    }

    private fun List<RichTextRange>.toCharacterStyle(): CharacterStyle {
        val link = firstOrNull { it.style == RichTextStyle.Link }?.data
        return CharacterStyle(
            bold = any { it.style == RichTextStyle.Bold }, italic = any { it.style == RichTextStyle.Italic },
            underline = any { it.style == RichTextStyle.Underline } || link != null, link = link,
        )
    }

    private fun CharacterStyle.toLegacyRanges(start: Int, end: Int) = buildList {
        if (bold) add(RichTextRange(RichTextStyle.Bold, start, end))
        if (italic) add(RichTextRange(RichTextStyle.Italic, start, end))
        if (underline) add(RichTextRange(RichTextStyle.Underline, start, end))
        link?.let { add(RichTextRange(RichTextStyle.Link, start, end, it)) }
    }

    private fun List<RichTextRange>.toParagraphStyle() = ParagraphStyle(namedStyle = when {
        any { it.style == RichTextStyle.HeadingOne } -> NamedParagraphStyle.Heading1
        any { it.style == RichTextStyle.Quote } -> NamedParagraphStyle.Quote
        else -> NamedParagraphStyle.Normal
    })

    private fun ParagraphStyle.toLegacyStyle() = when (namedStyle) {
        NamedParagraphStyle.Heading1, NamedParagraphStyle.Title -> RichTextStyle.HeadingOne
        NamedParagraphStyle.Quote -> RichTextStyle.Quote
        else -> null
    }
}
