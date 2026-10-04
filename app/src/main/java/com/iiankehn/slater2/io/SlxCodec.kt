package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextRange
import com.iiankehn.slater2.model.RichTextStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SlxAsset(
    val id: String,
    val extension: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class SlxDocument(
    val title: String,
    val body: RichTextDocument,
    val sourceDocumentId: String,
    val sourceRevision: Long,
    val sourceProduct: String = "notes",
    val createdAtEpochMillis: Long = sourceRevision,
    val updatedAtEpochMillis: Long = sourceRevision,
    val assets: List<SlxAsset> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Portable Slate rich-text package shared by Slate Notes and Slate Forge. */
object SlxCodec {
    const val MIME_TYPE = "application/vnd.core.slate.slx"
    const val FILE_EXTENSION = "slx"
    const val FORMAT_VERSION = 1

    private const val MAGIC = 0x534C5831 // SLX1
    private const val MAX_PACKAGE_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 25 * 1024 * 1024
    private const val MAX_ENTRIES = 2_048
    private const val MAX_RANGES = 100_000
    private const val MAX_STRING_BYTES = 16 * 1024 * 1024
    private const val MANIFEST_ENTRY = "manifest.bin"
    private const val DOCUMENT_ENTRY = "document.bin"

    fun encode(document: SlxDocument): ByteArray {
        require(document.assets.map { it.id }.distinct().size == document.assets.size) { "SLX asset ids must be unique." }
        val output = ByteArrayOutputStream()
        val content = documentBytes(document.body.normalized())
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(MANIFEST_ENTRY, manifestBytes(document, content))
            zip.writeEntry(DOCUMENT_ENTRY, content)
            document.assets.forEach { asset ->
                require(asset.id.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Invalid SLX asset id." }
                require(asset.extension.matches(Regex("[A-Za-z0-9]{1,8}"))) { "Invalid SLX asset extension." }
                require(asset.bytes.size <= MAX_ENTRY_BYTES) { "SLX asset is too large." }
                zip.writeEntry("assets/${asset.id}.${asset.extension.lowercase()}", asset.bytes)
            }
        }
        return output.toByteArray().also {
            require(it.size <= MAX_PACKAGE_BYTES) { "SLX document exceeds the 32 MB limit." }
        }
    }

    fun decode(bytes: ByteArray): SlxDocument {
        require(bytes.size <= MAX_PACKAGE_BYTES) { "SLX document exceeds the 32 MB limit." }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entries.size < MAX_ENTRIES) { "SLX contains too many entries." }
                require(!entry.isDirectory && safeEntryName(entry.name)) { "SLX contains an unsafe entry path." }
                require(entry.name !in entries) { "SLX contains duplicate entries." }
                val payload = zip.readLimited(MAX_ENTRY_BYTES)
                total += payload.size
                require(total <= MAX_PACKAGE_BYTES) { "SLX expanded data exceeds the 32 MB limit." }
                entries[entry.name] = payload
            }
        }
        val manifest = readManifest(requireNotNull(entries[MANIFEST_ENTRY]) { "SLX is missing its manifest." })
        val content = requireNotNull(entries[DOCUMENT_ENTRY]) { "SLX is missing its rich-text content." }
        require(content.sha256().contentEquals(manifest.documentDigest)) { "SLX rich-text content failed checksum validation." }
        val body = readDocument(content)
        val assets = manifest.assets.map { item ->
            val payload = requireNotNull(entries[item.entry]) { "SLX is missing asset ${item.id}." }
            require(payload.sha256().contentEquals(item.digest)) { "SLX asset ${item.id} failed checksum validation." }
            SlxAsset(item.id, item.extension, item.mimeType, payload)
        }
        return SlxDocument(
            title = manifest.title,
            body = body,
            sourceDocumentId = manifest.sourceDocumentId,
            sourceRevision = manifest.sourceRevision,
            sourceProduct = manifest.sourceProduct,
            createdAtEpochMillis = manifest.createdAt,
            updatedAtEpochMillis = manifest.updatedAt,
            assets = assets,
            warnings = manifest.warnings,
        )
    }

    private data class AssetManifest(val id: String, val extension: String, val mimeType: String, val entry: String, val digest: ByteArray)
    private data class Manifest(
        val title: String,
        val sourceDocumentId: String,
        val sourceRevision: Long,
        val sourceProduct: String,
        val createdAt: Long,
        val updatedAt: Long,
        val documentDigest: ByteArray,
        val assets: List<AssetManifest>,
        val warnings: List<String>,
    )

    private fun manifestBytes(document: SlxDocument, content: ByteArray) = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC); data.writeInt(FORMAT_VERSION)
            data.writeString(document.title); data.writeString(document.sourceDocumentId)
            data.writeLong(document.sourceRevision); data.writeString(document.sourceProduct)
            data.writeLong(document.createdAtEpochMillis); data.writeLong(document.updatedAtEpochMillis)
            val contentDigest = content.sha256(); data.writeInt(contentDigest.size); data.write(contentDigest)
            data.writeInt(document.assets.size)
            document.assets.forEach { asset ->
                data.writeString(asset.id); data.writeString(asset.extension.lowercase()); data.writeString(asset.mimeType)
                data.writeString("assets/${asset.id}.${asset.extension.lowercase()}")
                val digest = asset.bytes.sha256(); data.writeInt(digest.size); data.write(digest)
            }
            data.writeInt(document.warnings.size); document.warnings.forEach(data::writeString)
        }
    }.toByteArray()

    private fun readManifest(bytes: ByteArray): Manifest = DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        require(data.readInt() == MAGIC) { "SLX has an invalid signature." }
        val version = data.readInt(); require(version in 1..FORMAT_VERSION) { "Unsupported SLX version: $version." }
        val title = data.readString(); val sourceId = data.readString(); val revision = data.readLong()
        val sourceProduct = data.readString(); val created = data.readLong(); val updated = data.readLong()
        val contentDigestSize = data.readInt(); require(contentDigestSize == 32) { "SLX content checksum is invalid." }
        val contentDigest = ByteArray(contentDigestSize).also(data::readFully)
        val assetCount = data.readCount(MAX_ENTRIES)
        val assets = List(assetCount) {
            val id = data.readString(); val extension = data.readString(); val mime = data.readString(); val entry = data.readString()
            require(safeEntryName(entry) && entry.startsWith("assets/")) { "SLX asset path is unsafe." }
            val digestSize = data.readInt(); require(digestSize == 32) { "SLX asset checksum is invalid." }
            AssetManifest(id, extension, mime, entry, ByteArray(digestSize).also(data::readFully))
        }
        val warningCount = data.readCount(1_000)
        Manifest(title, sourceId, revision, sourceProduct, created, updated, contentDigest, assets, List(warningCount) { data.readString() })
    }

    private fun documentBytes(body: RichTextDocument) = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeString(body.text); data.writeInt(body.ranges.size)
            body.ranges.forEach { range ->
                data.writeString(range.style.name); data.writeInt(range.start); data.writeInt(range.end)
                data.writeBoolean(range.data != null); range.data?.let(data::writeString)
            }
        }
    }.toByteArray()

    private fun readDocument(bytes: ByteArray): RichTextDocument = DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        val text = data.readString(); val count = data.readCount(MAX_RANGES)
        val ranges = buildList {
            repeat(count) {
                val styleName = data.readString(); val start = data.readInt(); val end = data.readInt()
                val value = if (data.readBoolean()) data.readString() else null
                RichTextStyle.entries.firstOrNull { it.name == styleName }?.let { add(RichTextRange(it, start, end, value)) }
            }
        }
        RichTextDocument(text, ranges).normalized()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8_192); var total = 0
        while (true) {
            val count = read(buffer); if (count < 0) break
            total += count; require(total <= limit) { "SLX entry exceeds its safe decoding limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size <= MAX_STRING_BYTES) { "SLX text value is too large." }
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt(); require(size in 0..MAX_STRING_BYTES) { "Invalid SLX text length." }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readCount(maximum: Int): Int = readInt().also { require(it in 0..maximum) { "Invalid SLX collection size." } }
    private fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)
    private fun safeEntryName(name: String) = name.isNotBlank() && !name.startsWith('/') && !name.contains("\\") && name.split('/').none { it == ".." }
}
