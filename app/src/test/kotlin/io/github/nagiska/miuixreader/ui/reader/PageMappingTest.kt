package io.github.nagiska.miuixreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageMappingTest {
    @Test
    fun firstPageMapsToZero() {
        assertEquals(0.0, pageToProgression(1, 100), 0.0001)
    }

    @Test
    fun lastPageMapsToOne() {
        assertEquals(1.0, pageToProgression(100, 100), 0.0001)
    }

    @Test
    fun middlePageMapsToFraction() {
        assertEquals(0.5, pageToProgression(51, 101), 0.0001)
        assertEquals(0.25, pageToProgression(26, 101), 0.0001)
    }

    @Test
    fun singlePageTotalStaysZero() {
        assertEquals(0.0, pageToProgression(1, 1), 0.0001)
    }

    @Test
    fun outOfRangePagesAreClamped() {
        assertEquals(0.0, pageToProgression(0, 50), 0.0001)
        assertEquals(1.0, pageToProgression(99, 50), 0.0001)
    }
}
