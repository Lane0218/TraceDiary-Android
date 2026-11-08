package com.tracediary.app.data.notifications

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tracediary.app.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 通知固定 ID，只在同一业务日的已记录正文落库后撤销。 */
internal object ReminderNotification {
    suspend fun resetIfContentCleared(
        preferences: UserPreferencesRepository,
        date: LocalDate,
    ) {
        val lastReminderDate = preferences.preferences.first().lastDailyReminderDate
        if (!shouldResetAfterClear(lastReminderDate, date)) return
        preferences.setLastDailyReminderDate(null)
        Log.i(DailyReminderScheduler.TAG, "reset reminder duplicate marker after diary clear date=$date")
    }

    suspend fun dismissIfRecorded(
        context: Context,
        preferences: UserPreferencesRepository,
        date: LocalDate,
    ) {
        val lastReminderDate = preferences.preferences.first().lastDailyReminderDate
        if (!shouldDismiss(lastReminderDate, date)) return
        NotificationManagerCompat.from(context).cancel(DailyReminderWorker.NOTIFICATION_ID)
        Log.i(DailyReminderScheduler.TAG, "dismissed posted reminder after diary save date=$date")
    }

    internal fun shouldResetAfterClear(lastReminderDate: String?, clearedDate: LocalDate): Boolean =
        lastReminderDate == clearedDate.toString()

    internal fun shouldDismiss(lastReminderDate: String?, savedDate: LocalDate): Boolean =
        lastReminderDate == savedDate.toString()
}
