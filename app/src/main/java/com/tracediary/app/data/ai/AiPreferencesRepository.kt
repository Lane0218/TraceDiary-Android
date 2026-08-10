package com.tracediary.app.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tracediary.app.domain.model.AiConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AiPreferencesRepository {
    val configuration: Flow<AiConfiguration>
    suspend fun saveConnection(baseUrl: String, model: String)
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setIndexConsentGranted(granted: Boolean)
}

private val Context.aiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_preferences",
)

class DataStoreAiPreferencesRepository(context: Context) : AiPreferencesRepository {
    private val dataStore = context.applicationContext.aiPreferencesDataStore

    override val configuration: Flow<AiConfiguration> = dataStore.data.map { preferences ->
        AiConfiguration(
            enabled = preferences[KEY_ENABLED] ?: false,
            baseUrl = preferences[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            model = preferences[KEY_MODEL] ?: DEFAULT_MODEL,
            indexConsentGranted = preferences[KEY_INDEX_CONSENT_GRANTED] ?: false,
        )
    }

    override suspend fun saveConnection(baseUrl: String, model: String) {
        dataStore.edit { preferences ->
            preferences[KEY_BASE_URL] = baseUrl.trim().trimEnd('/')
            preferences[KEY_MODEL] = model.trim()
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    override suspend fun setIndexConsentGranted(granted: Boolean) {
        dataStore.edit { it[KEY_INDEX_CONSENT_GRANTED] = granted }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_INDEX_CONSENT_GRANTED = booleanPreferencesKey("index_consent_granted")
    }
}
