package io.github.nagiska.miuixreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressTest {
    @Test
    fun `legacy TXT restores pixel offsets without inventing whole-book progress`() {
        assertEquals(
            TxtReadingProgress(itemIndex = 12, scrollOffset = 240),
            decodeTxtReadingProgress("txt:12:240"),
        )
        assertNull(decodeTxtReadingProgress("txt:0:0")?.totalFraction)
    }

    @Test
    fun `TXT v2 restores relative offsets without inventing whole-book progress`() {
        assertEquals(
            TxtReadingProgress(itemIndex = 12, offsetFraction = 0.625f),
            decodeTxtReadingProgress("txt2:12:0.625"),
        )
        assertNull(decodeTxtReadingProgress("txt2:999:1.0")?.totalFraction)
    }

    @Test
    fun `TXT v3 round trips item offset and independent whole-book progress`() {
        val encoded = encodeTxtReadingProgress(37, 0.25f, 0.625f)

        assertEquals("txt3:37:0.25:0.625", encoded)
        assertEquals(
            TxtReadingProgress(itemIndex = 37, offsetFraction = 0.25f, totalFraction = 0.625f),
            decodeTxtReadingProgress(encoded),
        )
        assertEquals(
            TxtReadingProgress(itemIndex = 37, offsetFraction = 0.75f, totalFraction = 0f),
            decodeTxtReadingProgress(encodeTxtReadingProgress(37, 0.75f, 0f)),
        )
    }

    @Test
    fun `beginning and completed book endpoints are preserved`() {
        assertEquals(
            TxtReadingProgress(totalFraction = 0f),
            decodeTxtReadingProgress(encodeTxtReadingProgress(0, 0f, 0f)),
        )
        assertEquals(
            TxtReadingProgress(itemIndex = 99, offsetFraction = 1f, totalFraction = 1f),
            decodeTxtReadingProgress(encodeTxtReadingProgress(99, 1f, 1f)),
        )
    }

    @Test
    fun `decoding clamps negative positions and out-of-range finite fractions`() {
        assertEquals(TxtReadingProgress(), decodeTxtReadingProgress("txt:-12:-240"))
        assertEquals(TxtReadingProgress(), decodeTxtReadingProgress("txt2:-12:-0.5"))
        assertEquals(
            TxtReadingProgress(itemIndex = 12, offsetFraction = 1f),
            decodeTxtReadingProgress("txt2:12:2.0"),
        )
        assertEquals(
            TxtReadingProgress(offsetFraction = 0f, totalFraction = 1f),
            decodeTxtReadingProgress("txt3:-12:-0.5:2.0"),
        )
        assertEquals(
            TxtReadingProgress(offsetFraction = 1f, totalFraction = 0f),
            decodeTxtReadingProgress("txt3:0:2.0:-0.5"),
        )
    }

    @Test
    fun `encoding clamps positions and finite fractions`() {
        assertEquals("txt3:0:0.0:1.0", encodeTxtReadingProgress(-12, -0.5f, 2f))
        assertEquals("txt3:12:1.0:0.0", encodeTxtReadingProgress(12, Float.MAX_VALUE, -Float.MAX_VALUE))
    }

    @Test
    fun `non-finite saved fractions are rejected`() {
        listOf("NaN", "Infinity", "-Infinity", "1e100").forEach { invalid ->
            assertNull(invalid, decodeTxtReadingProgress("txt2:1:$invalid"))
            assertNull(invalid, decodeTxtReadingProgress("txt3:1:$invalid:0.5"))
            assertNull(invalid, decodeTxtReadingProgress("txt3:1:0.5:$invalid"))
        }
    }

    @Test
    fun `encoding invalid floats uses finite safe defaults`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals("txt3:1:0.0:0.5", encodeTxtReadingProgress(1, invalid, 0.5f))
            assertEquals("txt3:1:0.5:0.0", encodeTxtReadingProgress(1, 0.5f, invalid))
        }
    }

    @Test
    fun `malformed and unknown saved values gracefully return null`() {
        listOf(
            null, "", " ", "0:0", "txt4:0:0:0", "TXT:0:0",
            "txt", "txt:0", "txt:0:", "txt:0:1:2", "txt:a:0", "txt:0:a",
            "txt:2147483648:0", "txt:0:2147483648", "txt:0:0.5",
            "txt2:0", "txt2:0:", "txt2:0:0.5:0.5", "txt2:0:bad",
            "txt3:0:0.5", "txt3:0:0.5:", "txt3:0:0.5:bad", "txt3:0:0.5:0.5:extra",
        ).forEach { invalid ->
            assertNull(invalid ?: "null", decodeTxtReadingProgress(invalid))
        }
    }

    @Test
    fun `maximum supported integer positions are preserved`() {
        assertEquals(
            TxtReadingProgress(itemIndex = Int.MAX_VALUE, scrollOffset = Int.MAX_VALUE),
            decodeTxtReadingProgress("txt:2147483647:2147483647"),
        )
        assertEquals(
            TxtReadingProgress(itemIndex = Int.MAX_VALUE, offsetFraction = 0.5f, totalFraction = 1f),
            decodeTxtReadingProgress(encodeTxtReadingProgress(Int.MAX_VALUE, 0.5f, 1f)),
        )
    }
}
