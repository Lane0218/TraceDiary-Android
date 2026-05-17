package com.tracediary.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置页用到的全部偏好项。
 *
 * 注意：敏感字段（如数据密码、AccessKey Secret）不放在这里，
 * 留待后续接入 EncryptedSharedPreferences / Keystore。
 */
data class UserPreferences(
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "21:00",
    val savePasswordLocally: Boolean = true,
    val syncEnabled: Boolean = false,
    val syncProvider: String = "aliyun",
    val syncBucket: String = "",
    val syncRegion: String = "",
    val syncDirectory: String = "TraceDiary/",
    val syncDisplayName: String = ""
)

/**
 * 抽象出仓库接口，方便 ViewModel 注入与测试时使用 fake 实现。
 */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setReminderEnabled(value: Boolean)
    suspend fun setReminderTime(time: String)
    suspend fun setSavePasswordLocally(value: Boolean)
    suspend fun setSyncEnabled(value: Boolean)
    suspend fun setSyncProvider(value: String)
    suspend fun setSyncBucket(value: String)
    suspend fun setSyncRegion(value: String)
    suspend fun setSyncDirectory(value: String)
    suspend fun setSyncDisplayName(value: String)

    /**
     * 一次性写入云同步相关的非敏感配置。
     */
    suspend fun saveSyncConfiguration(
        provider: String,
        bucket: String,
        region: String,
        directory: String,
        displayName: String
    )
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * 默认实现：底层走 Jetpack DataStore Preferences。
 */
class DataStoreUserPreferencesRepository(
    context: Context
) : UserPreferencesRepository {

    private val dataStore: DataStore<Preferences> = context.applicationContext.userPreferencesDataStore

    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            dailyReminderEnabled = prefs[KEY_DAILY_REMINDER_ENABLED] ?: false,
            dailyReminderTime = prefs[KEY_DAILY_REMINDER_TIME] ?: "21:00",
            savePasswordLocally = prefs[KEY_SAVE_PASSWORD_LOCALLY] ?: true,
            syncEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            syncProvider = prefs[KEY_SYNC_PROVIDER] ?: "aliyun",
            syncBucket = prefs[KEY_SYNC_BUCKET].orEmpty(),
            syncRegion = prefs[KEY_SYNC_REGION].orEmpty(),
            syncDirectory = prefs[KEY_SYNC_DIRECTORY] ?: "TraceDiary/",
            syncDisplayName = prefs[KEY_SYNC_DISPLAY_NAME].orEmpty()
        )
    }

    override suspend fun setReminderEnabled(value: Boolean) {
        dataStore.edit { it[KEY_DAILY_REMINDER_ENABLED] = value }
    }

    override suspend fun setReminderTime(time: String) {
        dataStore.edit { it[KEY_DAILY_REMINDER_TIME] = time }
    }

    override suspend fun setSavePasswordLocally(value: Boolean) {
        dataStore.edit { it[KEY_SAVE_PASSWORD_LOCALLY] = value }
    }

    override suspend fun setSyncEnabled(value: Boolean) {
        dataStore.edit { it[KEY_SYNC_ENABLED] = value }
    }

    override suspend fun setSyncProvider(value: String) {
        dataStore.edit { it[KEY_SYNC_PROVIDER] = value }
    }

    override suspend fun setSyncBucket(value: String) {
        dataStore.edit { it[KEY_SYNC_BUCKET] = value }
    }

    override suspend fun setSyncRegion(value: String) {
        dataStore.edit { it[KEY_SYNC_REGION] = value }
    }

    override suspend fun setSyncDirectory(value: String) {
        dataStore.edit { it[KEY_SYNC_DIRECTORY] = value }
    }

    override suspend fun setSyncDisplayName(value: String) {
        dataStore.edit { it[KEY_SYNC_DISPLAY_NAME] = value }
    }

    override suspend fun saveSyncConfiguration(
        provider: String,
        bucket: String,
        region: String,
        directory: String,
        displayName: String
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_PROVIDER] = provider
            prefs[KEY_SYNC_BUCKET] = bucket
            prefs[KEY_SYNC_REGION] = region
            prefs[KEY_SYNC_DIRECTORY] = directory
            prefs[KEY_SYNC_DISPLAY_NAME] = displayName
        }
    }

    companion object {
        val KEY_DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val KEY_DAILY_REMINDER_TIME = stringPreferencesKey("daily_reminder_time")
        val KEY_SAVE_PASSWORD_LOCALLY = booleanPreferencesKey("save_password_locally")
        val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val KEY_SYNC_PROVIDER = stringPreferencesKey("sync_provider")
        val KEY_SYNC_BUCKET = stringPreferencesKey("sync_bucket")
        val KEY_SYNC_REGION = stringPreferencesKey("sync_region")
        val KEY_SYNC_DIRECTORY = stringPreferencesKey("sync_directory")
        val KEY_SYNC_DISPLAY_NAME = stringPreferencesKey("sync_display_name")
    }
}
