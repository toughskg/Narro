package com.narro.app.data.parser

import com.narro.app.domain.model.ImportFailure
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextEncodingDetectorTest {
    @Test
    fun detectsUtf8() {
        val source = "Narro는 텍스트를 읽습니다."
        val result = TextEncodingDetector.detect(source.toByteArray(StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, result.charset)
        assertEquals(source, result.sampleText)
    }

    @Test
    fun detectsEucKr() {
        val source = "한글 문서 읽기"
        val result = TextEncodingDetector.detect(source.toByteArray(Charset.forName("EUC-KR")))
        assertEquals("EUC-KR", result.charset.name())
        assertEquals(source, result.sampleText)
    }

    @Test
    fun detectsUtf16LittleEndianBom() {
        val source = "UTF-16 문서"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            source.toByteArray(StandardCharsets.UTF_16LE)
        val result = TextEncodingDetector.detect(bytes)
        assertEquals(StandardCharsets.UTF_16LE, result.charset)
        assertEquals(2, result.bomBytes)
        assertEquals(source, result.sampleText)
    }

    @Test
    fun rejectsBlankFile() {
        assertThrows(ImportFailure.EmptyFile::class.java) {
            TextEncodingDetector.detect(" \n\t".toByteArray())
        }
    }

    @Test
    fun rejectsBinaryControlCharacters() {
        assertThrows(ImportFailure.BinaryFile::class.java) {
            TextEncodingDetector.detect(byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte()))
        }
    }
}
