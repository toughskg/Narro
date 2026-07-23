package com.narro.app.data.parser

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSegmentParserTest {
    @Test
    fun createsIndexedSegmentsWithoutSplittingUnicodeCodePoints() {
        val sentence = "가나다😀 문장입니다.\n"
        val source = sentence.repeat(600)
        val file = Files.createTempFile("narro-parser", ".txt").toFile()
        try {
            file.writeText(source, StandardCharsets.UTF_8)
            val result = StreamingSegmentParser().parse(file, "doc-1")

            assertTrue(result.segments.size >= 2)
            assertEquals(source.codePointCount(0, source.length).toLong(), result.totalCharacters)
            assertEquals(600, result.totalSentences)
            assertEquals(0L, result.segments.first().startByteOffset)
            assertEquals(file.length(), result.segments.last().endByteOffset)
            result.segments.zipWithNext().forEach { (left, right) ->
                assertEquals(left.endByteOffset, right.startByteOffset)
                assertEquals(left.endCharacterOffset, right.startCharacterOffset)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun forceSplitsPunctuationFreeInputAtTtsLimit() {
        val source = "가".repeat(6_500)
        val file = Files.createTempFile("narro-long-sentence", ".txt").toFile()
        try {
            file.writeText(source, StandardCharsets.UTF_8)
            val result = StreamingSegmentParser().parse(file, "doc-2")
            assertEquals(3, result.totalSentences)
            assertEquals(6_500L, result.totalCharacters)
        } finally {
            file.delete()
        }
    }
}
