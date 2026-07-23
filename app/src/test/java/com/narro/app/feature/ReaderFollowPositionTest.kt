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
                visibleItems = listOf(
                    ReadingItemViewport("4:23", offset = 0, size = 80),
                    ReadingItemViewport("4:24", offset = 80, size = 80),
                    ReadingItemViewport("4:25", offset = 160, size = 80),
                    ReadingItemViewport("4:26", offset = 240, size = 80),
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 320,
            ),
        )
    }

    @Test
    fun scrollsBeforeNextSentenceIsClippedByBottomEdge() {
        assertTrue(
            shouldScrollToReadingItem(
                targetKey = "4:26",
                visibleItems = listOf(
                    ReadingItemViewport("4:23", offset = 0, size = 70),
                    ReadingItemViewport("4:24", offset = 70, size = 70),
                    ReadingItemViewport("4:25", offset = 140, size = 70),
                    ReadingItemViewport("4:26", offset = 210, size = 140),
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 320,
            ),
        )
    }

    @Test
    fun keepsViewportStillWhenSpokenSentenceIsFullyVisible() {
        assertFalse(
            shouldScrollToReadingItem(
                targetKey = "4:25",
                visibleItems = listOf(
                    ReadingItemViewport("4:23", offset = 0, size = 70),
                    ReadingItemViewport("4:24", offset = 70, size = 70),
                    ReadingItemViewport("4:25", offset = 140, size = 100),
                    ReadingItemViewport("4:26", offset = 240, size = 80),
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 320,
            ),
        )
    }

    @Test
    fun scrollsWhenSentenceIsClippedByTopEdge() {
        assertTrue(
            shouldScrollToReadingItem(
                targetKey = "4:23",
                visibleItems = listOf(
                    ReadingItemViewport("4:23", offset = -20, size = 80),
                    ReadingItemViewport("4:24", offset = 60, size = 80),
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 320,
            ),
        )
    }
}
