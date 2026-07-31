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
    fun brightImageReceivesModerateScrim() {
        val alpha = recommendedScrimAlpha(1.0)
        val resultingLuminance = 1.0 * (1.0 - alpha)

        assertTrue(alpha in MIN_IMAGE_SCRIM..MAX_IMAGE_SCRIM)
        assertTrue(alpha < 0.6f)
        assertTrue(resultingLuminance >= 0.38)
    }

    @Test
    fun darkImageKeepsMinimumScrim() {
        assertEquals(MIN_IMAGE_SCRIM, recommendedScrimAlpha(0.05))
    }
}
