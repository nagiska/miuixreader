package io.github.nagiska.miuixreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        fun create(context: Context): BookDatabase = Room.databaseBuilder(
            context,
            BookDatabase::class.java,
            "miuix-reader.db",
        ).build()
    }
}
