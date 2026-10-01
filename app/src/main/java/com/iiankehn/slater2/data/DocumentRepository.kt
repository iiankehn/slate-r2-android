package com.iiankehn.slater2.data

import com.iiankehn.slater2.model.Document
import com.iiankehn.slater2.model.RichTextDocument
import com.iiankehn.slater2.model.RichTextRange
import com.iiankehn.slater2.model.RichTextStyle
import com.iiankehn.slater2.io.R2DocumentBridge
import com.iiankehn.slater2.io.R2DocumentCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepository(
    private val dao: SlateDao,
) {
    val documents: Flow<List<Document>> = dao.observeDocuments().map { rows ->
        rows.map(DocumentWithRanges::toModel)
    }

    suspend fun initialize(starterDocuments: List<Document>) {
        recoverInterruptedWrites()
        if (dao.documentCount() == 0) {
            starterDocuments.forEach { save(it, journalFirst = false) }
        }
    }

    suspend fun save(document: Document, journalFirst: Boolean = true) {
        val normalized = document.copy(body = document.body.normalized())
        if (journalFirst) checkpoint(normalized)
        dao.replaceDocument(normalized.toEntity(), normalized.toRangeEntities())
    }

    suspend fun checkpoint(document: Document) {
        val normalized = document.copy(body = document.body.normalized())
        dao.insertRecovery(normalized.toRecovery())
        dao.trimRecovery(normalized.id, RECOVERY_LIMIT)
    }

    suspend fun delete(document: Document) {
        dao.insertRecovery(
            document.copy(
                updatedAtEpochMillis = maxOf(System.currentTimeMillis(), document.updatedAtEpochMillis + 1),
            ).toRecovery(isDeletion = true),
        )
        dao.trimRecovery(document.id, RECOVERY_LIMIT)
        dao.deleteDocument(document.id)
    }

    suspend fun history(documentId: String): List<Document> =
        dao.getRecoveryEntries(documentId).map(RecoveryEntryEntity::toModel)

    suspend fun permanentlyDelete(document: Document) {
        dao.deleteDocument(document.id)
    }

    private suspend fun recoverInterruptedWrites() {
        val current = dao.getDocuments().associateBy { it.document.id }
        val newestRecoveries = dao.getRecoveryEntries().distinctBy(RecoveryEntryEntity::documentId)
        newestRecoveries.forEach { recovery ->
            val stored = current[recovery.documentId]?.document
            if (!recovery.isDeletion && (stored == null || recovery.createdAtEpochMillis > stored.updatedAtEpochMillis)) {
                val recovered = recovery.toModel()
                dao.replaceDocument(recovered.toEntity(), recovered.toRangeEntities())
            }
        }
    }

    private companion object {
        const val RECOVERY_LIMIT = 30
    }
}

private fun Document.toEntity() = DocumentEntity(
    id = id,
    title = title,
    bodyText = body.text,
    updatedLabel = updatedLabel,
    isPinned = isPinned,
    isArchived = isArchived,
    isFavorite = isFavorite,
    isDeleted = isDeleted,
    folder = folder,
    tagsPayload = tags.sorted().joinToString(TAG_SEPARATOR),
    updatedAtEpochMillis = updatedAtEpochMillis,
    r2Payload = R2DocumentCodec.encode(currentR2()),
)

private fun Document.toRangeEntities() = body.normalized().ranges.map { range ->
    RichTextRangeEntity(
        documentId = id,
        style = range.style.name,
        start = range.start,
        end = range.end,
        data = range.data,
    )
}

private fun DocumentWithRanges.toModel(): Document {
    val legacyBody = RichTextDocument(
        text = document.bodyText,
        ranges = ranges.mapNotNull { range ->
            runCatching {
                RichTextRange(
                    style = RichTextStyle.valueOf(range.style),
                    start = range.start,
                    end = range.end,
                    data = range.data,
                )
            }.getOrNull()
        },
    ).normalized()
    val shell = Document(
    id = document.id,
    title = document.title,
    body = legacyBody,
    updatedLabel = document.updatedLabel,
    isPinned = document.isPinned,
    isArchived = document.isArchived,
    isFavorite = document.isFavorite,
    isDeleted = document.isDeleted,
    folder = document.folder,
    tags = document.tagsPayload.toTags(),
    updatedAtEpochMillis = document.updatedAtEpochMillis,
)
    val decoded = document.r2Payload.takeIf(String::isNotBlank)?.let { runCatching { R2DocumentCodec.decode(it) }.getOrNull() }
    return shell.copy(wordProcessingDocument = decoded ?: R2DocumentBridge.fromLegacy(shell))
}

private fun Document.toRecovery(isDeletion: Boolean = false) = RecoveryEntryEntity(
    documentId = id,
    title = title,
    bodyText = body.text,
    rangePayload = RangePayload.encode(body.ranges),
    updatedLabel = updatedLabel,
    isPinned = isPinned,
    isArchived = isArchived,
    isFavorite = isFavorite,
    isDeleted = isDeleted,
    folder = folder,
    tagsPayload = tags.sorted().joinToString(TAG_SEPARATOR),
    isDeletion = isDeletion,
    createdAtEpochMillis = updatedAtEpochMillis,
    r2Payload = R2DocumentCodec.encode(currentR2()),
)

private fun Document.currentR2() = wordProcessingDocument
    ?.takeIf { it.id == id && it.title == title && R2DocumentBridge.toLegacyBody(it) == body.normalized() }
    ?: R2DocumentBridge.fromLegacy(this)

private fun RecoveryEntryEntity.toModel(): Document {
    val shell = Document(
    id = documentId,
    title = title,
    body = RichTextDocument(bodyText, RangePayload.decode(rangePayload)).normalized(),
    updatedLabel = updatedLabel,
    isPinned = isPinned,
    isArchived = isArchived,
    isFavorite = isFavorite,
    isDeleted = isDeleted,
    folder = folder,
    tags = tagsPayload.toTags(),
    updatedAtEpochMillis = createdAtEpochMillis,
)
    val decoded = r2Payload.takeIf(String::isNotBlank)?.let { runCatching { R2DocumentCodec.decode(it) }.getOrNull() }
    return shell.copy(wordProcessingDocument = decoded ?: R2DocumentBridge.fromLegacy(shell))
}

internal object RangePayload {
    fun encode(ranges: List<RichTextRange>): String = ranges.joinToString(";") { range ->
        listOf(range.style.name, range.start, range.end, range.data.orEmpty())
            .joinToString(",") { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toString().toByteArray()) }
    }

    fun decode(payload: String): List<RichTextRange> = payload
        .split(';')
        .mapNotNull { encoded ->
            val parts = encoded.split(',')
            if (parts.size !in 3..4) return@mapNotNull null
            runCatching {
                val decoded = parts.map { String(java.util.Base64.getUrlDecoder().decode(it)) }
                RichTextRange(
                    style = RichTextStyle.valueOf(decoded[0]),
                    start = decoded[1].toInt(),
                    end = decoded[2].toInt(),
                    data = decoded.getOrNull(3)?.ifBlank { null },
                )
            }.recoverCatching {
                RichTextRange(
                    style = RichTextStyle.valueOf(parts[0]),
                    start = parts[1].toInt(),
                    end = parts[2].toInt(),
                )
            }.getOrNull()
        }
}

private const val TAG_SEPARATOR = "\u001F"
private fun String.toTags(): Set<String> = split(TAG_SEPARATOR).map(String::trim).filter(String::isNotEmpty).toSet()
