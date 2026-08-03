package io.github.nagiska.miuixreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tap-outside semantics: closing a panel must keep the chrome visible
 * (closePanel), while hide() dismisses the whole chrome.
 */
class ReaderChromeStateTest {
    @Test
    fun closePanelKeepsChromeVisible() {
        val state = ReaderChromeState()
        state.open(ReaderPanel.BOOKMARKS)
        assertTrue(state.visible)
        assertEquals(ReaderPanel.BOOKMARKS, state.panel)

        state.closePanel()
        assertEquals(ReaderPanel.NONE, state.panel)
        assertTrue(state.visible)
    }

    @Test
    fun hideClosesPanelAndChrome() {
        val state = ReaderChromeState()
        state.open(ReaderPanel.SEARCH)
        state.hide()
        assertEquals(ReaderPanel.NONE, state.panel)
        assertFalse(state.visible)
    }

    @Test
    fun toggleShowsAndHides() {
        val state = ReaderChromeState()
        state.toggle()
        assertTrue(state.visible)
        state.toggle()
        assertFalse(state.visible)
    }
}
