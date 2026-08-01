package io.github.nagiska.miuixreader.ui.reader

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.MIN_FONT_SCALE
import io.github.nagiska.miuixreader.data.MAX_FONT_SCALE
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderFontFamily
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.contrastTextColor
import io.github.nagiska.miuixreader.ui.stringResourceCompat
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
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
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

enum class ReaderPanel { NONE, TYPOGRAPHY, BACKGROUND }

data class ReaderPositionLabel(
    val value: String = "",
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
    BackHandler(enabled = chrome.panel != ReaderPanel.NONE) { chrome.closePanel() }
    BackHandler(
        enabled = autoHideEnabled && chrome.visible && chrome.panel == ReaderPanel.NONE,
    ) { chrome.hide() }
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
                val controlRegion = size.height * CHROME_TAP_REGION
                if (
                    down.position.y > controlRegion &&
                    down.position.y < size.height - controlRegion
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
            )
            ReaderBottomBar(
                visible = chrome.visible,
                label = progress.value,
                backdrop = backdrop,
                liquidGlassEnabled = preferences.liquidGlassEnabled,
            )
        }
        ReaderTypographySheet(
            show = chrome.panel == ReaderPanel.TYPOGRAPHY,
            preferences = preferences,
            backdrop = backdrop,
            onDismiss = chrome::closePanel,
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
            onDismiss = chrome::closePanel,
            onFollowTheme = onBackgroundFollowTheme,
            onColorChange = onBackgroundColorChange,
            onUseImage = onBackgroundImage,
            onImport = onImportBackground,
            onClearImage = onClearBackground,
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
) {
    val glassColor = readerGlassColor()
    val typographyDescription = stringResourceCompat(R.string.typography)
    AnimatedVisibility(
        visible = visible,
        enter = readerEnter { -it } + fadeIn(animationSpec = folmeSpring(0.9f, 0.38f)),
        exit = readerExit { -it } + fadeOut(animationSpec = folmeSpring(0.9f, 0.38f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(readerGlassModifier(preferences.liquidGlassEnabled, backdrop, glassColor, topShape())),
        ) {
            SmallTopAppBar(
                title = title,
                color = if (preferences.liquidGlassEnabled) Color.Transparent else MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
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
                    effects = { blur(14.dp.toPx()) },
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
                modifier = Modifier
                    .fillMaxWidth()
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
private fun ReaderTypographySheet(
    show: Boolean,
    preferences: ReaderPreferences,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
) {
    var fontScale by remember(preferences.fontScale) { mutableStateOf(preferences.fontScale) }
    val glassColor = readerGlassColor()
    OverlayBottomSheet(
        show = show,
        title = stringResourceCompat(R.string.typography),
        onDismissRequest = onDismiss,
        enableWindowDim = false,
        backgroundColor = if (preferences.liquidGlassEnabled) Color.Transparent else MiuixTheme.colorScheme.background,
        outsideMargin = DpSize(12.dp, 0.dp),
        insideMargin = DpSize(16.dp, 0.dp),
        modifier = readerGlassModifier(
            preferences.liquidGlassEnabled,
            backdrop,
            glassColor,
            RoundedCornerShape(28.dp),
        ),
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
    val glassColor = readerGlassColor()
    OverlayBottomSheet(
        show = show,
        title = stringResourceCompat(R.string.reader_background),
        onDismissRequest = onDismiss,
        enableWindowDim = false,
        backgroundColor = if (preferences.liquidGlassEnabled) Color.Transparent else MiuixTheme.colorScheme.background,
        outsideMargin = DpSize(12.dp, 0.dp),
        insideMargin = DpSize(16.dp, 0.dp),
        modifier = readerGlassModifier(
            preferences.liquidGlassEnabled,
            backdrop,
            glassColor,
            RoundedCornerShape(28.dp),
        ),
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
        effects = { blur(18.dp.toPx()) },
        onDrawSurface = { drawRect(color) },
    )
} else {
    Modifier
}

@Composable
private fun readerGlassColor(): Color =
    Color.White.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.07f else 0.20f)

private const val CHROME_EXIT_MILLIS = 450L
private const val CHROME_TAP_REGION = 0.24f
