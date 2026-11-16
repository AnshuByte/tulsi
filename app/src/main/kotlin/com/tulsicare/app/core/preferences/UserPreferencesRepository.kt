package com.tulsicare.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val KEY_CURRENT_GROUP_ID = stringPreferencesKey("current_group_id")
    private val KEY_PRE_REMINDER_LEAD_MINUTES = intPreferencesKey("pre_reminder_lead_minutes")

    val isSimpleMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SIMPLE_MODE] ?: false
    }

    val currentGroupId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENT_GROUP_ID]
    }

    val preReminderLeadMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_PRE_REMINDER_LEAD_MINUTES] ?: 10 // Default 10 min
    }

    suspend fun setSimpleMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SIMPLE_MODE] = enabled
        }
    }

    suspend fun setCurrentGroupId(groupId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_GROUP_ID] = groupId
        }
    }

    suspend fun setPreReminderLeadMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PRE_REMINDER_LEAD_MINUTES] = minutes
        }
    }

    suspend fun clearGroupId() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_CURRENT_GROUP_ID)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
