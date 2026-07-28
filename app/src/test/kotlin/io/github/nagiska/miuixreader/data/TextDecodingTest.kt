package io.github.nagiska.miuixreader.data

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodingTest {
    @Test
    fun `decodes utf8 bom`() {
        val content = "Miuix 阅读器"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            content.toByteArray(StandardCharsets.UTF_8)

        assertEquals(content, decodeText(bytes))
    }

    @Test
    fun `decodes utf16 little endian bom`() {
        val content = "Miuix 阅读器"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            content.toByteArray(StandardCharsets.UTF_16LE)

        assertEquals(content, decodeText(bytes))
    }

    @Test
    fun `falls back to gb18030`() {
        val content = "Miuix 阅读器"

        assertEquals(content, decodeText(content.toByteArray(charset("GB18030"))))
    }
}
