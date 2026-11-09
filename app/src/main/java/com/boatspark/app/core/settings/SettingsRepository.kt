package com.boatspark.app.core.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class TemperatureUnit { Fahrenheit, Celsius }

enum class StatusSection { Location, Device }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    // Settings are only preferences, so a corrupt file is replaced with defaults.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val temperatureUnit: Flow<TemperatureUnit> = preferences.map { preferences ->
        TemperatureUnit.entries.firstOrNull { it.name == preferences[KEY_TEMPERATURE_UNIT] }
            ?: DEFAULT_TEMPERATURE_UNIT
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        update { it[KEY_TEMPERATURE_UNIT] = unit.name }
    }

    /** Collapsible status sections; all start collapsed. */
    val expandedSections: Flow<Set<StatusSection>> = preferences.map { preferences ->
        StatusSection.entries.filterTo(mutableSetOf()) { preferences[expandedKey(it)] == true }
    }

    suspend fun setSectionExpanded(section: StatusSection, expanded: Boolean) {
        update { it[expandedKey(section)] = expanded }
    }

    suspend fun reset() {
        update { it.clear() }
    }

    /** A failed write only loses a preference change, so it is logged rather than crashing. */
    private suspend fun update(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (error: IOException) {
            Log.w(TAG, "Unable to save settings", error)
        }
    }

    companion object {
        val DEFAULT_TEMPERATURE_UNIT = TemperatureUnit.Fahrenheit
        private const val TAG = "SettingsRepository"
        private val KEY_TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        private fun expandedKey(section: StatusSection) =
            booleanPreferencesKey("section_expanded_${section.name.lowercase()}")
    }
}
