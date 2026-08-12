package io.github.nagiska.miuixreader

import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageStyleTest {
    @Test
    fun followThemeKeepsPageBackgroundInSyncWithTheAppTheme() {
        val script = buildEpubPageStyleScript(
            preferences = ReaderPreferences(),
            imageDataUri = null,
            fallbackDark = false,
        )

        assertTrue(script.contains("background-color:#FFFFFF"))
        assertFalse(script.contains("background-image:url"))
    }

    @Test
    fun imageBackgroundIsDarkenedWithoutEmbeddingExecutableText() {
        val script = buildEpubPageStyleScript(
            preferences = ReaderPreferences(
                readerBackgroundMode = ReaderBackgroundMode.IMAGE,
                readerBackgroundScrim = 0.45f,
            ),
            imageDataUri = "data:image/webp;base64,AAAA",
            fallbackDark = false,
        )

        assertTrue(script.contains("background-image:url('data:image/webp;base64,AAAA')"))
        assertTrue(script.contains("rgba(0,0,0,0.45)"))
        assertTrue(script.contains("color:#FFFFFF"))
    }

    @Test
    fun solidColorProducesOpaqueCssColor() {
        val script = buildEpubPageStyleScript(
            preferences = ReaderPreferences(
                readerBackgroundMode = ReaderBackgroundMode.COLOR,
                readerBackgroundColor = 0xFFF8F5EE.toInt(),
            ),
            imageDataUri = null,
            fallbackDark = true,
        )

        assertTrue(script.contains("background-color:#F8F5EE"))
        assertTrue(script.contains("color:#000000"))
    }
}
