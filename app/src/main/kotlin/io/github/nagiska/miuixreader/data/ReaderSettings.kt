package io.github.nagiska.miuixreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader-settings")

class ReaderSettings(private val context: Context) {
    private val backgroundMutex = Mutex()
    private val glassKey = booleanPreferencesKey("liquid_glass_enabled")
    private val glassOpacityKey = floatPreferencesKey("liquid_glass_opacity")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val bookshelfBackgroundPathKey = stringPreferencesKey("bookshelf_background_path")
    private val bookshelfBackgroundScrimKey = floatPreferencesKey("bookshelf_background_scrim")
    private val readerBackgroundModeKey = stringPreferencesKey("reader_background_mode")
    private val readerBackgroundColorKey = intPreferencesKey("reader_background_color")
    private val readerBackgroundPathKey = stringPreferencesKey("reader_background_path")
    private val readerBackgroundScrimKey = floatPreferencesKey("reader_background_scrim")
    private val fontFamilyKey = stringPreferencesKey("reader_font_family")
    private val fontScaleKey = floatPreferencesKey("reader_font_scale")
    private val fontWeightKey = intPreferencesKey("reader_font_weight")

    val preferences: Flow<ReaderPreferences> = context.readerSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            withContext(Dispatchers.IO) {
                val readerBackgroundPath = values[readerBackgroundPathKey].existingFilePath()
                val readerBackgroundMode = values[readerBackgroundModeKey]
                    .enumValueOrDefault(ReaderBackgroundMode.FOLLOW_THEME)
                ReaderPreferences(
                    themeMode = values[themeModeKey].enumValueOrDefault(AppThemeMode.SYSTEM),
                    liquidGlassEnabled = values[glassKey] ?: false,
                    liquidGlassOpacity = clampLiquidGlassOpacity(
                        values[glassOpacityKey] ?: DEFAULT_LIQUID_GLASS_OPACITY,
                    ),
                    bookshelfBackgroundPath = values[bookshelfBackgroundPathKey].existingFilePath(),
                    bookshelfBackgroundScrim = values[bookshelfBackgroundScrimKey]
                        ?.coerceIn(MIN_IMAGE_SCRIM, MAX_IMAGE_SCRIM)
                        ?: DEFAULT_IMAGE_SCRIM,
                    readerBackgroundMode = if (
                        readerBackgroundMode == ReaderBackgroundMode.IMAGE && readerBackgroundPath == null
                    ) {
                        ReaderBackgroundMode.FOLLOW_THEME
                    } else {
                        readerBackgroundMode
                    },
                    readerBackgroundColor = values[readerBackgroundColorKey]
                        ?: DEFAULT_READER_BACKGROUND,
                    readerBackgroundPath = readerBackgroundPath,
                    readerBackgroundScrim = values[readerBackgroundScrimKey]
                        ?.coerceIn(MIN_IMAGE_SCRIM, MAX_IMAGE_SCRIM)
                        ?: DEFAULT_IMAGE_SCRIM,
                    fontFamily = values[fontFamilyKey].enumValueOrDefault(ReaderFontFamily.ORIGINAL),
                    fontScale = values[fontScaleKey]?.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) ?: 1f,
                    fontWeight = values[fontWeightKey]?.coerceIn(300, 700) ?: 400,
                )
            }
        }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.readerSettingsDataStore.edit { it[glassKey] = enabled }
    }

    suspend fun setLiquidGlassOpacity(opacity: Float) {
        context.readerSettingsDataStore.edit {
            it[glassOpacityKey] = clampLiquidGlassOpacity(opacity)
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.readerSettingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setFontFamily(fontFamily: ReaderFontFamily) {
        context.readerSettingsDataStore.edit { it[fontFamilyKey] = fontFamily.name }
    }

    suspend fun setFontScale(scale: Float) {
        context.readerSettingsDataStore.edit {
            it[fontScaleKey] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
    }

    suspend fun setFontWeight(weight: Int) {
        context.readerSettingsDataStore.edit {
            it[fontWeightKey] = weight.coerceIn(300, 700)
        }
    }

    suspend fun setReaderBackgroundMode(mode: ReaderBackgroundMode) {
        context.readerSettingsDataStore.edit { it[readerBackgroundModeKey] = mode.name }
    }

    suspend fun setReaderBackgroundColor(color: Int) {
        context.readerSettingsDataStore.edit {
            it[readerBackgroundColorKey] = color or 0xFF000000.toInt()
            it[readerBackgroundModeKey] = ReaderBackgroundMode.COLOR.name
        }
    }

    suspend fun importBackground(target: BackgroundTarget, uri: Uri): Boolean =
        backgroundMutex.withLock {
            importBackgroundLocked(target, uri)
        }

    private suspend fun importBackgroundLocked(target: BackgroundTarget, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, BACKGROUND_DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) return@withContext false
            cleanupBackgroundDirectory(directory)
            val fileName = "${target.filePrefix}-${UUID.randomUUID()}.webp"
            val destination = File(directory, fileName)
            val temporary = File(directory, "$fileName.tmp")

            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    require(width in 1..MAX_SOURCE_DIMENSION && height in 1..MAX_SOURCE_DIMENSION)
                    val scale = minOf(1f, target.maxDimension / maxOf(width, height).toFloat())
                    decoder.setTargetSize(
                        maxOf(1, (width * scale).toInt()),
                        maxOf(1, (height * scale).toInt()),
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val scrim = try {
                    val calculatedScrim = recommendedScrimAlpha(sampleMaximumLuminance(bitmap))
                    temporary.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, target.compressionQuality, output))
                    }
                    calculatedScrim
                } finally {
                    bitmap.recycle()
                }

                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                var previousPath: String? = null
                context.readerSettingsDataStore.edit { values ->
                    when (target) {
                        BackgroundTarget.BOOKSHELF -> {
                            previousPath = values[bookshelfBackgroundPathKey]
                            values[bookshelfBackgroundPathKey] = destination.absolutePath
                            values[bookshelfBackgroundScrimKey] = scrim
                        }
                        BackgroundTarget.READER -> {
                            previousPath = values[readerBackgroundPathKey]
                            values[readerBackgroundPathKey] = destination.absolutePath
                            values[readerBackgroundScrimKey] = scrim
                            values[readerBackgroundModeKey] = ReaderBackgroundMode.IMAGE.name
                        }
                    }
                }
                previousPath
                    ?.takeUnless { it == destination.absolutePath }
                    ?.let { File(it).delete() }
                true
            } catch (error: Exception) {
                temporary.delete()
                destination.delete()
                if (error is CancellationException) throw error
                false
            }
        }

    suspend fun clearBackground(target: BackgroundTarget) = backgroundMutex.withLock {
        withContext(Dispatchers.IO) {
            var path: String? = null
            context.readerSettingsDataStore.edit { values ->
                path = when (target) {
                    BackgroundTarget.BOOKSHELF -> values.remove(bookshelfBackgroundPathKey)
                    BackgroundTarget.READER -> {
                        values[readerBackgroundModeKey] = ReaderBackgroundMode.FOLLOW_THEME.name
                        values.remove(readerBackgroundPathKey)
                    }
                }
            }
            path?.let { File(it).delete() }
            cleanupBackgroundDirectory(File(context.filesDir, BACKGROUND_DIRECTORY))
        }
    }

    private suspend fun cleanupBackgroundDirectory(directory: File) {
        if (!directory.isDirectory) return
        val values = context.readerSettingsDataStore.data.first()
        val referencedPaths = setOfNotNull(
            values[bookshelfBackgroundPathKey],
            values[readerBackgroundPathKey],
        )
        directory.listFiles()
            ?.filter { it.absolutePath !in referencedPaths }
            ?.forEach(File::delete)
    }

    private fun sampleMaximumLuminance(bitmap: Bitmap): Double {
        val width = minOf(bitmap.width, LUMINANCE_SAMPLE_SIZE)
        val height = minOf(bitmap.height, LUMINANCE_SAMPLE_SIZE)
        val sample = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return try {
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            pixels.maxOfOrNull(::relativeLuminance) ?: 0.0
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }

    private val BackgroundTarget.filePrefix: String
        get() = when (this) {
            BackgroundTarget.BOOKSHELF -> "bookshelf"
            BackgroundTarget.READER -> "reader"
        }

    private val BackgroundTarget.maxDimension: Int
        get() = when (this) {
            BackgroundTarget.BOOKSHELF -> MAX_BOOKSHELF_BACKGROUND_DIMENSION
            BackgroundTarget.READER -> MAX_READER_BACKGROUND_DIMENSION
        }

    private val BackgroundTarget.compressionQuality: Int
        get() = when (this) {
            BackgroundTarget.BOOKSHELF -> 84
            BackgroundTarget.READER -> 78
        }

    companion object {
        private const val BACKGROUND_DIRECTORY = "backgrounds"
        private const val MAX_BOOKSHELF_BACKGROUND_DIMENSION = 1920
        private const val MAX_READER_BACKGROUND_DIMENSION = 1280
        private const val MAX_SOURCE_DIMENSION = 100_000
        private const val LUMINANCE_SAMPLE_SIZE = 64
    }
}

private inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

private fun String?.existingFilePath(): String? = this?.takeIf { File(it).isFile }
