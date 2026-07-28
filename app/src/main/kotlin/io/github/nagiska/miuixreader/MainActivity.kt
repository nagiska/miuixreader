package io.github.nagiska.miuixreader

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.ui.ReaderApp
import io.github.nagiska.miuixreader.ui.LibraryViewModel

class MainActivity : FragmentActivity() {
    private val model: LibraryViewModel by viewModels {
        LibraryViewModel.Factory(application as ReaderApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris -> model.import(uris) }
            ReaderApp(
                viewModel = model,
                onImport = {
                    importLauncher.launch(
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
            )
        }
    }

    private fun openBook(book: BookEntity) {
        startActivity(ReaderActivity.intent(this, book.id))
    }
}
