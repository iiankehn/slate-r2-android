package com.iiankehn.slater2.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bodyText: String,
    val updatedLabel: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val isDeleted: Boolean,
    val folder: String,
    val tagsPayload: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "rich_text_ranges",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class RichTextRangeEntity(
    @PrimaryKey(autoGenerate = true) val rangeId: Long = 0,
    val documentId: String,
    val style: String,
    val start: Int,
    val end: Int,
    val data: String?,
)

data class DocumentWithRanges(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId",
    )
    val ranges: List<RichTextRangeEntity>,
)

@Entity(
    tableName = "recovery_entries",
    indices = [Index("documentId"), Index("createdAtEpochMillis")],
)
data class RecoveryEntryEntity(
    @PrimaryKey(autoGenerate = true) val recoveryId: Long = 0,
    val documentId: String,
    val title: String,
    val bodyText: String,
    val rangePayload: String,
    val updatedLabel: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val isDeleted: Boolean,
    val folder: String,
    val tagsPayload: String,
    val isDeletion: Boolean,
    val createdAtEpochMillis: Long,
)
