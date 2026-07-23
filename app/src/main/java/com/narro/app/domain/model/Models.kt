package com.narro.app.domain.model

data class Document(
    val id: String,
    val displayName: String,
    val fileSize: Long,
    val contentHash: String,
    val parserVersion: Int,
    val speechLocaleTag: String,
    val importedAt: Long,
    val totalCharacterCount: Long,
    val totalSentenceCount: Int,
    val totalSegmentCount: Int,
    val lastConfirmedSentenceIndex: Int,
    val lastConfirmedCharacterOffset: Long,
) {
    val progressPercent: Int
        get() = if (totalSentenceCount <= 0) 0
        else ((lastConfirmedSentenceIndex.toDouble() / totalSentenceCount) * 100)
            .toInt()
            .coerceIn(0, 100)
}

data class DocumentSegment(
    val documentId: String,
    val index: Int,
    val text: String,
    val startCharacterOffset: Long,
    val endCharacterOffset: Long,
    val startSentenceIndex: Int,
    val endSentenceIndex: Int,
)

data class Bookmark(
    val id: Long,
    val documentId: String,
    val documentName: String,
    val sentenceIndex: Int,
    val characterOffset: Long,
    val previewText: String,
    val createdAt: Long,
)

data class ReadingPosition(
    val sentenceIndex: Int,
    val characterOffset: Long,
)

enum class ImportStage {
    CHECKING,
    DETECTING_ENCODING,
    CONVERTING,
    PARSING,
    SAVING,
}

data class ImportProgress(
    val stage: ImportStage,
    val percent: Int? = null,
    val processedBytes: Long = 0,
)

sealed class ImportFailure(message: String) : Exception(message) {
    data object EmptyFile : ImportFailure("empty_file")
    data object BinaryFile : ImportFailure("binary_file")
    data object UnsupportedEncoding : ImportFailure("unsupported_encoding")
    data object FileTooLarge : ImportFailure("file_too_large")
    data object StorageLimit : ImportFailure("storage_limit")
    data class Duplicate(val existingName: String) : ImportFailure("duplicate")
    data object CannotOpen : ImportFailure("cannot_open")
    data object CannotSave : ImportFailure("cannot_save")
}
