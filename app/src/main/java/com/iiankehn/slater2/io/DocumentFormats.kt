package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedDocument(
    val title: String,
    val body: RichTextDocument,
    val warnings: List<String> = emptyList(),
    val wordProcessingDocument: WordProcessingDocument? = null,
)

data class DocxEmbeddedImage(
    val bytes: ByteArray,
    val extension: String,
    val contentType: String,
)

object DocumentFormats {
    const val MAX_DOCUMENT_BYTES = 25 * 1024 * 1024
    private const val MAX_DOCX_ENTRIES = 2_000
    private const val MAX_XML_BYTES = 20 * 1024 * 1024

    fun importText(bytes: ByteArray, fallbackTitle: String): ImportedDocument {
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "Document is larger than 25 MB." }
        return ImportedDocument(fallbackTitle, RichTextDocument.plain(bytes.toString(Charsets.UTF_8)))
    }

    fun exportText(body: RichTextDocument): ByteArray = body.text.toByteArray(Charsets.UTF_8)

    fun importMarkdown(bytes: ByteArray, fallbackTitle: String): ImportedDocument {
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "Document is larger than 25 MB." }
        val source = bytes.toString(Charsets.UTF_8)
        val output = StringBuilder()
        val ranges = mutableListOf<RichTextRange>()
        var title = fallbackTitle

        source.lines().forEachIndexed { index, rawLine ->
            var line = rawLine
            var heading = false
            var quote = false
            if (line.startsWith("# ")) {
                line = line.removePrefix("# ")
                heading = true
                if (title == fallbackTitle && line.isNotBlank()) title = line
            } else if (line.startsWith("> ")) {
                line = line.removePrefix("> ")
                quote = true
            }
            val lineStart = output.length
            val linkRegex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
            var cursor = 0
            linkRegex.findAll(line).forEach { match ->
                output.append(line.substring(cursor, match.range.first))
                val label = match.groupValues[1]
                val start = output.length
                output.append(label)
                ranges += RichTextRange(RichTextStyle.Link, start, output.length, match.groupValues[2])
                cursor = match.range.last + 1
            }
            output.append(line.substring(cursor))
            val lineEnd = output.length
            if (heading && lineEnd > lineStart) ranges += RichTextRange(RichTextStyle.HeadingOne, lineStart, lineEnd)
            if (quote && lineEnd > lineStart) ranges += RichTextRange(RichTextStyle.Quote, lineStart, lineEnd)
            if (index != source.lines().lastIndex) output.append('\n')
        }
        return ImportedDocument(title, RichTextDocument(output.toString(), ranges).normalized())
    }

    fun exportMarkdown(body: RichTextDocument): ByteArray {
        val normalized = body.normalized()
        val text = StringBuilder(normalized.text)
        normalized.ranges.sortedByDescending(RichTextRange::start).forEach { range ->
            when (range.style) {
                RichTextStyle.Bold -> wrap(text, range, "**", "**")
                RichTextStyle.Italic -> wrap(text, range, "_", "_")
                RichTextStyle.Underline -> wrap(text, range, "<u>", "</u>")
                RichTextStyle.Link -> wrap(text, range, "[", "](${range.data.orEmpty()})")
                RichTextStyle.HeadingOne -> text.insert(range.start, "# ")
                RichTextStyle.Quote -> text.insert(range.start, "> ")
                RichTextStyle.Image, RichTextStyle.Table -> Unit
            }
        }
        return text.toString().toByteArray(Charsets.UTF_8)
    }

    fun importDocx(bytes: ByteArray, fallbackTitle: String): ImportedDocument {
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "Document is larger than 25 MB." }
        var documentXml: ByteArray? = null
        var relationshipsXml: ByteArray? = null
        val marginParts = mutableMapOf<String, ByteArray>()
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                require(entries <= MAX_DOCX_ENTRIES) { "DOCX contains too many entries." }
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readLimited(MAX_XML_BYTES)
                } else if (entry.name == "word/_rels/document.xml.rels") {
                    relationshipsXml = zip.readLimited(MAX_XML_BYTES)
                } else if (entry.name.matches(Regex("word/(header|footer)\\d+\\.xml"))) {
                    marginParts[entry.name] = zip.readLimited(MAX_XML_BYTES)
                }
            }
        }
        val parsed = DocxImporter.parse(
            requireNotNull(documentXml) { "DOCX is missing word/document.xml." },
            relationshipsXml,
            fallbackTitle,
            marginParts,
        )
        return ImportedDocument(
            title = parsed.document.title,
            body = R2DocumentBridge.toLegacyBody(parsed.document),
            warnings = parsed.warnings,
            wordProcessingDocument = parsed.document,
        )
    }

    fun exportDocx(title: String, body: RichTextDocument): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val paragraphs = (listOf(title) + body.text.lines()).joinToString("") { line ->
                "<w:p><w:r><w:t xml:space=\"preserve\">${encodeXml(line)}</w:t></w:r></w:p>"
            }
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs</w:body></w:document>""".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    fun exportDocx(
        document: WordProcessingDocument,
        embeddedImages: Map<String, DocxEmbeddedImage> = emptyMap(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val imageBlocks = document.sections.flatMap { it.blocks }.filterIsInstance<ImageBlock>()
            .filter { it.id in embeddedImages }
        val imageRelationships = imageBlocks.mapIndexed { index, image -> image.id to "rIdImage${index + 1}" }.toMap()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            val marginOverrides = document.sections.mapIndexed { index, section ->
                buildString {
                    if (section.header.isNotEmpty()) append("<Override PartName=\"/word/header${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>")
                    if (section.footer.isNotEmpty()) append("<Override PartName=\"/word/footer${index + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>")
                }
            }.joinToString("")
            val imageDefaults = imageBlocks.mapNotNull { block -> embeddedImages[block.id] }
                .distinctBy { it.extension.lowercase() }
                .joinToString("") { "<Default Extension=\"${encodeXml(it.extension.lowercase())}\" ContentType=\"${encodeXml(it.contentType)}\"/>" }
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>$imageDefaults<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>$marginOverrides</Types>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            val marginRelationships = document.sections.mapIndexed { index, section ->
                buildString {
                    if (section.header.isNotEmpty()) append("<Relationship Id=\"rIdHeader${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"header${index + 1}.xml\"/>")
                    if (section.footer.isNotEmpty()) append("<Relationship Id=\"rIdFooter${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer${index + 1}.xml\"/>")
                }
            }.joinToString("")
            val imageRelsXml = imageBlocks.mapIndexed { index, block ->
                val image = embeddedImages.getValue(block.id)
                "<Relationship Id=\"rIdImage${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${index + 1}.${encodeXml(image.extension.lowercase())}\"/>"
            }.joinToString("")
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>$marginRelationships$imageRelsXml</Relationships>""".toByteArray())
            zip.closeEntry()
            imageBlocks.forEachIndexed { index, block ->
                val image = embeddedImages.getValue(block.id)
                zip.putNextEntry(ZipEntry("word/media/image${index + 1}.${image.extension.lowercase()}"))
                zip.write(image.bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("word/numbering.xml"))
            zip.write(numberingXml().toByteArray())
            zip.closeEntry()
            document.sections.forEachIndexed { index, section ->
                if (section.header.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry("word/header${index + 1}.xml"))
                    zip.write(marginPartXml("hdr", section.header).toByteArray())
                    zip.closeEntry()
                }
                if (section.footer.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry("word/footer${index + 1}.xml"))
                    zip.write(marginPartXml("ftr", section.footer).toByteArray())
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val body = document.sections.mapIndexed { index, section ->
                section.blocks.joinToString("") { it.toWordXml(imageRelationships) } + section.toSectionXml(index, index < document.sections.lastIndex)
            }.joinToString("")
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body</w:body></w:document>""".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun DocumentBlock.toWordXml(imageRelationships: Map<String, String> = emptyMap()): String = when (this) {
        is ParagraphBlock -> {
            val paragraphProperties = buildString {
                append("<w:pPr>")
                if (style.namedStyle != NamedParagraphStyle.Normal) append("<w:pStyle w:val=\"").append(style.namedStyle.name).append("\"/>")
                append("<w:jc w:val=\"").append(when (style.alignment) { ParagraphAlignment.Start -> "left"; ParagraphAlignment.Center -> "center"; ParagraphAlignment.End -> "right"; ParagraphAlignment.Justify -> "both" }).append("\"/>")
                append("<w:spacing w:before=\"").append((style.spaceBeforePoints * 20).toInt()).append("\" w:after=\"").append((style.spaceAfterPoints * 20).toInt()).append("\"/>")
                if (style.pageBreakBefore) append("<w:pageBreakBefore/>")
                style.list?.let { list ->
                    append("<w:numPr><w:ilvl w:val=\"").append(list.level).append("\"/><w:numId w:val=\"").append(list.kind.ordinal + 1).append("\"/></w:numPr>")
                }
                append("</w:pPr>")
            }
            "<w:p>$paragraphProperties${runs.joinToString("") { it.toWordXml() }}</w:p>"
        }
        is TableBlock -> "<w:tbl>${rows.mapIndexed { rowIndex, row -> "<w:tr>${if (rowIndex < headerRowCount) "<w:trPr><w:tblHeader/></w:trPr>" else ""}${row.cells.joinToString("") { cell -> "<w:tc><w:tcPr>${if (cell.columnSpan > 1) "<w:gridSpan w:val=\"${cell.columnSpan}\"/>" else ""}</w:tcPr>${cell.blocks.joinToString("") { it.toWordXml(imageRelationships) }}</w:tc>" }}</w:tr>" }.joinToString("")}</w:tbl>"
        is ImageBlock -> imageRelationships[id]?.let { relationshipId ->
            val width = ((widthPoints ?: 300f) * 12_700).toLong()
            val height = ((heightPoints ?: 200f) * 12_700).toLong()
            val numericId = id.hashCode().toLong().let { if (it < 0) -it else it }.coerceAtLeast(1)
            "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"$width\" cy=\"$height\"/><wp:docPr id=\"$numericId\" name=\"Picture\" descr=\"${encodeXml(description)}\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"$numericId\" name=\"Picture\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"$relationshipId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$width\" cy=\"$height\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        } ?: "<w:p><w:r><w:t xml:space=\"preserve\">${encodeXml(if (description.isBlank()) "[Image]" else "[Image: $description]")}</w:t></w:r></w:p>"
    }

    private fun TextRun.toWordXml(): String {
        val properties = buildString {
            append("<w:rPr><w:rFonts w:ascii=\"").append(encodeXml(style.fontFamily)).append("\"/>")
            append("<w:sz w:val=\"").append((style.fontSizePoints * 2).toInt()).append("\"/>")
            if (style.bold) append("<w:b/>"); if (style.italic) append("<w:i/>")
            if (style.underline) append("<w:u w:val=\"single\"/>"); if (style.strikeThrough) append("<w:strike/>")
            append("</w:rPr>")
        }
        return "<w:r>$properties<w:t xml:space=\"preserve\">${encodeXml(text)}</w:t></w:r>"
    }

    private fun DocumentSection.toSectionXml(index: Int, nextPage: Boolean): String {
        val width = page.widthPoints.times(20).toInt(); val height = page.heightPoints.times(20).toInt()
        val margins = page.margins
        val references = buildString {
            if (header.isNotEmpty()) append("<w:headerReference w:type=\"default\" r:id=\"rIdHeader${index + 1}\"/>")
            if (footer.isNotEmpty()) append("<w:footerReference w:type=\"default\" r:id=\"rIdFooter${index + 1}\"/>")
        }
        val sectionType = when {
            nextPage && start == SectionStart.Continuous -> "nextPage"
            start == SectionStart.Continuous -> "continuous"
            start == SectionStart.NextPage -> "nextPage"
            start == SectionStart.OddPage -> "oddPage"
            else -> "evenPage"
        }
        return "<w:sectPr>$references<w:type w:val=\"$sectionType\"/><w:pgSz w:w=\"$width\" w:h=\"$height\"/><w:pgMar w:top=\"${(margins.topPoints * 20).toInt()}\" w:right=\"${(margins.endPoints * 20).toInt()}\" w:bottom=\"${(margins.bottomPoints * 20).toInt()}\" w:left=\"${(margins.startPoints * 20).toInt()}\"/><w:cols w:num=\"${page.columns}\" w:space=\"${(page.columnSpacingPoints * 20).toInt()}\"/></w:sectPr>"
    }

    private fun marginPartXml(tag: String, paragraphs: List<ParagraphBlock>): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:$tag xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">${paragraphs.joinToString("") { it.toWordXml() }}</w:$tag>"

    private fun numberingXml(): String {
        fun levels(format: String, text: (Int) -> String): String = (0..8).joinToString("") { level ->
            "<w:lvl w:ilvl=\"$level\"><w:start w:val=\"1\"/><w:numFmt w:val=\"$format\"/><w:lvlText w:val=\"${text(level)}\"/><w:pPr><w:ind w:left=\"${720 * (level + 1)}\" w:hanging=\"360\"/></w:pPr></w:lvl>"
        }
        val bullet = levels("bullet") { listOf("•", "◦", "▪")[it % 3] }
        val decimal = levels("decimal") { "%${it + 1}." }
        val checklist = levels("bullet") { "☐" }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:numbering xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:abstractNum w:abstractNumId=\"0\">$bullet</w:abstractNum><w:abstractNum w:abstractNumId=\"1\">$decimal</w:abstractNum><w:abstractNum w:abstractNumId=\"2\">$checklist</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num><w:num w:numId=\"3\"><w:abstractNumId w:val=\"2\"/></w:num></w:numbering>"
    }

    private fun wrap(text: StringBuilder, range: RichTextRange, before: String, after: String) {
        text.insert(range.end, after)
        text.insert(range.start, before)
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "DOCX XML exceeds the safe decoding limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun encodeXml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

}
