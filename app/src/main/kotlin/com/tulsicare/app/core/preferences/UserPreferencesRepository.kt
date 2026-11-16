package com.tulsicare.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val KEY_SIMPLE_MODE = booleanPreferencesKey("simple_mode_enabled")

    val isSimpleMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SIMPLE_MODE] ?: false
    }

    suspend fun setSimpleMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SIMPLE_MODE] = enabled
        }
    }
}
