package io.github.nagiska.miuixreader.tts

import io.github.nagiska.miuixreader.data.NarrationEngine
import org.junit.Assert.assertFalse
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
    fun sessionRetainsSelectedLocalEngine() {
        val session = NarrationSession(
            bookId = 1L,
            title = "Book",
            engine = NarrationEngine.GSV_LOCAL,
            rate = 1f,
            gsvPort = 9880,
            segments = listOf(
                NarrationSegment("A sentence.", NarrationAnchor.Txt(0, 0f, 0f)),
            ),
        )

        assertTrue(session.engine == NarrationEngine.GSV_LOCAL)
        assertTrue(session.segments.single().text == "A sentence.")
    }
}
