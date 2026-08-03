package io.github.nagiska.miuixreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND kind = :kind AND locatorJson = :locatorJson LIMIT 1")
    suspend fun findPublication(bookId: Long, kind: String, locatorJson: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND kind = 'BOOKMARK' AND itemIndex = :itemIndex AND scrollOffset = :scrollOffset LIMIT 1")
    suspend fun findTxt(bookId: Long, itemIndex: Int, scrollOffset: Int): BookmarkEntity?

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
