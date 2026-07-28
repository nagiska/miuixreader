package io.github.nagiska.miuixreader.data

enum class BookFormat(
    val label: String,
    val mediaType: String,
    val extension: String,
) {
    EPUB("EPUB", "application/epub+zip", "epub"),
    TXT("TXT", "text/plain", "txt"),
    PDF("PDF", "application/pdf", "pdf"),
    CBZ("CBZ", "application/vnd.comicbook+zip", "cbz"),
    ;

    companion object {
        fun fromFileName(name: String): BookFormat? = when {
            name.endsWith(".epub", ignoreCase = true) -> EPUB
            name.endsWith(".txt", ignoreCase = true) -> TXT
            name.endsWith(".pdf", ignoreCase = true) -> PDF
            name.endsWith(".cbz", ignoreCase = true) -> CBZ
            else -> null
        }

        fun fromMimeType(mimeType: String?): BookFormat? {
            val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase()
            return when {
                normalized == "application/epub+zip" -> EPUB
                normalized?.startsWith("text/") == true -> TXT
                normalized == "application/pdf" -> PDF
                normalized == "application/vnd.comicbook+zip" -> CBZ
                else -> null
            }
        }
    }
}
