package com.iiankehn.slater2.layout

import com.iiankehn.slater2.model.CharacterStyle
import com.iiankehn.slater2.model.DocumentBlock
import com.iiankehn.slater2.model.DocumentSection
import com.iiankehn.slater2.model.ImageBlock
import com.iiankehn.slater2.model.PageSetup
import com.iiankehn.slater2.model.ParagraphBlock
import com.iiankehn.slater2.model.TableBlock
import com.iiankehn.slater2.model.TextRun
import com.iiankehn.slater2.model.WordProcessingDocument
import kotlin.math.max

data class PointRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class LaidOutTextLine(
    val text: String,
    val bounds: PointRect,
    val sourceStart: Int,
    val sourceEnd: Int,
)

enum class FragmentKind { Paragraph, Table, Image, Header, Footer }

data class LayoutFragment(
    val blockId: String,
    val kind: FragmentKind,
    val bounds: PointRect,
    val lines: List<LaidOutTextLine> = emptyList(),
    val continuedFromPrevious: Boolean = false,
    val continuesOnNext: Boolean = false,
)

data class LayoutColumn(
    val index: Int,
    val bounds: PointRect,
    val fragments: List<LayoutFragment>,
)

data class LayoutPage(
    val index: Int,
    val sectionIndex: Int,
    val setup: PageSetup,
    val columns: List<LayoutColumn>,
    val header: List<LayoutFragment>,
    val footer: List<LayoutFragment>,
)

data class DocumentLayout(
    val pages: List<LayoutPage>,
    val blockLocations: Map<String, List<Pair<Int, PointRect>>>,
) {
    val pageCount: Int get() = pages.size
}

interface TextMetrics {
    fun advance(character: Char, style: CharacterStyle): Float
    fun lineHeight(style: CharacterStyle): Float
}

/** Platform-neutral metrics keep page boundaries reproducible in UI, PDF, print, and tests. */
object DeterministicTextMetrics : TextMetrics {
    override fun advance(character: Char, style: CharacterStyle): Float {
        val widthFactor = when {
            character == '\t' -> 2f
            character.isWhitespace() -> 0.33f
            character in "ilI.,'`!|:;" -> 0.28f
            character in "MW@#%&" -> 0.86f
            character.code > 0x2FFF -> 1f
            else -> 0.54f
        }
        val emphasis = if (style.bold) 1.04f else 1f
        return style.fontSizePoints * widthFactor * emphasis
    }

    override fun lineHeight(style: CharacterStyle): Float = style.fontSizePoints * 1.2f
}

class DocumentLayoutEngine(
    private val metrics: TextMetrics = DeterministicTextMetrics,
) {
    fun layout(document: WordProcessingDocument): DocumentLayout {
        val pages = mutableListOf<MutablePage>()
        document.sections.forEachIndexed { sectionIndex, section ->
            val state = SectionFlow(sectionIndex, section, pages)
            section.blocks.forEachIndexed { blockIndex, block ->
                if (block is ParagraphBlock && block.style.pageBreakBefore && state.hasContent) state.nextPage()
                if (block is ParagraphBlock && block.style.keepWithNext) {
                    val next = section.blocks.getOrNull(blockIndex + 1)
                    val needed = estimate(block, section.page.columnWidthPoints) + (next?.let { estimate(it, section.page.columnWidthPoints) } ?: 0f)
                    if (needed <= state.columnHeight && needed > state.remainingHeight) state.nextColumn()
                }
                when (block) {
                    is ParagraphBlock -> flowParagraph(block, state)
                    is TableBlock -> flowTable(block, state)
                    is ImageBlock -> flowImage(block, state)
                }
            }
        }
        if (pages.isEmpty()) {
            val section = document.sections.first()
            pages += MutablePage(0, 0, section, createColumns(section.page))
        }
        val immutable = pages.map { it.freeze() }
        val locations = buildMap<String, MutableList<Pair<Int, PointRect>>> {
            immutable.forEach { page ->
                (page.columns.flatMap(LayoutColumn::fragments) + page.header + page.footer).forEach { fragment ->
                    getOrPut(fragment.blockId) { mutableListOf() } += page.index to fragment.bounds
                }
            }
        }
        return DocumentLayout(immutable, locations)
    }

    private fun flowParagraph(block: ParagraphBlock, state: SectionFlow) {
        val width = state.columnWidth - block.style.startIndentPoints - block.style.endIndentPoints
        val lines = breakLines(block.runs, width.coerceAtLeast(24f), block.style.lineSpacing)
        state.ensureSpace(block.style.spaceBeforePoints + lines.firstOrNull()?.bounds?.height.orZero())
        state.advance(block.style.spaceBeforePoints)
        var index = 0
        var continued = false
        while (index < lines.size) {
            if (state.remainingHeight < lines[index].bounds.height) state.nextColumn()
            val start = index
            val top = state.y
            while (index < lines.size && state.remainingHeight >= lines[index].bounds.height) {
                state.advance(lines[index].bounds.height)
                index += 1
            }
            var lineTop = top
            val positioned = lines.subList(start, index).map { line ->
                line.copy(bounds = PointRect(state.x + block.style.startIndentPoints, lineTop, state.x + block.style.startIndentPoints + width, lineTop + line.bounds.height))
                    .also { lineTop += line.bounds.height }
            }
            state.add(
                LayoutFragment(
                    blockId = block.id,
                    kind = FragmentKind.Paragraph,
                    bounds = PointRect(state.x, top, state.x + state.columnWidth, state.y),
                    lines = positioned,
                    continuedFromPrevious = continued,
                    continuesOnNext = index < lines.size,
                ),
            )
            continued = true
            if (index < lines.size) state.nextColumn()
        }
        if (lines.isEmpty()) {
            val height = metrics.lineHeight(block.runs.first().style) * block.style.lineSpacing
            state.ensureSpace(height)
            val top = state.y
            state.advance(height)
            state.add(LayoutFragment(block.id, FragmentKind.Paragraph, PointRect(state.x, top, state.x + state.columnWidth, state.y)))
        }
        state.advance(block.style.spaceAfterPoints.coerceAtMost(state.remainingHeight))
    }

    private fun flowTable(block: TableBlock, state: SectionFlow) {
        val rowHeights = block.rows.map { row ->
            max(24f, row.cells.maxOf { cell -> cell.blocks.sumOf { estimate(it, state.columnWidth / row.cells.size).toDouble() }.toFloat() + 8f })
                .coerceAtMost(state.columnHeight)
        }
        var row = 0
        var continued = false
        while (row < rowHeights.size) {
            state.ensureSpace(rowHeights[row])
            val top = state.y
            val start = row
            while (row < rowHeights.size && state.remainingHeight >= rowHeights[row]) {
                state.advance(rowHeights[row])
                row += 1
            }
            state.add(LayoutFragment(block.id, FragmentKind.Table, PointRect(state.x, top, state.x + state.columnWidth, state.y), continuedFromPrevious = continued, continuesOnNext = row < rowHeights.size))
            continued = true
            if (row < rowHeights.size) state.nextColumn()
            if (row == start) error("Table row could not be laid out.")
        }
    }

    private fun flowImage(block: ImageBlock, state: SectionFlow) {
        val width = (block.widthPoints ?: state.columnWidth).coerceAtMost(state.columnWidth)
        val requestedHeight = block.heightPoints ?: (width * 0.75f)
        val height = requestedHeight.coerceAtMost(state.columnHeight)
        state.ensureSpace(height)
        val top = state.y
        state.advance(height)
        state.add(LayoutFragment(block.id, FragmentKind.Image, PointRect(state.x, top, state.x + width, state.y)))
    }

    private fun breakLines(runs: List<TextRun>, maxWidth: Float, spacing: Float): List<LaidOutTextLine> {
        val characters = runs.flatMap { run -> run.text.map { it to run.style } }
        if (characters.isEmpty()) return emptyList()
        val lines = mutableListOf<LaidOutTextLine>()
        var start = 0
        while (start < characters.size) {
            var end = start
            var width = 0f
            var lastBreak = -1
            var maxHeight = 0f
            while (end < characters.size) {
                val (character, style) = characters[end]
                if (character == '\n') break
                val advance = metrics.advance(character, style)
                if (end > start && width + advance > maxWidth) break
                width += advance
                maxHeight = max(maxHeight, metrics.lineHeight(style) * spacing)
                if (character.isWhitespace() || character == '-') lastBreak = end + 1
                end += 1
            }
            if (end < characters.size && characters[end].first != '\n' && lastBreak > start) end = lastBreak
            if (end == start && characters[end].first != '\n') end += 1
            val text = characters.subList(start, end).joinToString("") { it.first.toString() }.trimEnd()
            val height = maxHeight.takeIf { it > 0f } ?: metrics.lineHeight(characters[start].second) * spacing
            lines += LaidOutTextLine(text, PointRect(0f, 0f, maxWidth, height), start, end)
            start = end
            if (start < characters.size && characters[start].first == '\n') start += 1
            while (start < characters.size && characters[start].first == ' ') start += 1
        }
        return lines
    }

    private fun estimate(block: DocumentBlock, width: Float): Float = when (block) {
        is ParagraphBlock -> block.style.spaceBeforePoints + breakLines(block.runs, width, block.style.lineSpacing).sumOf { it.bounds.height.toDouble() }.toFloat() + block.style.spaceAfterPoints
        is TableBlock -> block.rows.size * 28f
        is ImageBlock -> (block.heightPoints ?: ((block.widthPoints ?: width) * 0.75f)).coerceAtMost(720f)
    }

    private inner class SectionFlow(
        val sectionIndex: Int,
        val section: DocumentSection,
        private val pages: MutableList<MutablePage>,
    ) {
        private var page: MutablePage
        private var columnIndex = 0
        var y = 0f
            private set

        init {
            val previous = pages.lastOrNull()
            page = if (previous == null || section.startsOnNewPage || previous.setup != section.page) createPage() else previous
            columnIndex = if (page === previous) previous.columns.indexOfLast { it.fragments.isNotEmpty() }.coerceAtLeast(0) else 0
            y = page.columns[columnIndex].fragments.lastOrNull()?.bounds?.bottom ?: page.columns[columnIndex].bounds.top
        }

        val column get() = page.columns[columnIndex]
        val x get() = column.bounds.left
        val columnWidth get() = column.bounds.width
        val columnHeight get() = column.bounds.height
        val remainingHeight get() = column.bounds.bottom - y
        val hasContent get() = page.columns.any { it.fragments.isNotEmpty() }

        fun add(fragment: LayoutFragment) { column.fragments += fragment }
        fun advance(points: Float) { y += points }
        fun ensureSpace(points: Float) { if (points > remainingHeight && hasContent) nextColumn() }

        fun nextColumn() {
            if (columnIndex + 1 < page.columns.size) {
                columnIndex += 1
                y = column.bounds.top
            } else {
                nextPage()
            }
        }

        fun nextPage() {
            page = createPage()
            columnIndex = 0
            y = column.bounds.top
        }

        private fun createPage(): MutablePage {
            val created = MutablePage(pages.size, sectionIndex, section, createColumns(section.page))
            pages += created
            return created
        }
    }

    private data class MutableColumn(val index: Int, val bounds: PointRect, val fragments: MutableList<LayoutFragment> = mutableListOf())

    private inner class MutablePage(
        val index: Int,
        val sectionIndex: Int,
        val section: DocumentSection,
        val columns: List<MutableColumn>,
    ) {
        val setup get() = section.page
        fun freeze(): LayoutPage = LayoutPage(
            index,
            sectionIndex,
            setup,
            columns.map { LayoutColumn(it.index, it.bounds, it.fragments.toList()) },
            layOutMarginBlocks(section.header, FragmentKind.Header, setup.margins.topPoints),
            layOutMarginBlocks(section.footer, FragmentKind.Footer, setup.heightPoints - setup.margins.bottomPoints),
        )

        private fun layOutMarginBlocks(blocks: List<ParagraphBlock>, kind: FragmentKind, baseline: Float): List<LayoutFragment> {
            val broken = blocks.map { it to breakLines(it.runs, setup.contentWidthPoints, it.style.lineSpacing) }
            val totalHeight = broken.sumOf { (_, lines) -> lines.sumOf { it.bounds.height.toDouble() } }.toFloat()
            var blockTop = if (kind == FragmentKind.Header) max(0f, baseline - totalHeight - 6f) else baseline + 6f
            return broken.map { (block, lines) ->
                var lineTop = blockTop
                val positioned = lines.map { line ->
                    line.copy(bounds = PointRect(setup.margins.startPoints, lineTop, setup.widthPoints - setup.margins.endPoints, lineTop + line.bounds.height))
                        .also { lineTop += line.bounds.height }
                }
                LayoutFragment(block.id, kind, PointRect(setup.margins.startPoints, blockTop, setup.widthPoints - setup.margins.endPoints, lineTop), positioned)
                    .also { blockTop = lineTop }
            }
        }
    }

    private fun createColumns(setup: PageSetup): List<MutableColumn> = List(setup.columns) { index ->
        val left = setup.margins.startPoints + index * (setup.columnWidthPoints + setup.columnSpacingPoints)
        MutableColumn(index, PointRect(left, setup.margins.topPoints, left + setup.columnWidthPoints, setup.heightPoints - setup.margins.bottomPoints))
    }
}

private fun Float?.orZero(): Float = this ?: 0f
