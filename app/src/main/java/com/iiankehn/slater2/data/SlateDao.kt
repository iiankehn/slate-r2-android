package com.iiankehn.slater2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SlateDao {
    @Transaction
    @Query("SELECT * FROM documents ORDER BY isDeleted ASC, isArchived ASC, isPinned DESC, isFavorite DESC, updatedAtEpochMillis DESC")
    abstract fun observeDocuments(): Flow<List<DocumentWithRanges>>

    @Transaction
    @Query("SELECT * FROM documents")
    abstract suspend fun getDocuments(): List<DocumentWithRanges>

    @Query("SELECT COUNT(*) FROM documents")
    abstract suspend fun documentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertDocument(document: DocumentEntity)

    @Query("DELETE FROM rich_text_ranges WHERE documentId = :documentId")
    protected abstract suspend fun deleteRanges(documentId: String)

    @Insert
    protected abstract suspend fun insertRanges(ranges: List<RichTextRangeEntity>)

    @Transaction
    open suspend fun replaceDocument(
        document: DocumentEntity,
        ranges: List<RichTextRangeEntity>,
    ) {
        upsertDocument(document)
        deleteRanges(document.id)
        if (ranges.isNotEmpty()) insertRanges(ranges)
    }

    @Query("DELETE FROM documents WHERE id = :documentId")
    abstract suspend fun deleteDocument(documentId: String)

    @Insert
    abstract suspend fun insertRecovery(entry: RecoveryEntryEntity)

    @Query("SELECT * FROM recovery_entries ORDER BY createdAtEpochMillis DESC, recoveryId DESC")
    abstract suspend fun getRecoveryEntries(): List<RecoveryEntryEntity>

    @Query("SELECT * FROM recovery_entries WHERE documentId = :documentId AND isDeletion = 0 ORDER BY createdAtEpochMillis DESC, recoveryId DESC")
    abstract suspend fun getRecoveryEntries(documentId: String): List<RecoveryEntryEntity>

    @Query(
        """
        DELETE FROM recovery_entries
        WHERE documentId = :documentId
          AND recoveryId NOT IN (
              SELECT recoveryId FROM recovery_entries
              WHERE documentId = :documentId
              ORDER BY createdAtEpochMillis DESC
              LIMIT :keepCount
          )
        """,
    )
    abstract suspend fun trimRecovery(documentId: String, keepCount: Int)
}
