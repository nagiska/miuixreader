package io.github.nagiska.miuixreader.data

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class ReaderFontFamily { ORIGINAL, SANS_SERIF, SERIF, MONOSPACE }

enum class ReaderBackgroundMode { FOLLOW_THEME, COLOR, IMAGE }

enum class BackgroundTarget { BOOKSHELF, READER }

data class ReaderPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val liquidGlassEnabled: Boolean = false,
    val bookshelfBackgroundPath: String? = null,
    val bookshelfBackgroundScrim: Float = DEFAULT_IMAGE_SCRIM,
    val readerBackgroundMode: ReaderBackgroundMode = ReaderBackgroundMode.FOLLOW_THEME,
    val readerBackgroundColor: Int = DEFAULT_READER_BACKGROUND,
    val readerBackgroundPath: String? = null,
    val readerBackgroundScrim: Float = DEFAULT_IMAGE_SCRIM,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.ORIGINAL,
    val fontScale: Float = 1f,
    val fontWeight: Int = 400,
)

const val DEFAULT_IMAGE_SCRIM = 0.45f
const val MIN_IMAGE_SCRIM = 0.28f
const val MAX_IMAGE_SCRIM = 0.55f
const val MIN_FONT_SCALE = 0.75f
const val MAX_FONT_SCALE = 2f
const val DEFAULT_READER_BACKGROUND: Int = -461330

fun contrastTextColor(background: Int): Int {
    val luminance = relativeLuminance(background)
    val whiteContrast = 1.05 / (luminance + 0.05)
    val blackContrast = (luminance + 0.05) / 0.05
    return if (whiteContrast >= blackContrast) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
}

internal fun recommendedScrimAlpha(maxLuminance: Double): Float {
    if (maxLuminance <= 0.0) return DEFAULT_IMAGE_SCRIM
    return (1.0 - TARGET_IMAGE_LUMINANCE / maxLuminance)
        .toFloat()
        .coerceIn(MIN_IMAGE_SCRIM, MAX_IMAGE_SCRIM)
}

private const val TARGET_IMAGE_LUMINANCE = 0.38

internal fun relativeLuminance(color: Int): Double {
    fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.04045) normalized / 12.92
        else Math.pow((normalized + 0.055) / 1.055, 2.4)
    }

    val red = channel(color ushr 16 and 0xFF)
    val green = channel(color ushr 8 and 0xFF)
    val blue = channel(color and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}
