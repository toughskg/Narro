package com.narro.app.data.parser

import com.narro.app.domain.model.ImportFailure
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DetectedEncoding(
    val charset: Charset,
    val bomBytes: Int,
    val sampleText: String,
)

object TextEncodingDetector {
    private val eucKr: Charset = Charset.forName("EUC-KR")

    fun detect(sample: ByteArray): DetectedEncoding {
        if (sample.isEmpty()) throw ImportFailure.EmptyFile

        val detected = when {
            sample.startsWith(0xEF, 0xBB, 0xBF) -> decode(sample, StandardCharsets.UTF_8, 3)
            sample.startsWith(0xFF, 0xFE) -> decode(sample, StandardCharsets.UTF_16LE, 2)
            sample.startsWith(0xFE, 0xFF) -> decode(sample, StandardCharsets.UTF_16BE, 2)
            looksLikeUtf16(sample, littleEndian = true) -> decode(sample, StandardCharsets.UTF_16LE, 0)
            looksLikeUtf16(sample, littleEndian = false) -> decode(sample, StandardCharsets.UTF_16BE, 0)
            else -> tryDecode(sample, StandardCharsets.UTF_8, 0)
                ?: tryDecode(sample, eucKr, 0)
                ?: throw ImportFailure.UnsupportedEncoding
        }

        validateText(detected.sampleText)
        return detected
    }

    private fun decode(bytes: ByteArray, charset: Charset, offset: Int): DetectedEncoding =
        tryDecode(bytes, charset, offset) ?: throw ImportFailure.UnsupportedEncoding

    private fun tryDecode(bytes: ByteArray, charset: Charset, offset: Int): DetectedEncoding? {
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val input = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
            val output = CharBuffer.allocate((input.remaining() * decoder.maxCharsPerByte()).toInt() + 2)
            val result = decoder.decode(input, output, false)
            if (result.isError) result.throwException()
            output.flip()
            DetectedEncoding(charset, offset, output.toString())
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun looksLikeUtf16(bytes: ByteArray, littleEndian: Boolean): Boolean {
        if (bytes.size < 4 || bytes.size % 2 != 0) return false
        val pairs = bytes.size / 2
        var expectedNulls = 0
        var oppositeNulls = 0
        for (index in 0 until pairs) {
            val evenNull = bytes[index * 2].toInt() == 0
            val oddNull = bytes[index * 2 + 1].toInt() == 0
            if (littleEndian) {
                if (oddNull) expectedNulls++
                if (evenNull) oppositeNulls++
            } else {
                if (evenNull) expectedNulls++
                if (oddNull) oppositeNulls++
            }
        }
        return expectedNulls.toDouble() / pairs >= 0.30 &&
            oppositeNulls.toDouble() / pairs <= 0.05
    }

    private fun validateText(text: String) {
        if (text.isBlank()) throw ImportFailure.EmptyFile
        if ('\u0000' in text) throw ImportFailure.BinaryFile
        var controls = 0
        var visible = 0
        text.codePoints().forEach { codePoint ->
            when {
                codePoint == '\t'.code || codePoint == '\r'.code || codePoint == '\n'.code -> Unit
                Character.isISOControl(codePoint) -> controls++
                else -> visible++
            }
        }
        if (controls > 0 && controls.toDouble() / (controls + visible).coerceAtLeast(1) > 0.01) {
            throw ImportFailure.BinaryFile
        }
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
}
