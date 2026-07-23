package com.narro.app.domain.repository

import android.net.Uri
import com.narro.app.domain.model.Bookmark
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ImportProgress
import com.narro.app.domain.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeBookmarks(): Flow<List<Bookmark>>
    suspend fun getDocument(documentId: String): Document?
    suspend fun importDocument(
        uri: Uri,
        allowDuplicate: Boolean = false,
        onProgress: (ImportProgress) -> Unit = {},
    ): Document
    suspend fun loadSegmentWindow(documentId: String, centerIndex: Int): List<DocumentSegment>
    suspend fun findSegmentIndex(documentId: String, sentenceIndex: Int): Int
    suspend fun saveConfirmedPosition(documentId: String, position: ReadingPosition)
    suspend fun addBookmark(documentId: String, position: ReadingPosition, previewText: String)
    suspend fun deleteBookmark(bookmarkId: Long)
    suspend fun deleteDocument(documentId: String)
}
