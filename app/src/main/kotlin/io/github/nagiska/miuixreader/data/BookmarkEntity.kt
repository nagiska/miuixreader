package io.github.nagiska.miuixreader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Kind of a reader bookmark: page bookmark or highlight. */
object BookmarkKind {
    const val BOOKMARK = "BOOKMARK"
    const val HIGHLIGHT = "HIGHLIGHT"
}

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: String = BookmarkKind.BOOKMARK,
    /** Publications: [org.readium.r2.shared.publication.Locator] as JSON. */
    val locatorJson: String? = null,
    /** TXT: chunk index and scroll offset inside the chunk. */
    val itemIndex: Int = -1,
    val scrollOffset: Int = 0,
    val excerpt: String = "",
    val createdAt: Long,
)
