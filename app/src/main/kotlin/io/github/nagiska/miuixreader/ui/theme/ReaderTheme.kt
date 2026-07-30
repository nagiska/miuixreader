package io.github.nagiska.miuixreader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.nagiska.miuixreader.data.AppThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun ReaderTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val controller = remember(themeMode) {
        ThemeController(
            when (themeMode) {
                AppThemeMode.SYSTEM -> ColorSchemeMode.System
                AppThemeMode.LIGHT -> ColorSchemeMode.Light
                AppThemeMode.DARK -> ColorSchemeMode.Dark
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}
