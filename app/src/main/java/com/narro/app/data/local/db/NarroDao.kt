package com.narro.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NarroDao {
    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE contentHash = :contentHash LIMIT 1")
    suspend fun findByHash(contentHash: String): DocumentEntity?

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM documents")
    suspend fun totalDocumentBytes(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegments(segments: List<DocumentSegmentEntity>)

    @Transaction
    suspend fun insertDocumentWithSegments(
        document: DocumentEntity,
        segments: List<DocumentSegmentEntity>,
    ) {
        insertDocument(document)
        insertSegments(segments)
    }

    @Query(
        """
        SELECT * FROM document_segments
        WHERE documentId = :documentId AND segmentIndex BETWEEN :fromIndex AND :toIndex
        ORDER BY segmentIndex
        """,
    )
    suspend fun getSegments(
        documentId: String,
        fromIndex: Int,
        toIndex: Int,
    ): List<DocumentSegmentEntity>

    @Query(
        """
        SELECT * FROM document_segments
        WHERE documentId = :documentId
          AND :sentenceIndex BETWEEN startSentenceIndex AND endSentenceIndex
        LIMIT 1
        """,
    )
    suspend fun findSegmentForSentence(
        documentId: String,
        sentenceIndex: Int,
    ): DocumentSegmentEntity?

    @Query(
        """
        UPDATE documents
        SET lastConfirmedSentenceIndex = :sentenceIndex,
            lastConfirmedCharacterOffset = :characterOffset,
            lastConfirmedAt = :confirmedAt
        WHERE documentId = :documentId
        """,
    )
    suspend fun updateConfirmedPosition(
        documentId: String,
        sentenceIndex: Int,
        characterOffset: Long,
        confirmedAt: Long,
    )

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query(
        """
        SELECT b.bookmarkId, b.documentId, d.displayName AS documentName,
               b.sentenceIndex, b.characterOffset, b.previewText, b.createdAt
        FROM bookmarks b
        JOIN documents d ON d.documentId = b.documentId
        ORDER BY b.createdAt DESC
        """,
    )
    fun observeBookmarks(): Flow<List<BookmarkWithDocument>>

    @Query("DELETE FROM bookmarks WHERE bookmarkId = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM documents WHERE documentId = :documentId")
    suspend fun deleteDocumentRow(documentId: String)
}
