package io.github.nagiska.miuixreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTextTest {
    @Test
    fun splitsChineseAtSentenceBoundariesAndKeepsOffsets() {
        val text = "  \u7b2c\u4e00\u53e5\u3002\u7b2c\u4e8c\u53e5\uff01\n\u7b2c\u4e09\u53e5\u3002"
        val slices = splitNarrationText(text, preferredMinCharacters = 4, maxCharacters = 8)

        assertEquals(listOf("\u7b2c\u4e00\u53e5\u3002", "\u7b2c\u4e8c\u53e5\uff01", "\u7b2c\u4e09\u53e5\u3002"), slices.map { it.text })
        assertEquals(text.indexOf('\u7b2c'), slices.first().startOffset)
        assertEquals("\u7b2c\u4e09\u53e5\u3002", text.substring(slices.last().startOffset, slices.last().endOffset))
    }

    @Test
    fun hardLimitDoesNotSplitSurrogatePairs() {
        val text = "abcdef\ud83d\ude03ghijklmnop"
        val slices = splitNarrationText(text, preferredMinCharacters = 4, maxCharacters = 7)

        assertEquals(text, slices.joinToString("") { text.substring(it.startOffset, it.endOffset) })
        assertFalse(slices.any { it.text.contains('\ufffd') })
    }

    @Test
    fun chunksExposeOriginalOffsetsAcrossRemovedNewline() {
        val text = "abcd\nefgh"
        val chunks = buildNarrationTextChunks(text, chunkSize = 5)

        assertEquals(listOf("abcd", "efgh"), chunks.map { it.text })
        assertEquals(listOf(0, 5), chunks.map { it.startOffset })
        val anchor = textAnchorForOffset(chunks, 7, text.length)
        assertEquals(1, anchor.itemIndex)
        assertEquals(0.5f, anchor.offsetFraction, 0.001f)
    }

    @Test
    fun publicationStartsAtNearestCurrentBlock() {
        val blocks = listOf(
            PublicationNarrationBlock("chapter one", "one", "chapter.xhtml", 0.0),
            PublicationNarrationBlock("middle text", "two", "chapter.xhtml", 0.5),
            PublicationNarrationBlock("chapter two", "three", "next.xhtml", 0.0),
        )

        assertEquals(1, findPublicationStartBlock(blocks, "chapter.xhtml", 0.7))
        val segments = buildPublicationNarrationSegments(blocks, "chapter.xhtml", 0.7)
        assertTrue(segments.first().anchor == NarrationAnchor.Publication("two"))
        assertTrue(segments.any { it.anchor == NarrationAnchor.Publication("three") })
    }

    @Test
    fun publicationStartsAtCurrentHighlightInsideTheBlock() {
        val blocks = listOf(
            PublicationNarrationBlock("already spoken. continue here.", "one", "chapter.xhtml", 0.0),
            PublicationNarrationBlock("next paragraph.", "two", "chapter.xhtml", 0.2),
        )

        val segments = buildPublicationNarrationSegments(
            blocks = blocks,
            href = "chapter.xhtml#section",
            progression = 0.1,
            highlight = "continue here.",
        )

        assertEquals("continue here.", segments.first().text)
        assertEquals(NarrationAnchor.Publication("one"), segments.first().anchor)
    }

    @Test
    fun publicationPrefersCurrentElementSelectorOverChapterProgression() {
        val blocks = listOf(
            PublicationNarrationBlock(
                "first paragraph",
                "one",
                "chapter.xhtml",
                0.0,
                cssSelector = "p:nth-of-type(1)",
            ),
            PublicationNarrationBlock(
                "second paragraph",
                "two",
                "chapter.xhtml",
                0.1,
                cssSelector = "p:nth-of-type(2)",
            ),
        )

        assertEquals(
            1,
            findPublicationStartBlock(
                blocks = blocks,
                href = "chapter.xhtml",
                progression = 0.0,
                highlight = "first paragraph second paragraph",
                cssSelector = "p:nth-of-type(2)",
            ),
        )
    }
}
