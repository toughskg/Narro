package com.narro.app.data.parser

import com.narro.app.data.local.db.DocumentSegmentEntity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class ParseResult(
    val segments: List<DocumentSegmentEntity>,
    val totalCharacters: Long,
    val totalSentences: Int,
)

class StreamingSegmentParser {
    fun parse(file: File, documentId: String): ParseResult {
        val segments = mutableListOf<DocumentSegmentEntity>()
        var byteOffset = 0L
        var characterOffset = 0L
        var sentenceIndex = 0
        var sentenceCharacters = 0
        var sentenceHasContent = false
        var segmentCharacters = 0
        var segmentSentences = 0
        var segmentStartByte = 0L
        var segmentStartCharacter = 0L
        var segmentStartSentence = 0

        fun finishSegment() {
            if (segmentCharacters == 0) return
            segments += DocumentSegmentEntity(
                documentId = documentId,
                segmentIndex = segments.size,
                startByteOffset = segmentStartByte,
                endByteOffset = byteOffset,
                startCharacterOffset = segmentStartCharacter,
                endCharacterOffset = characterOffset,
                startSentenceIndex = segmentStartSentence,
                endSentenceIndex = (sentenceIndex - 1).coerceAtLeast(segmentStartSentence),
            )
            segmentStartByte = byteOffset
            segmentStartCharacter = characterOffset
            segmentStartSentence = sentenceIndex
            segmentCharacters = 0
            segmentSentences = 0
        }

        fun finishSentence() {
            if (sentenceCharacters == 0) return
            if (!sentenceHasContent) {
                sentenceCharacters = 0
                return
            }
            sentenceIndex++
            segmentSentences++
            sentenceCharacters = 0
            sentenceHasContent = false
            if ((segmentCharacters >= TARGET_SEGMENT_CHARS || segmentSentences >= TARGET_SENTENCES) &&
                segmentCharacters >= MIN_SEGMENT_CHARS || segmentCharacters >= MAX_SEGMENT_CHARS
            ) {
                finishSegment()
            }
        }

        BufferedReader(InputStreamReader(file.inputStream(), StandardCharsets.UTF_8), BUFFER_SIZE).use { reader ->
            var pending: Int? = null
            while (true) {
                val first = pending ?: reader.read()
                pending = null
                if (first == -1) break
                val codePoint = if (Character.isHighSurrogate(first.toChar())) {
                    val second = reader.read()
                    if (second != -1 && Character.isLowSurrogate(second.toChar())) {
                        Character.toCodePoint(first.toChar(), second.toChar())
                    } else {
                        if (second != -1) pending = second
                        first
                    }
                } else {
                    first
                }

                byteOffset += String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
                characterOffset++
                sentenceCharacters++
                segmentCharacters++
                if (!Character.isWhitespace(codePoint)) sentenceHasContent = true

                if (isSentenceBoundary(codePoint) || sentenceCharacters >= MAX_UTTERANCE_CHARS) {
                    finishSentence()
                }
            }
        }

        finishSentence()
        finishSegment()
        return ParseResult(segments, characterOffset, sentenceIndex)
    }

    private fun isSentenceBoundary(codePoint: Int): Boolean = when (codePoint) {
        '.'.code, '!'.code, '?'.code, '\n'.code, 0x3002, 0xFF01, 0xFF1F -> true
        else -> false
    }

    companion object {
        const val PARSER_VERSION = 1
        const val MIN_SEGMENT_CHARS = 2_048
        const val TARGET_SEGMENT_CHARS = 8_192
        const val MAX_SEGMENT_CHARS = 12_288
        const val TARGET_SENTENCES = 40
        const val MAX_UTTERANCE_CHARS = 3_000
        private const val BUFFER_SIZE = 16 * 1_024
    }
}
