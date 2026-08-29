package io.github.nagiska.miuixreader.tts

data class NarrationSlice(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

data class NarrationTextChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

data class PublicationNarrationBlock(
    val text: String,
    val locatorJson: String,
    val href: String,
    val progression: Double?,
    val highlight: String? = null,
    val cssSelector: String? = null,
)

private val collapsedWhitespace = Regex("\\s+")
private val strongBoundaries = setOf('.', '!', '?', '\u3002', '\uff01', '\uff1f', '\uff1b', ';')
private val softBoundaries = setOf(',', '\u3001', '\uff0c', ':', '\uff1a')

internal fun splitNarrationText(
    text: String,
    startOffset: Int = 0,
    preferredMinCharacters: Int = 48,
    maxCharacters: Int = 160,
): List<NarrationSlice> {
    require(preferredMinCharacters > 0)
    require(maxCharacters >= preferredMinCharacters)
    if (text.isEmpty()) return emptyList()

    val result = mutableListOf<NarrationSlice>()
    var cursor = startOffset.coerceIn(0, text.length)
    if (cursor in 1 until text.length && Character.isLowSurrogate(text[cursor])) cursor++

    while (cursor < text.length) {
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        if (cursor >= text.length) break

        val segmentStart = cursor
        var index = cursor
        var characterCount = 0
        var preferredEnd = -1
        var end = -1

        while (index < text.length && characterCount < maxCharacters) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            index += width
            characterCount++
            val character = codePoint.toChar()

            when {
                character == '\n' || character == '\r' -> {
                    end = index
                    break
                }
                character in strongBoundaries && characterCount >= preferredMinCharacters -> {
                    end = index
                    break
                }
                character in strongBoundaries || character in softBoundaries -> preferredEnd = index
            }
        }

        if (end < 0) {
            end = when {
                index >= text.length -> text.length
                preferredEnd > segmentStart -> preferredEnd
                else -> index
            }
        }
        if (end <= segmentStart) break

        val spoken = text.substring(segmentStart, end)
            .replace(collapsedWhitespace, " ")
            .trim()
        if (spoken.isNotEmpty()) {
            result += NarrationSlice(spoken, segmentStart, end)
        }
        cursor = end
    }
    return result
}

internal fun buildNarrationTextChunks(
    text: String,
    chunkSize: Int = 4_000,
): List<NarrationTextChunk> {
    require(chunkSize > 0)
    if (text.isEmpty()) return listOf(NarrationTextChunk("", 0, 0))

    val chunks = ArrayList<NarrationTextChunk>((text.length + chunkSize - 1) / chunkSize)
    var start = 0
    while (start < text.length) {
        var end = minOf(start + chunkSize, text.length)
        val lineBreak = if (end < text.length) text.lastIndexOf('\n', end - 1) else -1
        if (lineBreak >= start + chunkSize / 2) {
            end = lineBreak
        } else if (end > start && end < text.length && Character.isHighSurrogate(text[end - 1])) {
            end--
        }
        if (end <= start) end = minOf(start + 1, text.length)
        chunks += NarrationTextChunk(text.substring(start, end), start, end)
        start = if (end < text.length && text[end] == '\n') end + 1 else end
    }
    if (text.endsWith('\n')) chunks += NarrationTextChunk("", text.length, text.length)
    return chunks
}

internal fun textAnchorForOffset(
    chunks: List<NarrationTextChunk>,
    offset: Int,
    textLength: Int,
): NarrationAnchor.Txt {
    require(chunks.isNotEmpty())
    val normalizedOffset = offset.coerceIn(0, textLength.coerceAtLeast(0))
    val search = chunks.binarySearchBy(normalizedOffset) { it.startOffset }
    val index = (if (search >= 0) search else -search - 2).coerceIn(0, chunks.lastIndex)
    val chunk = chunks[index]
    val fraction = if (chunk.text.isEmpty()) {
        0f
    } else {
        (normalizedOffset - chunk.startOffset).toFloat().div(chunk.text.length).coerceIn(0f, 1f)
    }
    val total = if (textLength <= 0) 0f else normalizedOffset.toFloat() / textLength
    return NarrationAnchor.Txt(index, fraction, total.coerceIn(0f, 1f))
}

internal fun buildTxtNarrationSegments(
    text: String,
    chunks: List<NarrationTextChunk>,
    startOffset: Int,
): List<NarrationSegment> = splitNarrationText(text, startOffset).map { slice ->
    NarrationSegment(
        text = slice.text,
        anchor = textAnchorForOffset(chunks, slice.startOffset, text.length),
    )
}

internal fun findPublicationStartBlock(
    blocks: List<PublicationNarrationBlock>,
    href: String?,
    progression: Double?,
    highlight: String? = null,
    cssSelector: String? = null,
): Int {
    if (blocks.isEmpty() || href == null) return 0
    val normalizedHref = href.substringBefore('#').substringBefore('?')
    val matching = blocks.indices.filter {
        blocks[it].href.substringBefore('#').substringBefore('?') == normalizedHref
    }
    val targetSelector = cssSelector?.takeIf(String::isNotBlank)
    if (targetSelector != null) {
        matching.firstOrNull { blocks[it].cssSelector == targetSelector }?.let { return it }
    }
    val targetText = highlight?.takeIf(String::isNotBlank)
    if (matching.isEmpty()) {
        if (targetText != null) {
            blocks.indexOfFirst { it.text.contains(targetText) || it.highlight == targetText }
                .takeIf { it >= 0 }
                ?.let { return it }
        }
        return 0
    }
    if (targetText != null) {
        matching.firstOrNull { index ->
            blocks[index].text.contains(targetText) || blocks[index].highlight == targetText
        }?.let { return it }
    }
    if (progression == null) return matching.first()
    return matching.lastOrNull { index ->
        val blockProgression = blocks[index].progression
        blockProgression != null && blockProgression <= progression
    } ?: matching.first()
}

internal fun buildPublicationNarrationSegments(
    blocks: List<PublicationNarrationBlock>,
    href: String?,
    progression: Double?,
    highlight: String? = null,
    cssSelector: String? = null,
): List<NarrationSegment> {
    val firstBlock = findPublicationStartBlock(blocks, href, progression, highlight, cssSelector)
    return blocks.drop(firstBlock).flatMapIndexed { offset, block ->
        val text = if (offset == 0 && !highlight.isNullOrBlank()) {
            val highlightStart = block.text.indexOf(highlight)
            if (highlightStart >= 0) block.text.substring(highlightStart) else block.text
        } else {
            block.text
        }
        splitNarrationText(text).map { slice ->
            NarrationSegment(slice.text, NarrationAnchor.Publication(block.locatorJson))
        }
    }
}
