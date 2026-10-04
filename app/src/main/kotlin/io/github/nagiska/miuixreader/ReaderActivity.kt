package io.github.nagiska.miuixreader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BookFormat
import io.github.nagiska.miuixreader.data.BookmarkEntity
import io.github.nagiska.miuixreader.data.BookmarkKind
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderFontFamily
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.data.contrastTextColor
import io.github.nagiska.miuixreader.data.decodeText
import io.github.nagiska.miuixreader.data.decodeTxtReadingProgress
import io.github.nagiska.miuixreader.data.encodeTxtReadingProgress
import io.github.nagiska.miuixreader.ui.reader.ReaderBackdrop
import io.github.nagiska.miuixreader.ui.reader.ReaderChrome
import io.github.nagiska.miuixreader.ui.reader.ReaderChromeState
import io.github.nagiska.miuixreader.ui.reader.ReaderPositionLabel
import io.github.nagiska.miuixreader.ui.reader.ReaderSearchResult
import io.github.nagiska.miuixreader.ui.reader.flattenToc
import io.github.nagiska.miuixreader.ui.reader.pageToProgression
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import io.github.nagiska.miuixreader.tts.NarrationAnchor
import io.github.nagiska.miuixreader.tts.NarrationPhase
import io.github.nagiska.miuixreader.tts.NarrationPlaybackState
import io.github.nagiska.miuixreader.tts.NarrationService
import io.github.nagiska.miuixreader.tts.NarrationSession
import io.github.nagiska.miuixreader.tts.NarrationSegment
import io.github.nagiska.miuixreader.tts.PublicationNarrationBlock
import io.github.nagiska.miuixreader.tts.buildNarrationTextChunks
import io.github.nagiska.miuixreader.tts.buildPublicationNarrationSegments
import io.github.nagiska.miuixreader.tts.buildTxtNarrationSegments
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUri
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private data class BackgroundDataUri(val signature: String, val uri: String)

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
    private val containerId = View.generateViewId()
    private val chromeState = ReaderChromeState()
    private val publicationBackdrop = ReaderBackdrop()
    private var publicationPosition by mutableStateOf(ReaderPositionLabel())
    private var publication: Publication? = null
    private var navigator: Navigator? = null
    private var epubNavigator: EpubNavigatorFragment? = null
    private var publicationReader: PublicationReader? = null
    private var publicationPositionCount = 0
    private var chromeView: ComposeView? = null
    private var latestPreferences = ReaderPreferences()
    private var initialPreferences = ReaderPreferences()
    private var capturePending = false
    private var backdropGeneration = 0L
    private var backdropDirty = true
    private var backdropCaptureJob: Job? = null
    private var wallpaperJob: Job? = null
    private var backdropBackgroundKey: String? = null
    private var pageStyleGeneration = 0L
    private val pageStyleMutex = Mutex()
    private var chromeDrawn = false
    private var backGestureInProgress = false
    private var navigationBarsVisible: Boolean? = null
    private var readerContentReady = false
    private var textCheckpoint: (() -> Unit)? = null
    private var lastStableProgression: String? = null
    private var checkpointFrozen = false
    private var lastBackdropRefreshAt = 0L
    private var cachedBackgroundImage: BackgroundDataUri? = null
    private var narrationState by mutableStateOf(NarrationPlaybackState())
    private var narrationBuildJob: Job? = null
    private var pendingNarrationStart: (() -> Unit)? = null
    private var lastNarrationLocatorJson: String? = null
    private var narrationStartNavigationPending = false
    private var typographyRestoreJob: Job? = null
    private var typographyGeneration = 0L
    private var suppressProgression = false

    private val readerSettings get() = (application as ReaderApplication).settings
    private val touchExplorationEnabled: Boolean
        get() = (getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val success = readerSettings.importBackground(BackgroundTarget.READER, uri)
                if (!success) {
                    android.widget.Toast.makeText(
                        this@ReaderActivity,
                        getString(R.string.background_import_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingNarrationStart?.invoke()
        pendingNarrationStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        hideStatusBar()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                readerSettings.preferences.collectLatest { preferences ->
                    latestPreferences = preferences
                    updateSystemBars(preferences)
                    updatePublicationBackground(preferences)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NarrationService.currentState.collectLatest(::applyNarrationState)
            }
        }
        lifecycleScope.launch {
            initialPreferences = readerSettings.preferences.first()
            latestPreferences = initialPreferences
            val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
            val repository = (application as ReaderApplication).books
            val book = try {
                repository.getBook(bookId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            currentBookId = book?.id ?: -1L
            lastStableProgression = book?.progression
            applyNarrationState(NarrationService.currentState.value)
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

    private fun applyNarrationState(state: NarrationPlaybackState) {
        if (state.bookId != currentBookId) {
            narrationState = NarrationPlaybackState()
            clearNarrationHighlight()
            return
        }
        narrationState = state
        if (state.phase != NarrationPhase.PLAYING) {
            if (state.phase in setOf(NarrationPhase.IDLE, NarrationPhase.ERROR)) {
                narrationStartNavigationPending = false
                clearNarrationHighlight()
            }
            return
        }
        val anchor = state.anchor as? NarrationAnchor.Publication
        if (anchor == null) {
            clearNarrationHighlight()
            return
        }
        val nav = navigator ?: return
        if (anchor.locatorJson == lastNarrationLocatorJson) return
        val locator = runCatching { Locator.fromJSON(JSONObject(anchor.locatorJson)) }.getOrNull() ?: return
        lastNarrationLocatorJson = anchor.locatorJson
        if (narrationStartNavigationPending) {
            // The first segment was selected from the current viewport. Re-loading its locator
            // here can reset a paginated WebView to the start of the resource.
            narrationStartNavigationPending = false
        } else {
            nav.go(locator, animated = false)
        }
        val decorable = nav as? DecorableNavigator ?: return
        if (!decorable.supportsDecorationStyle(Decoration.Style.Highlight::class)) return
        lifecycleScope.launch {
            decorable.applyDecorations(
                listOf(
                    Decoration(
                        id = "narration-current",
                        locator = locator,
                        style = Decoration.Style.Highlight(tint = 0x4DFFB300),
                    ),
                ),
                NARRATION_DECORATION_GROUP,
            )
        }
    }

    private fun clearNarrationHighlight() {
        if (lastNarrationLocatorJson == null) return
        lastNarrationLocatorJson = null
        val decorable = navigator as? DecorableNavigator ?: return
        lifecycleScope.launch {
            runCatching { decorable.applyDecorations(emptyList(), NARRATION_DECORATION_GROUP) }
        }
    }

    private fun toggleNarration(onStart: () -> Unit) {
        when (narrationState.phase) {
            NarrationPhase.PREPARING, NarrationPhase.BUFFERING, NarrationPhase.PLAYING ->
                NarrationService.pause(this)
            NarrationPhase.PAUSED -> NarrationService.resume(this)
            NarrationPhase.IDLE, NarrationPhase.ERROR -> startWithNotificationPermission(onStart)
        }
    }

    private fun startWithNotificationPermission(onStart: () -> Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingNarrationStart = onStart
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStart()
        }
    }

    private fun startPublicationNarration(title: String) {
        narrationBuildJob?.cancel()
        if (narrationState.isActive) NarrationService.stop(this)
        clearNarrationHighlight()
        narrationStartNavigationPending = true
        narrationState = NarrationPlaybackState(
            bookId = currentBookId,
            title = title,
            phase = NarrationPhase.PREPARING,
        )
        narrationBuildJob = lifecycleScope.launch {
            try {
                val pub = publication ?: error(getString(R.string.narration_no_text))
                val pageLocator = navigator?.currentLocator?.value
                val current = (navigator as? VisualNavigator)?.firstVisibleElementLocator()
                    ?.let { visibleLocator ->
                        visibleLocator.copyWithLocations(
                            progression = visibleLocator.locations.progression
                                ?: pageLocator?.locations?.progression,
                            totalProgression = visibleLocator.locations.totalProgression
                                ?: pageLocator?.locations?.totalProgression,
                        )
                    }
                    ?: pageLocator
                val publicationContent = pub.content()
                    ?: error(getString(R.string.narration_no_text))
                val blocks = withContext(Dispatchers.Default) {
                    publicationContent.elements().flatMap { element ->
                        val textual = element as? Content.TextualElement
                            ?: return@flatMap emptyList<PublicationNarrationBlock>()
                        val elementText = textual.text
                        val parts = if (textual is Content.TextElement && textual.segments.isNotEmpty()) {
                            textual.segments.map { segment -> segment.text to segment.locator }
                        } else {
                            listOfNotNull(textual.text?.let { text -> text to textual.locator })
                        }
                        parts.mapNotNull { (text, locator) ->
                            text.takeIf(String::isNotBlank)?.let {
                                PublicationNarrationBlock(
                                    text = it,
                                    locatorJson = locator.toJSON().toString(),
                                    href = locator.href.toString(),
                                    progression = locator.locations.progression,
                                    totalProgression = locator.locations.totalProgression,
                                    highlight = locator.text.highlight,
                                    cssSelector = locator.locations.otherLocations["cssSelector"] as? String,
                                    elementText = elementText,
                                )
                            }
                        }
                    }
                }
                val segments = withContext(Dispatchers.Default) {
                    buildPublicationNarrationSegments(
                        blocks,
                        current?.href?.toString(),
                        current?.locations?.progression,
                        current?.text?.highlight,
                        current?.locations?.otherLocations?.get("cssSelector") as? String,
                        current?.locations?.totalProgression,
                    )
                }
                startNarrationSession(title, segments)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                narrationState = narrationState.copy(
                    phase = NarrationPhase.ERROR,
                    errorMessage = error.message ?: getString(R.string.narration_no_text),
                )
            }
        }
    }

    private fun startTextNarration(title: String, content: String, startOffset: Int) {
        narrationBuildJob?.cancel()
        if (narrationState.isActive) NarrationService.stop(this)
        clearNarrationHighlight()
        narrationStartNavigationPending = false
        narrationState = NarrationPlaybackState(
            bookId = currentBookId,
            title = title,
            phase = NarrationPhase.PREPARING,
        )
        narrationBuildJob = lifecycleScope.launch {
            try {
                val segments = withContext(Dispatchers.Default) {
                    val chunks = buildNarrationTextChunks(content)
                    buildTxtNarrationSegments(content, chunks, startOffset.coerceIn(0, content.length))
                }
                startNarrationSession(title, segments)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                narrationState = narrationState.copy(
                    phase = NarrationPhase.ERROR,
                    errorMessage = error.message ?: getString(R.string.narration_no_text),
                )
            }
        }
    }

    private fun startNarrationSession(title: String, segments: List<NarrationSegment>) {
        if (segments.isEmpty()) {
            narrationState = narrationState.copy(
                phase = NarrationPhase.ERROR,
                errorMessage = getString(R.string.narration_no_text),
            )
            return
        }
        NarrationService.start(
            this,
            NarrationSession(
                bookId = currentBookId,
                title = title,
                segments = segments,
            ),
        )
    }

    private fun stopNarrationIfActive() {
        narrationBuildJob?.cancel()
        narrationBuildJob = null
        narrationStartNavigationPending = false
        if (narrationState.isActive) {
            NarrationService.stop(this)
            narrationState = NarrationPlaybackState()
            clearNarrationHighlight()
        }
    }

    private fun saveProgressionIfAllowed(bookId: Long, progression: String, finalCheckpoint: Boolean = false) {
        if (bookId < 0 || checkpointFrozen || (suppressProgression && !finalCheckpoint)) return
        val typographyGenerationAtRequest = typographyGeneration
        val narrationAtRequest = NarrationService.currentState.value
        if (narrationAtRequest.bookId == bookId && narrationAtRequest.isActive) return
        lastStableProgression = progression
        val app = application as ReaderApplication
        app.readingScope.launch {
            app.readingProgressMutex.withLock {
                if (
                    typographyGenerationAtRequest == typographyGeneration &&
                    (!suppressProgression || finalCheckpoint) &&
                    lastStableProgression == progression &&
                    NarrationService.currentState.value.let { narration ->
                        narration.bookId != bookId || !narration.isActive
                    }
                ) {
                    try {
                        app.books.saveProgression(bookId, progression)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("ReaderActivity", "Could not save reading checkpoint", error)
                    }
                }
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
        val epubListener = object : EpubNavigatorFragment.Listener {
            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                if (!url.isHttp) return
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    // The publication remains open when no browser is available.
                }
            }
        }
        val pdfListener = object : PdfNavigatorFragment.Listener {}
        val imageListener = object : ImageNavigatorFragment.Listener {}
        val epubPaginationListener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                // Skip the style re-injection while dragging the progress
                // slider — it re-renders the WebView and makes the sheet flicker.
                lifecycleScope.launch {
                    if (!seekingProgression) {
                        applyEpubPageStyle(latestPreferences)
                        refreshPublicationBackdrop()
                    }
                }
            }

            override fun onPageLoaded() {
                lifecycleScope.launch {
                    applyEpubPageStyle(latestPreferences)
                    refreshPublicationBackdrop()
                }
            }
        }
        val readerType = try {
            when {
                opened.conformsTo(Publication.Profile.PDF) -> PublicationReader.PDF
                opened.conformsTo(Publication.Profile.DIVINA) -> PublicationReader.IMAGE
                opened.conformsTo(Publication.Profile.EPUB) || opened.readingOrder.allAreHtml ->
                    PublicationReader.EPUB
                else -> null
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
        publicationReader = readerType
        updateSystemBars(latestPreferences)
        val supportsTypography = readerType == PublicationReader.EPUB &&
            opened.metadata.layout != Layout.FIXED
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
                        initialPreferences = initialPreferences.toEpubPreferences(),
                        listener = epubListener,
                        paginationListener = epubPaginationListener,
                    )
                }
                else -> null
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
        if (factory == null) {
            opened.close()
            publication = null
            showError(getString(R.string.reader_format_unsupported))
            return
        }

        publicationPositionCount = try {
            withContext(Dispatchers.IO) { opened.positions().size }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            0
        }
        withContext(Dispatchers.Main) {
            createReaderRoot(book.title, supportsTypography)
            supportFragmentManager.fragmentFactory = factory
            supportFragmentManager.beginTransaction()
                .replace(
                    containerId,
                    when (readerType) {
                        PublicationReader.PDF -> PdfNavigatorFragment::class.java
                        PublicationReader.IMAGE -> ImageNavigatorFragment::class.java
                        PublicationReader.EPUB -> EpubNavigatorFragment::class.java
                        null -> error("Reader type was checked before creating the fragment")
                    },
                    Bundle(),
                    NAVIGATOR_TAG,
                )
                .commitNow()
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator
            epubNavigator = navigator as? EpubNavigatorFragment
            readerContentReady = true
            updateSystemBars(latestPreferences)
            updatePublicationBackground(latestPreferences)
            installPublicationInput()
            observeProgression(book)
            observeBookmarks(book.id)
            observePublicationPreferences(supportsTypography)
            applyNarrationState(NarrationService.currentState.value)
            if (touchExplorationEnabled) showPublicationChrome()
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

    private fun createReaderRoot(title: String, supportsTypography: Boolean): FrameLayout {
        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        val container = FrameLayout(this).apply { id = containerId }
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        val chrome = composeChrome(title, supportsTypography).apply {
            visibility = View.GONE
        }
        chromeView = chrome
        root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        return root
    }

    private fun composeChrome(title: String, supportsTypography: Boolean): ComposeView =
        ComposeView(this).also { composeView ->
            composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            composeView.setContent {
                val preferences by readerSettings.preferences.collectAsStateWithLifecycle(
                    initialValue = initialPreferences,
                )
                ReaderTheme(themeMode = preferences.themeMode) {
                    ReaderChrome(
                        title = title,
                        preferences = preferences,
                        chrome = chromeState,
                        progress = publicationPosition,
                        supportsTypography = supportsTypography,
                        backdrop = publicationBackdrop,
                        readerImagePath = preferences.readerBackgroundPath,
                        readerImageScrim = preferences.readerBackgroundScrim,
                        onBack = ::finish,
                        onFontFamilyChange = { updateFontFamily(it) },
                        onFontScaleChange = { updateFontScale(it) },
                        onFontWeightChange = { updateFontWeight(it) },
                        onBackgroundFollowTheme = { updateBackgroundMode(ReaderBackgroundMode.FOLLOW_THEME) },
                        onBackgroundColorChange = { updateBackgroundColor(it) },
                        onBackgroundImage = { updateBackgroundMode(ReaderBackgroundMode.IMAGE) },
                        onImportBackground = { backgroundLauncher.launch(arrayOf("image/*")) },
                        onClearBackground = { clearReaderBackground() },
                        onBackgroundScrimChange = { scrim ->
                            lifecycleScope.launch { readerSettings.setReaderImageScrim(scrim) }
                        },
                        autoHideEnabled = !touchExplorationEnabled,
                        onSeekPage = { page ->
                            stopNarrationIfActive()
                            seekToPage(page)
                        },
                        tableOfContents = publication?.tableOfContents.orEmpty(),
                        onTocClick = { link ->
                            stopNarrationIfActive()
                            navigator?.go(link)
                            chromeState.closePanel()
                        },
                        searchResults = searchResults,
                        searching = searching,
                        onSearchQuery = ::handleSearchQuery,
                        onSearchResultClick = ::handleSearchResultClick,
                        bookmarks = bookmarks,
                        bookmarked = bookmarked,
                        onToggleBookmark = ::toggleBookmark,
                        onBookmarkClick = ::handleBookmarkClick,
                        onBookmarkDelete = ::handleBookmarkDelete,
                        searchAvailable = publicationReader == PublicationReader.EPUB,
                        narrationAvailable = publicationReader == PublicationReader.EPUB,
                        narrationState = narrationState,
                        onNarrationToggle = {
                            toggleNarration { startPublicationNarration(title) }
                        },
                        onNarrationStop = {
                            stopNarrationIfActive()
                        },
                        onVisibilityChanged = { visible ->
                            onReaderChromeVisibilityChanged(visible)
                            if (!visible && !chromeState.visible) {
                                composeView.visibility = View.GONE
                                refreshPublicationBackdrop()
                            }
                        },
                        onBackGestureChanged = ::onReaderBackGestureChanged,
                    )
                }
            }
        }

    private fun installPublicationInput() {
        val visualNavigator = navigator as? VisualNavigator ?: return
        visualNavigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val height = visualNavigator.publicationView.height.toFloat()
                    if (height <= 0f) return false
                    val activationTop = height * CHROME_TAP_REGION_TOP
                    val activationBottom = height * CHROME_TAP_REGION_BOTTOM
                    val isActivationTap = event.point.y <= activationTop ||
                        event.point.y >= height - activationBottom
                    if (!isActivationTap) return false
                    showPublicationChrome()
                    return true
                }
            },
        )
        (navigator as? OverflowableNavigator)?.let { overflowable ->
            overflowable.addInputListener(
                DirectionalNavigationAdapter(overflowable, animatedTransition = true),
            )
        }
    }

    private fun showPublicationChrome() {
        val view = chromeView ?: return
        if (chromeState.visible) {
            chromeState.hide()
            return
        }
        // Never delay input or hide chrome for an asynchronous window capture.
        backdropCaptureJob?.cancel()
        backdropGeneration++
        backdropDirty = true
        view.visibility = View.VISIBLE
        view.bringToFront()
        chromeState.show()
        onReaderChromeVisibilityChanged(true)
    }

    /** Invalidates old page pixels; capture is allowed only after chrome fully exits. */
    private fun refreshPublicationBackdrop(clearSnapshot: Boolean = true) {
        backdropGeneration++
        backdropDirty = true
        if (clearSnapshot) publicationBackdrop.clear()
        scheduleBackdropCapture()
    }

    private fun scheduleBackdropCapture() {
        backdropCaptureJob?.cancel()
        if (
            !backdropDirty || capturePending || seekingProgression || backGestureInProgress ||
            chromeState.visible || chromeDrawn || chromeView?.visibility != View.GONE ||
            isDestroyed || !readerContentReady || !latestPreferences.liquidGlassEnabled
        ) return
        val generation = backdropGeneration
        backdropCaptureJob = lifecycleScope.launch {
            val remaining = BACKDROP_REFRESH_MIN_INTERVAL_MILLIS -
                (SystemClock.uptimeMillis() - lastBackdropRefreshAt)
            if (remaining > 0L) delay(remaining)
            if (!awaitWindowFrame()) return@launch
            if (generation != backdropGeneration || chromeDrawn || chromeState.visible) return@launch
            capturePublicationBackdrop(generation)
        }
    }

    private suspend fun awaitWindowFrame(): Boolean = withTimeoutOrNull(750L) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val decor = window.decorView
            val observer = decor.viewTreeObserver
            val committed = Runnable { if (continuation.isActive) continuation.resume(Unit) }
            val listener = decor.doOnPreDraw {
                if (continuation.isActive) {
                    if (decor.isHardwareAccelerated) {
                        decor.viewTreeObserver.registerFrameCommitCallback(committed)
                    } else {
                        decor.post(committed)
                    }
                }
            }
            continuation.invokeOnCancellation {
                listener.removeListener()
                if (observer.isAlive) observer.unregisterFrameCommitCallback(committed)
                decor.removeCallbacks(committed)
            }
            decor.invalidate()
        }
    } != null

    private fun updatePublicationBackground(preferences: ReaderPreferences) {
        if (publicationReader == null) return
        val customBackground = publicationReader == PublicationReader.EPUB &&
            publication?.metadata?.layout != Layout.FIXED
        val mode = if (customBackground) preferences.readerBackgroundMode else ReaderBackgroundMode.COLOR
        val color = when {
            !customBackground -> Color.Black
            mode == ReaderBackgroundMode.COLOR -> Color(preferences.readerBackgroundColor)
            mode == ReaderBackgroundMode.IMAGE -> Color.Black
            isDark(preferences.themeMode) -> Color.Black
            else -> Color.White
        }
        val file = preferences.readerBackgroundPath?.takeIf { mode == ReaderBackgroundMode.IMAGE }?.let(::File)
        val width = window.decorView.width.coerceAtLeast(1)
        val height = window.decorView.height.coerceAtLeast(1)
        val key = "$mode:$color:${file?.absolutePath}:${file?.length()}:${file?.lastModified()}:" +
            "${preferences.readerBackgroundScrim}:${preferences.liquidGlassEnabled}:$width:$height"
        if (key == backdropBackgroundKey) return
        backdropBackgroundKey = key
        wallpaperJob?.cancel()
        publicationBackdrop.setBackground(color, width = width, height = height)
        refreshPublicationBackdrop()
        if (mode != ReaderBackgroundMode.IMAGE) {
            cachedBackgroundImage = null
        }
        if (file == null || !preferences.liquidGlassEnabled) return
        wallpaperJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val scale = minOf(1f, MAX_CAPTURE_DIMENSION / maxOf(info.size.width, info.size.height).toFloat())
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }.getOrNull()
            } ?: return@launch
            if (key != backdropBackgroundKey || isDestroyed) {
                bitmap.recycle()
                return@launch
            }
            publicationBackdrop.setBackground(color, bitmap, preferences.readerBackgroundScrim, width, height)
            refreshPublicationBackdrop()
        }
    }

    private var seekJob: Job? = null
    private var seekingProgression = false
    private var seekingPublication = false
    private var latestSeekPage = 1
    private var lastSeekPageConsumed = 1
    private var cachedPositions: List<Locator>? = null
    private var searchJob: Job? = null
    private var searchHighlightJob: Job? = null
    private var searchResults by mutableStateOf(emptyList<ReaderSearchResult>())
    private var searching by mutableStateOf(false)

    private fun handleSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults = emptyList()
            searching = false
            return
        }
        searching = true
        searchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) { searchInPublication(query) }
            searchResults = results
            searching = false
        }
    }

    private fun handleSearchResultClick(result: ReaderSearchResult) {
        stopNarrationIfActive()
        result.locator?.let { locator ->
            navigator?.go(locator)
            showSearchHighlight(locator)
        }
        chromeState.closePanel()
    }

    private fun showSearchHighlight(locator: Locator) {
        val decorable = navigator as? DecorableNavigator ?: return
        if (!decorable.supportsDecorationStyle(Decoration.Style.Highlight::class)) return
        searchHighlightJob?.cancel()
        val group = "search-${SystemClock.uptimeMillis()}"
        searchHighlightJob = lifecycleScope.launch {
            try {
                decorable.applyDecorations(
                    listOf(
                        Decoration(
                            id = "search-hit",
                            locator = locator,
                            style = Decoration.Style.Highlight(tint = 0x66FFD54F),
                        ),
                    ),
                    group,
                )
                delay(3_000)
            } finally {
                withContext(NonCancellable) {
                    try {
                        decorable.applyDecorations(emptyList(), group)
                    } catch (_: Exception) {
                        // The navigator may already be closing with the activity.
                    }
                }
            }
        }
    }

    private suspend fun searchInPublication(query: String): List<ReaderSearchResult> {
        val pub = publication ?: return emptyList()
        // EPUB only: PDF/image publications have no text content service.
        if (publicationReader != PublicationReader.EPUB) return emptyList()
        val content = pub.content() ?: return emptyList()
        val tocTitles = flattenToc(pub.tableOfContents).associate { it.link.href.toString() to (it.link.title ?: "") }
        return content.elements().mapNotNull { element ->
            val text = (element as? Content.TextualElement)?.text ?: return@mapNotNull null
            if (!text.contains(query, ignoreCase = true)) return@mapNotNull null
            ReaderSearchResult(
                title = tocTitles[element.locator.href.toString()] ?: "",
                snippet = text.trim().take(160),
                locator = element.locator,
            )
        }.take(100)
    }

    /**
     * Jumps to [page] (1-based) using the publication's own positions list, so
     * the slider page and the rendered page always match exactly. Follows the
     * finger: each jump runs to completion, then picks up the newest page.
     */
    private fun seekToPage(page: Int) {
        latestSeekPage = page
        if (seekingPublication) return
        seekingPublication = true
        // While the progress slider is being dragged, skip backdrop re-captures
        // (they hide the chrome for one frame and make the sheet flicker).
        seekingProgression = true
        seekJob = lifecycleScope.launch {
            try {
                while (true) {
                    val target = latestSeekPage
                    lastSeekPageConsumed = target
                    val pub = publication ?: break
                    val nav = navigator ?: break
                    val seekResult = withContext(Dispatchers.Default) {
                        val positions = cachedPositions ?: pub.positions().also {
                            cachedPositions = it
                        }
                        val locator = positions.getOrNull(target - 1) ?: run {
                            val total = positions.size.coerceAtLeast(1)
                            pub.locateProgression(pageToProgression(target, total))
                        }
                        locator to positions.size
                    }
                    val locator = seekResult.first
                    if (locator != null) {
                        // Keep the slider's total in sync with the positions list
                        // (re-pagination after a font change recalculates it).
                        if (seekResult.second > 0) {
                            publicationPositionCount = seekResult.second
                        }
                        nav.go(locator, animated = false)
                    }
                    if (target == latestSeekPage) break
                    // Let the page render before the next jump; without this
                    // pause a fast drag swaps whole pages every frame and
                    // flickers.
                    delay(80)
                }
            } finally {
                seekingPublication = false
                seekingProgression = false
                if (latestSeekPage != lastSeekPageConsumed) {
                    // A new request arrived while the executor was finishing
                    // (e.g. a second drag started right after releasing);
                    // restart so it is not dropped.
                    seekToPage(latestSeekPage)
                } else {
                    // Restore the style injection that was skipped while
                    // dragging (onPageChanged may not fire again for the
                    // current resource).
                    applyEpubPageStyle(latestPreferences)
                    refreshPublicationBackdrop()
                }
            }
        }
    }

    private fun capturePublicationBackdrop(generation: Long) {
        if (
            capturePending || isDestroyed || !latestPreferences.liquidGlassEnabled ||
            generation != backdropGeneration || chromeDrawn || chromeState.visible ||
            chromeView?.visibility != View.GONE || backGestureInProgress
        ) return
        val width = window.decorView.width
        val height = window.decorView.height
        if (width <= 0 || height <= 0) return
        val captureScale = minOf(1f, MAX_CAPTURE_DIMENSION / maxOf(width, height).toFloat())
        val captureWidth = maxOf(1, (width * captureScale).toInt())
        val captureHeight = maxOf(1, (height * captureScale).toInt())
        // This buffer belongs exclusively to this request until its callback completes.
        val bitmap = try {
            Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return
        }
        capturePending = true
        lastBackdropRefreshAt = SystemClock.uptimeMillis()
        try {
            val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                capturePending = false
                if (
                    result == PixelCopy.SUCCESS && !isDestroyed &&
                    generation == backdropGeneration && latestPreferences.liquidGlassEnabled &&
                    !chromeDrawn && !chromeState.visible && chromeView?.visibility == View.GONE &&
                    width == window.decorView.width && height == window.decorView.height
                ) {
                    publicationBackdrop.setBitmap(bitmap, width, height)
                    backdropDirty = false
                } else {
                    // Only an unpublished, completed buffer can be recycled safely.
                    bitmap.recycle()
                }
                if (!isDestroyed && generation != backdropGeneration) scheduleBackdropCapture()
            }
            PixelCopy.request(
                window,
                bitmap,
                listener,
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Exception) {
            capturePending = false
            bitmap.recycle()
        }
    }

    private fun parseTextPosition(progression: String?): TextPosition {
        val saved = decodeTxtReadingProgress(progression)
        return TextPosition(
            itemIndex = saved?.itemIndex ?: 0,
            scrollOffset = saved?.scrollOffset ?: 0,
            offsetFraction = saved?.offsetFraction ?: 0f,
            totalFraction = saved?.totalFraction ?: 0f,
        )
    }

    private fun showTextContent(book: BookEntity, content: String) {
        currentBookId = book.id
        readerContentReady = true
        updateSystemBars(latestPreferences)
        observeBookmarks(book.id)
        if (touchExplorationEnabled) chromeState.show()
        val initialPosition = parseTextPosition(book.progression)
        setContent {
            val preferences by readerSettings.preferences.collectAsStateWithLifecycle(
                initialValue = initialPreferences,
            )
            ReaderTheme(themeMode = preferences.themeMode) {
                TextReaderScreen(
                    title = book.title,
                    content = content,
                    initialPosition = initialPosition,
                    preferences = preferences,
                    chrome = chromeState,
                    autoHideEnabled = !touchExplorationEnabled,
                    bookmarks = bookmarks,
                    onToggleTxtBookmark = { index, offset ->
                        lifecycleScope.launch {
                            val books = (application as ReaderApplication).books
                            val existing = books.findTxtBookmark(book.id, index, offset)
                            if (existing != null) {
                                books.removeBookmark(existing.id)
                            } else {
                                books.addBookmark(
                                    BookmarkEntity(
                                        bookId = book.id,
                                        kind = BookmarkKind.BOOKMARK,
                                        itemIndex = index,
                                        scrollOffset = offset,
                                        excerpt = content.substring(
                                            (index * 4_000).coerceAtMost(content.length),
                                            ((index + 1) * 4_000).coerceAtMost(content.length),
                                        ).take(80),
                                        createdAt = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                    },
                    onBookmarkDelete = ::handleBookmarkDelete,
                    onProgress = { position ->
                        saveProgressionIfAllowed(
                            book.id,
                            encodeTxtReadingProgress(position.itemIndex, position.offsetFraction, position.totalFraction),
                        )
                    },
                    onPositionChanged = { position ->
                        if (!suppressProgression && !checkpointFrozen) {
                            lastStableProgression = encodeTxtReadingProgress(
                                position.itemIndex, position.offsetFraction, position.totalFraction,
                            )
                        }
                    },
                    onRegisterCheckpoint = { checkpoint -> textCheckpoint = checkpoint },
                    onChromeVisibilityChanged = ::onReaderChromeVisibilityChanged,
                    onBackGestureChanged = ::onReaderBackGestureChanged,
                    onBack = ::finish,
                    onFontFamilyChange = ::updateFontFamily,
                    onFontScaleChange = ::updateFontScale,
                    onFontWeightChange = ::updateFontWeight,
                    onBackgroundFollowTheme = {
                        updateBackgroundMode(ReaderBackgroundMode.FOLLOW_THEME)
                    },
                    onBackgroundColorChange = ::updateBackgroundColor,
                    onBackgroundImage = { updateBackgroundMode(ReaderBackgroundMode.IMAGE) },
                    onImportBackground = { backgroundLauncher.launch(arrayOf("image/*")) },
                    onClearBackground = ::clearReaderBackground,
                    onBackgroundScrimChange = { scrim ->
                        lifecycleScope.launch { readerSettings.setReaderImageScrim(scrim) }
                    },
                    narrationState = narrationState,
                    onNarrationToggle = { startOffset ->
                        toggleNarration {
                            startTextNarration(book.title, content, startOffset)
                        }
                    },
                    onNarrationStop = {
                        stopNarrationIfActive()
                    },
                )
            }
        }
    }

    private fun showLoading(title: String) {
        setContent {
            ReaderTheme(themeMode = initialPreferences.themeMode) {
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
            ReaderTheme(themeMode = initialPreferences.themeMode) {
                ReaderStatus(
                    title = getString(R.string.app_name),
                    message = message,
                    onBack = ::finish,
                )
            }
        }
    }

    private var bookmarks by mutableStateOf(emptyList<BookmarkEntity>())
    private var bookmarked by mutableStateOf(false)
    private var currentBookId: Long = -1L

    private fun observeBookmarks(bookId: Long) {
        lifecycleScope.launch {
            (application as ReaderApplication).books.observeBookmarks(bookId)
                .collect { list ->
                    bookmarks = list
                    val current = navigator?.currentLocator?.value
                    bookmarked = current != null && list.any {
                        it.kind == BookmarkKind.BOOKMARK &&
                            it.locatorJson == current.toJSON().toString()
                    }
                    refreshHighlights()
                }
        }
    }

    private fun toggleBookmark() {
        val bookId = currentBookId
        if (bookId < 0) return
        val books = (application as ReaderApplication).books
        lifecycleScope.launch {
            // EPUB selection first: save it as a highlight decoration.
            val selectable = navigator as? SelectableNavigator
            val selection = selectable?.currentSelection()
            if (selection != null) {
                val json = selection.locator.toJSON().toString()
                val existing = books.findPublicationBookmark(bookId, BookmarkKind.HIGHLIGHT, json)
                if (existing != null) {
                    books.removeBookmark(existing.id)
                } else {
                    books.addBookmark(
                        BookmarkEntity(
                            bookId = bookId,
                            kind = BookmarkKind.HIGHLIGHT,
                            locatorJson = json,
                            excerpt = selection.locator.text.before?.take(80) ?: "",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                selectable.clearSelection()
                return@launch
            }
            val locator = navigator?.currentLocator?.value ?: return@launch
            val json = locator.toJSON().toString()
            val existing = books.findPublicationBookmark(bookId, BookmarkKind.BOOKMARK, json)
            if (existing != null) {
                books.removeBookmark(existing.id)
            } else {
                books.addBookmark(
                    BookmarkEntity(
                        bookId = bookId,
                        kind = BookmarkKind.BOOKMARK,
                        locatorJson = json,
                        excerpt = locator.text.before?.take(80) ?: "",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Renders the saved EPUB highlights via the navigator's decoration API. */
    private fun refreshHighlights() {
        val decorable = navigator as? DecorableNavigator ?: return
        if (!decorable.supportsDecorationStyle(Decoration.Style.Highlight::class)) return
        val decorations = bookmarks
            .filter { it.kind == BookmarkKind.HIGHLIGHT && it.locatorJson != null }
            .mapNotNull { bookmark ->
                runCatching { Locator.fromJSON(JSONObject(bookmark.locatorJson!!)) }
                    .getOrNull()
                    ?.let { locator ->
                        Decoration(
                            id = "bm-${bookmark.id}",
                            locator = locator,
                            style = Decoration.Style.Highlight(tint = 0x33FFEB3B),
                        )
                    }
            }
        lifecycleScope.launch { decorable.applyDecorations(decorations, "bookmarks") }
    }

    private fun handleBookmarkClick(bookmark: BookmarkEntity) {
        stopNarrationIfActive()
        bookmark.locatorJson?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()?.let {
                navigator?.go(it)
            }
        }
        chromeState.closePanel()
    }

    private fun handleBookmarkDelete(bookmark: BookmarkEntity) {
        lifecycleScope.launch {
            (application as ReaderApplication).books.removeBookmark(bookmark.id)
        }
    }

    private fun observeProgression(book: BookEntity) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator?.currentLocator?.collectLatest { locator ->
                    val total = publicationPositionCount
                    val position = locator.locations.position
                        ?: locator.locations.totalProgression?.let { progression ->
                            if (total > 0) (progression * total).toInt().coerceIn(0, total - 1) + 1 else null
                        }
                    publicationPosition = ReaderPositionLabel(
                        formatPublicationPosition(locator),
                        fraction = (locator.locations.totalProgression ?: 0.0).toFloat(),
                        page = position ?: 1,
                        totalPages = total,
                    )
                    // Pin state must follow the page (and the bookmark list).
                    val locatorJson = locator.toJSON().toString()
                    bookmarked = bookmarks.any {
                        it.kind == BookmarkKind.BOOKMARK && it.locatorJson == locatorJson
                    }
                    refreshPublicationBackdrop()
                    saveProgressionIfAllowed(book.id, locator.toJSON().toString())
                }
            }
        }
    }

    private fun observePublicationPreferences(supportsTypography: Boolean) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                readerSettings.preferences.collectLatest { preferences ->
                    latestPreferences = preferences
                    updatePublicationBackground(preferences)
                    if (supportsTypography) {
                        epubNavigator?.submitPreferences(preferences.toEpubPreferences())
                        applyEpubPageStyle(preferences)
                    }
                    refreshPublicationBackdrop()
                }
            }
        }
    }

    private suspend fun loadBackgroundDataUri(preferences: ReaderPreferences): BackgroundDataUri? {
        val path = preferences.readerBackgroundPath
            ?.takeIf { preferences.readerBackgroundMode == ReaderBackgroundMode.IMAGE }
            ?: return null
        val file = File(path)
        val signature = "$path:${file.length()}:${file.lastModified()}"
        cachedBackgroundImage?.takeIf { it.signature == signature }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                BackgroundDataUri(
                    signature = signature,
                    uri = "data:image/webp;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun applyEpubPageStyle(preferences: ReaderPreferences) {
        val epub = epubNavigator ?: return
        if (publication?.metadata?.layout == Layout.FIXED) return
        val generation = ++pageStyleGeneration
        val backgroundImage = loadBackgroundDataUri(preferences)
        if (generation != pageStyleGeneration || preferences != latestPreferences) return
        val script = buildEpubPageStyleScript(
            preferences = preferences,
            imageDataUri = backgroundImage?.uri,
            fallbackDark = isDark(preferences.themeMode),
            generation = generation,
        )
        pageStyleMutex.withLock {
            if (generation != pageStyleGeneration || preferences != latestPreferences || epub !== epubNavigator) return
            cachedBackgroundImage = backgroundImage
            try {
                epub.evaluateJavascript(script)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The data-URI script can exceed the binder transaction limit or a
                // page transition can remove the WebView. Retry without the image.
                val fallbackScript = buildEpubPageStyleScript(
                    preferences = preferences,
                    imageDataUri = null,
                    fallbackDark = isDark(preferences.themeMode),
                    generation = generation,
                )
                try {
                    if (generation == pageStyleGeneration && preferences == latestPreferences) {
                        epub.evaluateJavascript(fallbackScript)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Even the fallback failed; the page keeps its default style.
                }
            }
        }
    }

    private fun ReaderPreferences.toEpubPreferences(): EpubPreferences {
        val explicitBackground = when (readerBackgroundMode) {
            ReaderBackgroundMode.FOLLOW_THEME -> null
            ReaderBackgroundMode.COLOR -> readerBackgroundColor
            ReaderBackgroundMode.IMAGE -> android.graphics.Color.BLACK
        }
        val explicitText = explicitBackground?.let(::contrastTextColor)
        val dark = when (readerBackgroundMode) {
            ReaderBackgroundMode.FOLLOW_THEME -> isDark(themeMode)
            ReaderBackgroundMode.COLOR -> explicitText == android.graphics.Color.WHITE
            ReaderBackgroundMode.IMAGE -> true
        }
        return EpubPreferences(
            backgroundColor = explicitBackground?.let(::ReadiumColor),
            textColor = explicitText?.let(::ReadiumColor),
            theme = if (dark || readerBackgroundMode == ReaderBackgroundMode.IMAGE) {
                ReadiumTheme.DARK
            } else {
                ReadiumTheme.LIGHT
            },
            fontFamily = when (fontFamily) {
                ReaderFontFamily.ORIGINAL -> null
                ReaderFontFamily.SANS_SERIF -> ReadiumFontFamily.SANS_SERIF
                ReaderFontFamily.SERIF -> ReadiumFontFamily.SERIF
                ReaderFontFamily.MONOSPACE -> ReadiumFontFamily.MONOSPACE
            },
            fontSize = fontScale.toDouble(),
            fontWeight = fontWeight / 400.0,
        )
    }

    private fun formatPublicationPosition(locator: Locator): String {
        val total = publicationPositionCount
        val position = locator.locations.position
            ?: locator.locations.totalProgression?.let { progression ->
                if (total > 0) (progression * total).toInt().coerceIn(0, total - 1) + 1 else null
            }
        val percent = ((locator.locations.totalProgression ?: 0.0) * 100).toInt().coerceIn(0, 100)
        return when (publicationReader) {
            PublicationReader.PDF, PublicationReader.IMAGE, PublicationReader.EPUB -> {
                if (position != null && total > 0) {
                    getString(R.string.reader_page_count, position, total)
                } else {
                    getString(R.string.reader_progress_percent, percent)
                }
            }
            null -> ""
        }
    }

    private fun updateFontFamily(fontFamily: ReaderFontFamily) {
        stopNarrationIfActive()
        scheduleTypographyChange(
            change = { readerSettings.setFontFamily(fontFamily) },
            applied = { it.fontFamily == fontFamily },
        )
    }

    private fun updateFontScale(scale: Float) {
        stopNarrationIfActive()
        scheduleTypographyChange(
            change = { readerSettings.setFontScale(scale) },
            applied = { it.fontScale == scale },
        )
    }

    private fun updateFontWeight(weight: Int) {
        stopNarrationIfActive()
        scheduleTypographyChange(
            change = { readerSettings.setFontWeight(weight) },
            applied = { it.fontWeight == weight },
        )
    }

    private fun scheduleTypographyChange(
        change: suspend () -> Unit,
        applied: (ReaderPreferences) -> Boolean,
    ) {
        typographyGeneration++
        refreshPublicationBackdrop()
        val generation = typographyGeneration
        typographyRestoreJob?.cancel()
        suppressProgression = true
        typographyRestoreJob = lifecycleScope.launch {
            try {
                val anchor = if (publicationReader == PublicationReader.EPUB) {
                    (navigator as? VisualNavigator)?.firstVisibleElementLocator()
                        ?: navigator?.currentLocator?.value
                } else {
                    null
                }
                change()
                withTimeoutOrNull(TYPOGRAPHY_PREFERENCE_TIMEOUT_MILLIS) {
                    readerSettings.preferences.first { preferences -> applied(preferences) }
                }
                if (generation != typographyGeneration) return@launch
                delay(TYPOGRAPHY_RESTORE_DELAY_MILLIS)
                if (generation == typographyGeneration) {
                    anchor?.let { navigator?.go(it, animated = false) }
                    delay(TYPOGRAPHY_RESTORE_SETTLE_DELAY_MILLIS)
                }
            } finally {
                if (generation == typographyGeneration) {
                    suppressProgression = false
                }
            }
        }
        refreshPositionCount()
    }

    /** Recomputes the total page count after re-pagination (e.g. font changes). */
    private fun refreshPositionCount() {
        cachedPositions = null
        lifecycleScope.launch {
            val size = withContext(Dispatchers.Default) { publication?.positions()?.size ?: 0 }
            if (size > 0) publicationPositionCount = size
        }
    }

    private fun updateBackgroundMode(mode: ReaderBackgroundMode) {
        lifecycleScope.launch { readerSettings.setReaderBackgroundMode(mode) }
    }

    private fun updateBackgroundColor(color: Int) {
        lifecycleScope.launch { readerSettings.setReaderBackgroundColor(color) }
    }

    private fun clearReaderBackground() {
        lifecycleScope.launch { readerSettings.clearBackground(BackgroundTarget.READER) }
    }

    private fun isDark(mode: AppThemeMode): Boolean = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM ->
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateSystemBars(preferences: ReaderPreferences) {
        val darkBackground = when (publicationReader) {
            PublicationReader.PDF, PublicationReader.IMAGE -> true
            PublicationReader.EPUB -> if (publication?.metadata?.layout == Layout.FIXED) {
                true
            } else when (preferences.readerBackgroundMode) {
                ReaderBackgroundMode.FOLLOW_THEME -> isDark(preferences.themeMode)
                ReaderBackgroundMode.COLOR ->
                    contrastTextColor(preferences.readerBackgroundColor) == android.graphics.Color.WHITE
                ReaderBackgroundMode.IMAGE -> true
            }
            null -> when (preferences.readerBackgroundMode) {
                ReaderBackgroundMode.FOLLOW_THEME -> isDark(preferences.themeMode)
                ReaderBackgroundMode.COLOR ->
                    contrastTextColor(preferences.readerBackgroundColor) == android.graphics.Color.WHITE
                ReaderBackgroundMode.IMAGE -> true
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkBackground
            isAppearanceLightNavigationBars = !darkBackground
        }
        applyReaderSystemBars()
    }

    private fun hideStatusBar() {
        applyReaderSystemBars()
    }

    private fun onReaderChromeVisibilityChanged(visible: Boolean) {
        chromeDrawn = visible
        applyReaderSystemBars()
    }

    private fun onReaderBackGestureChanged(active: Boolean) {
        backGestureInProgress = active
        if (!active) {
            applyReaderSystemBars()
            scheduleBackdropCapture()
        }
    }

    private fun applyReaderSystemBars() {
        if (backGestureInProgress) return
        val showNavigation = !readerContentReady || chromeDrawn || chromeState.visible || touchExplorationEnabled
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showNavigation) {
                show(WindowInsetsCompat.Type.navigationBars())
            } else {
                hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
        if (navigationBarsVisible != showNavigation) {
            navigationBarsVisible = showNavigation
            // Keep the last clean page available under the controls. Once the
            // controls exit we invalidate it and capture the new viewport.
            refreshPublicationBackdrop(clearSnapshot = !showNavigation)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyReaderSystemBars()
    }

    override fun onStop() {
        // Native predictive back remains unhandled when chrome is hidden. Save from
        // lifecycle checkpoints rather than intercepting the cross-activity animation.
        if (suppressProgression) {
            lastStableProgression?.let { saveProgressionIfAllowed(currentBookId, it, finalCheckpoint = true) }
        } else {
            textCheckpoint?.invoke()
            navigator?.currentLocator?.value?.let { locator ->
                saveProgressionIfAllowed(currentBookId, locator.toJSON().toString(), finalCheckpoint = true)
            }
            if (navigator == null) {
                lastStableProgression?.let { saveProgressionIfAllowed(currentBookId, it, finalCheckpoint = true) }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        // onStop has already submitted its last stable checkpoint. Disposing a
        // reflowing TXT composition must not replace that app-scoped write.
        checkpointFrozen = true
        narrationBuildJob?.cancel()
        typographyRestoreJob?.cancel()
        backdropCaptureJob?.cancel()
        wallpaperJob?.cancel()
        backdropGeneration++
        pageStyleGeneration++
        pendingNarrationStart = null
        super.onDestroy()
        chromeView = null
        navigator = null
        epubNavigator = null
        publication?.close()
        publication = null
        publicationBackdrop.setBackground(Color.Black)
        textCheckpoint = null
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "readium_navigator"
        private const val MAX_TEXT_LENGTH = 16 * 1024 * 1024
        private const val MAX_TEXT_BYTES = 32L * 1024L * 1024L
        private const val CHROME_TAP_REGION_TOP = 0.10f
        private const val CHROME_TAP_REGION_BOTTOM = 0.24f
        private const val MAX_CAPTURE_DIMENSION = 1280
        private const val BACKDROP_REFRESH_MIN_INTERVAL_MILLIS = 400L
        private const val NARRATION_DECORATION_GROUP = "narration"
        private const val TYPOGRAPHY_PREFERENCE_TIMEOUT_MILLIS = 2_000L
        private const val TYPOGRAPHY_RESTORE_DELAY_MILLIS = 450L
        private const val TYPOGRAPHY_RESTORE_SETTLE_DELAY_MILLIS = 350L

        fun intent(context: android.content.Context, bookId: Long) =
            android.content.Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}

@Composable
private fun ReaderStatus(title: String, message: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(message)
        }
    }
}

@Composable
private fun TextReaderScreen(
    title: String,
    content: String,
    initialPosition: TextPosition,
    preferences: ReaderPreferences,
    chrome: ReaderChromeState,
    autoHideEnabled: Boolean,
    bookmarks: List<BookmarkEntity>,
    onToggleTxtBookmark: (Int, Int) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    onProgress: (TextPosition) -> Unit,
    onPositionChanged: (TextPosition) -> Unit,
    onRegisterCheckpoint: ((() -> Unit)?) -> Unit,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    onBackGestureChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onBackgroundFollowTheme: () -> Unit,
    onBackgroundColorChange: (Int) -> Unit,
    onBackgroundImage: () -> Unit,
    onImportBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundScrimChange: (Float) -> Unit,
    narrationState: NarrationPlaybackState,
    onNarrationToggle: (Int) -> Unit,
    onNarrationStop: () -> Unit,
) {
    val chunkItems = remember(content) { buildNarrationTextChunks(content) }
    val chunks = remember(chunkItems) { chunkItems.map { it.text } }
    val chunkStartOffsets = remember(chunkItems) { chunkItems.map { it.startOffset } }
    val totalCharacterCount = content.length.coerceAtLeast(1)
    val initialItem = initialPosition.itemIndex.coerceIn(0, maxOf(0, chunks.lastIndex))
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialItem,
        initialFirstVisibleItemScrollOffset = if (initialPosition.offsetFraction > 0f) {
            0
        } else {
            initialPosition.scrollOffset
        },
    )
    var initialPositionRestored by remember { mutableStateOf(false) }
    val saveProgress by rememberUpdatedState(onProgress)
    val positionChanged by rememberUpdatedState(onPositionChanged)
    fun currentPosition(): TextPosition? {
        if (!initialPositionRestored || listState.layoutInfo.totalItemsCount == 0) return null
        val itemSize = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == listState.firstVisibleItemIndex }?.size ?: return null
        if (itemSize <= 0) return null
        val offsetFraction = (listState.firstVisibleItemScrollOffset.toFloat() / itemSize).coerceIn(0f, 1f)
        return TextPosition(
            itemIndex = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset,
            offsetFraction = offsetFraction,
            totalFraction = textProgression(
                chunks, chunkStartOffsets, totalCharacterCount,
                listState.firstVisibleItemIndex, offsetFraction,
                isAtEnd = content.isNotEmpty() && !listState.canScrollForward,
            ),
        )
    }
    val backdrop = rememberLayerBackdrop()
    val typographySignature = listOf(
        preferences.fontFamily,
        preferences.fontScale,
        preferences.fontWeight,
    )
    var previousTypographySignature by remember { mutableStateOf(typographySignature) }
    var pendingTypographyOffset by remember { mutableStateOf<Int?>(null) }
    val initialProgression = textProgression(
        chunks = chunks,
        chunkStartOffsets = chunkStartOffsets,
        totalCharacterCount = totalCharacterCount,
        itemIndex = initialItem,
        offsetFraction = initialPosition.offsetFraction,
        isAtEnd = false,
    )
    var progressLabel by remember {
        mutableStateOf(
            ReaderPositionLabel(
                textProgressLabel(initialProgression),
                fraction = initialProgression,
            ),
        )
    }
    LaunchedEffect(listState, initialItem, initialPosition.offsetFraction) {
        if (initialPosition.offsetFraction > 0f) {
            val itemSize = snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == initialItem }?.size ?: 0
            }.first { it > 0 }
            listState.scrollToItem(
                initialItem,
                (itemSize * initialPosition.offsetFraction).roundToInt().coerceAtLeast(0),
            )
        } else {
            // Legacy pixel offsets may normalize past the requested item after
            // a font-size change. A measured visible item is enough to save it.
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.size > 0 }
            }.first { it }
        }
        initialPositionRestored = true
    }
    LaunchedEffect(listState, chunks, chunkStartOffsets, totalCharacterCount) {
        snapshotFlow { currentPosition() }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { position ->
                val progression = position.totalFraction
                progressLabel = ReaderPositionLabel(
                    textProgressLabel(progression),
                    fraction = progression,
                )
                positionChanged(position)
                delay(750)
                saveProgress(position)
            }
    }
    fun currentTextOffset(): Int {
        val index = listState.firstVisibleItemIndex.coerceIn(0, chunkItems.lastIndex)
        val chunk = chunkItems[index]
        val itemSize = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.size
            ?: 0
        val fraction = if (itemSize > 0) {
            listState.firstVisibleItemScrollOffset.toFloat() / itemSize
        } else {
            0f
        }
        return (chunk.startOffset + (chunk.text.length * fraction.coerceIn(0f, 1f)).toInt())
            .coerceIn(0, content.length)
    }
    LaunchedEffect(typographySignature) {
        if (previousTypographySignature == typographySignature) return@LaunchedEffect
        previousTypographySignature = typographySignature
        val target = pendingTypographyOffset ?: return@LaunchedEffect
        pendingTypographyOffset = null
        val targetOffset = target.coerceIn(0, content.length.coerceAtLeast(1) - 1)
        val search = chunkStartOffsets.binarySearch(targetOffset)
        val index = (if (search >= 0) search else -search - 2).coerceIn(0, chunks.lastIndex)
        listState.scrollToItem(index, 0)
        val itemSize = withTimeoutOrNull(500) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
            }.first { it > 0 }
        } ?: 0
        val charsInChunk = chunks[index].length.coerceAtLeast(1)
        val inChunk = (targetOffset - chunkStartOffsets[index]).coerceIn(0, charsInChunk - 1)
        listState.scrollToItem(
            index,
            (itemSize * inChunk.toFloat() / charsInChunk).roundToInt(),
        )
    }
    DisposableEffect(listState, chunks.size) {
        val checkpoint: () -> Unit = {
            currentPosition()?.let { position ->
                positionChanged(position)
                saveProgress(position)
            }
        }
        onRegisterCheckpoint(checkpoint)
        onDispose {
            checkpoint()
            onRegisterCheckpoint(null)
        }
    }
    val seekScope = rememberCoroutineScope()
    var latestSeekFraction by remember { mutableFloatStateOf(0f) }
    var seekingText by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<ReaderSearchResult>()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val handleSearchQuery: (String) -> Unit = { query ->
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults = emptyList()
            searching = false
        } else {
            searching = true
            searchJob = seekScope.launch {
            val results = withContext(Dispatchers.Default) {
                val lowered = content.lowercase()
                val q = query.lowercase()
                val out = mutableListOf<ReaderSearchResult>()
                var from = 0
                while (out.size < 100) {
                    val idx = lowered.indexOf(q, from)
                    if (idx < 0) break
                    val search = chunkStartOffsets.binarySearch(idx)
                    val itemIndex =
                        (if (search >= 0) search else -search - 2).coerceIn(0, chunks.lastIndex)
                    val start = (idx - 30).coerceAtLeast(0)
                    val end = (idx + q.length + 30).coerceAtMost(content.length)
                    out += ReaderSearchResult(
                        title = textProgressLabel(
                            textProgression(
                                chunks = chunks,
                                chunkStartOffsets = chunkStartOffsets,
                                totalCharacterCount = totalCharacterCount,
                                itemIndex = itemIndex,
                                offsetFraction = 0f,
                                isAtEnd = false,
                            ),
                        ),
                        snippet = content.substring(start, end).trim(),
                        itemIndex = itemIndex,
                        scrollOffset = 0,
                        hitChar = idx,
                    )
                    from = idx + q.length
                }
                out
            }
            searchResults = results
            searching = false
        }
        }
    }
    val handleSearchResultClick: (ReaderSearchResult) -> Unit = { result ->
        if (narrationState.isActive) onNarrationStop()
        if (result.itemIndex >= 0) {
            seekScope.launch {
                listState.scrollToItem(result.itemIndex, 0)
                if (result.hitChar >= 0) {
                    // Fine-tune inside the chunk by character ratio once the
                    // item has laid out.
                    val itemSize = snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == result.itemIndex }?.size ?: 0
                    }.first { it > 0 }
                    val charsInChunk = chunks[result.itemIndex].length.coerceAtLeast(1)
                    val inChunk =
                        (result.hitChar - chunkStartOffsets[result.itemIndex])
                            .coerceIn(0, charsInChunk - 1)
                    listState.scrollToItem(
                        result.itemIndex,
                        (itemSize * inChunk.toFloat() / charsInChunk).roundToInt(),
                    )
                }
            }
        }
        chrome.closePanel()
    }
    val txtBookmarked = bookmarks.any {
        it.kind == BookmarkKind.BOOKMARK &&
            it.itemIndex == listState.firstVisibleItemIndex &&
            it.scrollOffset == listState.firstVisibleItemScrollOffset
    }
    val handleTxtBookmarkClick: (BookmarkEntity) -> Unit = { bookmark ->
        if (narrationState.isActive) onNarrationStop()
        if (bookmark.itemIndex >= 0) {
            seekScope.launch { listState.scrollToItem(bookmark.itemIndex, bookmark.scrollOffset) }
        }
        chrome.closePanel()
    }
    val seekTo: (Float) -> Unit = { fraction ->
        latestSeekFraction = fraction
        if (!seekingText) {
            seekingText = true
            seekScope.launch {
                try {
                    // Serial executor: each scroll runs to completion (never
                    // cancelled mid-way, which left the list stuck mid-item and
                    // made the page flicker); after each jump it picks up the
                    // newest requested fraction and keeps chasing the finger.
                    while (true) {
                        val targetFraction = latestSeekFraction
                        val target = (totalCharacterCount * targetFraction.coerceIn(0f, 1f)).toInt()
                            .coerceIn(0, totalCharacterCount - 1)
                        val search = chunkStartOffsets.binarySearch(target)
                        val index = (if (search >= 0) search else -search - 2).coerceIn(0, chunks.lastIndex)
                        listState.scrollToItem(index, 0)
                        // Wait for the item to lay out, then fine-tune by
                        // character ratio (with a timeout so a zero-size
                        // layout frame cannot stall the seeker forever).
                        val itemSize = withTimeoutOrNull(500) {
                            snapshotFlow {
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                            }.first { it > 0 }
                        } ?: 0
                        val charsInChunk = chunks[index].length.coerceAtLeast(1)
                        val inChunk = (target - chunkStartOffsets[index]).coerceIn(0, charsInChunk - 1)
                        listState.scrollToItem(
                            index,
                            (itemSize * inChunk.toFloat() / charsInChunk).roundToInt(),
                        )
                        if (targetFraction == latestSeekFraction) break
                    }
                } finally {
                    seekingText = false
                }
            }
        }
    }
    val narrationAnchor = narrationState.anchor as? NarrationAnchor.Txt
    LaunchedEffect(narrationAnchor) {
        if (narrationAnchor != null && narrationState.isActive) {
            seekTo(narrationAnchor.totalFraction)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        // Snapshot chrome visibility when the gesture starts so a
                        // concurrent dismiss (tap-outside, drag-down) in the same
                        // frame cannot re-trigger a show() right after hide().
                        val wasHidden = !chrome.visible
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (up == null) return@awaitEachGesture
                        val activationTop = size.height * TEXT_CHROME_TAP_REGION_TOP
                        val activationBottom = size.height * TEXT_CHROME_TAP_REGION_BOTTOM
                        if (wasHidden && (
                                down.position.y <= activationTop ||
                                    down.position.y >= size.height - activationBottom
                                )
                        ) {
                            chrome.show()
                        }
                    }
                },
        ) {
            TextPageBackground(preferences)
            val textColor = readerTextColor(preferences)
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    state = listState,
                    contentPadding = PaddingValues(20.dp),
                ) {
                    items(chunks) { chunk ->
                        Text(
                            text = chunk,
                            style = MiuixTheme.textStyles.body1.copy(
                                fontFamily = preferences.fontFamily.toComposeFontFamily(),
                                fontSize = (18f * preferences.fontScale).sp,
                                fontWeight = FontWeight(preferences.fontWeight),
                                color = textColor,
                            ),
                        )
                    }
                }
            }
        }
        ReaderChrome(
            title = title,
            preferences = preferences,
            chrome = chrome,
            progress = progressLabel,
            supportsTypography = true,
            backdrop = backdrop,
            readerImagePath = preferences.readerBackgroundPath,
            readerImageScrim = preferences.readerBackgroundScrim,
            onBack = onBack,
            onFontFamilyChange = {
                pendingTypographyOffset = currentTextOffset()
                onFontFamilyChange(it)
            },
            onFontScaleChange = {
                pendingTypographyOffset = currentTextOffset()
                onFontScaleChange(it)
            },
            onFontWeightChange = {
                pendingTypographyOffset = currentTextOffset()
                onFontWeightChange(it)
            },
            onBackgroundFollowTheme = onBackgroundFollowTheme,
            onBackgroundColorChange = onBackgroundColorChange,
            onBackgroundImage = onBackgroundImage,
            onImportBackground = onImportBackground,
            onClearBackground = onClearBackground,
            onBackgroundScrimChange = onBackgroundScrimChange,
            autoHideEnabled = autoHideEnabled,
            onVisibilityChanged = onChromeVisibilityChanged,
            onBackGestureChanged = onBackGestureChanged,
            onSeekFraction = { fraction ->
                if (narrationState.isActive) onNarrationStop()
                seekTo(fraction)
            },
            searchResults = searchResults,
            searching = searching,
            onSearchQuery = handleSearchQuery,
            onSearchResultClick = handleSearchResultClick,
            bookmarks = bookmarks,
            bookmarked = txtBookmarked,
            onToggleBookmark = {
                onToggleTxtBookmark(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            },
            onBookmarkClick = handleTxtBookmarkClick,
            onBookmarkDelete = onBookmarkDelete,
            narrationAvailable = true,
            narrationState = narrationState,
            onNarrationToggle = {
                onNarrationToggle(currentTextOffset())
            },
            onNarrationStop = onNarrationStop,
        )
    }
}

@Composable
private fun TextPageBackground(preferences: ReaderPreferences) {
    val backgroundColor = when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.FOLLOW_THEME -> MiuixTheme.colorScheme.background
        ReaderBackgroundMode.COLOR -> Color(preferences.readerBackgroundColor)
        // Black with white text stays readable even when the image fails
        // to load or is missing.
        ReaderBackgroundMode.IMAGE -> Color.Black
    }
    Box(Modifier.fillMaxSize().background(backgroundColor)) {
        if (
            preferences.readerBackgroundMode == ReaderBackgroundMode.IMAGE &&
            preferences.readerBackgroundPath != null
        ) {
            AsyncImage(
                model = preferences.readerBackgroundPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = preferences.readerBackgroundScrim)),
            )
        }
    }
}

@Composable
private fun readerTextColor(preferences: ReaderPreferences): Color =
    when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.FOLLOW_THEME -> MiuixTheme.colorScheme.onBackground
        ReaderBackgroundMode.COLOR -> Color(contrastTextColor(preferences.readerBackgroundColor))
        // White text on the (possibly image-less) black IMAGE surface stays
        // readable in every state.
        ReaderBackgroundMode.IMAGE -> Color.White
    }

private fun ReaderFontFamily.toComposeFontFamily(): ComposeFontFamily = when (this) {
    ReaderFontFamily.ORIGINAL -> ComposeFontFamily.Default
    ReaderFontFamily.SANS_SERIF -> ComposeFontFamily.SansSerif
    ReaderFontFamily.SERIF -> ComposeFontFamily.Serif
    ReaderFontFamily.MONOSPACE -> ComposeFontFamily.Monospace
}

private fun textProgressLabel(progression: Float): String =
    "${(progression.coerceIn(0f, 1f) * 100).toInt()}%"

private data class TextPosition(
    val itemIndex: Int,
    val scrollOffset: Int,
    val offsetFraction: Float = 0f,
    val totalFraction: Float = 0f,
)

private fun textProgression(
    chunks: List<String>,
    chunkStartOffsets: List<Int>,
    totalCharacterCount: Int,
    itemIndex: Int,
    offsetFraction: Float,
    isAtEnd: Boolean,
): Float {
    if (isAtEnd) return 1f
    val index = itemIndex.coerceIn(0, maxOf(0, chunks.lastIndex))
    val charactersBefore = chunkStartOffsets.getOrElse(index) { 0 }
    val charactersInItem = chunks.getOrNull(index)?.length ?: 0
    return (
        charactersBefore + charactersInItem * offsetFraction.coerceIn(0f, 1f)
        ) / totalCharacterCount.coerceAtLeast(1).toFloat()
}

private const val TEXT_CHROME_TAP_REGION_TOP = 0.10f
private const val TEXT_CHROME_TAP_REGION_BOTTOM = 0.24f

private enum class PublicationReader { EPUB, PDF, IMAGE }
