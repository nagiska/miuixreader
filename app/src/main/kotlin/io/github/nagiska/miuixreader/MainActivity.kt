package io.github.nagiska.miuixreader

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.ImportOutcome
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
        hideStatusBar()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as ReaderApplication).settings.preferences.collectLatest { preferences ->
                    updateSystemBars(preferences)
                }
            }
        }
        // A recreated activity already handled its share/import intent; only a
        // fresh launch (or onNewIntent) processes incoming files.
        if (savedInstanceState == null) processSharedIntent(intent)
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
                onEditBook = model::updateMetadata,
            )
        }
    }

    private fun openBook(book: BookEntity) {
        startActivity(ReaderActivity.intent(this, book.id))
    }

    private var processingShare = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processSharedIntent(intent)
    }

    /** Handles ACTION_VIEW / ACTION_SEND / ClipData: import then open the book. */
    private fun processSharedIntent(intent: Intent?) {
        if (intent == null || processingShare) return
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(uris::add)
            Intent.ACTION_SEND ->
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let(uris::add)
            }
        }
        if (uris.isEmpty()) return
        processingShare = true
        lifecycleScope.launch {
            val books = (application as ReaderApplication).books
            try {
                uris.forEach { uri ->
                    when (val outcome = books.import(uri)) {
                        is ImportOutcome.Imported -> openBookById(outcome.id)
                        is ImportOutcome.Duplicate -> openBookById(outcome.bookId)
                        is ImportOutcome.Unsupported, is ImportOutcome.Failed -> Unit
                    }
                }
            } finally {
                processingShare = false
            }
        }
    }

    private fun openBookById(id: Long) {
        startActivity(ReaderActivity.intent(this, id))
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
        hideStatusBar()
    }

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }
}
