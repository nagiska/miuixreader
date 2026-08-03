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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.MIN_FONT_SCALE
import io.github.nagiska.miuixreader.data.MAX_FONT_SCALE
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderFontFamily
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.contrastTextColor
import io.github.nagiska.miuixreader.ui.stringResourceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

enum class ReaderPanel { NONE, TYPOGRAPHY, BACKGROUND, PROGRESS }

data class ReaderPositionLabel(
    val value: String = "",
    val fraction: Float = 0f,
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
    autoHideEnabled: Boolean = true,
    onVisibilityChanged: (Boolean) -> Unit = {},
    onSeekProgress: (Float) -> Unit = {},
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
                val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                if (up == null) return@awaitEachGesture
                if (
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
            onDismiss = chrome::hide,
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
            onDismiss = chrome::hide,
            backProgress = sheetBackProgress,
            onFollowTheme = onBackgroundFollowTheme,
            onColorChange = onBackgroundColorChange,
            onUseImage = onBackgroundImage,
            onImport = onImportBackground,
            onClearImage = onClearBackground,
        )
        ReaderProgressSheet(
            show = chrome.panel == ReaderPanel.PROGRESS,
            fraction = progress.fraction,
            liquidGlassEnabled = preferences.liquidGlassEnabled,
            backdrop = backdrop,
            onDismiss = chrome::hide,
            onSeek = onSeekProgress,
            backProgress = sheetBackProgress,
        )
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
    backProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val glassColor = readerGlassColor()
    val typographyDescription = stringResourceCompat(R.string.typography)
    AnimatedVisibility(
        visible = visible,
        enter = readerEnter { -it } + fadeIn(animationSpec = folmeSpring(0.9f, 0.38f)),
        exit = readerExit { -it } + fadeOut(animationSpec = folmeSpring(0.9f, 0.38f)),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -backProgress * size.height }
                .then(readerGlassModifier(preferences.liquidGlassEnabled, backdrop, glassColor, topShape()))
                .then(
                    if (!preferences.liquidGlassEnabled) {
                        Modifier.background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                    } else {
                        Modifier
                    },
                ),
        ) {
            SmallTopAppBar(
                title = title,
                modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
                color = Color.Transparent,
                navigationIcon = {
                    GlassCircleButton(
                        enabled = preferences.liquidGlassEnabled,
                        backdrop = backdrop,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = stringResourceCompat(R.string.back))
                        }
                    }
                },
                actions = {
                    if (supportsTypography) {
                        GlassCircleButton(
                            enabled = preferences.liquidGlassEnabled,
                            backdrop = backdrop,
                        ) {
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
                        }
                    }
                    if (supportsTypography) {
                        GlassCircleButton(
                            enabled = preferences.liquidGlassEnabled,
                            backdrop = backdrop,
                        ) {
                            IconButton(onClick = onBackground) {
                                Icon(
                                    MiuixIcons.Background,
                                    contentDescription = stringResourceCompat(R.string.reader_background),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun GlassCircleButton(
    enabled: Boolean,
    backdrop: Backdrop,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        val glassColor = readerGlassColor()
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(14.dp.toPx())
                    },
                    onDrawSurface = { drawRect(glassColor) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun ReaderBottomBar(
    visible: Boolean,
    label: String,
    backdrop: Backdrop,
    liquidGlassEnabled: Boolean,
    onClick: (() -> Unit)? = null,
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
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center,
                )
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
    onDismiss: () -> Unit,
    backProgress: Float = 0f,
    content: @Composable () -> Unit,
) {
    var offsetY by remember { mutableStateOf(0f) }
    var sheetHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }
    val glassColor = readerGlassColor()
    val panelGlassColor = glassColor.copy(alpha = (glassColor.alpha + 0.08f).coerceAtMost(0.45f))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(show) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (up == null) return@awaitEachGesture
                        val aboveSheet = down.position.y < size.height - sheetHeight
                        if (aboveSheet) {
                            onDismiss()
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
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
                        .navigationBarsPadding()
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

@Composable
private fun ReaderTypographySheet(
    show: Boolean,
    preferences: ReaderPreferences,
    backdrop: Backdrop,
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
    onDismiss: () -> Unit,
    backProgress: Float = 0f,
    onFollowTheme: () -> Unit,
    onColorChange: (Int) -> Unit,
    onUseImage: () -> Unit,
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
                    color = MiuixTheme.colorScheme.background,
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
                    pressFeedbackType = PressFeedbackType.Sink,
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
        }
    }
}

@Composable
private fun ReaderProgressSheet(
    show: Boolean,
    fraction: Float,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSeek: (Float) -> Unit,
    backProgress: Float = 0f,
) {
    var localValue by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    var finishJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(show, fraction, dragging) {
        if (!show) {
            dragging = false
        } else if (!dragging) {
            localValue = fraction
        }
    }
    ReaderGlassSheet(
        show = show,
        title = stringResourceCompat(R.string.reader_progress),
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        onDismiss = onDismiss,
        backProgress = backProgress,
    ) {
        SliderPreference(
            value = localValue,
            onValueChange = { value ->
                finishJob?.cancel()
                dragging = true
                localValue = value
                onSeek(value)
            },
            onValueChangeFinished = {
                onSeek(localValue)
                finishJob?.cancel()
                finishJob = scope.launch {
                    delay(150)
                    dragging = false
                }
            },
            valueText = "${(localValue * 100).toInt()}%",
            valueRange = 0f..1f,
            showKeyPoints = true,
            keyPoints = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            hapticEffect = top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect.Step,
        )
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

private fun topShape() = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

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
private fun readerGlassColor(): Color =
    Color.White.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.08f else 0.18f)

private const val CHROME_EXIT_MILLIS = 450L
