package io.github.nagiska.miuixreader

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BookFormat
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.data.decodeText
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import top.yukonga.miuix.kmp.icon.extended.Back
import java.io.File

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
    private val containerId = View.generateViewId()
    private var publication: Publication? = null
    private var navigator: Navigator? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
            val repository = (application as ReaderApplication).books
            val book = try {
                repository.getBook(bookId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (book == null || !File(book.path).isFile) {
                showError(getString(R.string.reader_book_missing))
                return@launch
            }
            try {
                repository.markOpened(book, null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Reading should still work if the timestamp cannot be persisted.
            }
            if (book.bookFormat == BookFormat.TXT) {
                showTextReader(book)
            } else {
                showPublicationReader(book)
            }
        }
    }

    private suspend fun showPublicationReader(book: BookEntity) {
        showLoading(book.title)
        val opened = try {
            openPublication(book)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (opened == null) {
            showError(getString(R.string.reader_open_failed))
            return
        }
        publication = opened
        val initialLocator = book.progression?.let { progression ->
            try {
                Locator.fromJSON(JSONObject(progression))
            } catch (_: Exception) {
                null
            }
        }
        val epubListener = object : EpubNavigatorFragment.Listener {}
        val pdfListener = object : PdfNavigatorFragment.Listener {}
        val imageListener = object : ImageNavigatorFragment.Listener {}
        val readerType = try {
            when {
                opened.conformsTo(Publication.Profile.PDF) -> PublicationReader.PDF
                opened.conformsTo(Publication.Profile.DIVINA) -> PublicationReader.IMAGE
                opened.conformsTo(Publication.Profile.EPUB) || opened.readingOrder.allAreHtml ->
                    PublicationReader.EPUB
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        val factory = try {
            when (readerType) {
                PublicationReader.PDF -> {
                    PdfNavigatorFactory(opened, PdfiumEngineProvider())
                        .createFragmentFactory(initialLocator = initialLocator, listener = pdfListener)
                }
                PublicationReader.IMAGE -> {
                    ImageNavigatorFragment.createFactory(opened, initialLocator, imageListener)
                }
                PublicationReader.EPUB -> {
                    EpubNavigatorFactory(opened).createFragmentFactory(
                        initialLocator = initialLocator,
                        listener = epubListener,
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (factory == null) {
            opened.close()
            publication = null
            showError(getString(R.string.reader_format_unsupported))
            return
        }

        withContext(Dispatchers.Main) {
            createReaderRoot(book.title)
            supportFragmentManager.fragmentFactory = factory
            supportFragmentManager.beginTransaction()
                .replace(containerId, when (readerType) {
                    PublicationReader.PDF -> PdfNavigatorFragment::class.java
                    PublicationReader.IMAGE -> ImageNavigatorFragment::class.java
                    PublicationReader.EPUB -> EpubNavigatorFragment::class.java
                    null -> error("Reader type was checked before creating the fragment")
                }, Bundle(), NAVIGATOR_TAG)
                .commitNow()
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator
            (navigator as? OverflowableNavigator)?.let { overflowable ->
                overflowable.addInputListener(
                    DirectionalNavigationAdapter(overflowable, animatedTransition = true),
                )
            }
            observeProgression(book)
        }
    }

    private suspend fun openPublication(book: BookEntity): Publication? = withContext(Dispatchers.IO) {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(contentResolver, httpClient)
        val asset = assetRetriever.retrieve(File(book.path)).getOrNull() ?: return@withContext null
        val parser = DefaultPublicationParser(
            context = this@ReaderActivity,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = PdfiumDocumentFactory(this@ReaderActivity),
        )
        PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrNull()
    }

    private suspend fun showTextReader(book: BookEntity) {
        val text = withContext(Dispatchers.IO) {
            try {
                val file = File(book.path)
                require(file.length() <= MAX_TEXT_BYTES) { "Text file is too large" }
                decodeText(file.readBytes()).take(MAX_TEXT_LENGTH)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        if (text == null) {
            showError(getString(R.string.reader_open_failed))
        } else {
            showTextContent(book, text)
        }
    }

    private fun createReaderRoot(title: String): FrameLayout {
        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        val container = FrameLayout(this).apply { id = containerId }
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            composeChrome(title),
            FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP },
        )
        setContentView(root)
        return root
    }

    private fun composeChrome(title: String): ComposeView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ReaderTheme { ReaderChrome(title = title, onBack = ::finish) }
        }
    }

    private fun showTextContent(book: BookEntity, content: String) {
        val progression = book.progression
            ?.takeIf { it.startsWith(TXT_PROGRESSION_PREFIX) }
            ?.removePrefix(TXT_PROGRESSION_PREFIX)
            ?.split(':', limit = 2)
        val initialPosition = TextPosition(
            itemIndex = progression?.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            scrollOffset = progression?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
        setContent {
            ReaderTheme {
                TextReaderScreen(
                    title = book.title,
                    content = content,
                    initialPosition = initialPosition,
                    onProgress = { position ->
                        lifecycleScope.launch {
                            (application as ReaderApplication).books.saveProgression(
                                book.id,
                                "$TXT_PROGRESSION_PREFIX${position.itemIndex}:${position.scrollOffset}",
                            )
                        }
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    private fun showLoading(title: String) {
        setContent {
            ReaderTheme {
                ReaderStatus(
                    title = title,
                    message = getString(R.string.reader_loading),
                    onBack = ::finish,
                )
            }
        }
    }

    private fun showError(message: String) {
        setContent {
            ReaderTheme {
                ReaderStatus(
                    title = getString(R.string.app_name),
                    message = message,
                    onBack = ::finish,
                )
            }
        }
    }

    private fun observeProgression(book: BookEntity) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                navigator?.currentLocator?.collectLatest { locator ->
                    (application as ReaderApplication).books.saveProgression(
                        book.id,
                        locator.toJSON().toString(),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        navigator = null
        publication?.close()
        publication = null
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "readium_navigator"
        private const val MAX_TEXT_LENGTH = 16 * 1024 * 1024
        private const val MAX_TEXT_BYTES = 32L * 1024L * 1024L
        private const val TXT_PROGRESSION_PREFIX = "txt:"

        fun intent(context: android.content.Context, bookId: Long) =
            android.content.Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}

@Composable
private fun ReaderChrome(title: String, onBack: () -> Unit) {
    top.yukonga.miuix.kmp.basic.TopAppBar(
        title = title,
        navigationIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                top.yukonga.miuix.kmp.basic.Icon(
                    top.yukonga.miuix.kmp.icon.MiuixIcons.Back,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    )
}

@Composable
private fun ReaderStatus(title: String, message: String, onBack: () -> Unit) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = { ReaderChrome(title = title, onBack = onBack) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            top.yukonga.miuix.kmp.basic.Text(message)
        }
    }
}

@Composable
private fun TextReaderScreen(
    title: String,
    content: String,
    initialPosition: TextPosition,
    onProgress: (TextPosition) -> Unit,
    onBack: () -> Unit,
) {
    val chunks = remember(content) { chunkText(content) }
    val initialItem = initialPosition.itemIndex.coerceIn(0, maxOf(0, chunks.lastIndex))
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialItem,
        initialFirstVisibleItemScrollOffset = initialPosition.scrollOffset,
    )
    LaunchedEffect(listState) {
        snapshotFlow {
            TextPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
            .distinctUntilChanged()
            .collectLatest { position ->
                delay(750)
                onProgress(position)
            }
    }
    DisposableEffect(listState) {
        onDispose {
            onProgress(TextPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset))
        }
    }
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = { ReaderChrome(title = title, onBack = onBack) },
    ) { paddingValues ->
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                state = listState,
                contentPadding = PaddingValues(20.dp),
            ) {
                items(chunks) { chunk ->
                    top.yukonga.miuix.kmp.basic.Text(
                        text = chunk,
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1.copy(
                            fontSize = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}

private data class TextPosition(val itemIndex: Int, val scrollOffset: Int)

private enum class PublicationReader { EPUB, PDF, IMAGE }

private fun chunkText(text: String, chunkSize: Int = 4_000): List<String> {
    if (text.isEmpty()) return listOf("")
    val chunks = ArrayList<String>((text.length + chunkSize - 1) / chunkSize)
    var start = 0
    while (start < text.length) {
        var end = minOf(start + chunkSize, text.length)
        val lineBreak = if (end < text.length) text.lastIndexOf('\n', end - 1) else -1
        if (lineBreak >= start + chunkSize / 2) {
            end = lineBreak
        } else if (end < text.length && Character.isHighSurrogate(text[end - 1])) {
            end--
        }
        chunks += text.substring(start, end)
        start = if (end < text.length && text[end] == '\n') end + 1 else end
    }
    if (text.endsWith('\n')) chunks += ""
    return chunks
}
