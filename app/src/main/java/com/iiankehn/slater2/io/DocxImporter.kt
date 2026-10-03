package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal data class ParsedDocx(
    val document: WordProcessingDocument,
    val warnings: List<String>,
    val embeddedImages: List<ImportedEmbeddedImage> = emptyList(),
)

/** Bounded OOXML reader for Word content that Slate can represent without executing package data. */
internal object DocxImporter {
    fun parse(
        documentXml: ByteArray,
        relationshipsXml: ByteArray?,
        fallbackTitle: String,
        marginParts: Map<String, ByteArray> = emptyMap(),
        mediaParts: Map<String, ByteArray> = emptyMap(),
    ): ParsedDocx {
        val root = parseXml(documentXml)
        val relationships = relationshipsXml?.let { parseRelationships(it) }.orEmpty()
        val body = root.elements().firstOrNull { it.localName == "body" }
            ?: error("DOCX is missing the Word document body.")
        var blockNumber = 0
        var containsDrawing = false
        val importedImages = mutableListOf<ImportedEmbeddedImage>()
        val parsedBlocks = body.elements().mapNotNull { element ->
            when (element.localName) {
                "p" -> {
                    val drawing = element.descendants("drawing").isNotEmpty() || element.descendants("pict").isNotEmpty()
                    containsDrawing = containsDrawing || drawing
                    val blip = element.descendants("blip").firstOrNull()
                    val relationshipId = blip?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                        .orEmpty().ifBlank { blip?.getAttribute("r:embed").orEmpty() }.ifBlank { null }
                    val target = relationshipId?.let(relationships::get)
                    val path = target?.let { if (it.startsWith("word/")) it else "word/${it.removePrefix("/")}" }
                    val bytes = path?.let(mediaParts::get)
                    if (bytes != null) {
                        val id = "image-${blockNumber++}"
                        val extension = path.substringAfterLast('.', "bin").lowercase()
                        importedImages += ImportedEmbeddedImage(id, bytes, extension)
                        val description = element.descendants("docPr").firstOrNull()?.getAttribute("descr").orEmpty()
                        ImageBlock(id, "slate-import://$id", description)
                    } else parseParagraph(element, "paragraph-${blockNumber++}", relationships)
                }
                "tbl" -> parseTable(element, "table-${blockNumber++}", relationships)
                else -> null
            }
        }
        val blocks = if (parsedBlocks.any { it is ParagraphBlock }) parsedBlocks else parsedBlocks + ParagraphBlock("paragraph-${blockNumber++}")
        val sectionProperties = body.descendants("sectPr").lastOrNull()
        val setup = sectionProperties?.let(::parsePageSetup) ?: PageSetup()
        fun marginBlocks(kind: String): List<ParagraphBlock> {
            val reference = sectionProperties?.child("${kind}Reference") ?: return emptyList()
            val relationshipId = reference.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                .ifBlank { reference.getAttribute("r:id") }
            val target = relationships[relationshipId] ?: return emptyList()
            val path = if (target.startsWith("word/")) target else "word/${target.removePrefix("/")}"
            val xml = marginParts[path] ?: return emptyList()
            return parseXml(xml).elements("p").mapIndexed { index, paragraph ->
                parseParagraph(paragraph, "$kind-$index", relationships)
            }
        }
        val header = marginBlocks("header")
        val footer = marginBlocks("footer")
        val firstText = blocks.filterIsInstance<ParagraphBlock>().firstOrNull { it.runs.any { run -> run.text.isNotBlank() } }
            ?.runs?.joinToString("") { it.text }?.trim()
        val title = firstText?.takeIf { it.length <= 160 } ?: fallbackTitle
        val document = WordProcessingDocument(
            id = "imported-docx",
            title = title,
            sections = listOf(DocumentSection(
                page = setup,
                blocks = blocks,
                header = header,
                footer = footer,
                start = when (sectionProperties?.child("type")?.attribute("val")) {
                    "nextPage" -> SectionStart.NextPage
                    "oddPage" -> SectionStart.OddPage
                    "evenPage" -> SectionStart.EvenPage
                    else -> SectionStart.Continuous
                },
            )),
        )
        val warnings = buildList {
            if (containsDrawing && importedImages.isEmpty()) add("Embedded drawings were detected but could not be extracted.")
            else if (importedImages.isNotEmpty()) add("Embedded pictures were imported; review their placement and wrapping.")
            if ((header.isEmpty() && relationships.values.any { it.contains("header", true) }) ||
                (footer.isEmpty() && relationships.values.any { it.contains("footer", true) })) {
                add("Some Word headers or footers could not be imported and require review.")
            }
            add("Review imported pagination because Word font metrics may differ from Slate.")
        }
        return ParsedDocx(document, warnings, importedImages)
    }

    private fun parseParagraph(element: Element, id: String, relationships: Map<String, String>): ParagraphBlock {
        val properties = element.child("pPr")
        val runs = element.elements().flatMap { child ->
            when (child.localName) {
                "r" -> listOf(parseRun(child))
                "hyperlink" -> {
                    val relationshipId = child.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        .ifBlank { child.getAttribute("r:id") }
                    child.elements("r").map { parseRun(it, link = relationships[relationshipId]) }
                }
                else -> emptyList()
            }
        }.filter { it.text.isNotEmpty() }.ifEmpty { listOf(TextRun()) }
        return ParagraphBlock(id, runs, parseParagraphStyle(properties))
    }

    private fun parseRun(element: Element, link: String? = null): TextRun {
        val properties = element.child("rPr")
        val text = buildString {
            element.elements().forEach { child ->
                when (child.localName) {
                    "t", "instrText" -> append(child.textContent)
                    "tab" -> append('\t')
                    "br", "cr" -> append('\n')
                }
            }
        }
        val size = properties?.child("sz")?.attribute("val")?.toFloatOrNull()?.div(2f)?.coerceIn(4f, 288f) ?: 11f
        val color = properties?.child("color")?.attribute("val")?.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }
            ?.toLong(16)?.let { 0xFF000000 or it } ?: 0xFF111318
        return TextRun(text, CharacterStyle(
            fontFamily = properties?.child("rFonts")?.attribute("ascii")?.ifBlank { null } ?: "sans-serif",
            fontSizePoints = size,
            bold = properties.hasEnabled("b"), italic = properties.hasEnabled("i"),
            underline = properties?.child("u")?.attribute("val")?.let { it != "none" } ?: false,
            strikeThrough = properties.hasEnabled("strike"), foregroundArgb = color, link = link,
        ))
    }

    private fun parseParagraphStyle(properties: Element?): ParagraphStyle {
        val named = when (properties?.child("pStyle")?.attribute("val")?.lowercase()) {
            "title" -> NamedParagraphStyle.Title
            "subtitle" -> NamedParagraphStyle.Subtitle
            "heading1", "heading 1" -> NamedParagraphStyle.Heading1
            "heading2", "heading 2" -> NamedParagraphStyle.Heading2
            "heading3", "heading 3" -> NamedParagraphStyle.Heading3
            "quote", "intensequote" -> NamedParagraphStyle.Quote
            "caption" -> NamedParagraphStyle.Caption
            else -> NamedParagraphStyle.Normal
        }
        val spacing = properties?.child("spacing")
        val indent = properties?.child("ind")
        val numbering = properties?.child("numPr")
        return ParagraphStyle(
            namedStyle = named,
            alignment = when (properties?.child("jc")?.attribute("val")) {
                "center" -> ParagraphAlignment.Center
                "right", "end" -> ParagraphAlignment.End
                "both", "distribute" -> ParagraphAlignment.Justify
                else -> ParagraphAlignment.Start
            },
            lineSpacing = spacing?.attribute("line")?.toFloatOrNull()?.div(240f)?.coerceIn(0.5f, 10f) ?: 1.15f,
            spaceBeforePoints = spacing.twips("before"), spaceAfterPoints = spacing.twips("after", 8f),
            startIndentPoints = indent.twips("start", indent.twips("left")),
            endIndentPoints = indent.twips("end", indent.twips("right")),
            firstLineIndentPoints = indent.twips("firstLine"),
            keepWithNext = properties?.child("keepNext") != null,
            pageBreakBefore = properties?.child("pageBreakBefore") != null,
            list = numbering?.let { ListStyle(ListKind.Numbered, it.child("ilvl")?.attribute("val")?.toIntOrNull()?.coerceIn(0, 8) ?: 0) },
        )
    }

    private fun parseTable(element: Element, id: String, relationships: Map<String, String>): TableBlock {
        var paragraphNumber = 0
        val parsedRows = element.elements("tr").map { row ->
            TableRow(row.elements("tc").map { cell ->
                val span = cell.child("tcPr")?.child("gridSpan")?.attribute("val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val paragraphs = cell.elements("p").map { parseParagraph(it, "$id-cell-${paragraphNumber++}", relationships) }
                    .ifEmpty { listOf(ParagraphBlock("$id-cell-${paragraphNumber++}")) }
                TableCell(paragraphs, columnSpan = span)
            })
        }.filter { it.cells.isNotEmpty() }
        val columnCount = parsedRows.maxOfOrNull { it.cells.size } ?: 1
        val rows = parsedRows.map { row -> row.copy(cells = row.cells + List(columnCount - row.cells.size) { TableCell() }) }
        return TableBlock(id, rows.ifEmpty { listOf(TableRow(listOf(TableCell()))) })
    }

    private fun parsePageSetup(element: Element): PageSetup {
        val size = element.child("pgSz")
        var width = size?.attribute("w")?.toFloatOrNull()?.div(20f) ?: PageSize.Letter.widthPoints
        var height = size?.attribute("h")?.toFloatOrNull()?.div(20f) ?: PageSize.Letter.heightPoints
        val landscape = size?.attribute("orient") == "landscape" || width > height
        if (landscape && width > height) { val swap = width; width = height; height = swap }
        val pageSize = PageSize.entries.minBy { kotlin.math.abs(it.widthPoints - width) + kotlin.math.abs(it.heightPoints - height) }
        val custom = kotlin.math.abs(pageSize.widthPoints - width) + kotlin.math.abs(pageSize.heightPoints - height) > 4f
        val margins = element.child("pgMar")
        val columns = element.child("cols")
        return PageSetup(
            size = pageSize,
            orientation = if (landscape) PageOrientation.Landscape else PageOrientation.Portrait,
            margins = PageMargins(margins.twips("top", 72f), margins.twips("right", 72f), margins.twips("bottom", 72f), margins.twips("left", 72f)),
            columns = columns?.attribute("num")?.toIntOrNull()?.coerceIn(1, 4) ?: 1,
            columnSpacingPoints = columns.twips("space", 18f),
            customWidthPoints = width.takeIf { custom },
            customHeightPoints = height.takeIf { custom },
        )
    }

    private fun parseRelationships(xml: ByteArray): Map<String, String> = parseXml(xml).elements()
        .mapNotNull { relationship ->
            relationship.getAttribute("Id").takeIf(String::isNotBlank)?.let { it to relationship.getAttribute("Target") }
        }.toMap()

    private fun parseXml(bytes: ByteArray): Element {
        val source = bytes.toString(Charsets.UTF_8)
        require(!source.contains("<!DOCTYPE", true) && !source.contains("<!ENTITY", true)) { "DOCX contains prohibited XML declarations." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.elements(localName: String? = null): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }.filterIsInstance<Element>().filter { localName == null || it.localName == localName }
    private fun Element.child(localName: String): Element? = elements(localName).firstOrNull()
    private fun Element.descendants(localName: String): List<Element> = getElementsByTagNameNS("*", localName).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
    }
    private fun Element.attribute(localName: String): String = getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", localName)
        .ifBlank { getAttribute("w:$localName") }.ifBlank { getAttribute(localName) }
    private fun Element?.hasEnabled(name: String): Boolean = this?.child(name)?.attribute("val")?.let { it !in setOf("0", "false", "off") } ?: (this?.child(name) != null)
    private fun Element?.twips(name: String, default: Float = 0f): Float = this?.attribute(name)?.toFloatOrNull()?.div(20f)?.coerceAtLeast(0f) ?: default
}
