package io.github.nagiska.miuixreader.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal fun decodeText(bytes: ByteArray): String = when {
    bytes.startsWith(0xEF, 0xBB, 0xBF) ->
        String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
    bytes.startsWith(0xFF, 0xFE) ->
        String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
    bytes.startsWith(0xFE, 0xFF) ->
        String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
    else -> try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        String(bytes, charset("GB18030"))
    }
}

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { index ->
        (this[index].toInt() and 0xFF) == prefix[index]
    }
