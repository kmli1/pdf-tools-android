package com.pdfatolyesi.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.settings by preferencesDataStore("settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("theme")
    val theme: Flow<ThemeMode> = context.settings.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { values -> ThemeMode.entries.firstOrNull { it.name == values[key] } ?: ThemeMode.SYSTEM }
    suspend fun setTheme(mode: ThemeMode) { context.settings.edit { it[key] = mode.name } }
}
