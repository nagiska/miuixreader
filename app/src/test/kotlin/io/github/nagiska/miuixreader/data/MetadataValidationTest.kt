package io.github.nagiska.miuixreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.nagiska.miuixreader.data.MAX_TEXT_FIELD_LENGTH

class MetadataValidationTest {
    @Test
    fun trimsTitleAndAuthor() {
        val result = sanitizeMetadata("  My Book  ", "  Author A  ")
        assertEquals("My Book", result!!.first)
        assertEquals("Author A", result.second)
    }

    @Test
    fun blankTitleIsRejected() {
        assertNull(sanitizeMetadata("   ", "Author"))
        assertNull(sanitizeMetadata("", ""))
    }

    @Test
    fun longFieldsAreCapped() {
        val long = "x".repeat(500)
        val result = sanitizeMetadata(long, long)
        assertEquals(MAX_TEXT_FIELD_LENGTH, result!!.first.length)
        assertEquals(MAX_TEXT_FIELD_LENGTH, result.second.length)
    }
}
