package com.narro.app.feature

import com.narro.app.domain.model.DocumentSegment
import com.narro.app.domain.model.ReadingPosition
import com.narro.app.playback.model.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderInteractionTest {
    @Test
    fun completedActiveDocumentReturnsToDocumentList() {
        assertTrue(
            shouldReturnToDocuments(
                playbackStatus = PlaybackStatus.COMPLETED,
                playbackDocumentId = "document-1",
                readerDocumentId = "document-1",
            ),
        )
        assertFalse(
            shouldReturnToDocuments(
                playbackStatus = PlaybackStatus.COMPLETED,
                playbackDocumentId = "document-1",
                readerDocumentId = "document-2",
            ),
        )
    }

    @Test
    fun manualPositionCanBeSelectedOnlyWhenPlaybackIsNotActive() {
        assertTrue(canSelectReadingPosition(PlaybackStatus.IDLE))
        assertTrue(canSelectReadingPosition(PlaybackStatus.PAUSED_BY_USER))
        assertTrue(canSelectReadingPosition(PlaybackStatus.STOPPED))
        assertFalse(canSelectReadingPosition(PlaybackStatus.INITIALIZING))
        assertFalse(canSelectReadingPosition(PlaybackStatus.PLAYING))
        assertFalse(canSelectReadingPosition(PlaybackStatus.PAUSED_BY_FOCUS))
    }

    @Test
    fun selectedSentenceOverridesStoredPositionForNextPlayback() {
        val selected = ReadingPosition(sentenceIndex = 14, characterOffset = 320L)
        val stored = ReadingPosition(sentenceIndex = 3, characterOffset = 70L)

        assertEquals(selected, playbackStartPosition(selected, stored))
        assertEquals(stored, playbackStartPosition(null, stored))
    }

    @Test
    fun displayedSentenceCarriesItsExactStartOffset() {
        val items = displaySentences(
            DocumentSegment(
                documentId = "document-1",
                index = 2,
                text = "첫 문장.둘째 문장.",
                startCharacterOffset = 100L,
                endCharacterOffset = 111L,
                startSentenceIndex = 8,
                endSentenceIndex = 9,
            ),
        )

        assertEquals(ReadingPosition(8, 100L), items[0].position)
        assertEquals(ReadingPosition(9, 105L), items[1].position)
    }
}
