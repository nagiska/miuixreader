package io.github.nagiska.miuixreader

import android.app.Application
import io.github.nagiska.miuixreader.data.BookDatabase
import io.github.nagiska.miuixreader.data.BookRepository
import io.github.nagiska.miuixreader.data.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex

class ReaderApplication : Application() {
    // Checkpoints outlive their activity/service; all writers share the same ordering lock.
    internal val readingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val readingProgressMutex = Mutex()
    val database: BookDatabase by lazy { BookDatabase.create(this) }
    val books: BookRepository by lazy {
        BookRepository(this, database.bookDao(), database.bookmarkDao())
    }
    val settings: ReaderSettings by lazy { ReaderSettings(this) }
}
