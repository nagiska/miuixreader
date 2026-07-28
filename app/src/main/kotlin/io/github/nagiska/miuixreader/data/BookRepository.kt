package io.github.nagiska.miuixreader.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class BookRepository(
    context: Context,
    private val dao: BookDao,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val libraryDir = File(context.filesDir, "library").also { it.mkdirs() }
    private val booksDir = File(libraryDir, "books").also { it.mkdirs() }
    private val coversDir = File(libraryDir, "covers").also { it.mkdirs() }

    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()

    suspend fun getBook(id: Long): BookEntity? = dao.getById(id)

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported book"
        val metadata = try {
            queryMetadata(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withContext ImportOutcome.Failed(fallbackName, error)
        }
        val format = BookFormat.fromFileName(metadata.name)
            ?: BookFormat.fromMimeType(metadata.mimeType)
            ?: return@withContext ImportOutcome.Unsupported(metadata.name)

        val id = UUID.randomUUID().toString()
        val destination = File(booksDir, "$id.${format.extension}")
        val temporary = File(booksDir, "$id.part")
        try {
            val digest = copyAndDigest(uri, temporary)
            if (dao.findBySha256(digest) != null) {
                temporary.delete()
                return@withContext ImportOutcome.Duplicate(metadata.name)
            }

            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }

            val extracted = when (format) {
                BookFormat.EPUB -> extractEpubMetadata(destination, id)
                BookFormat.CBZ -> extractCbzMetadata(destination, id, metadata.name)
                BookFormat.TXT -> TextMetadata(title = titleFromName(metadata.name))
                BookFormat.PDF -> TextMetadata(title = titleFromName(metadata.name))
            }
            val entity = BookEntity(
                title = extracted.title.ifBlank { titleFromName(metadata.name) }.take(MAX_TEXT_FIELD_LENGTH),
                author = extracted.author.take(MAX_TEXT_FIELD_LENGTH),
                format = format.name,
                path = destination.absolutePath,
                originalName = metadata.name.take(MAX_ORIGINAL_NAME_LENGTH),
                sizeBytes = metadata.sizeBytes ?: destination.length(),
                sha256 = digest,
                addedAt = System.currentTimeMillis(),
                coverPath = extracted.coverPath,
            )
            try {
                val bookId = dao.insert(entity)
                ImportOutcome.Imported(bookId, entity.copy(id = bookId))
            } catch (error: Exception) {
                destination.delete()
                extracted.coverPath?.let(::File)?.delete()
                throw error
            }
        } catch (error: CancellationException) {
            temporary.delete()
            destination.delete()
            throw error
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            ImportOutcome.Failed(metadata.name, error)
        }
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        dao.deleteById(book.id)
        File(book.path).delete()
        book.coverPath?.let(::File)?.delete()
    }

    suspend fun markOpened(book: BookEntity, progression: String?) {
        dao.update(
            book.copy(
                lastOpenedAt = System.currentTimeMillis(),
                progression = progression ?: book.progression,
            )
        )
    }

    suspend fun saveProgression(bookId: Long, progression: String) {
        val book = dao.getById(bookId) ?: return
        dao.update(book.copy(progression = progression, lastOpenedAt = System.currentTimeMillis()))
    }

    private fun queryMetadata(uri: Uri): SourceMetadata {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported book"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { cursor.getString(it) }
                        ?.takeIf(String::isNotBlank)
                        ?.let { name = it }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { cursor.getLong(it) }
                        ?.takeIf { it >= 0 }
                        ?.let { size = it }
                }
            }
        return SourceMetadata(
            name = name,
            sizeBytes = size,
            mimeType = resolver.getType(uri),
        )
    }

    private fun copyAndDigest(uri: Uri, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri)
            ?: error("Cannot open $uri")
        input.use { source: InputStream ->
            DigestInputStream(source, digest).use { hashed ->
                FileOutputStream(destination).use { output ->
                    hashed.copyTo(output)
                }
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    private fun extractEpubMetadata(file: File, id: String): TextMetadata {
        return try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: error("EPUB container is missing")
                val container = zip.getInputStream(containerEntry).use { input ->
                    readRootFile(input.readLimitedBytes(MAX_METADATA_BYTES).inputStream())
                }
                val opfEntry = zip.getEntry(container) ?: error("EPUB package is missing")
                zip.getInputStream(opfEntry).use { opfInput ->
                    val input = opfInput.readLimitedBytes(MAX_METADATA_BYTES).inputStream()
                    val parser = Xml.newPullParser().apply {
                        setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
                        setInput(input, null)
                    }
                    var title = ""
                    var author = ""
                    var coverId: String? = null
                    var coverHref: String? = null
                    val manifest = mutableMapOf<String, String>()
                    var currentTag = ""
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                currentTag = parser.name.substringAfterLast(':')
                                if (currentTag == "meta" && parser.getAttributeValue(null, "name") == "cover") {
                                    coverId = parser.getAttributeValue(null, "content")
                                } else if (currentTag == "item") {
                                    val itemId = parser.getAttributeValue(null, "id")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!itemId.isNullOrBlank() && !href.isNullOrBlank()) {
                                        manifest[itemId] = href
                                        val properties = parser.getAttributeValue(null, "properties")
                                            ?.split(Regex("\\s+"))
                                            .orEmpty()
                                        if ("cover-image" in properties) coverHref = href
                                    }
                                }
                            }
                            XmlPullParser.TEXT -> when (currentTag) {
                                "title" -> if (title.isBlank()) title = parser.text.trim()
                                "creator" -> if (author.isBlank()) author = parser.text.trim()
                            }
                            XmlPullParser.END_TAG -> currentTag = ""
                        }
                        event = parser.next()
                    }
                    val coverEntry = (coverHref ?: coverId?.let(manifest::get))
                        ?.let { href -> zip.getEntry(resolveZipPath(container, href)) }
                    val coverPath = coverEntry?.let { entry ->
                        val extension = entry.name.substringAfterLast('.', "").lowercase()
                        if (entry.size in 1..MAX_COVER_BYTES && extension in IMAGE_EXTENSIONS) {
                            val cover = File(coversDir, "$id.$extension")
                            zip.getInputStream(entry).use { coverInput ->
                                FileOutputStream(cover).use { coverOutput -> coverInput.copyTo(coverOutput) }
                            }
                            cover.absolutePath
                        } else {
                            null
                        }
                    }
                    TextMetadata(title = title, author = author, coverPath = coverPath)
                }
            }
        } catch (_: Exception) {
            TextMetadata()
        }
    }

    private fun readRootFile(input: InputStream): String {
        val parser = Xml.newPullParser().apply {
            setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
            setInput(input, null)
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
                    ?: error("EPUB rootfile is missing")
            }
            event = parser.next()
        }
        error("EPUB container has no rootfile")
    }

    private fun extractCbzMetadata(file: File, id: String, originalName: String): TextMetadata {
        return try {
            ZipFile(file).use { zip ->
                val image = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('/').lowercase() in IMAGE_EXTENSIONS }
                    .sortedBy { it.name.lowercase() }
                    .firstOrNull()
                    ?: return@use TextMetadata(title = titleFromName(originalName))
                if (image.size !in 1..MAX_COVER_BYTES) {
                    return@use TextMetadata(title = titleFromName(originalName))
                }
                val cover = File(coversDir, "$id.${image.name.substringAfterLast('.').lowercase()}")
                zip.getInputStream(image).use { input ->
                    FileOutputStream(cover).use { output -> input.copyTo(output, bufferSize = 16 * 1024) }
                }
                TextMetadata(title = titleFromName(originalName), coverPath = cover.absolutePath)
            }
        } catch (_: Exception) {
            TextMetadata(title = titleFromName(originalName))
        }
    }

    private fun resolveZipPath(basePath: String, href: String): String {
        val base = basePath.substringBeforeLast('/', "")
        val decoded = Uri.decode(href.substringBefore('#').substringBefore('?'))
        val resolved = when {
            decoded.startsWith('/') -> decoded.removePrefix("/")
            base.isBlank() -> decoded
            else -> "$base/$decoded"
        }
        val segments = mutableListOf<String>()
        resolved.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    private fun InputStream.readLimitedBytes(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "EPUB metadata is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun titleFromName(name: String): String = name.substringBeforeLast('.', name)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .ifBlank { "Untitled" }

    private data class SourceMetadata(
        val name: String,
        val sizeBytes: Long?,
        val mimeType: String?,
    )

    private data class TextMetadata(
        val title: String = "",
        val author: String = "",
        val coverPath: String? = null,
    )

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
        private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        private const val MAX_COVER_BYTES = 10L * 1024L * 1024L
        private const val MAX_TEXT_FIELD_LENGTH = 500
        private const val MAX_ORIGINAL_NAME_LENGTH = 1_000
    }
}

sealed interface ImportOutcome {
    data class Imported(val id: Long, val book: BookEntity) : ImportOutcome
    data class Duplicate(val name: String) : ImportOutcome
    data class Unsupported(val name: String) : ImportOutcome
    data class Failed(val name: String, val error: Throwable) : ImportOutcome
}
