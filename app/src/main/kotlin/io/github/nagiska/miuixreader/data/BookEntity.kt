package io.github.nagiska.miuixreader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["sha256"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val format: String,
    val path: String,
    val originalName: String,
    val sizeBytes: Long,
    val sha256: String,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val progression: String? = null,
    val coverPath: String? = null,
)

val BookEntity.bookFormat: BookFormat
    get() = BookFormat.entries.firstOrNull { it.name == format } ?: BookFormat.TXT
