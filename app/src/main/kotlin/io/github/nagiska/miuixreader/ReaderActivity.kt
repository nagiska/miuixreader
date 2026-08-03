package io.github.nagiska.miuixreader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
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
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderFontFamily
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.data.contrastTextColor
import io.github.nagiska.miuixreader.data.decodeText
import io.github.nagiska.miuixreader.ui.reader.ReaderBackdrop
import io.github.nagiska.miuixreader.ui.reader.ReaderChrome
import io.github.nagiska.miuixreader.ui.reader.ReaderChromeState
import io.github.nagiska.miuixreader.ui.reader.ReaderPositionLabel
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.publication.services.positions
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
    private var captureIndex = 0
    private var captureBitmaps: Array<Bitmap?> = arrayOf(null, null)
    private var lastBackdropRefreshAt = 0L
    private var cachedBackgroundSignature: String? = null
    private var cachedBackgroundDataUri: String? = null

    private val readerSettings get() = (application as ReaderApplication).settings
    private val touchExplorationEnabled: Boolean
        get() = (getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                readerSettings.importBackground(BackgroundTarget.READER, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        hideStatusBar()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                readerSettings.preferences.collectLatest { preferences ->
                    updateSystemBars(preferences)
                    latestPreferences = preferences
                }
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
                // Re-pagination (e.g. font size change) changes the page count.
                publicationPositionCount = totalPages
                refreshPublicationBackdrop()
                lifecycleScope.launch { applyEpubPageStyle(latestPreferences) }
            }

            override fun onPageLoaded() {
                lifecycleScope.launch {
                    delay(80)
                    applyEpubPageStyle(latestPreferences)
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
            installPublicationInput()
            observeProgression(book)
            observePublicationPreferences(supportsTypography)
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
                        autoHideEnabled = !touchExplorationEnabled,
                        onSeekProgress = { seekPublication(it) },
                        onVisibilityChanged = { visible ->
                            if (!visible && !chromeState.visible) {
                                composeView.visibility = View.GONE
                                publicationBackdrop.clear()
                            }
                        },
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
        if (capturePending) return
        fun reveal() {
            capturePending = false
            view.visibility = View.VISIBLE
            view.bringToFront()
            chromeState.show()
        }
        if (!latestPreferences.liquidGlassEnabled) {
            reveal()
            return
        }
        capturePublicationBackdrop(onComplete = { reveal() })
    }

    /**
     * Refreshes the glass snapshot after the page content changes while the
     * chrome is visible. The chrome overlay is hidden for one frame so the
     * captured window does not include the bars or sheets themselves.
     */
    private fun refreshPublicationBackdrop() {
        if (
            seekingProgression ||
            !chromeState.visible ||
            capturePending ||
            isDestroyed ||
            !latestPreferences.liquidGlassEnabled
        ) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackdropRefreshAt < BACKDROP_REFRESH_MIN_INTERVAL_MILLIS) return
        lastBackdropRefreshAt = now
        val chrome = chromeView ?: return
        chrome.alpha = 0f
        lifecycleScope.launch {
            delay(BACKDROP_REFRESH_CAPTURE_DELAY_MILLIS)
            capturePublicationBackdrop(onComplete = { chrome.alpha = 1f })
        }
    }

    private var seekJob: Job? = null
    private var seekingProgression = false
    private var seekingPublication = false
    private var latestSeekFraction = 0f
    private var lastSeekFractionConsumed = 0f

    /** Jumps the publication to [fraction] (0..1), following the finger. */
    private fun seekPublication(fraction: Float) {
        latestSeekFraction = fraction
        if (seekingPublication) return
        seekingPublication = true
        // While the progress slider is being dragged, skip backdrop re-captures
        // (they hide the chrome for one frame and make the sheet flicker).
        seekingProgression = true
        seekJob = lifecycleScope.launch {
            try {
                // Serial executor: every go() runs to completion (never
                // cancelled, which dropped queued jumps while dragging), then
                // picks up the newest requested fraction and keeps chasing.
                while (true) {
                    val target = latestSeekFraction
                    lastSeekFractionConsumed = target
                    val pub = publication ?: break
                    val nav = navigator ?: break
                    val locator = withContext(Dispatchers.Default) {
                        pub.locateProgression(target.toDouble().coerceIn(0.0, 1.0))
                    }
                    if (locator != null) nav.go(locator, animated = false)
                    if (target == latestSeekFraction) break
                }
            } finally {
                seekingPublication = false
                seekingProgression = false
                if (latestSeekFraction != lastSeekFractionConsumed) {
                    // A new request arrived while the executor was finishing
                    // (e.g. a second drag started right after releasing);
                    // restart so it is not dropped.
                    seekPublication(latestSeekFraction)
                } else {
                    refreshPublicationBackdrop()
                }
            }
        }
    }

    private fun capturePublicationBackdrop(onComplete: (() -> Unit)? = null) {
        if (capturePending || isDestroyed || !latestPreferences.liquidGlassEnabled) {
            onComplete?.invoke()
            return
        }
        val width = window.decorView.width
        val height = window.decorView.height
        if (width <= 0 || height <= 0) {
            onComplete?.invoke()
            return
        }
        capturePending = true
        val captureScale = minOf(1f, MAX_CAPTURE_DIMENSION / maxOf(width, height).toFloat())
        val captureWidth = maxOf(1, (width * captureScale).toInt())
        val captureHeight = maxOf(1, (height * captureScale).toInt())
        val index = captureIndex
        var bitmap = captureBitmaps[index]
        if (bitmap == null || bitmap.isRecycled || bitmap.width != captureWidth || bitmap.height != captureHeight) {
            bitmap = try {
                Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
            } catch (_: Throwable) {
                null
            }
            captureBitmaps[index] = bitmap
        }
        if (bitmap == null) {
            capturePending = false
            publicationBackdrop.clear()
            onComplete?.invoke()
            return
        }
        try {
            val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                capturePending = false
                if (isDestroyed) return@OnPixelCopyFinishedListener
                if (result == PixelCopy.SUCCESS) {
                    captureIndex = (index + 1) % captureBitmaps.size
                    val recycled = publicationBackdrop.setBitmap(bitmap, width, height)
                    if (recycled != null) {
                        // The backdrop recycled the previous buffer; drop any
                        // capture-pool slot that still references it so the
                        // next capture allocates a fresh bitmap instead of
                        // handing a recycled one to PixelCopy.
                        for (i in captureBitmaps.indices) {
                            if (captureBitmaps[i] === recycled) captureBitmaps[i] = null
                        }
                    }
                } else {
                    publicationBackdrop.clear()
                }
                onComplete?.invoke()
            }
            PixelCopy.request(
                window,
                bitmap,
                listener,
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Exception) {
            capturePending = false
            publicationBackdrop.clear()
            onComplete?.invoke()
        }
    }

    private fun parseTextPosition(progression: String?): TextPosition {
        val normalized = progression
            ?.takeIf { it.startsWith(TXT_PROGRESSION_V2_PREFIX) }
            ?.removePrefix(TXT_PROGRESSION_V2_PREFIX)
            ?.split(':', limit = 2)
        if (normalized != null) {
            val fraction = normalized.getOrNull(1)
                ?.toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 0f
            return TextPosition(
                itemIndex = normalized.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                scrollOffset = 0,
                offsetFraction = fraction,
            )
        }
        val legacy = progression
            ?.takeIf { it.startsWith(TXT_PROGRESSION_PREFIX) }
            ?.removePrefix(TXT_PROGRESSION_PREFIX)
            ?.split(':', limit = 2)
        return TextPosition(
            itemIndex = legacy?.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            scrollOffset = legacy?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun showTextContent(book: BookEntity, content: String) {
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
                    onProgress = { position ->
                        lifecycleScope.launch {
                            (application as ReaderApplication).books.saveProgression(
                                book.id,
                                "$TXT_PROGRESSION_V2_PREFIX${position.itemIndex}:" +
                                    position.offsetFraction.coerceIn(0f, 1f),
                            )
                        }
                    },
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
                    refreshPublicationBackdrop()
                    (application as ReaderApplication).books.saveProgression(
                        book.id,
                        locator.toJSON().toString(),
                    )
                }
            }
        }
    }

    private fun observePublicationPreferences(supportsTypography: Boolean) {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                readerSettings.preferences.collectLatest { preferences ->
                    latestPreferences = preferences
                    if (!preferences.liquidGlassEnabled) publicationBackdrop.clear()
                    if (supportsTypography) {
                        epubNavigator?.submitPreferences(preferences.toEpubPreferences())
                        cachedBackgroundDataUri = loadBackgroundDataUri(preferences)
                        applyEpubPageStyle(preferences)
                    }
                }
            }
        }
    }

    private suspend fun loadBackgroundDataUri(preferences: ReaderPreferences): String? {
        val path = preferences.readerBackgroundPath
            ?.takeIf { preferences.readerBackgroundMode == ReaderBackgroundMode.IMAGE }
            ?: return null
        val file = File(path)
        val signature = "$path:${file.length()}:${file.lastModified()}"
        if (signature == cachedBackgroundSignature && cachedBackgroundDataUri != null) {
            return cachedBackgroundDataUri
        }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                cachedBackgroundSignature = signature
                "data:image/webp;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun applyEpubPageStyle(preferences: ReaderPreferences) {
        val epub = epubNavigator ?: return
        val imageDataUri = loadBackgroundDataUri(preferences)
        cachedBackgroundDataUri = imageDataUri
        val script = buildEpubPageStyleScript(
            preferences = preferences,
            imageDataUri = imageDataUri,
            fallbackDark = isDark(preferences.themeMode),
        )
        try {
            epub.evaluateJavascript(script)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A page transition can remove the WebView before the style script runs.
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
            PublicationReader.PDF, PublicationReader.IMAGE -> {
                if (position != null && total > 0) {
                    getString(R.string.reader_page_count, position, total)
                } else {
                    getString(R.string.reader_progress_percent, percent)
                }
            }
            PublicationReader.EPUB -> {
                if (publication?.metadata?.layout == Layout.FIXED && position != null && total > 0) {
                    getString(R.string.reader_page_count, position, total)
                } else if (position != null && total > 0) {
                    getString(R.string.reader_position_count, position, total, percent)
                } else {
                    getString(R.string.reader_progress_percent, percent)
                }
            }
            null -> ""
        }
    }

    private fun updateFontFamily(fontFamily: ReaderFontFamily) {
        lifecycleScope.launch { readerSettings.setFontFamily(fontFamily) }
    }

    private fun updateFontScale(scale: Float) {
        lifecycleScope.launch { readerSettings.setFontScale(scale) }
    }

    private fun updateFontWeight(weight: Int) {
        lifecycleScope.launch { readerSettings.setFontWeight(weight) }
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

    override fun onDestroy() {
        super.onDestroy()
        chromeView = null
        navigator = null
        epubNavigator = null
        captureBitmaps.forEach { it?.recycle() }
        captureBitmaps = arrayOf(null, null)
        publication?.close()
        publication = null
        publicationBackdrop.clear()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "readium_navigator"
        private const val MAX_TEXT_LENGTH = 16 * 1024 * 1024
        private const val MAX_TEXT_BYTES = 32L * 1024L * 1024L
        private const val TXT_PROGRESSION_PREFIX = "txt:"
        private const val TXT_PROGRESSION_V2_PREFIX = "txt2:"
        private const val CHROME_TAP_REGION_TOP = 0.10f
        private const val CHROME_TAP_REGION_BOTTOM = 0.24f
        private const val MAX_CAPTURE_DIMENSION = 1280
        private const val BACKDROP_REFRESH_MIN_INTERVAL_MILLIS = 400L
        private const val BACKDROP_REFRESH_CAPTURE_DELAY_MILLIS = 32L

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
    onProgress: (TextPosition) -> Unit,
    onBack: () -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onBackgroundFollowTheme: () -> Unit,
    onBackgroundColorChange: (Int) -> Unit,
    onBackgroundImage: () -> Unit,
    onImportBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    val chunks = remember(content) { chunkText(content) }
    val chunkStartOffsets = remember(chunks) {
        buildList {
            var offset = 0
            chunks.forEach { chunk ->
                add(offset)
                offset += chunk.length
            }
        }
    }
    val totalCharacterCount = remember(chunks) { chunks.sumOf { it.length }.coerceAtLeast(1) }
    val initialItem = initialPosition.itemIndex.coerceIn(0, maxOf(0, chunks.lastIndex))
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialItem,
        initialFirstVisibleItemScrollOffset = if (initialPosition.offsetFraction > 0f) {
            0
        } else {
            initialPosition.scrollOffset
        },
    )
    val backdrop = rememberLayerBackdrop()
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
                    .firstOrNull { it.index == initialItem }
                    ?.size
                    ?: 0
            }.first { it > 0 }
            listState.scrollToItem(
                initialItem,
                (itemSize * initialPosition.offsetFraction).roundToInt().coerceAtLeast(0),
            )
        }
    }
    LaunchedEffect(listState, chunks, chunkStartOffsets, totalCharacterCount) {
        snapshotFlow {
            val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val offsetFraction = if (firstItemSize > 0) {
                listState.firstVisibleItemScrollOffset.toFloat() / firstItemSize
            } else {
                0f
            }
            TextScrollSnapshot(
                position = TextPosition(
                    itemIndex = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    offsetFraction = offsetFraction.coerceIn(0f, 1f),
                ),
                isAtEnd = !listState.canScrollForward,
            )
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                val position = snapshot.position
                val progression = textProgression(
                    chunks = chunks,
                    chunkStartOffsets = chunkStartOffsets,
                    totalCharacterCount = totalCharacterCount,
                    itemIndex = position.itemIndex,
                    offsetFraction = position.offsetFraction,
                    isAtEnd = snapshot.isAtEnd,
                )
                progressLabel = ReaderPositionLabel(
                    textProgressLabel(progression),
                    fraction = progression,
                )
                delay(750)
                onProgress(position)
            }
    }
    DisposableEffect(listState, chunks.size) {
        onDispose {
            val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val offsetFraction = if (itemSize > 0) {
                listState.firstVisibleItemScrollOffset.toFloat() / itemSize
            } else {
                0f
            }
            onProgress(
                TextPosition(
                    itemIndex = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    offsetFraction = offsetFraction.coerceIn(0f, 1f),
                ),
            )
        }
    }
    val seekScope = rememberCoroutineScope()
    var latestSeekFraction by remember { mutableFloatStateOf(0f) }
    var seekingText by remember { mutableStateOf(false) }
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
            onFontFamilyChange = onFontFamilyChange,
            onFontScaleChange = onFontScaleChange,
            onFontWeightChange = onFontWeightChange,
            onBackgroundFollowTheme = onBackgroundFollowTheme,
            onBackgroundColorChange = onBackgroundColorChange,
            onBackgroundImage = onBackgroundImage,
            onImportBackground = onImportBackground,
            onClearBackground = onClearBackground,
            autoHideEnabled = autoHideEnabled,
            onSeekProgress = seekTo,
        )
    }
}

@Composable
private fun TextPageBackground(preferences: ReaderPreferences) {
    val backgroundColor = when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.FOLLOW_THEME -> MiuixTheme.colorScheme.background
        ReaderBackgroundMode.COLOR -> Color(preferences.readerBackgroundColor)
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
)

private data class TextScrollSnapshot(
    val position: TextPosition,
    val isAtEnd: Boolean,
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
