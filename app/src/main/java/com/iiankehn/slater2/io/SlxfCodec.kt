package com.iiankehn.slater2.io

import com.iiankehn.slater2.model.WordProcessingDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SlxfDocument(
    val document: WordProcessingDocument,
    val sourceRevision: Long,
    val assets: Map<String, SlxAsset> = emptyMap(),
)

/** Forge-native package preserving the complete page, section, table, image and style model. */
object SlxfCodec {
    const val MIME_TYPE = "application/vnd.core.slate.slxf"
    const val FILE_EXTENSION = "slxf"
    const val FORMAT_VERSION = 1

    private const val MAGIC = 0x534C5846 // SLXF
    private const val MAX_PACKAGE_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRIES = 4_096
    private const val MANIFEST_ENTRY = "manifest.bin"
    private const val DOCUMENT_ENTRY = "forge.bin"

    fun encode(value: SlxfDocument): ByteArray {
        require(value.assets.keys.all { it.matches(Regex("[A-Za-z0-9._-]{1,120}")) }) { "Invalid SLXF asset id." }
        val output = ByteArrayOutputStream()
        val content = R2DocumentCodec.encodeBytes(value.document)
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(MANIFEST_ENTRY, manifestBytes(value, content))
            zip.writeEntry(DOCUMENT_ENTRY, content)
            value.assets.forEach { (blockId, asset) ->
                require(asset.bytes.size <= MAX_ENTRY_BYTES) { "SLXF asset is too large." }
                zip.writeEntry("assets/$blockId.${asset.extension.lowercase()}", asset.bytes)
            }
        }
        return output.toByteArray().also { require(it.size <= MAX_PACKAGE_BYTES) { "SLXF document exceeds the 64 MB limit." } }
    }

    fun decode(bytes: ByteArray): SlxfDocument {
        require(bytes.size <= MAX_PACKAGE_BYTES) { "SLXF document exceeds the 64 MB limit." }
        val entries = linkedMapOf<String, ByteArray>(); var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entries.size < MAX_ENTRIES) { "SLXF contains too many entries." }
                require(!entry.isDirectory && safeEntryName(entry.name)) { "SLXF contains an unsafe entry path." }
                require(entry.name !in entries) { "SLXF contains duplicate entries." }
                val payload = zip.readLimited(MAX_ENTRY_BYTES); total += payload.size
                require(total <= MAX_PACKAGE_BYTES) { "SLXF expanded data exceeds the 64 MB limit." }
                entries[entry.name] = payload
            }
        }
        val manifest = readManifest(requireNotNull(entries[MANIFEST_ENTRY]) { "SLXF is missing its manifest." })
        val content = requireNotNull(entries[DOCUMENT_ENTRY]) { "SLXF is missing its Forge document." }
        require(content.sha256().contentEquals(manifest.documentDigest)) { "SLXF Forge content failed checksum validation." }
        val document = R2DocumentCodec.decodeBytes(content)
        val assets = manifest.assets.associate { item ->
            val payload = requireNotNull(entries[item.entry]) { "SLXF is missing asset ${item.blockId}." }
            require(payload.sha256().contentEquals(item.digest)) { "SLXF asset ${item.blockId} failed checksum validation." }
            item.blockId to SlxAsset(item.blockId, item.extension, item.mimeType, payload)
        }
        return SlxfDocument(document, manifest.sourceRevision, assets)
    }

    private data class AssetManifest(val blockId: String, val extension: String, val mimeType: String, val entry: String, val digest: ByteArray)
    private data class Manifest(val sourceRevision: Long, val documentDigest: ByteArray, val assets: List<AssetManifest>)

    private fun manifestBytes(value: SlxfDocument, content: ByteArray) = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC); data.writeInt(FORMAT_VERSION); data.writeLong(value.sourceRevision)
            val contentDigest = content.sha256(); data.writeInt(contentDigest.size); data.write(contentDigest)
            data.writeInt(value.assets.size)
            value.assets.forEach { (blockId, asset) ->
                data.writeString(blockId); data.writeString(asset.extension.lowercase()); data.writeString(asset.mimeType)
                data.writeString("assets/$blockId.${asset.extension.lowercase()}")
                val digest = asset.bytes.sha256(); data.writeInt(digest.size); data.write(digest)
            }
        }
    }.toByteArray()

    private fun readManifest(bytes: ByteArray): Manifest = DataInputStream(ByteArrayInputStream(bytes)).use { data ->
        require(data.readInt() == MAGIC) { "SLXF has an invalid signature." }
        val version = data.readInt(); require(version in 1..FORMAT_VERSION) { "Unsupported SLXF version: $version." }
        val revision = data.readLong()
        val contentDigestSize = data.readInt(); require(contentDigestSize == 32) { "SLXF content checksum is invalid." }
        val contentDigest = ByteArray(contentDigestSize).also(data::readFully)
        val count = data.readInt(); require(count in 0..MAX_ENTRIES) { "Invalid SLXF asset count." }
        val assets = List(count) {
            val blockId = data.readString(); val extension = data.readString(); val mime = data.readString(); val entry = data.readString()
            require(safeEntryName(entry) && entry.startsWith("assets/")) { "SLXF asset path is unsafe." }
            val digestSize = data.readInt(); require(digestSize == 32) { "SLXF asset checksum is invalid." }
            AssetManifest(blockId, extension, mime, entry, ByteArray(digestSize).also(data::readFully))
        }
        Manifest(revision, contentDigest, assets)
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) { putNextEntry(ZipEntry(name)); write(bytes); closeEntry() }
    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8_192); var total = 0
        while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= limit) { "SLXF entry is too large." }; output.write(buffer, 0, count) }
        return output.toByteArray()
    }
    private fun DataOutputStream.writeString(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size <= 16 * 1024 * 1024); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.readString(): String { val size = readInt(); require(size in 0..16 * 1024 * 1024); return ByteArray(size).also(::readFully).toString(Charsets.UTF_8) }
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
    private fun safeEntryName(name: String) = name.isNotBlank() && !name.startsWith('/') && !name.contains("\\") && name.split('/').none { it == ".." }
}
