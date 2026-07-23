package com.narro.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val displayName: String,
    val filePath: String,
    val fileSize: Long,
    val contentHash: String,
    val parserVersion: Int,
    val speechLocaleTag: String,
    val importedAt: Long,
    val totalCharacterCount: Long,
    val totalSentenceCount: Int,
    val totalSegmentCount: Int,
    val lastConfirmedSentenceIndex: Int = 0,
    val lastConfirmedCharacterOffset: Long = 0,
    val lastConfirmedAt: Long? = null,
)

@Entity(
    tableName = "document_segments",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId", "segmentIndex"], unique = true),
        Index(value = ["documentId"]),
    ],
)
data class DocumentSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val segmentIndex: Int,
    val startByteOffset: Long,
    val endByteOffset: Long,
    val startCharacterOffset: Long,
    val endCharacterOffset: Long,
    val startSentenceIndex: Int,
    val endSentenceIndex: Int,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0,
    val documentId: String,
    val sentenceIndex: Int,
    val characterOffset: Long,
    val parserVersion: Int,
    val contentHash: String,
    val previewText: String,
    val createdAt: Long,
)

data class BookmarkWithDocument(
    val bookmarkId: Long,
    val documentId: String,
    val documentName: String,
    val sentenceIndex: Int,
    val characterOffset: Long,
    val previewText: String,
    val createdAt: Long,
)
