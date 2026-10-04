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
        assertTrue(script.contains("background-image:none!important"))
        assertFalse(script.contains("url('data:image"))
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

        assertTrue(script.contains("url('data:image/webp;base64,AAAA')"))
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
        assertTrue(script.contains("background-image:none!important"))
    }

    @Test
    fun replacingImageStyleRemovesWallpaperAndRejectsOlderAsyncScripts() {
        val image = buildEpubPageStyleScript(
            preferences = ReaderPreferences(readerBackgroundMode = ReaderBackgroundMode.IMAGE),
            imageDataUri = "data:image/webp;base64,AAAA",
            fallbackDark = true,
            generation = 4L,
        )
        val solid = buildEpubPageStyleScript(
            preferences = ReaderPreferences(
                readerBackgroundMode = ReaderBackgroundMode.COLOR,
                readerBackgroundColor = 0xFFF8F5EE.toInt(),
            ),
            imageDataUri = null,
            fallbackDark = false,
            generation = 5L,
        )

        assertTrue(image.contains("window.__miuixReaderStyleGeneration=4;"))
        assertTrue(solid.contains("window.__miuixReaderStyleGeneration=5;"))
        assertTrue(solid.contains("background-image:none!important"))
        assertFalse(solid.contains("url('data:image"))
    }
}
