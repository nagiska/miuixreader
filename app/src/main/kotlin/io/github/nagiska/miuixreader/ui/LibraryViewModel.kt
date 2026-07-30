package io.github.nagiska.miuixreader.ui

import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.nagiska.miuixreader.ReaderApplication
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BookRepository
import io.github.nagiska.miuixreader.data.ImportOutcome
import io.github.nagiska.miuixreader.data.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val query: String = "",
    val preferences: ReaderPreferences = ReaderPreferences(),
    val isImporting: Boolean = false,
    val message: String? = null,
)

class LibraryViewModel(
    application: ReaderApplication,
    private val repository: BookRepository = application.books,
) : AndroidViewModel(application) {
    private val query = MutableStateFlow("")
    private val isImporting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeBooks(),
        query,
        application.settings.preferences,
        isImporting,
        message,
    ) { books, search, preferences, importing, status ->
        val normalized = search.trim()
        LibraryUiState(
            books = books.filter { book ->
                normalized.isBlank() || book.title.contains(normalized, ignoreCase = true) ||
                    book.author.contains(normalized, ignoreCase = true)
            },
            query = search,
            preferences = preferences,
            isImporting = importing,
            message = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || !isImporting.compareAndSet(false, true)) return
        message.value = null
        viewModelScope.launch {
            var imported = 0
            var skipped = 0
            var failed = 0
            try {
                uris.forEach { uri ->
                    when (repository.import(uri)) {
                        is ImportOutcome.Imported -> imported++
                        is ImportOutcome.Duplicate, is ImportOutcome.Unsupported -> skipped++
                        is ImportOutcome.Failed -> failed++
                    }
                }
                message.value = getApplication<ReaderApplication>().getString(
                    R.string.import_summary,
                    imported,
                    skipped,
                    failed,
                )
            } finally {
                isImporting.value = false
            }
        }
    }

    fun delete(book: BookEntity) {
        viewModelScope.launch { repository.delete(book) }
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch { getApplication<ReaderApplication>().settings.setLiquidGlassEnabled(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { getApplication<ReaderApplication>().settings.setThemeMode(mode) }
    }

    fun importBackground(target: BackgroundTarget, uri: Uri) {
        viewModelScope.launch {
            val imported = getApplication<ReaderApplication>().settings.importBackground(target, uri)
            message.value = getApplication<ReaderApplication>().getString(
                if (imported) R.string.background_imported else R.string.background_import_failed,
            )
        }
    }

    fun clearBackground(target: BackgroundTarget) {
        viewModelScope.launch {
            getApplication<ReaderApplication>().settings.clearBackground(target)
        }
    }

    fun clearMessage() {
        message.value = null
    }

    class Factory(private val application: ReaderApplication) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application) as T
        }
    }
}
