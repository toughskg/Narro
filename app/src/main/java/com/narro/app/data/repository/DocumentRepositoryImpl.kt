package com.narro.app.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.narro.app.data.local.db.BookmarkEntity
import com.narro.app.data.local.db.DocumentEntity
import com.narro.app.data.local.db.DocumentSegmentEntity
import com.narro.app.data.local.db.NarroDatabase
import com.narro.app.data.parser.DetectedEncoding
import com.narro.app.data.parser.StreamingSegmentParser
import com.narro.app.data.parser.TextEncodingDetector
import com.narro.app.domain.model.Bookmark
import com.narro.app.domain.model.Document
import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ImportFailure
import com.narro.app.domain.model.ImportProgress
import com.narro.app.domain.model.ImportStage
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.domain.repository.DocumentRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val contentResolver: ContentResolver,
    private val database: NarroDatabase,
    private val filesDir: File,
    private val parser: StreamingSegmentParser = StreamingSegmentParser(),
) : DocumentRepository {
    private val dao = database.narroDao()
    private val documentsDir = File(filesDir, "documents").apply { mkdirs() }
    private val tempDir = File(filesDir, "tmp").apply { mkdirs() }

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeDocuments().map { entities -> entities.map(DocumentEntity::toDomain) }

    override fun observeBookmarks(): Flow<List<Bookmark>> =
        dao.observeBookmarks().map { rows ->
            rows.map {
                Bookmark(
                    id = it.bookmarkId,
                    documentId = it.documentId,
                    documentName = it.documentName,
                    sentenceIndex = it.sentenceIndex,
                    characterOffset = it.characterOffset,
                    previewText = it.previewText,
                    createdAt = it.createdAt,
                )
            }
        }

    override suspend fun getDocument(documentId: String): Document? =
        withContext(Dispatchers.IO) { dao.getDocument(documentId)?.toDomain() }

    override suspend fun importDocument(
        uri: Uri,
        allowDuplicate: Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): Document = withContext(Dispatchers.IO) {
        onProgress(ImportProgress(ImportStage.CHECKING))
        val metadata = readMetadata(uri)
        if (metadata.size != null && metadata.size > MAX_DOCUMENT_BYTES) {
            throw ImportFailure.FileTooLarge
        }
        if (dao.totalDocumentBytes() >= MAX_LOCAL_BYTES) throw ImportFailure.StorageLimit

        val sample = contentResolver.openInputStream(uri)?.use(::readSample)
            ?: throw ImportFailure.CannotOpen
        onProgress(ImportProgress(ImportStage.DETECTING_ENCODING))
        val encoding = TextEncodingDetector.detect(sample)

        val documentId = UUID.randomUUID().toString()
        val tempFile = File(tempDir, "$documentId.tmp")
        val targetFile = File(documentsDir, "$documentId.txt")
        try {
            onProgress(ImportProgress(ImportStage.CONVERTING, 0))
            val conversion = convertToUtf8(uri, encoding, metadata.size, tempFile, onProgress)
            if (conversion.totalCharacters == 0L) throw ImportFailure.EmptyFile
            if (dao.totalDocumentBytes() + conversion.outputBytes > MAX_LOCAL_BYTES) {
                throw ImportFailure.StorageLimit
            }
            val existing = dao.findByHash(conversion.sha256)
            if (existing != null && !allowDuplicate) {
                throw ImportFailure.Duplicate(existing.displayName)
            }

            onProgress(ImportProgress(ImportStage.PARSING))
            val parsed = parser.parse(tempFile, documentId)
            if (parsed.totalCharacters == 0L || parsed.segments.isEmpty()) {
                throw ImportFailure.EmptyFile
            }

            onProgress(ImportProgress(ImportStage.SAVING, 100))
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = false)
                tempFile.delete()
            }
            val entity = DocumentEntity(
                documentId = documentId,
                displayName = metadata.displayName,
                filePath = targetFile.absolutePath,
                fileSize = targetFile.length(),
                contentHash = conversion.sha256,
                parserVersion = StreamingSegmentParser.PARSER_VERSION,
                speechLocaleTag = Locale.getDefault().toLanguageTag(),
                importedAt = System.currentTimeMillis(),
                totalCharacterCount = parsed.totalCharacters,
                totalSentenceCount = parsed.totalSentences,
                totalSegmentCount = parsed.segments.size,
            )
            try {
                database.withTransaction { dao.insertDocumentWithSegments(entity, parsed.segments) }
            } catch (error: Exception) {
                targetFile.delete()
                throw error
            }
            entity.toDomain()
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            targetFile.delete()
            throw cancelled
        } catch (known: ImportFailure) {
            tempFile.delete()
            throw known
        } catch (error: Exception) {
            tempFile.delete()
            targetFile.delete()
            throw ImportFailure.CannotSave
        }
    }

    override suspend fun loadSegmentWindow(
        documentId: String,
        centerIndex: Int,
    ): List<DocumentSegment> = withContext(Dispatchers.IO) {
        val document = dao.getDocument(documentId) ?: return@withContext emptyList()
        dao.getSegments(documentId, (centerIndex - 1).coerceAtLeast(0), centerIndex + 1)
            .map { it.toDomain(readSegment(document.filePath, it)) }
    }

    override suspend fun findSegmentIndex(documentId: String, sentenceIndex: Int): Int =
        withContext(Dispatchers.IO) {
            dao.findSegmentForSentence(documentId, sentenceIndex)?.segmentIndex ?: 0
        }

    override suspend fun saveConfirmedPosition(
        documentId: String,
        position: ReadingPosition,
    ) = withContext(Dispatchers.IO) {
        dao.updateConfirmedPosition(
            documentId = documentId,
            sentenceIndex = position.sentenceIndex,
            characterOffset = position.characterOffset,
            confirmedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun addBookmark(
        documentId: String,
        position: ReadingPosition,
        previewText: String,
    ) = withContext(Dispatchers.IO) {
        val document = dao.getDocument(documentId) ?: return@withContext
        dao.insertBookmark(
            BookmarkEntity(
                documentId = documentId,
                sentenceIndex = position.sentenceIndex,
                characterOffset = position.characterOffset,
                parserVersion = document.parserVersion,
                contentHash = document.contentHash,
                previewText = previewText.take(160),
                createdAt = System.currentTimeMillis(),
            ),
        )
        Unit
    }

    override suspend fun deleteBookmark(bookmarkId: Long) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(bookmarkId)
    }

    override suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        val document = dao.getDocument(documentId) ?: return@withContext
        val file = File(document.filePath)
        if (file.exists() && !file.delete()) throw IllegalStateException("file_delete_failed")
        dao.deleteDocumentRow(documentId)
    }

    private fun readSegment(filePath: String, segment: DocumentSegmentEntity): String {
        val length = (segment.endByteOffset - segment.startByteOffset).toInt()
        val bytes = ByteArray(length)
        RandomAccessFile(filePath, "r").use { file ->
            file.seek(segment.startByteOffset)
            file.readFully(bytes)
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun convertToUtf8(
        uri: Uri,
        encoding: DetectedEncoding,
        expectedSize: Long?,
        target: File,
        onProgress: (ImportProgress) -> Unit,
    ): ConversionResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val source = contentResolver.openInputStream(uri) ?: throw ImportFailure.CannotOpen
        val countingInput = CountingInputStream(BufferedInputStream(source, IO_BUFFER))
        repeat(encoding.bomBytes) {
            if (countingInput.read() == -1) throw ImportFailure.UnsupportedEncoding
        }
        val decoder = encoding.charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        var nonWhitespace = 0L
        var controls = 0L
        var totalCodePoints = 0L
        var lastProgress = -1
        try {
            InputStreamReader(countingInput, decoder).use { reader ->
                val countingOutput = CountingOutputStream(
                    DigestOutputStream(BufferedOutputStream(target.outputStream(), IO_BUFFER), digest),
                )
                OutputStreamWriter(countingOutput, StandardCharsets.UTF_8).use { writer ->
                    val buffer = CharArray(8 * 1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read == -1) break
                        writer.write(buffer, 0, read)
                        writer.flush()
                        if (countingOutput.count > MAX_DOCUMENT_BYTES) throw ImportFailure.FileTooLarge

                        var index = 0
                        while (index < read) {
                            val cp = Character.codePointAt(buffer, index, read)
                            totalCodePoints++
                            when {
                                cp == 0 -> throw ImportFailure.BinaryFile
                                cp == '\t'.code || cp == '\r'.code || cp == '\n'.code -> Unit
                                Character.isISOControl(cp) -> controls++
                                !Character.isWhitespace(cp) -> nonWhitespace++
                            }
                            index += Character.charCount(cp)
                        }
                        if (controls.toDouble() / totalCodePoints.coerceAtLeast(1) > CONTROL_RATIO) {
                            throw ImportFailure.BinaryFile
                        }
                        val percent = expectedSize?.takeIf { it > 0 }?.let {
                            ((countingInput.count * 100L) / it).toInt().coerceIn(0, 99)
                        }
                        if (percent != lastProgress) {
                            lastProgress = percent ?: lastProgress
                            onProgress(
                                ImportProgress(
                                    ImportStage.CONVERTING,
                                    percent,
                                    countingInput.count,
                                ),
                            )
                        }
                    }
                    writer.flush()
                }
            }
        } catch (known: ImportFailure) {
            throw known
        } catch (_: Exception) {
            throw ImportFailure.UnsupportedEncoding
        }
        if (nonWhitespace == 0L) throw ImportFailure.EmptyFile
        return ConversionResult(
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            outputBytes = target.length(),
            totalCharacters = totalCodePoints,
        )
    }

    private fun readMetadata(uri: Uri): Metadata {
        var displayName: String? = null
        var size: Long? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return Metadata(displayName?.takeIf { it.isNotBlank() } ?: "document.txt", size)
    }

    private fun readSample(input: InputStream): ByteArray {
        val buffer = ByteArray(SAMPLE_BYTES)
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return buffer.copyOf(total)
    }

    private data class Metadata(val displayName: String, val size: Long?)
    private data class ConversionResult(
        val sha256: String,
        val outputBytes: Long,
        val totalCharacters: Long,
    )

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count: Long = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) count += it }
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count: Long = 0
            private set

        override fun write(value: Int) {
            out.write(value)
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }
    }

    companion object {
        const val MAX_DOCUMENT_BYTES = 20L * 1024 * 1024
        const val MAX_LOCAL_BYTES = 200L * 1024 * 1024
        private const val SAMPLE_BYTES = 64 * 1024
        private const val IO_BUFFER = 32 * 1024
        private const val CONTROL_RATIO = 0.01
    }
}

private fun DocumentEntity.toDomain() = Document(
    id = documentId,
    displayName = displayName,
    fileSize = fileSize,
    contentHash = contentHash,
    parserVersion = parserVersion,
    speechLocaleTag = speechLocaleTag,
    importedAt = importedAt,
    totalCharacterCount = totalCharacterCount,
    totalSentenceCount = totalSentenceCount,
    totalSegmentCount = totalSegmentCount,
    lastConfirmedSentenceIndex = lastConfirmedSentenceIndex,
    lastConfirmedCharacterOffset = lastConfirmedCharacterOffset,
)

private fun DocumentSegmentEntity.toDomain(text: String) = DocumentSegment(
    documentId = documentId,
    index = segmentIndex,
    text = text,
    startCharacterOffset = startCharacterOffset,
    endCharacterOffset = endCharacterOffset,
    startSentenceIndex = startSentenceIndex,
    endSentenceIndex = endSentenceIndex,
)
