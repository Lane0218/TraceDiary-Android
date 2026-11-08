package com.tracediary.app.data.notifications

import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tracediary.app.MainActivity
import com.tracediary.app.R
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.settings.parseBoundaryTime
import com.tracediary.app.data.settings.resolveAppDate
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.isRecorded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** 接收精确闹钟、设备重启和系统时间变化广播。 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(DailyReminderScheduler.TAG, "received action=${intent.action} wallTime=${LocalDateTime.now()}")
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = appContext as? TraceDiaryApplication ?: return@launch
                if (intent.action == DailyReminderScheduler.ACTION_FIRE) {
                    handleReminder(application, intent)
                } else {
                    rescheduleFromPreferences(application)
                }
            } catch (error: Throwable) {
                Log.e(DailyReminderScheduler.TAG, "receiver failed action=${intent.action} error=${error.javaClass.simpleName}")
                runCatching { (appContext as? TraceDiaryApplication)?.let { rescheduleFromPreferences(it) } }
                    .onFailure { recoveryError ->
                        Log.e(DailyReminderScheduler.TAG, "receiver recovery failed error=${recoveryError.javaClass.simpleName}")
                    }
            } finally {
                Log.i(DailyReminderScheduler.TAG, "receiver finished action=${intent.action} durationMs=${SystemClock.elapsedRealtime() - startedAt}")
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(
        application: TraceDiaryApplication,
        intent: Intent,
    ) {
        val targetDate = intent.getStringExtra(DailyReminderScheduler.EXTRA_TARGET_BUSINESS_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: run {
                Log.w(DailyReminderScheduler.TAG, "skipped alarm: missing or invalid target date")
                rescheduleFromPreferences(application)
                return
            }
        val prefs = application.userPreferencesRepository.preferences.first()
        if (!prefs.dailyReminderEnabled || !application.appPasswordManager.isRememberPasswordEnabled()) {
            Log.i(DailyReminderScheduler.TAG, "skipped alarm target=$targetDate reason=disabled_or_password_not_remembered")
            application.dailyReminderScheduler.cancel()
            return
        }
        val currentBusinessDate = resolveAppDate(LocalDateTime.now(), parseBoundaryTime(prefs.dayBoundaryTime))
        if (!application.isDataLayerReady()) {
            Log.w(DailyReminderScheduler.TAG, "skipped alarm target=$targetDate current=$currentBusinessDate reason=database_unavailable")
            rescheduleFromPreferences(application)
            return
        }
        val entry = try {
            application.diaryRepository.getEntry(targetDate)
        } catch (error: Throwable) {
            Log.e(DailyReminderScheduler.TAG, "skipped alarm target=$targetDate reason=diary_query_failed error=${error.javaClass.simpleName}")
            rescheduleFromPreferences(application)
            return
        }
        val manager = NotificationManagerCompat.from(application)
        DailyReminderWorker.ensureChannel(application)
        val notificationsEnabled = manager.areNotificationsEnabled()
        Log.i(DailyReminderScheduler.TAG, "checking target=$targetDate current=$currentBusinessDate entry=${when { entry == null -> "missing"; entry.isRecorded -> "recorded"; else -> "blank" }} last=${prefs.lastDailyReminderDate} notificationsEnabled=$notificationsEnabled")
        val skipReason = reminderSkipReason(
            notificationsEnabled = notificationsEnabled,
            currentBusinessDate = currentBusinessDate,
            targetDate = targetDate,
            entry = entry,
            lastReminderDate = prefs.lastDailyReminderDate,
        )
        if (skipReason != null) {
            Log.i(DailyReminderScheduler.TAG, "skipped alarm target=$targetDate reason=$skipReason")
            rescheduleFromPreferences(application)
            return
        }

        val launchIntent = Intent(application, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            application,
            0,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(application, DailyReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天还没记录吗？")
            .setContentText("打开 TraceDiary 写下今天的故事")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify(DailyReminderWorker.NOTIFICATION_ID, notification)
            application.userPreferencesRepository.setLastDailyReminderDate(targetDate.toString())
            Log.i(DailyReminderScheduler.TAG, "posted reminder target=$targetDate")
            val entryAfterPost = application.diaryRepository.getEntry(targetDate)
            if (entryAfterPost?.isRecorded == true) {
                manager.cancel(DailyReminderWorker.NOTIFICATION_ID)
                Log.i(DailyReminderScheduler.TAG, "dismissed reminder posted during diary save target=$targetDate")
            }
        } catch (error: SecurityException) {
            Log.w(DailyReminderScheduler.TAG, "notification denied target=$targetDate error=${error.javaClass.simpleName}")
        }
        rescheduleFromPreferences(application)
    }

    companion object {
        internal fun reminderSkipReason(
            notificationsEnabled: Boolean,
            currentBusinessDate: LocalDate,
            targetDate: LocalDate,
            entry: DiaryEntry?,
            lastReminderDate: String?,
        ): String? = when {
            !notificationsEnabled -> "notification_permission_denied"
            currentBusinessDate != targetDate -> "target_business_date_mismatch"
            entry?.isRecorded == true -> "entry_recorded"
            lastReminderDate == targetDate.toString() -> "already_reminded"
            else -> null
        }

        internal fun shouldDeliverReminder(
            currentBusinessDate: LocalDate,
            targetDate: LocalDate,
            entry: DiaryEntry?,
            lastReminderDate: String?,
        ): Boolean =
            currentBusinessDate == targetDate &&
                lastReminderDate != targetDate.toString() &&
                entry?.isRecorded != true
    }

    private suspend fun rescheduleFromPreferences(
        application: TraceDiaryApplication,
    ) {
        val prefs = application.userPreferencesRepository.preferences.first()
        if (!prefs.dailyReminderEnabled ||
            !application.appPasswordManager.isRememberPasswordEnabled() ||
            !application.dailyReminderScheduler.canScheduleExactAlarms()
        ) {
            Log.i(DailyReminderScheduler.TAG, "recovery cancelled: reminder disabled, password unavailable, or exact alarm denied")
            application.dailyReminderScheduler.cancel()
            return
        }
        application.dailyReminderScheduler.scheduleForRecovery(
            timeOfDay = DailyReminderScheduler.parseTime(prefs.dailyReminderTime),
            boundaryTime = parseBoundaryTime(prefs.dayBoundaryTime),
        )
    }
}
