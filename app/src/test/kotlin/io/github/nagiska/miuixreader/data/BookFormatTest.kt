package io.github.nagiska.miuixreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormatTest {
    @Test
    fun recognizesSupportedExtensions() {
        assertEquals(BookFormat.EPUB, BookFormat.fromFileName("Novel.EPUB"))
        assertEquals(BookFormat.TXT, BookFormat.fromFileName("notes.txt"))
        assertEquals(BookFormat.PDF, BookFormat.fromFileName("manual.pdf"))
        assertEquals(BookFormat.CBZ, BookFormat.fromFileName("comic.cbz"))
    }

    @Test
    fun rejectsUnknownExtensions() {
        assertNull(BookFormat.fromFileName("archive.zip"))
        assertNull(BookFormat.fromMimeType("application/octet-stream"))
    }

    @Test
    fun recognizesTextMimeVariants() {
        assertEquals(BookFormat.TXT, BookFormat.fromMimeType("text/plain"))
        assertEquals(BookFormat.TXT, BookFormat.fromMimeType("text/x-readme; charset=utf-8"))
    }
}
