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
                }
            }
        }
        val parsed = DocxImporter.parse(
            requireNotNull(documentXml) { "DOCX is missing word/document.xml." },
            relationshipsXml,
            fallbackTitle,
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

    fun exportDocx(document: WordProcessingDocument): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val body = document.sections.mapIndexed { index, section ->
                section.blocks.joinToString("") { it.toWordXml() } + section.toSectionXml(index < document.sections.lastIndex)
            }.joinToString("")
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>""".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun DocumentBlock.toWordXml(): String = when (this) {
        is ParagraphBlock -> {
            val paragraphProperties = buildString {
                append("<w:pPr>")
                if (style.namedStyle != NamedParagraphStyle.Normal) append("<w:pStyle w:val=\"").append(style.namedStyle.name).append("\"/>")
                append("<w:jc w:val=\"").append(when (style.alignment) { ParagraphAlignment.Start -> "left"; ParagraphAlignment.Center -> "center"; ParagraphAlignment.End -> "right"; ParagraphAlignment.Justify -> "both" }).append("\"/>")
                append("<w:spacing w:before=\"").append((style.spaceBeforePoints * 20).toInt()).append("\" w:after=\"").append((style.spaceAfterPoints * 20).toInt()).append("\"/>")
                if (style.pageBreakBefore) append("<w:pageBreakBefore/>")
                append("</w:pPr>")
            }
            "<w:p>$paragraphProperties${runs.joinToString("") { it.toWordXml() }}</w:p>"
        }
        is TableBlock -> "<w:tbl>${rows.joinToString("") { row -> "<w:tr>${row.cells.joinToString("") { cell -> "<w:tc>${cell.blocks.joinToString("") { it.toWordXml() }}</w:tc>" }}</w:tr>" }}</w:tbl>"
        is ImageBlock -> "<w:p><w:r><w:t xml:space=\"preserve\">${encodeXml(if (description.isBlank()) "[Image]" else "[Image: $description]")}</w:t></w:r></w:p>"
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

    private fun DocumentSection.toSectionXml(nextPage: Boolean): String {
        val width = page.widthPoints.times(20).toInt(); val height = page.heightPoints.times(20).toInt()
        val margins = page.margins
        return "<w:sectPr>${if (nextPage || startsOnNewPage) "<w:type w:val=\"nextPage\"/>" else ""}<w:pgSz w:w=\"$width\" w:h=\"$height\"/><w:pgMar w:top=\"${(margins.topPoints * 20).toInt()}\" w:right=\"${(margins.endPoints * 20).toInt()}\" w:bottom=\"${(margins.bottomPoints * 20).toInt()}\" w:left=\"${(margins.startPoints * 20).toInt()}\"/><w:cols w:num=\"${page.columns}\" w:space=\"${(page.columnSpacingPoints * 20).toInt()}\"/></w:sectPr>"
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
