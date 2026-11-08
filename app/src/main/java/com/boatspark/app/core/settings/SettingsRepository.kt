package com.boatspark.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TemperatureUnit { Fahrenheit, Celsius }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val temperatureUnit: Flow<TemperatureUnit> = dataStore.data.map { preferences ->
        TemperatureUnit.entries.firstOrNull { it.name == preferences[KEY_TEMPERATURE_UNIT] }
            ?: DEFAULT_TEMPERATURE_UNIT
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[KEY_TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    companion object {
        val DEFAULT_TEMPERATURE_UNIT = TemperatureUnit.Fahrenheit
        private val KEY_TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
    }
}
