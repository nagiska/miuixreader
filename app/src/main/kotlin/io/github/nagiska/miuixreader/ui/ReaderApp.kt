package io.github.nagiska.miuixreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.BookFormat
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun ReaderApp(
    viewModel: LibraryViewModel,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onImportBookshelfBackground: () -> Unit,
    onImportReaderBackground: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3_500)
            viewModel.clearMessage()
        }
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }
    BackHandler(enabled = !settingsVisible && searchVisible) {
        viewModel.setQuery("")
        searchVisible = false
    }

    ReaderTheme(themeMode = state.preferences.themeMode) {
        val homeBackdrop = rememberLayerBackdrop()
        Scaffold(containerColor = Color.Transparent) { _ ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(homeBackdrop)) {
                    HomeBackground(
                        path = state.preferences.bookshelfBackgroundPath,
                        scrim = state.preferences.bookshelfBackgroundScrim,
                    )
                }
                if (settingsVisible) {
                    SettingsScreen(
                        preferences = state.preferences,
                        onLiquidGlassChange = viewModel::setLiquidGlassEnabled,
                        onThemeModeChange = viewModel::setThemeMode,
                        onImportBookshelfBackground = onImportBookshelfBackground,
                        onImportReaderBackground = onImportReaderBackground,
                        onClearBookshelfBackground = {
                            viewModel.clearBackground(BackgroundTarget.BOOKSHELF)
                        },
                        onClearReaderBackground = {
                            viewModel.clearBackground(BackgroundTarget.READER)
                        },
                        onBack = { settingsVisible = false },
                    )
                } else {
                    BookshelfScreen(
                        state = state,
                        liquidGlassEnabled = state.preferences.liquidGlassEnabled,
                        backdrop = homeBackdrop,
                        searchVisible = searchVisible,
                        onSearchVisibleChange = { searchVisible = it },
                        onQueryChange = viewModel::setQuery,
                        onImport = onImport,
                        onOpen = onOpen,
                        onDelete = { pendingDelete = it },
                        onSettings = { settingsVisible = true },
                    )
                }
                pendingDelete?.let { book ->
                    top.yukonga.miuix.kmp.overlay.OverlayDialog(
                        title = stringResourceCompat(R.string.delete_book_title),
                        summary = book.title,
                        show = true,
                        onDismissRequest = { pendingDelete = null },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                text = stringResourceCompat(R.string.cancel),
                                onClick = { pendingDelete = null },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = stringResourceCompat(R.string.delete),
                                onClick = {
                                    viewModel.delete(book)
                                    pendingDelete = null
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBackground(
    path: String?,
    scrim: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        if (path != null) {
            AsyncImage(
                model = path,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrim.coerceIn(0f, 0.9f))),
            )
        }
    }
}

@Composable
private fun BookshelfScreen(
    state: LibraryUiState,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (BookEntity) -> Unit,
    onSettings: () -> Unit,
) {
    val hasBackgroundImage = state.preferences.bookshelfBackgroundPath != null
    val listState = rememberLazyListState()
    val titleCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }
    val headerColor = if (hasBackgroundImage) Color.White else MiuixTheme.colorScheme.onBackground
    Scaffold(
        modifier = Modifier.fillMaxSize().padding(top = HOME_PAGE_TOP_OFFSET),
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .background(
                        if (hasBackgroundImage) {
                            Color.Transparent
                        } else {
                            MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)
                        },
                    )
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searchVisible) {
                        SearchField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClose = {
                                onQueryChange("")
                                onSearchVisibleChange(false)
                            },
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = if (titleCollapsed) Alignment.Center else Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResourceCompat(R.string.bookshelf_title),
                                color = headerColor,
                                fontSize = if (titleCollapsed) 16.sp else 22.sp,
                                fontWeight = if (titleCollapsed) FontWeight.Medium else FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        IconButton(onClick = { onSearchVisibleChange(true) }) {
                            Icon(
                                MiuixIcons.Search,
                                contentDescription = stringResourceCompat(R.string.search),
                                tint = headerColor,
                            )
                        }
                        IconButton(onClick = onImport) {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = stringResourceCompat(R.string.import_books),
                                tint = headerColor,
                            )
                        }
                        IconButton(onClick = onSettings) {
                            Icon(
                                MiuixIcons.Settings,
                                contentDescription = stringResourceCompat(R.string.settings),
                                tint = headerColor,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            if (state.books.isEmpty()) {
                if (state.query.isBlank()) {
                    EmptyShelf(
                        onImport = onImport,
                        contentColor = headerColor,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Text(
                        text = stringResourceCompat(R.string.search_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = headerColor,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            liquidGlassEnabled = liquidGlassEnabled,
                            backdrop = backdrop,
                            onClick = { onOpen(book) },
                            onDelete = { onDelete(book) },
                        )
                    }
                }
            }
            if (state.isImporting || state.message != null) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isImporting) {
                        Card {
                            Text(
                                text = stringResourceCompat(R.string.importing),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    state.message?.let { message ->
                        Card {
                            Text(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(
    onImport: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResourceCompat(R.string.bookshelf_empty), color = contentColor)
        Button(onClick = onImport) {
            Icon(MiuixIcons.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResourceCompat(R.string.import_books))
        }
    }
}

@Composable
private fun BookRow(
    book: BookEntity,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    if (liquidGlassEnabled) {
        RealtimeGlassCard(
            backdrop = backdrop,
            onClick = onClick,
        ) {
            BookRowContent(book = book, onDelete = onDelete)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.CornerRadius,
            pressFeedbackType = PressFeedbackType.Sink,
            showIndication = true,
            onClick = onClick,
        ) {
            BookRowContent(book = book, onDelete = onDelete)
        }
    }
}

@Composable
private fun RealtimeGlassCard(
    backdrop: Backdrop,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressProgress = remember { Animatable(0f) }
    val animationSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 600f,
    )
    val glassColor = homeGlassColor()
    val cardShape = RoundedCornerShape(CardDefaults.CornerRadius)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction: Interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch {
                    pressProgress.animateTo(1f, animationSpec)
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> launch {
                    pressProgress.animateTo(0f, animationSpec)
                }
                else -> Unit
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cardShape },
                effects = {
                    vibrancy()
                    blur(20.dp.toPx())
                },
                layerBlock = {
                    val scale = lerp(1f, 0.94f, pressProgress.value)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = { drawRect(glassColor) },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        cornerRadius = CardDefaults.CornerRadius,
        colors = CardDefaults.defaultColors(color = Color.Transparent),
    ) {
        content()
    }
}

@Composable
private fun BookRowContent(
    book: BookEntity,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = book.coverPath,
                contentDescription = null,
                modifier = Modifier.size(width = 58.dp, height = 82.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (book.bookFormat) {
                            BookFormat.CBZ -> MiuixIcons.Image
                            else -> MiuixIcons.File
                        },
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = book.bookFormat.label,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = book.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
            )
            if (book.author.isNotBlank()) {
                Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body2)
            }
            Text(
                text = "${book.bookFormat.label} · ${formatBytes(book.sizeBytes)}",
                style = MiuixTheme.textStyles.body2,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(MiuixIcons.Delete, contentDescription = stringResourceCompat(R.string.delete))
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        label = stringResourceCompat(R.string.search_hint),
        useLabelAsPlaceholder = true,
        singleLine = true,
        leadingIcon = {
            Icon(
                MiuixIcons.Search,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    onClose()
                },
            ) {
                Icon(MiuixIcons.Close, contentDescription = stringResourceCompat(R.string.close))
            }
        },
    )
}

@Composable
private fun SettingsScreen(
    preferences: ReaderPreferences,
    onLiquidGlassChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onImportBookshelfBackground: () -> Unit,
    onImportReaderBackground: () -> Unit,
    onClearBookshelfBackground: () -> Unit,
    onClearReaderBackground: () -> Unit,
    onBack: () -> Unit,
) {
    val glassEnabled = preferences.liquidGlassEnabled
    val hasBackgroundImage = preferences.bookshelfBackgroundPath != null
    val settingsTopBarColor = if (hasBackgroundImage) {
        Color.Transparent
    } else {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = stringResourceCompat(R.string.settings),
                modifier = Modifier
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    .background(settingsTopBarColor),
                color = settingsTopBarColor,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResourceCompat(R.string.back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(text = stringResourceCompat(R.string.appearance))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ),
                cornerRadius = CardDefaults.CornerRadius,
            ) {
                Text(
                    text = stringResourceCompat(R.string.theme_mode),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                )
                TabRowWithContour(
                    tabs = listOf(
                        stringResourceCompat(R.string.theme_system),
                        stringResourceCompat(R.string.theme_light),
                        stringResourceCompat(R.string.theme_dark),
                    ),
                    selectedTabIndex = preferences.themeMode.ordinal,
                    onTabSelected = { index ->
                        AppThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
                SwitchPreference(
                    title = stringResourceCompat(R.string.liquid_glass),
                    summary = stringResourceCompat(R.string.liquid_glass_summary),
                    checked = glassEnabled,
                    onCheckedChange = onLiquidGlassChange,
                )
            }
            SmallTitle(text = stringResourceCompat(R.string.backgrounds))
            BackgroundPreferenceCard(
                title = stringResourceCompat(R.string.bookshelf_background),
                summary = stringResourceCompat(
                    if (preferences.bookshelfBackgroundPath == null) {
                        R.string.background_default
                    } else {
                        R.string.background_custom
                    },
                ),
                imagePath = preferences.bookshelfBackgroundPath,
                onClick = onImportBookshelfBackground,
                onClear = onClearBookshelfBackground,
            )
            Spacer(Modifier.height(12.dp))
            BackgroundPreferenceCard(
                title = stringResourceCompat(R.string.reader_background),
                summary = stringResourceCompat(
                    when (preferences.readerBackgroundMode) {
                        ReaderBackgroundMode.FOLLOW_THEME -> R.string.background_default
                        ReaderBackgroundMode.COLOR -> R.string.background_solid
                        ReaderBackgroundMode.IMAGE -> R.string.background_custom
                    },
                ),
                imagePath = preferences.readerBackgroundPath,
                onClick = onImportReaderBackground,
                onClear = onClearReaderBackground,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BackgroundPreferenceCard(
    title: String,
    summary: String,
    imagePath: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
        cornerRadius = CardDefaults.CornerRadius,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (imagePath == null) {
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Image, contentDescription = null)
                }
            } else {
                AsyncImage(
                    model = imagePath,
                    contentDescription = null,
                    modifier = Modifier.size(width = 76.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium))
                Text(summary, style = MiuixTheme.textStyles.body2)
            }
            if (imagePath != null) {
                IconButton(onClick = onClear) {
                    Icon(MiuixIcons.Delete, contentDescription = stringResourceCompat(R.string.restore_default))
                }
            }
        }
    }
}

@Composable
internal fun stringResourceCompat(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

@Composable
private fun homeGlassColor(): Color =
    Color.White.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.08f else 0.16f)

private val HOME_PAGE_TOP_OFFSET = 12.dp

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
