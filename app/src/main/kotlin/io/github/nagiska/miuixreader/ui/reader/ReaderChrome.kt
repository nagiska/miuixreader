package io.github.nagiska.miuixreader.ui.reader

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.BookmarkEntity
import io.github.nagiska.miuixreader.data.MIN_FONT_SCALE
import io.github.nagiska.miuixreader.data.MAX_FONT_SCALE
import io.github.nagiska.miuixreader.data.MAX_IMAGE_SCRIM
import io.github.nagiska.miuixreader.data.MIN_IMAGE_SCRIM
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderFontFamily
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.contrastTextColor
import io.github.nagiska.miuixreader.ui.LocalLiquidGlassOpacity
import io.github.nagiska.miuixreader.ui.stringResourceCompat
import io.github.nagiska.miuixreader.tts.NarrationPhase
import io.github.nagiska.miuixreader.tts.NarrationPlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.VolumeOff
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Stable
class ReaderChromeState {
    var visible by mutableStateOf(false)
        private set
    var panel by mutableStateOf(ReaderPanel.NONE)
        private set
    private var interactionToken by mutableIntStateOf(0)

    fun show() {
        visible = true
        interactionToken++
    }

    fun hide() {
        panel = ReaderPanel.NONE
        visible = false
        interactionToken++
    }

    fun toggle() {
        if (visible) hide() else show()
    }

    fun open(panel: ReaderPanel) {
        visible = true
        this.panel = panel
        interactionToken++
    }

    fun closePanel() {
        panel = ReaderPanel.NONE
        interactionToken++
    }

    @Composable
    fun AutoHide(enabled: Boolean = true) {
        LaunchedEffect(enabled, visible, panel, interactionToken) {
            if (enabled && visible && panel == ReaderPanel.NONE) {
                delay(AUTO_HIDE_MILLIS)
                hide()
            }
        }
    }

    companion object {
        private const val AUTO_HIDE_MILLIS = 4_000L
    }
}

enum class ReaderPanel {
    NONE,
    TYPOGRAPHY,
    BACKGROUND,
    NARRATION,
    PROGRESS,
    TABLE_OF_CONTENTS,
    SEARCH,
    BOOKMARKS,
}

data class ReaderPositionLabel(
    val value: String = "",
    val fraction: Float = 0f,
    val page: Int = 1,
    val totalPages: Int = 0,
)

@Composable
fun ReaderChrome(
    title: String,
    preferences: ReaderPreferences,
    chrome: ReaderChromeState,
    progress: ReaderPositionLabel,
    supportsTypography: Boolean,
    backdrop: Backdrop,
    readerImagePath: String?,
    readerImageScrim: Float,
    onBack: () -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onBackgroundFollowTheme: () -> Unit,
    onBackgroundColorChange: (Int) -> Unit,
    onBackgroundImage: () -> Unit,
    onImportBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundScrimChange: (Float) -> Unit = {},
    autoHideEnabled: Boolean = true,
    onVisibilityChanged: (Boolean) -> Unit = {},
    onSeekPage: (Int) -> Unit = {},
    onSeekFraction: (Float) -> Unit = {},
    tableOfContents: List<Link> = emptyList(),
    onTocClick: (Link) -> Unit = {},
    searchResults: List<ReaderSearchResult> = emptyList(),
    searching: Boolean = false,
    onSearchQuery: (String) -> Unit = {},
    onSearchResultClick: (ReaderSearchResult) -> Unit = {},
    bookmarks: List<BookmarkEntity> = emptyList(),
    bookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onBookmarkClick: (BookmarkEntity) -> Unit = {},
    onBookmarkDelete: (BookmarkEntity) -> Unit = {},
    searchAvailable: Boolean = true,
    narrationAvailable: Boolean = false,
    narrationState: NarrationPlaybackState = NarrationPlaybackState(),
    onNarrationToggle: () -> Unit = {},
    onNarrationStop: () -> Unit = {},
) {
    chrome.AutoHide(enabled = autoHideEnabled)
    LaunchedEffect(chrome.visible) {
        if (chrome.visible) {
            onVisibilityChanged(true)
        } else {
            delay(CHROME_EXIT_MILLIS)
            if (!chrome.visible) onVisibilityChanged(false)
        }
    }
    var sheetBackProgress by remember { mutableFloatStateOf(0f) }
    var chromeBackProgress by remember { mutableFloatStateOf(0f) }
    // The chrome floats over the reader background; pick a light or dark color
    // scheme for its text/controls based on that background blended with the
    // white glass layer, so a white reader background stays readable in a dark
    // system theme (and vice versa).
    val systemBackground = MiuixTheme.colorScheme.background
    val readerBackground = when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.FOLLOW_THEME -> systemBackground
        ReaderBackgroundMode.COLOR -> Color(preferences.readerBackgroundColor)
        ReaderBackgroundMode.IMAGE -> Color.Black
    }
    val chromeColors = remember(
        preferences.readerBackgroundMode,
        preferences.readerBackgroundColor,
        preferences.liquidGlassOpacity,
        systemBackground,
    ) {
        val glassAlpha = (
            if (systemBackground.luminance() < 0.5f) 0.08f else 0.18f
            ) * preferences.liquidGlassOpacity + 0.08f * preferences.liquidGlassOpacity
        val blendedGlass = Color(
            red = (glassAlpha + readerBackground.red * (1f - glassAlpha)).coerceIn(0f, 1f),
            green = (glassAlpha + readerBackground.green * (1f - glassAlpha)).coerceIn(0f, 1f),
            blue = (glassAlpha + readerBackground.blue * (1f - glassAlpha)).coerceIn(0f, 1f),
        )
        if (blendedGlass.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
    }
    // Predictive back: an open sheet follows the back gesture down, then closes.
    PredictiveBackHandler(enabled = chrome.panel != ReaderPanel.NONE) { progress ->
        try {
            progress.collect { event -> sheetBackProgress = event.progress }
            sheetBackProgress = 1f
            chrome.closePanel()
        } catch (_: CancellationException) {
            sheetBackProgress = 0f
        } finally {
            delay(CHROME_EXIT_MILLIS)
            sheetBackProgress = 0f
        }
    }
    // Predictive back: visible chrome bars follow the gesture (top bar slides
    // up, bottom bar slides down), then hide.
    PredictiveBackHandler(
        enabled = autoHideEnabled && chrome.visible && chrome.panel == ReaderPanel.NONE,
    ) { progress ->
        try {
            progress.collect { event -> chromeBackProgress = event.progress }
            chromeBackProgress = 1f
            chrome.hide()
        } catch (_: CancellationException) {
            chromeBackProgress = 0f
        } finally {
            delay(CHROME_EXIT_MILLIS)
            chromeBackProgress = 0f
        }
    }
    var topBarBottom by remember { mutableStateOf(0f) }
    var bottomBarTop by remember { mutableStateOf(Float.MAX_VALUE) }
    val dismissModifier = if (
        autoHideEnabled && chrome.visible && chrome.panel == ReaderPanel.NONE
    ) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val slop = viewConfiguration.touchSlop
                var moved = 0f
                var isTap = true
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) {
                        isTap = !change.isConsumed
                        break
                    }
                    if (change.isConsumed) {
                        isTap = false
                        break
                    }
                    moved += change.positionChange().getDistance()
                    if (moved > slop) {
                        isTap = false
                        break
                    }
                }
                if (
                    isTap &&
                    down.position.y > topBarBottom &&
                    down.position.y < bottomBarTop
                ) {
                    chrome.hide()
                }
            }
        }
    } else {
        Modifier
    }

    CompositionLocalProvider(
        LocalLiquidGlassOpacity provides preferences.liquidGlassOpacity,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            Box(
                Modifier
                    .fillMaxSize()
                    .then(dismissModifier),
            ) {
            ReaderTopBar(
                visible = chrome.visible,
                title = title,
                preferences = preferences,
                supportsTypography = supportsTypography,
                backdrop = backdrop,
                onBack = onBack,
                onTypography = { chrome.open(ReaderPanel.TYPOGRAPHY) },
                onBackground = { chrome.open(ReaderPanel.BACKGROUND) },
                onToc = { chrome.open(ReaderPanel.TABLE_OF_CONTENTS) },
                tocAvailable = tableOfContents.isNotEmpty(),
                onSearch = { chrome.open(ReaderPanel.SEARCH) },
                searchAvailable = searchAvailable,
                onNarration = { chrome.open(ReaderPanel.NARRATION) },
                narrationAvailable = narrationAvailable,
                colors = chromeColors,
                backProgress = chromeBackProgress,
                modifier = Modifier.onGloballyPositioned {
                    topBarBottom = it.positionInWindow().y + it.size.height.toFloat()
                },
            )
            ReaderBottomBar(
                visible = chrome.visible,
                label = progress.value,
                backdrop = backdrop,
                liquidGlassEnabled = preferences.liquidGlassEnabled,
                onClick = { chrome.open(ReaderPanel.PROGRESS) },
                onOpenBookmarks = { chrome.open(ReaderPanel.BOOKMARKS) },
                onToggleBookmark = onToggleBookmark,
                bookmarked = bookmarked,
                narrationAvailable = narrationAvailable,
                narrationPhase = narrationState.phase,
                onNarrationToggle = onNarrationToggle,
                colors = chromeColors,
                backProgress = chromeBackProgress,
                modifier = Modifier.onGloballyPositioned {
                    bottomBarTop = it.positionInWindow().y
                },
            )
        }
        ReaderTypographySheet(
            show = chrome.panel == ReaderPanel.TYPOGRAPHY,
            preferences = preferences,
            backdrop = backdrop,
            onDismiss = chrome::closePanel,
            colors = chromeColors,
            backProgress = sheetBackProgress,
            onFontFamilyChange = onFontFamilyChange,
            onFontScaleChange = onFontScaleChange,
            onFontWeightChange = onFontWeightChange,
        )
        ReaderBackgroundSheet(
            show = chrome.panel == ReaderPanel.BACKGROUND,
            preferences = preferences,
            backdrop = backdrop,
            imagePath = readerImagePath,
            imageScrim = readerImageScrim,
            colors = chromeColors,
            readerBackground = readerBackground,
            onDismiss = chrome::closePanel,
            backProgress = sheetBackProgress,
            onFollowTheme = onBackgroundFollowTheme,
            onColorChange = onBackgroundColorChange,
            onUseImage = onBackgroundImage,
            onScrimChange = onBackgroundScrimChange,
            onImport = onImportBackground,
            onClearImage = onClearBackground,
        )
        ReaderNarrationSheet(
            show = chrome.panel == ReaderPanel.NARRATION,
            liquidGlassEnabled = preferences.liquidGlassEnabled,
            state = narrationState,
            backdrop = backdrop,
            colors = chromeColors,
            onDismiss = chrome::closePanel,
            backProgress = sheetBackProgress,
            onToggle = onNarrationToggle,
            onStop = onNarrationStop,
        )
        ReaderProgressSheet(
            show = chrome.panel == ReaderPanel.PROGRESS,
            fraction = progress.fraction,
            page = progress.page,
            totalPages = progress.totalPages,
            liquidGlassEnabled = preferences.liquidGlassEnabled,
            backdrop = backdrop,
            onDismiss = chrome::closePanel,
            colors = chromeColors,
            onSeekPage = onSeekPage,
            onSeekFraction = onSeekFraction,
            backProgress = sheetBackProgress,
        )
        ReaderTocSheet(
            show = chrome.panel == ReaderPanel.TABLE_OF_CONTENTS,
            toc = tableOfContents,
            liquidGlassEnabled = preferences.liquidGlassEnabled,
            backdrop = backdrop,
            onDismiss = chrome::closePanel,
            colors = chromeColors,
            onTocClick = onTocClick,
            backProgress = sheetBackProgress,
        )
        ReaderSearchSheet(
            show = chrome.panel == ReaderPanel.SEARCH,
            results = searchResults,
            searching = searching,
            liquidGlassEnabled = preferences.liquidGlassEnabled,
            backdrop = backdrop,
            onDismiss = chrome::closePanel,
            colors = chromeColors,
            onQueryChange = onSearchQuery,
            onResultClick = onSearchResultClick,
            backProgress = sheetBackProgress,
        )
            ReaderBookmarkSheet(
                show = chrome.panel == ReaderPanel.BOOKMARKS,
                bookmarks = bookmarks,
                liquidGlassEnabled = preferences.liquidGlassEnabled,
                backdrop = backdrop,
                onDismiss = chrome::closePanel,
                colors = chromeColors,
                onBookmarkClick = onBookmarkClick,
                onBookmarkDelete = onBookmarkDelete,
                backProgress = sheetBackProgress,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    visible: Boolean,
    title: String,
    preferences: ReaderPreferences,
    supportsTypography: Boolean,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onTypography: () -> Unit,
    onBackground: () -> Unit,
    onToc: () -> Unit,
    tocAvailable: Boolean,
    onSearch: () -> Unit,
    searchAvailable: Boolean,
    onNarration: () -> Unit,
    narrationAvailable: Boolean,
    colors: Colors,
    backProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val glassColor = readerGlassColor()
    val typographyDescription = stringResourceCompat(R.string.typography)
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = readerEnter { -it } + fadeIn(animationSpec = folmeSpring(0.9f, 0.38f)),
        exit = readerExit { -it } + fadeOut(animationSpec = folmeSpring(0.9f, 0.38f)),
    ) {
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                val shape = RoundedCornerShape(CardDefaults.CornerRadius)
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .graphicsLayer { translationY = -backProgress * size.height }
                            .then(
                                readerGlassModifier(
                                    preferences.liquidGlassEnabled,
                                    backdrop,
                                    glassColor,
                                    shape,
                                ),
                            )
                            .then(
                                if (!preferences.liquidGlassEnabled) {
                                    Modifier.clip(shape).background(
                                        MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = stringResourceCompat(R.string.back))
                        }
                        BoxWithConstraints(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            val titleUnits = remember(title) {
                                title.sumOf { character ->
                                    if (character.code > 0xFF) 1.0 else 0.56
                                }.toFloat().coerceAtLeast(1f)
                            }
                            val density = LocalDensity.current.density
                            val fontScale = LocalDensity.current.fontScale
                            val fittedSize = (maxWidth.value / (titleUnits * density * fontScale))
                                .coerceIn(10f, 17f).sp
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MiuixTheme.textStyles.title3.copy(
                                    fontSize = fittedSize,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                        if (tocAvailable) {
                            IconButton(onClick = onToc) {
                                Icon(
                                    MiuixIcons.ListView,
                                    contentDescription = stringResourceCompat(R.string.table_of_contents),
                                )
                            }
                        }
                        if (searchAvailable) {
                            IconButton(onClick = onSearch) {
                                Icon(
                                    MiuixIcons.Search,
                                    contentDescription = stringResourceCompat(R.string.search_in_book),
                                )
                            }
                        }
                        if (narrationAvailable) {
                            IconButton(onClick = onNarration) {
                                Icon(
                                    MiuixIcons.VolumeUp,
                                    contentDescription = stringResourceCompat(R.string.narration),
                                )
                            }
                        }
                        if (supportsTypography) {
                            IconButton(onClick = onTypography) {
                                Text(
                                    text = "Aa",
                                    modifier = Modifier.semantics {
                                        contentDescription = typographyDescription
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = onBackground) {
                                Icon(
                                    MiuixIcons.Background,
                                    contentDescription = stringResourceCompat(R.string.reader_background),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    visible: Boolean,
    label: String,
    backdrop: Backdrop,
    liquidGlassEnabled: Boolean,
    colors: Colors,
    onClick: (() -> Unit)? = null,
    onOpenBookmarks: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    bookmarked: Boolean = false,
    narrationAvailable: Boolean = false,
    narrationPhase: NarrationPhase = NarrationPhase.IDLE,
    onNarrationToggle: () -> Unit = {},
    backProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val glassColor = readerGlassColor()
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = readerEnter { it } + fadeIn(animationSpec = folmeSpring(0.9f, 0.38f)),
        exit = readerExit { it } + fadeOut(animationSpec = folmeSpring(0.9f, 0.38f)),
    ) {
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        onClick = onClick,
                        modifier = modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = backProgress * size.height }
                            .then(
                                readerGlassModifier(
                                    liquidGlassEnabled,
                                    backdrop,
                                    glassColor,
                                    RoundedCornerShape(CardDefaults.CornerRadius),
                                ),
                            ),
                        colors = if (liquidGlassEnabled) {
                            CardDefaults.defaultColors(color = Color.Transparent)
                        } else {
                            CardDefaults.defaultColors()
                        },
                        cornerRadius = CardDefaults.CornerRadius,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onOpenBookmarks) {
                                Icon(
                                    MiuixIcons.Favorites,
                                    contentDescription = stringResourceCompat(R.string.bookmarks),
                                )
                            }
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                                style = MiuixTheme.textStyles.body2,
                                textAlign = TextAlign.Center,
                            )
                            if (narrationAvailable) {
                                val playing = narrationPhase in setOf(
                                    NarrationPhase.PREPARING,
                                    NarrationPhase.BUFFERING,
                                    NarrationPhase.PLAYING,
                                )
                                IconButton(onClick = onNarrationToggle) {
                                    Icon(
                                        imageVector = if (playing) MiuixIcons.Pause else MiuixIcons.Play,
                                        contentDescription = stringResourceCompat(
                                            if (playing) R.string.narration_pause else R.string.narration_play,
                                        ),
                                    )
                                }
                            }
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    MiuixIcons.Pin,
                                    contentDescription = stringResourceCompat(R.string.bookmark_current_page),
                                    modifier = Modifier.semantics {
                                        selected = bookmarked
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderGlassSheet(
    show: Boolean,
    title: String,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    backProgress: Float = 0f,
    content: @Composable () -> Unit,
) {
    var offsetY by remember { mutableStateOf(0f) }
    var sheetHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }
    val glassColor = readerGlassColor()
    val panelGlassColor = glassColor.copy(
        alpha = (glassColor.alpha + 0.08f * LocalLiquidGlassOpacity.current).coerceAtMost(0.45f),
    )
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    LaunchedEffect(show) {
        if (show) offsetY = 0f
    }

    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = readerEnter { it } + fadeIn(animationSpec = folmeSpring(0.9f, 0.38f)),
        exit = readerExit { it } + fadeOut(animationSpec = folmeSpring(0.9f, 0.38f)),
    ) {
        MiuixTheme(colors = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(show) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    val slop = viewConfiguration.touchSlop
                                    var moved = 0f
                                    var isTap = true
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (change.changedToUp()) {
                                            isTap = !change.isConsumed
                                            break
                                        }
                                        if (change.isConsumed) {
                                            isTap = false
                                            break
                                        }
                                        moved += change.positionChange().getDistance()
                                        if (moved > slop) {
                                            isTap = false
                                            break
                                        }
                                    }
                                    if (isTap) onDismiss()
                                }
                            },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.ime)
                                    .only(WindowInsetsSides.Bottom),
                            )
                            .graphicsLayer { translationY = offsetY + backProgress * sheetHeight },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { sheetHeight = it.height }
                                .then(
                                    if (liquidGlassEnabled) {
                                        readerGlassModifier(
                                            preferencesEnabled = true,
                                            backdrop = backdrop,
                                            color = panelGlassColor,
                                            shape = sheetShape,
                                        )
                                    } else {
                                        Modifier.clip(sheetShape).background(MiuixTheme.colorScheme.background)
                                    },
                                )
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .pointerInput(show) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                            },
                                            onDragEnd = {
                                                if (offsetY > dismissThreshold) {
                                                    onDismiss()
                                                } else {
                                                    offsetY = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                offsetY = 0f
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(45.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.28f)),
                                )
                            }
                            Text(
                                text = title,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                textAlign = TextAlign.Center,
                            )
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTypographySheet(
    show: Boolean,
    preferences: ReaderPreferences,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    backProgress: Float = 0f,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
) {
    var fontScale by remember(preferences.fontScale) { mutableStateOf(preferences.fontScale) }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.typography),
        liquidGlassEnabled = preferences.liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResourceCompat(R.string.font_family), style = MiuixTheme.textStyles.body1)
            TabRowWithContour(
                tabs = listOf(
                    stringResourceCompat(R.string.font_original),
                    stringResourceCompat(R.string.font_sans_serif),
                    stringResourceCompat(R.string.font_serif),
                    stringResourceCompat(R.string.font_monospace),
                ),
                selectedTabIndex = preferences.fontFamily.ordinal,
                onTabSelected = { index ->
                    ReaderFontFamily.entries.getOrNull(index)?.let(onFontFamilyChange)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SliderPreference(
                value = fontScale,
                onValueChange = { value ->
                    fontScale = value
                    onFontScaleChange(value)
                },
                title = stringResourceCompat(R.string.font_size),
                valueText = "${(fontScale * 100).toInt()}%",
                valueRange = MIN_FONT_SCALE..MAX_FONT_SCALE,
                steps = 4,
                showKeyPoints = true,
                keyPoints = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f),
                hapticEffect = top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect.Step,
            )
            Text(stringResourceCompat(R.string.font_weight), style = MiuixTheme.textStyles.body1)
            TabRowWithContour(
                tabs = listOf("300", "400", "500", "600", "700"),
                selectedTabIndex = ((preferences.fontWeight - 300) / 100).coerceIn(0, 4),
                onTabSelected = { index -> onFontWeightChange(300 + index * 100) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReaderBackgroundSheet(
    show: Boolean,
    preferences: ReaderPreferences,
    backdrop: Backdrop,
    imagePath: String?,
    imageScrim: Float,
    colors: Colors,
    readerBackground: Color,
    onDismiss: () -> Unit,
    backProgress: Float = 0f,
    onFollowTheme: () -> Unit,
    onColorChange: (Int) -> Unit,
    onUseImage: () -> Unit,
    onScrimChange: (Float) -> Unit,
    onImport: () -> Unit,
    onClearImage: () -> Unit,
) {
    var customColor by remember(preferences.readerBackgroundColor) {
        mutableStateOf(Color(preferences.readerBackgroundColor))
    }
    var customPaletteVisible by remember { mutableStateOf(false) }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.reader_background),
        liquidGlassEnabled = preferences.liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResourceCompat(R.string.background_colors), style = MiuixTheme.textStyles.body1)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackgroundSwatch(
                    color = readerBackground,
                    selected = preferences.readerBackgroundMode == ReaderBackgroundMode.FOLLOW_THEME,
                    label = stringResourceCompat(R.string.theme_system),
                    showLetter = true,
                    onClick = onFollowTheme,
                )
                listOf(
                    Color(0xFFF8F5EE) to stringResourceCompat(R.string.background_paper),
                    Color(0xFFF2E7D2) to stringResourceCompat(R.string.background_warm),
                    Color(0xFFE8EAED) to stringResourceCompat(R.string.background_gray),
                    Color(0xFFDDE8DD) to stringResourceCompat(R.string.background_green),
                    Color(0xFF171717) to stringResourceCompat(R.string.background_black),
                ).forEach { color ->
                    BackgroundSwatch(
                        color = color.first,
                        selected = preferences.readerBackgroundMode == ReaderBackgroundMode.COLOR &&
                            (preferences.readerBackgroundColor and 0x00FFFFFF) == (color.first.toArgb() and 0x00FFFFFF),
                        label = color.second,
                        showLetter = false,
                        onClick = { onColorChange(color.first.toArgb()) },
                    )
                }
            }
            TextButton(
                text = stringResourceCompat(R.string.custom_color),
                onClick = { customPaletteVisible = !customPaletteVisible },
            )
            if (customPaletteVisible) {
                ColorPalette(
                    color = customColor,
                    onColorChanged = { color ->
                        customColor = color.copy(alpha = 1f)
                        onColorChange(customColor.toArgb())
                    },
                    showPreview = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(MiuixIcons.Image, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResourceCompat(R.string.import_background))
                }
                if (imagePath != null) {
                    IconButton(onClick = onClearImage) {
                        Icon(MiuixIcons.Delete, contentDescription = stringResourceCompat(R.string.restore_default))
                    }
                }
            }
            if (imagePath != null) {
                val imageSelected = preferences.readerBackgroundMode == ReaderBackgroundMode.IMAGE
                val imageDescription = stringResourceCompat(R.string.use_imported_background)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = imageDescription
                            selected = imageSelected
                        },
                    colors = CardDefaults.defaultColors(color = Color.Transparent),
                    cornerRadius = CardDefaults.CornerRadius,
                    pressFeedbackType = PressFeedbackType.Tilt,
                    showIndication = true,
                    holdDownState = imageSelected,
                    onClick = onUseImage,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    ) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = imageScrim)),
                        )
                    }
                }
            }
            if (imagePath != null) {
                SliderPreference(
                    value = imageScrim,
                    onValueChange = onScrimChange,
                    title = stringResourceCompat(R.string.background_scrim),
                    valueText = "${(imageScrim * 100).toInt()}%",
                    valueRange = MIN_IMAGE_SCRIM..MAX_IMAGE_SCRIM,
                    steps = 4,
                    showKeyPoints = false,
                )
            }
        }
    }
}

@Composable
private fun ReaderNarrationSheet(
    show: Boolean,
    liquidGlassEnabled: Boolean,
    state: NarrationPlaybackState,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    backProgress: Float = 0f,
) {
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.narration),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResourceCompat(R.string.narration_system_summary), style = MiuixTheme.textStyles.body2)
            Text(stringResourceCompat(R.string.narration_speed_hint), style = MiuixTheme.textStyles.body2)
            state.segmentCount.takeIf { it > 0 }?.let { count ->
                Text(
                    stringResourceCompat(
                        R.string.narration_segment_progress,
                        (state.segmentIndex + 1).coerceAtMost(count),
                        count,
                    ),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            state.lastStartDelayMillis?.let { delay ->
                Text(
                    stringResourceCompat(R.string.narration_start_delay, delay),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            state.lastGapMillis?.let { gap ->
                Text(
                    stringResourceCompat(R.string.narration_gap, gap),
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Text(
                stringResourceCompat(R.string.narration_queue, state.queueDepth, state.maxQueueDepth),
                style = MiuixTheme.textStyles.body2,
            )
            state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                Text(error, style = MiuixTheme.textStyles.body2)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pausable = state.phase in setOf(
                    NarrationPhase.PREPARING,
                    NarrationPhase.BUFFERING,
                    NarrationPhase.PLAYING,
                )
                Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (pausable) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResourceCompat(
                            when {
                                pausable -> R.string.narration_pause
                                state.phase == NarrationPhase.PAUSED -> R.string.narration_resume
                                else -> R.string.narration_start
                            },
                        ),
                    )
                }
                if (state.isActive) {
                    IconButton(onClick = onStop) {
                        Icon(
                            MiuixIcons.VolumeOff,
                            contentDescription = stringResourceCompat(R.string.narration_stop),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReaderProgressSheet(
    show: Boolean,
    fraction: Float,
    page: Int,
    totalPages: Int,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    onSeekPage: (Int) -> Unit,
    onSeekFraction: (Float) -> Unit,
    backProgress: Float = 0f,
) {
    // Publications seek by page number (one slider step per page); TXT has no
    // pages and keeps the percentage mode.
    val usePages = totalPages > 1
    val initialPage = page.coerceIn(1, totalPages.coerceAtLeast(1))
    var localValue by remember { mutableFloatStateOf(fraction) }
    var localPage by remember { mutableIntStateOf(initialPage) }
    var dragging by remember { mutableStateOf(false) }
    var finishJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(show, fraction, dragging) {
        if (!show) {
            dragging = false
        } else if (!dragging) {
            localValue = fraction
            localPage = page.coerceIn(1, totalPages.coerceAtLeast(1))
        }
    }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.reader_progress),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        SliderPreference(
            value = if (usePages) localPage.toFloat() else localValue,
            onValueChange = { value ->
                finishJob?.cancel()
                dragging = true
                if (usePages) {
                    localPage = value.roundToInt().coerceIn(1, totalPages)
                    onSeekPage(localPage)
                } else {
                    localValue = value
                    onSeekFraction(value)
                }
            },
            onValueChangeFinished = {
                if (usePages) onSeekPage(localPage) else onSeekFraction(localValue)
                finishJob?.cancel()
                finishJob = scope.launch {
                    delay(150)
                    dragging = false
                }
            },
            valueText = if (usePages) {
                "$localPage / $totalPages"
            } else {
                "${(localValue * 100).toInt()}%"
            },
            valueRange = if (usePages) 1f..totalPages.toFloat() else 0f..1f,
            steps = if (usePages) (totalPages - 2).coerceAtLeast(0) else 0,
            showKeyPoints = !usePages,
            keyPoints = if (usePages) null else listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            hapticEffect = top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect.Step,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReaderTocSheet(
    show: Boolean,
    toc: List<Link>,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    onTocClick: (Link) -> Unit,
    backProgress: Float = 0f,
) {
    val entries = remember(toc) { flattenToc(toc) }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.table_of_contents),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        ) {
            items(entries) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTocClick(entry.link) }
                        .padding(
                            start = 16.dp + 16.dp * entry.depth,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.link.title ?: entry.link.href.toString(),
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReaderSearchSheet(
    show: Boolean,
    results: List<ReaderSearchResult>,
    searching: Boolean,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onResultClick: (ReaderSearchResult) -> Unit,
    backProgress: Float = 0f,
) {
    val state = remember { TextFieldState() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val dismissSearch = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }
    LaunchedEffect(show) {
        if (show) {
            delay(120)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            query = ""
            state.edit { replace(0, length, "") }
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { query = it }
    }
    // Debounced, cancellable query emission (empty query clears the results).
    LaunchedEffect(query) {
        delay(300)
        onQueryChange(query.trim())
    }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.search_in_book),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = dismissSearch,
        backProgress = backProgress,
    ) {
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = stringResourceCompat(R.string.search_hint_in_book),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        ) {
            if (searching) {
                item {
                    Text(
                        text = stringResourceCompat(R.string.searching),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        text = stringResourceCompat(R.string.no_results),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(results) { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                onResultClick(result)
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = result.title.ifBlank { stringResourceCompat(R.string.search_result) },
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = result.snippet,
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReaderBookmarkSheet(
    show: Boolean,
    bookmarks: List<BookmarkEntity>,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    colors: Colors,
    onDismiss: () -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    backProgress: Float = 0f,
) {
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.bookmarks),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        colors = colors,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        if (bookmarks.isEmpty()) {
            Text(
                text = stringResourceCompat(R.string.no_bookmarks),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                items(bookmarks) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(bookmark) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = bookmark.excerpt.ifBlank {
                                    stringResourceCompat(R.string.bookmark)
                                },
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = dateFormat.format(java.util.Date(bookmark.createdAt)),
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                        IconButton(onClick = { onBookmarkDelete(bookmark) }) {
                            Icon(
                                MiuixIcons.Delete,
                                contentDescription = stringResourceCompat(R.string.delete),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BackgroundSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    showLetter: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        colors = CardDefaults.defaultColors(color = color),
        cornerRadius = CardDefaults.CornerRadius,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        holdDownState = selected,
        onClick = onClick,
    ) {
        if (showLetter) {
            Text(
                text = "A",
                modifier = Modifier.padding(12.dp),
                color = contrastTextColor(color.toArgb()).let(::Color),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun readerEnter(offset: (Int) -> Int): EnterTransition =
    slideInVertically(animationSpec = folmeSpring(0.9f, 0.38f), initialOffsetY = offset)

private fun readerExit(offset: (Int) -> Int): ExitTransition =
    slideOutVertically(animationSpec = folmeSpring(0.9f, 0.38f), targetOffsetY = offset)

@Composable
private fun readerGlassModifier(
    preferencesEnabled: Boolean,
    backdrop: Backdrop,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
) = if (preferencesEnabled) {
    Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(20.dp.toPx())
        },
        onDrawSurface = { drawRect(color) },
    )
} else {
    Modifier
}

@Composable
private fun readerGlassColor(): Color {
    val baseAlpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.08f else 0.18f
    return Color.White.copy(alpha = baseAlpha * LocalLiquidGlassOpacity.current)
}

private const val CHROME_EXIT_MILLIS = 450L

/** A search hit: a publication [locator] (EPUB) or a TXT [itemIndex]/[scrollOffset]. */
data class ReaderSearchResult(
    val title: String,
    val snippet: String,
    val locator: Locator? = null,
    val itemIndex: Int = -1,
    val scrollOffset: Int = 0,
    /** TXT: absolute character offset of the hit, for fine positioning. */
    val hitChar: Int = -1,
)

/** A table-of-contents entry flattened with its nesting [depth] (0 = top level). */
internal data class TocEntry(val link: Link, val depth: Int)

/** Maps a 1-based [page] to a 0..1 total progression across [total] pages. */
internal fun pageToProgression(page: Int, total: Int): Double =
    if (total > 1) {
        (page - 1).coerceIn(0, total - 1).toDouble() / (total - 1)
    } else {
        0.0
    }

/** Flattens a [Link] tree (children recursive) into an ordered, depth-tagged list. */
internal fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> =
    links.flatMap { link ->
        listOf(TocEntry(link, depth)) + flattenToc(link.children, depth + 1)
    }
