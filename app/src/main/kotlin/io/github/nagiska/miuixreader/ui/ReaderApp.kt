package io.github.nagiska.miuixreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import io.github.nagiska.miuixreader.R
import io.github.nagiska.miuixreader.data.BookEntity
import io.github.nagiska.miuixreader.data.bookFormat
import io.github.nagiska.miuixreader.ui.theme.ReaderTheme
import java.util.Locale
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ReaderApp(
    viewModel: LibraryViewModel,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }
    val backdrop = rememberLayerBackdrop()

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

    ReaderTheme {
        Scaffold { _ ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (isSystemInDarkTheme()) Color(0xFF111214) else Color(0xFFF5F6F8))
                        .then(
                            if (state.liquidGlassEnabled) Modifier.layerBackdrop(backdrop) else Modifier,
                        ),
                )
                if (settingsVisible) {
                    SettingsScreen(
                        liquidGlassEnabled = state.liquidGlassEnabled,
                        onLiquidGlassChange = viewModel::setLiquidGlassEnabled,
                        onBack = { settingsVisible = false },
                    )
                } else {
                    BookshelfScreen(
                        state = state,
                        liquidGlassEnabled = state.liquidGlassEnabled,
                        backdrop = backdrop,
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
private fun BookshelfScreen(
    state: LibraryUiState,
    liquidGlassEnabled: Boolean,
    backdrop: LayerBackdrop,
    searchVisible: Boolean,
    onSearchVisibleChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (BookEntity) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = stringResourceCompat(R.string.bookshelf_title))
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
                    EmptyShelf(onImport = onImport, modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = stringResourceCompat(R.string.search_empty),
                        modifier = Modifier.align(Alignment.Center),
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
                        Card(cornerRadius = 8.dp) {
                            Text(
                                text = stringResourceCompat(R.string.importing),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    state.message?.let { message ->
                        Card(cornerRadius = 8.dp) {
                            Text(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResourceCompat(R.string.bookshelf_empty))
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
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val glassModifier = if (liquidGlassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(8.dp) },
            effects = { blur(12.dp.toPx()) },
            onDrawSurface = {
                drawRect(Color.White.copy(alpha = if (darkTheme) 0.08f else 0.42f))
            },
        )
    } else {
        Modifier
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(glassModifier),
        cornerRadius = 8.dp,
        colors = if (liquidGlassEnabled) {
            CardDefaults.defaultColors(color = Color.Transparent)
        } else {
            CardDefaults.defaultColors()
        },
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
                        .background(if (isSystemInDarkTheme()) Color(0xFF303238) else Color(0xFFE3E7ED)),
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
                if (isSystemInDarkTheme()) Color(0xFF25272C) else Color(0xFFE8EBF0),
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
    liquidGlassEnabled: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResourceCompat(R.string.settings),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                cornerRadius = 8.dp,
            ) {
                SwitchPreference(
                    title = stringResourceCompat(R.string.liquid_glass),
                    summary = stringResourceCompat(R.string.liquid_glass_summary),
                    checked = liquidGlassEnabled,
                    onCheckedChange = onLiquidGlassChange,
                )
            }
        }
    }
}

@Composable
private fun stringResourceCompat(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

@Composable
private fun pluralResourceCompat(id: Int, count: Int): String =
    androidx.compose.ui.res.pluralStringResource(id, count, count)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
