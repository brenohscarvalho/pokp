package com.pokp.pokedex.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small wrapper over DataStore for app-level preferences. */
class SettingsPrefs(private val context: Context) {

    val lastUpdated: Flow<Long> = context.dataStore.data.map { it[LAST_UPDATED] ?: 0L }

    suspend fun setLastUpdated(epochMillis: Long) {
        context.dataStore.edit { it[LAST_UPDATED] = epochMillis }
    }

    private companion object {
        val LAST_UPDATED = longPreferencesKey("last_updated")
    }
}
