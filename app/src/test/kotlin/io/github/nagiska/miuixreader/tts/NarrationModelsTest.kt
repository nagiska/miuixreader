package io.github.nagiska.miuixreader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationModelsTest {
    @Test
    fun idleStateIsNotActive() {
        assertFalse(NarrationPlaybackState().isActive)
    }

    @Test
    fun bufferingStateIsActiveForCurrentBook() {
        assertTrue(
            NarrationPlaybackState(
                bookId = 42L,
                phase = NarrationPhase.BUFFERING,
            ).isActive,
        )
    }

    @Test
    fun errorStateIsNotActive() {
        assertFalse(
            NarrationPlaybackState(
                bookId = 42L,
                phase = NarrationPhase.ERROR,
            ).isActive,
        )
    }

    @Test
    fun sessionRetainsOrderedSegments() {
        val session = NarrationSession(
            bookId = 1L,
            title = "Book",
            segments = listOf(
                NarrationSegment("A sentence.", NarrationAnchor.Txt(0, 0f, 0f)),
            ),
        )

        assertEquals("A sentence.", session.segments.single().text)
    }
}
