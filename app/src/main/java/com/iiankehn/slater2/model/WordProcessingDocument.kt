package com.iiankehn.slater2.model

/**
 * Device-independent document model for Slate Forge (R2).
 *
 * Dimensions are stored in points so the model is stable across Android display densities and
 * maps directly to PDF and print units. UI code is responsible for converting points to pixels.
 */
data class WordProcessingDocument(
    val id: String,
    val title: String,
    val sections: List<DocumentSection> = listOf(DocumentSection()),
    val metadata: DocumentMetadata = DocumentMetadata(),
) {
    init {
        require(id.isNotBlank()) { "A document id cannot be blank." }
        require(sections.isNotEmpty()) { "A word-processing document needs at least one section." }
    }
}

data class DocumentMetadata(
    val author: String = "",
    val subject: String = "",
    val keywords: Set<String> = emptySet(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    init {
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "A document cannot be updated before it was created."
        }
    }
}

data class DocumentSection(
    val page: PageSetup = PageSetup(),
    val blocks: List<DocumentBlock> = listOf(ParagraphBlock()),
    val header: List<ParagraphBlock> = emptyList(),
    val footer: List<ParagraphBlock> = emptyList(),
    val start: SectionStart = SectionStart.Continuous,
) {
    init {
        require(blocks.isNotEmpty()) { "A section needs at least one content block." }
    }
}

enum class SectionStart { Continuous, NextPage, OddPage, EvenPage }

data class PageSetup(
    val size: PageSize = PageSize.Letter,
    val orientation: PageOrientation = PageOrientation.Portrait,
    val margins: PageMargins = PageMargins(),
    val columns: Int = 1,
    val columnSpacingPoints: Float = 18f,
    val customWidthPoints: Float? = null,
    val customHeightPoints: Float? = null,
) {
    init {
        require(columns in 1..4) { "Slate Forge supports between one and four text columns." }
        require(columnSpacingPoints >= 0f) { "Column spacing cannot be negative." }
        require((customWidthPoints == null) == (customHeightPoints == null)) { "Custom page dimensions must be supplied together." }
        require(customWidthPoints == null || customWidthPoints in 144f..1440f) { "Custom page width must be between 2 and 20 inches." }
        require(customHeightPoints == null || customHeightPoints in 144f..1440f) { "Custom page height must be between 2 and 20 inches." }
        require(widthPoints > margins.startPoints + margins.endPoints) { "Horizontal margins must leave printable space." }
        require(heightPoints > margins.topPoints + margins.bottomPoints) { "Vertical margins must leave printable space." }
        require(columnWidthPoints > 0f) { "Columns and spacing must leave printable space." }
    }

    val widthPoints: Float
        get() {
            val portraitWidth = customWidthPoints ?: size.widthPoints
            val portraitHeight = customHeightPoints ?: size.heightPoints
            return if (orientation == PageOrientation.Portrait) portraitWidth else portraitHeight
        }

    val heightPoints: Float
        get() {
            val portraitWidth = customWidthPoints ?: size.widthPoints
            val portraitHeight = customHeightPoints ?: size.heightPoints
            return if (orientation == PageOrientation.Portrait) portraitHeight else portraitWidth
        }

    val contentWidthPoints: Float
        get() = widthPoints - margins.startPoints - margins.endPoints

    val contentHeightPoints: Float
        get() = heightPoints - margins.topPoints - margins.bottomPoints

    val columnWidthPoints: Float
        get() = (contentWidthPoints - columnSpacingPoints * (columns - 1)) / columns
}

enum class PageSize(val widthPoints: Float, val heightPoints: Float) {
    Letter(612f, 792f),
    Legal(612f, 1008f),
    A4(595f, 842f),
    A5(420f, 595f),
}

enum class PageOrientation { Portrait, Landscape }

data class PageMargins(
    val topPoints: Float = 72f,
    val endPoints: Float = 72f,
    val bottomPoints: Float = 72f,
    val startPoints: Float = 72f,
) {
    init {
        require(listOf(topPoints, endPoints, bottomPoints, startPoints).all { it >= 0f }) {
            "Page margins cannot be negative."
        }
    }
}

sealed interface DocumentBlock {
    val id: String
}

data class ParagraphBlock(
    override val id: String = "paragraph-0",
    val runs: List<TextRun> = listOf(TextRun()),
    val style: ParagraphStyle = ParagraphStyle(),
) : DocumentBlock {
    init {
        require(id.isNotBlank()) { "A paragraph id cannot be blank." }
        require(runs.isNotEmpty()) { "A paragraph needs at least one text run." }
    }
}

data class TableBlock(
    override val id: String,
    val rows: List<TableRow>,
    val headerRowCount: Int = 0,
) : DocumentBlock {
    init {
        require(id.isNotBlank()) { "A table id cannot be blank." }
        require(rows.isNotEmpty()) { "A table needs at least one row." }
        require(headerRowCount in 0..rows.size) { "Header rows must exist in the table." }
        val columnCount = rows.first().cells.size
        require(columnCount > 0 && rows.all { it.cells.size == columnCount }) {
            "Every table row must contain the same non-zero number of cells."
        }
    }
}

data class TableRow(val cells: List<TableCell>)

data class TableCell(
    val blocks: List<ParagraphBlock> = listOf(ParagraphBlock()),
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    init {
        require(blocks.isNotEmpty()) { "A table cell needs content." }
        require(columnSpan >= 0 && rowSpan >= 0) { "Table spans cannot be negative." }
        require((columnSpan == 0) == (rowSpan == 0)) { "Covered merge cells must use zero for both spans." }
    }
}

data class ImageBlock(
    override val id: String,
    val sourceUri: String,
    val description: String = "",
    val widthPoints: Float? = null,
    val heightPoints: Float? = null,
    val wrapping: ImageWrapping = ImageWrapping.Inline,
    val offsetXPoints: Float = 0f,
    val offsetYPoints: Float = 0f,
) : DocumentBlock {
    init {
        require(id.isNotBlank()) { "An image id cannot be blank." }
        require(sourceUri.isNotBlank()) { "An image needs a source URI." }
        require(widthPoints == null || widthPoints > 0f) { "Image width must be positive." }
        require(heightPoints == null || heightPoints > 0f) { "Image height must be positive." }
        require(offsetXPoints in -1440f..1440f && offsetYPoints in -1440f..1440f) { "Image offsets exceed the supported range." }
    }
}

enum class ImageWrapping { Inline, Square, BehindText, InFrontOfText }

data class TextRun(
    val text: String = "",
    val style: CharacterStyle = CharacterStyle(),
)

data class CharacterStyle(
    val fontFamily: String = "sans-serif",
    val fontSizePoints: Float = 11f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val foregroundArgb: Long = 0xFF111318,
    val backgroundArgb: Long? = null,
    val link: String? = null,
) {
    init {
        require(fontFamily.isNotBlank()) { "A font family cannot be blank." }
        require(fontSizePoints in 4f..288f) { "Font size must be between 4 and 288 points." }
    }
}

data class ParagraphStyle(
    val namedStyle: NamedParagraphStyle = NamedParagraphStyle.Normal,
    val alignment: ParagraphAlignment = ParagraphAlignment.Start,
    val lineSpacing: Float = 1.15f,
    val spaceBeforePoints: Float = 0f,
    val spaceAfterPoints: Float = 8f,
    val startIndentPoints: Float = 0f,
    val endIndentPoints: Float = 0f,
    val firstLineIndentPoints: Float = 0f,
    val keepWithNext: Boolean = false,
    val pageBreakBefore: Boolean = false,
    val list: ListStyle? = null,
) {
    init {
        require(lineSpacing in 0.5f..10f) { "Line spacing must be between 0.5 and 10." }
        require(spaceBeforePoints >= 0f && spaceAfterPoints >= 0f) {
            "Paragraph spacing cannot be negative."
        }
    }
}

enum class NamedParagraphStyle { Normal, Title, Subtitle, Heading1, Heading2, Heading3, Quote, Caption }

enum class ParagraphAlignment { Start, Center, End, Justify }

data class ListStyle(
    val kind: ListKind,
    val level: Int = 0,
    val startAt: Int = 1,
    val checked: Boolean = false,
) {
    init {
        require(level in 0..8) { "List nesting must be between zero and eight." }
        require(startAt > 0) { "Numbered lists must start with a positive value." }
    }
}

enum class ListKind { Bulleted, Numbered, Checklist }

/** Input sources are explicit so command routing never assumes a phone-only interaction model. */
enum class InputModality { Touch, Stylus, MouseTrackpad, HardwareKeyboard }

enum class R2FormFactor { Phone, Tablet, Foldable, GooglebookAndroid }
