package com.narro.app.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFollowPositionTest {
    @Test
    fun scrollsWhenSpokenSentenceMovesOutsideVisibleArea() {
        assertTrue(
            shouldScrollToReadingItem(
                targetKey = "4:27",
                visibleKeys = listOf("4:23", "4:24", "4:25", "4:26"),
            ),
        )
    }

    @Test
    fun keepsViewportStillWhenSpokenSentenceIsAlreadyVisible() {
        assertFalse(
            shouldScrollToReadingItem(
                targetKey = "4:25",
                visibleKeys = listOf("4:23", "4:24", "4:25", "4:26"),
            ),
        )
    }
}
