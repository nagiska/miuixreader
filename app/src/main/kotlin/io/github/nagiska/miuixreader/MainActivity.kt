package io.github.nagiska.miuixreader

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.ui.ReaderApp
import io.github.nagiska.miuixreader.ui.LibraryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val model: LibraryViewModel by viewModels {
        LibraryViewModel.Factory(application as ReaderApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as ReaderApplication).settings.preferences.collectLatest { preferences ->
                    updateSystemBars(preferences)
                }
            }
        }
        setContent {
            val bookImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris -> model.import(uris) }
            val bookshelfBackgroundLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let { model.importBackground(BackgroundTarget.BOOKSHELF, it) } }
            val readerBackgroundLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let { model.importBackground(BackgroundTarget.READER, it) } }
            ReaderApp(
                viewModel = model,
                onImport = {
                    bookImportLauncher.launch(
                        arrayOf(
                            "application/epub+zip",
                            "text/plain",
                            "application/pdf",
                            "application/vnd.comicbook+zip",
                            "application/zip",
                            "application/octet-stream",
                        ),
                    )
                },
                onOpen = ::openBook,
                onImportBookshelfBackground = {
                    bookshelfBackgroundLauncher.launch(arrayOf("image/*"))
                },
                onImportReaderBackground = {
                    readerBackgroundLauncher.launch(arrayOf("image/*"))
                },
            )
        }
    }

    private fun openBook(book: BookEntity) {
        startActivity(ReaderActivity.intent(this, book.id))
    }

    private fun updateSystemBars(preferences: ReaderPreferences) {
        val isDark = when (preferences.themeMode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
            AppThemeMode.SYSTEM ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars =
                preferences.bookshelfBackgroundPath == null && !isDark
        }
    }
}
