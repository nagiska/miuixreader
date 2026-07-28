package io.github.nagiska.miuixreader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader-settings")

class ReaderSettings(private val context: Context) {
    private val glassKey = booleanPreferencesKey("liquid_glass_enabled")

    val liquidGlassEnabled: Flow<Boolean> = context.readerSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it[glassKey] ?: false }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.readerSettingsDataStore.edit { it[glassKey] = enabled }
    }
}
