package com.narro.app.playback.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsPlaybackPositionTest {
    @Test
    fun utteranceStartMovesVisiblePositionToSentenceBeingSpoken() {
        val position = utteranceStartPosition(
            sentenceIndex = 17,
            characterOffset = 842L,
        )

        assertEquals(17, position.sentenceIndex)
        assertEquals(842L, position.characterOffset)
    }
}
