package io.github.nagiska.miuixreader

import android.app.Application
import io.github.nagiska.miuixreader.data.BookDatabase
import io.github.nagiska.miuixreader.data.BookRepository
import io.github.nagiska.miuixreader.data.ReaderSettings

class ReaderApplication : Application() {
    val database: BookDatabase by lazy { BookDatabase.create(this) }
    val books: BookRepository by lazy {
        BookRepository(this, database.bookDao(), database.bookmarkDao())
    }
    val settings: ReaderSettings by lazy { ReaderSettings(this) }
}
