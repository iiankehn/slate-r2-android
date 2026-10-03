package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned, bounded storage format for the complete R2 document model. */
object R2DocumentCodec {
    private const val MAGIC = 0x534C5232
    private const val VERSION = 4
    private const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
    private const val MAX_COLLECTION_SIZE = 100_000
    private const val MAX_STRING_BYTES = 8 * 1024 * 1024

    fun encode(document: WordProcessingDocument): String {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeString(document.id)
                data.writeString(document.title)
                data.writeMetadata(document.metadata)
                data.writeList(document.sections) { writeSection(it) }
            }
        }.toByteArray()
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "R2 document exceeds the 32 MB storage limit." }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(payload: String): WordProcessingDocument {
        val bytes = runCatching { Base64.getDecoder().decode(payload) }
            .getOrElse { throw IllegalArgumentException("R2 document payload is not valid Base64.", it) }
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "R2 document exceeds the 32 MB storage limit." }
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "R2 document has an invalid signature." }
            val version = data.readInt()
            require(version in 1..VERSION) { "Unsupported R2 document version: $version." }
            val document = WordProcessingDocument(
                id = data.readString(),
                title = data.readString(),
                metadata = data.readMetadata(),
                sections = data.readList { readSection(version) },
            )
            require(data.available() == 0) { "R2 document contains unexpected trailing data." }
            document
        }
    }

    private fun DataOutputStream.writeMetadata(value: DocumentMetadata) {
        writeString(value.author); writeString(value.subject)
        writeList(value.keywords.sorted()) { writeString(it) }
        writeLong(value.createdAtEpochMillis); writeLong(value.updatedAtEpochMillis)
    }

    private fun DataInputStream.readMetadata() = DocumentMetadata(
        author = readString(), subject = readString(), keywords = readList { readString() }.toSet(),
        createdAtEpochMillis = readLong(), updatedAtEpochMillis = readLong(),
    )

    private fun DataOutputStream.writeSection(value: DocumentSection) {
        writePage(value.page)
        writeList(value.blocks) { writeBlock(it) }
        writeList(value.header) { writeParagraph(it) }
        writeList(value.footer) { writeParagraph(it) }
        writeInt(value.start.ordinal)
    }

    private fun DataInputStream.readSection(version: Int) = DocumentSection(
        page = readPage(version), blocks = readList { readBlock(version) }, header = readList { readParagraph(version) },
        footer = readList { readParagraph(version) },
        start = if (version == 1) {
            if (readBoolean()) SectionStart.NextPage else SectionStart.Continuous
        } else readEnum(),
    )

    private fun DataOutputStream.writePage(value: PageSetup) {
        writeInt(value.size.ordinal); writeInt(value.orientation.ordinal)
        with(value.margins) { writeFloat(topPoints); writeFloat(endPoints); writeFloat(bottomPoints); writeFloat(startPoints) }
        writeInt(value.columns); writeFloat(value.columnSpacingPoints)
        writeNullableFloat(value.customWidthPoints); writeNullableFloat(value.customHeightPoints)
    }

    private fun DataInputStream.readPage(version: Int = VERSION): PageSetup {
        val size = readEnum<PageSize>(); val orientation = readEnum<PageOrientation>()
        val margins = PageMargins(readFloat(), readFloat(), readFloat(), readFloat())
        val columns = readInt(); val spacing = readFloat()
        return PageSetup(
            size, orientation, margins, columns, spacing,
            if (version >= 2) readNullableFloat() else null,
            if (version >= 2) readNullableFloat() else null,
        )
    }

    private fun DataOutputStream.writeBlock(value: DocumentBlock) = when (value) {
        is ParagraphBlock -> { writeByte(1); writeParagraph(value) }
        is TableBlock -> {
            writeByte(2); writeString(value.id); writeInt(value.headerRowCount)
            writeList(value.rows) { row -> writeList(row.cells) { cell ->
                writeInt(cell.columnSpan); writeInt(cell.rowSpan); writeList(cell.blocks) { writeParagraph(it) }
            } }
        }
        is ImageBlock -> {
            writeByte(3); writeString(value.id); writeString(value.sourceUri); writeString(value.description)
            writeNullableFloat(value.widthPoints); writeNullableFloat(value.heightPoints); writeInt(value.wrapping.ordinal)
            writeFloat(value.offsetXPoints); writeFloat(value.offsetYPoints)
        }
    }

    private fun DataInputStream.readBlock(version: Int): DocumentBlock = when (readUnsignedByte()) {
        1 -> readParagraph(version)
        2 -> {
            val id = readString(); val headerRows = readInt()
            TableBlock(id, readList { TableRow(readList {
                val columnSpan = readInt(); val rowSpan = readInt()
                TableCell(readList { readParagraph(version) }, columnSpan, rowSpan)
            }) }, headerRows)
        }
        3 -> ImageBlock(
            readString(), readString(), readString(), readNullableFloat(), readNullableFloat(), readEnum(),
            if (version >= 4) readFloat() else 0f,
            if (version >= 4) readFloat() else 0f,
        )
        else -> throw IllegalArgumentException("Unknown R2 block type.")
    }

    private fun DataOutputStream.writeParagraph(value: ParagraphBlock) {
        writeString(value.id); writeList(value.runs) { writeRun(it) }; writeParagraphStyle(value.style)
    }

    private fun DataInputStream.readParagraph(version: Int) = ParagraphBlock(readString(), readList { readRun() }, readParagraphStyle(version))

    private fun DataOutputStream.writeRun(value: TextRun) {
        writeString(value.text)
        with(value.style) {
            writeString(fontFamily); writeFloat(fontSizePoints); writeBoolean(bold); writeBoolean(italic)
            writeBoolean(underline); writeBoolean(strikeThrough); writeLong(foregroundArgb)
            writeNullableLong(backgroundArgb); writeNullableString(link)
        }
    }

    private fun DataInputStream.readRun(): TextRun {
        val text = readString()
        return TextRun(text, CharacterStyle(readString(), readFloat(), readBoolean(), readBoolean(), readBoolean(), readBoolean(), readLong(), readNullableLong(), readNullableString()))
    }

    private fun DataOutputStream.writeParagraphStyle(value: ParagraphStyle) = with(value) {
        writeInt(namedStyle.ordinal); writeInt(alignment.ordinal); writeFloat(lineSpacing)
        writeFloat(spaceBeforePoints); writeFloat(spaceAfterPoints); writeFloat(startIndentPoints)
        writeFloat(endIndentPoints); writeFloat(firstLineIndentPoints); writeBoolean(keepWithNext); writeBoolean(pageBreakBefore)
        writeBoolean(list != null); list?.let { writeInt(it.kind.ordinal); writeInt(it.level); writeInt(it.startAt); writeBoolean(it.checked) }
    }

    private fun DataInputStream.readParagraphStyle(version: Int): ParagraphStyle {
        val named = readEnum<NamedParagraphStyle>(); val alignment = readEnum<ParagraphAlignment>()
        val lineSpacing = readFloat(); val before = readFloat(); val after = readFloat()
        val start = readFloat(); val end = readFloat(); val first = readFloat()
        val keep = readBoolean(); val breakBefore = readBoolean()
        val list = if (readBoolean()) ListStyle(readEnum(), readInt(), readInt(), if (version >= 3) readBoolean() else false) else null
        return ParagraphStyle(named, alignment, lineSpacing, before, after, start, end, first, keep, breakBefore, list)
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "R2 text value is too large." }
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt(); require(size in 0..MAX_STRING_BYTES) { "Invalid R2 text length." }
        return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); value?.let { writeString(it) } }
    private fun DataInputStream.readNullableString() = if (readBoolean()) readString() else null
    private fun DataOutputStream.writeNullableFloat(value: Float?) { writeBoolean(value != null); value?.let(::writeFloat) }
    private fun DataInputStream.readNullableFloat() = if (readBoolean()) readFloat() else null
    private fun DataOutputStream.writeNullableLong(value: Long?) { writeBoolean(value != null); value?.let(::writeLong) }
    private fun DataInputStream.readNullableLong() = if (readBoolean()) readLong() else null

    private fun <T> DataOutputStream.writeList(values: List<T>, writeItem: DataOutputStream.(T) -> Unit) {
        require(values.size <= MAX_COLLECTION_SIZE) { "R2 collection is too large." }
        writeInt(values.size); values.forEach { writeItem(it) }
    }

    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> {
        val size = readInt(); require(size in 0..MAX_COLLECTION_SIZE) { "Invalid R2 collection size." }
        return List(size) { readItem() }
    }

    private inline fun <reified T : Enum<T>> DataInputStream.readEnum(): T {
        val values = enumValues<T>(); val ordinal = readInt()
        require(ordinal in values.indices) { "Invalid R2 enum value." }
        return values[ordinal]
    }
}
