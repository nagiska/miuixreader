package io.github.nagiska.miuixreader

import io.github.nagiska.miuixreader.data.ReaderBackgroundMode
import io.github.nagiska.miuixreader.data.ReaderPreferences
import io.github.nagiska.miuixreader.data.contrastTextColor
import java.util.Locale

internal fun buildEpubPageStyleScript(
    preferences: ReaderPreferences,
    imageDataUri: String?,
    fallbackDark: Boolean,
): String {
    val styleId = "miuix-reader-page-style"
    if (preferences.readerBackgroundMode == ReaderBackgroundMode.FOLLOW_THEME) {
        val fallback = if (fallbackDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val css = "html,body{background-color:${fallback.toCssColor()}!important;}"
        return "(function(){var s=document.getElementById('$styleId');" +
            "if(!s){s=document.createElement('style');s.id='$styleId';document.head.appendChild(s);}" +
            "s.textContent=${css.toJavaScriptString()};})();"
    }

    val background = when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.FOLLOW_THEME -> error("Follow-theme style is handled above")
        ReaderBackgroundMode.COLOR -> preferences.readerBackgroundColor
        ReaderBackgroundMode.IMAGE -> 0xFF000000.toInt()
    }
    val text = when (preferences.readerBackgroundMode) {
        ReaderBackgroundMode.IMAGE -> 0xFFFFFFFF.toInt()
        else -> contrastTextColor(background)
    }
    val imageCss = imageDataUri
        ?.takeIf { preferences.readerBackgroundMode == ReaderBackgroundMode.IMAGE }
        ?.let { uri ->
            val alpha = preferences.readerBackgroundScrim.coerceIn(0f, 0.9f)
            "background-image:url('$uri')!important;background-color:rgba(0,0,0,$alpha)!important;" +
                "background-blend-mode:multiply!important;background-size:cover!important;" +
                "background-position:center!important;"
        }
        .orEmpty()
    val css = buildString {
        append("html{background-color:")
        append(background.toCssColor())
        append("!important;")
        append(imageCss)
        append("}")
        append("body{background-color:transparent!important;background-image:none!important;}")
        append("html,body{")
        append("color:")
        append(text.toCssColor())
        append("!important;}")
    }
    return "(function(){var s=document.getElementById('$styleId');" +
        "if(!s){s=document.createElement('style');s.id='$styleId';document.head.appendChild(s);}" +
        "s.textContent=${css.toJavaScriptString()};})();"
}

private fun Int.toCssColor(): String = String.format(Locale.ROOT, "#%06X", this and 0xFFFFFF)

private fun String.toJavaScriptString(): String = buildString(length + 2) {
    append('"')
    for (character in this@toJavaScriptString) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(character)
        }
    }
    append('"')
}
