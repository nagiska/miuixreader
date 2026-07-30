package io.github.nagiska.miuixreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAppearanceTest {
    @Test
    fun lightBackgroundUsesDarkText() {
        assertEquals(0xFF000000.toInt(), contrastTextColor(0xFFFFFFFF.toInt()))
    }

    @Test
    fun darkBackgroundUsesWhiteText() {
        assertEquals(0xFFFFFFFF.toInt(), contrastTextColor(0xFF101010.toInt()))
    }

    @Test
    fun brightImageReceivesReadableScrim() {
        val alpha = recommendedScrimAlpha(1.0)
        val resultingLuminance = 1.0 * (1.0 - alpha)
        val whiteContrast = 1.05 / (resultingLuminance + 0.05)

        assertTrue(alpha in DEFAULT_IMAGE_SCRIM..0.82f)
        assertTrue(whiteContrast >= 4.45)
    }

    @Test
    fun darkImageKeepsMinimumScrim() {
        assertEquals(DEFAULT_IMAGE_SCRIM, recommendedScrimAlpha(0.05))
    }
}
