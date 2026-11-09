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
import java.time.DayOfWeek

/**
 * 设置页用到的全部偏好项。
 *
 * 注意：敏感字段（如数据密码、AccessKey Secret）不放在这里，
 * 留待后续接入 EncryptedSharedPreferences / Keystore。
 */
data class UserPreferences(
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "21:00",
    val dayBoundaryTime: String = "00:00",
    val lastDailyReminderDate: String? = null,
    val savePasswordLocally: Boolean = true,
    val syncEnabled: Boolean = false,
    val syncProvider: String = "aliyun",
    val syncDisplayName: String = "",
    val syncBucket: String = "",
    val syncRegion: String = "",
    val syncDirectory: String = "TraceDiary/",
    val sameDayEntrySortOrder: SameDayEntrySortOrder = SameDayEntrySortOrder.EarliestFirst,
    val calendarWeekStart: CalendarWeekStart = CalendarWeekStart.Monday,
    val themeColor: ThemeColor = ThemeColor.Forest,
)

enum class ThemeColor(val storageValue: String, val label: String) {
    Forest("forest", "墨绿"),
    MistBlue("mist_blue", "海蓝"),
    DuskPurple("dusk_purple", "鸢尾紫"),
    RoseBerry("rose_berry", "玫瑰莓"),
    Terracotta("terracotta", "琥珀棕"),
    Graphite("graphite", "石墨");

    companion object {
        fun fromStorageValue(value: String?): ThemeColor =
            entries.firstOrNull { it.storageValue == value } ?: Forest
    }
}

enum class SameDayEntrySortOrder(val storageValue: String) {
    EarliestFirst("earliest_first"),
    LatestFirst("latest_first");

    companion object {
        fun fromStorageValue(value: String?): SameDayEntrySortOrder = when (value) {
            EarliestFirst.storageValue -> EarliestFirst
            LatestFirst.storageValue -> LatestFirst
            else -> EarliestFirst
        }
    }
}

enum class CalendarWeekStart(
    val storageValue: String,
    val dayOfWeek: DayOfWeek,
) {
    Monday("monday", DayOfWeek.MONDAY),
    Sunday("sunday", DayOfWeek.SUNDAY);

    companion object {
        fun fromStorageValue(value: String?): CalendarWeekStart = when (value) {
            Monday.storageValue -> Monday
            Sunday.storageValue -> Sunday
            else -> Monday
        }
    }
}

/**
 * 抽象出仓库接口，方便 ViewModel 注入与测试时使用 fake 实现。
 */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setReminderEnabled(value: Boolean)
    suspend fun setReminderTime(time: String)
    suspend fun setLastDailyReminderDate(value: String?) {}
    suspend fun setDayBoundaryTime(time: String)
    suspend fun setSavePasswordLocally(value: Boolean)
    suspend fun setSyncEnabled(value: Boolean)
    suspend fun setSyncProvider(value: String)
    suspend fun setSyncDisplayName(value: String)
    suspend fun setSyncBucket(value: String)
    suspend fun setSyncRegion(value: String)
    suspend fun setSyncDirectory(value: String)

    suspend fun setSameDayEntrySortOrder(value: SameDayEntrySortOrder)
    suspend fun setCalendarWeekStart(value: CalendarWeekStart)
    suspend fun setThemeColor(value: ThemeColor)

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
            dayBoundaryTime = prefs[KEY_DAY_BOUNDARY_TIME] ?: "00:00",
            lastDailyReminderDate = prefs[KEY_LAST_DAILY_REMINDER_DATE],
            savePasswordLocally = prefs[KEY_SAVE_PASSWORD_LOCALLY] ?: true,
            syncEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            syncProvider = prefs[KEY_SYNC_PROVIDER] ?: "aliyun",
            syncDisplayName = prefs[KEY_SYNC_DISPLAY_NAME].orEmpty(),
            syncBucket = prefs[KEY_SYNC_BUCKET].orEmpty(),
            syncRegion = prefs[KEY_SYNC_REGION].orEmpty(),
            syncDirectory = prefs[KEY_SYNC_DIRECTORY] ?: "TraceDiary/",
            sameDayEntrySortOrder = SameDayEntrySortOrder.fromStorageValue(prefs[KEY_SAME_DAY_ENTRY_SORT_ORDER]),
            calendarWeekStart = CalendarWeekStart.fromStorageValue(prefs[KEY_CALENDAR_WEEK_START]),
            themeColor = ThemeColor.fromStorageValue(prefs[KEY_THEME_COLOR]),
        )
    }

    override suspend fun setReminderEnabled(value: Boolean) {
        dataStore.edit { it[KEY_DAILY_REMINDER_ENABLED] = value }
    }

    override suspend fun setReminderTime(time: String) {
        dataStore.edit { it[KEY_DAILY_REMINDER_TIME] = time }
    }

    override suspend fun setLastDailyReminderDate(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_LAST_DAILY_REMINDER_DATE)
            else prefs[KEY_LAST_DAILY_REMINDER_DATE] = value
        }
    }

    override suspend fun setDayBoundaryTime(time: String) {
        dataStore.edit { it[KEY_DAY_BOUNDARY_TIME] = time }
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

    override suspend fun setSyncDisplayName(value: String) {
        dataStore.edit { it[KEY_SYNC_DISPLAY_NAME] = value }
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

    override suspend fun setSameDayEntrySortOrder(value: SameDayEntrySortOrder) {
        dataStore.edit { it[KEY_SAME_DAY_ENTRY_SORT_ORDER] = value.storageValue }
    }

    override suspend fun setCalendarWeekStart(value: CalendarWeekStart) {
        dataStore.edit { it[KEY_CALENDAR_WEEK_START] = value.storageValue }
    }

    override suspend fun setThemeColor(value: ThemeColor) {
        dataStore.edit { it[KEY_THEME_COLOR] = value.storageValue }
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
            prefs[KEY_SYNC_DISPLAY_NAME] = displayName
            prefs[KEY_SYNC_BUCKET] = bucket
            prefs[KEY_SYNC_REGION] = region
            prefs[KEY_SYNC_DIRECTORY] = directory
        }
    }

    companion object {
        val KEY_DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val KEY_DAILY_REMINDER_TIME = stringPreferencesKey("daily_reminder_time")
        val KEY_DAY_BOUNDARY_TIME = stringPreferencesKey("day_boundary_time")
        val KEY_LAST_DAILY_REMINDER_DATE = stringPreferencesKey("last_daily_reminder_date")
        val KEY_SAVE_PASSWORD_LOCALLY = booleanPreferencesKey("save_password_locally")
        val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val KEY_SYNC_PROVIDER = stringPreferencesKey("sync_provider")
        val KEY_SYNC_DISPLAY_NAME = stringPreferencesKey("sync_display_name")
        val KEY_SYNC_BUCKET = stringPreferencesKey("sync_bucket")
        val KEY_SYNC_REGION = stringPreferencesKey("sync_region")
        val KEY_SYNC_DIRECTORY = stringPreferencesKey("sync_directory")
        val KEY_SAME_DAY_ENTRY_SORT_ORDER = stringPreferencesKey("same_day_entry_sort_order")
        val KEY_CALENDAR_WEEK_START = stringPreferencesKey("calendar_week_start")
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
    }
}
