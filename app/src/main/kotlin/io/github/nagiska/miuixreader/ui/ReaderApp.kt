package io.github.nagiska.miuixreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.AppThemeMode
import io.github.nagiska.miuixreader.data.BackgroundTarget
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import java.util.Locale
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
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
        Scaffold(containerColor = Color.Transparent) { _ ->
            Box(Modifier.fillMaxSize()) {
                HomeBackground(
                    path = state.preferences.bookshelfBackgroundPath,
                    scrim = state.preferences.bookshelfBackgroundScrim,
                )
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
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (BookEntity) -> Unit,
    onSettings: () -> Unit,
) {
    val hasBackgroundImage = state.preferences.bookshelfBackgroundPath != null
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier.background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)),
            ) {
                TopAppBar(
                    title = stringResourceCompat(R.string.bookshelf_title),
                    color = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = when {
                                state.query.isNotBlank() && state.books.isEmpty() ->
                                    stringResourceCompat(R.string.search_empty)
                                state.books.isEmpty() -> stringResourceCompat(R.string.bookshelf_empty_short)
                                else -> pluralResourceCompat(R.plurals.books_count, state.books.size)
                            },
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                    IconButton(onClick = { onSearchVisibleChange(true) }) {
                        Icon(MiuixIcons.Search, contentDescription = stringResourceCompat(R.string.search))
                    }
                    IconButton(onClick = onImport) {
                        Icon(MiuixIcons.Add, contentDescription = stringResourceCompat(R.string.import_books))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(MiuixIcons.Settings, contentDescription = stringResourceCompat(R.string.settings))
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
                        contentColor = if (hasBackgroundImage) Color.White else MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Text(
                        text = stringResourceCompat(R.string.search_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = if (hasBackgroundImage) Color.White else MiuixTheme.colorScheme.onBackground,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        cornerRadius = 8.dp,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
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
                    Text(book.bookFormat.label, style = MiuixTheme.textStyles.body2)
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
    Row(
        modifier = modifier
            .height(44.dp)
            .background(
                MiuixTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(14.dp),
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MiuixIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focusRequester),
            singleLine = true,
            decorationBox = { field ->
                if (query.isBlank()) Text(stringResourceCompat(R.string.search_hint), style = MiuixTheme.textStyles.body2)
                field()
            },
        )
        IconButton(
            onClick = {
                keyboardController?.hide()
                onClose()
            },
        ) {
            Icon(MiuixIcons.Close, contentDescription = stringResourceCompat(R.string.close))
        }
    }
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
    Scaffold(
        containerColor = if (hasBackgroundImage) {
            MiuixTheme.colorScheme.background.copy(alpha = 0.76f)
        } else {
            Color.Transparent
        },
        topBar = {
            TopAppBar(
                title = stringResourceCompat(R.string.settings),
                modifier = Modifier.background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)),
                color = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
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
                cornerRadius = 8.dp,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        cornerRadius = 8.dp,
        pressFeedbackType = PressFeedbackType.Sink,
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
private fun pluralResourceCompat(id: Int, count: Int): String =
    androidx.compose.ui.res.pluralStringResource(id, count, count)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
