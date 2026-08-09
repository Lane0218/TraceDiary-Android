package com.tracediary.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tracediary.app.MainActivity
import com.tracediary.app.R
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.settings.parseBoundaryTime
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.isRecorded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 接收精确闹钟、设备重启和系统时间变化广播。 */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = appContext as? TraceDiaryApplication ?: return@launch
                if (intent.action == DailyReminderScheduler.ACTION_FIRE) {
                    handleReminder(application, intent)
                } else {
                    rescheduleFromPreferences(application, recoverMissed = true)
                }
            } finally {
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
                rescheduleFromPreferences(application, recoverMissed = true)
                return
            }
        val prefs = application.userPreferencesRepository.preferences.first()
        if (!prefs.dailyReminderEnabled || !application.appPasswordManager.isRememberPasswordEnabled()) {
            application.dailyReminderScheduler.cancel()
            return
        }
        val currentBusinessDate = application.resolveCurrentAppDate()
        if (!application.isDataLayerReady()) {
            // 后台无法打开加密数据库时不应把“未知”当成“没有日记”。
            rescheduleFromPreferences(application)
            return
        }
        val entry = try {
            application.diaryRepository.getEntry(targetDate)
        } catch (_: Throwable) {
            rescheduleFromPreferences(application)
            return
        }
        val manager = NotificationManagerCompat.from(application)
        DailyReminderWorker.ensureChannel(application)
        if (!manager.areNotificationsEnabled() || !shouldDeliverReminder(
                currentBusinessDate = currentBusinessDate,
                targetDate = targetDate,
                entry = entry,
                lastReminderDate = prefs.lastDailyReminderDate,
            )
        ) {
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
        } catch (_: SecurityException) {
            // 权限刚被系统撤销时不记录成功状态，等待用户重新授权后再尝试。
        }
        rescheduleFromPreferences(application)
    }

    companion object {
        internal fun shouldDeliverReminder(
            currentBusinessDate: LocalDate,
            targetDate: LocalDate,
            entry: DiaryEntry?,
            lastReminderDate: String?,
        ): Boolean =
            !currentBusinessDate.isAfter(targetDate) &&
                lastReminderDate != targetDate.toString() &&
                entry?.isRecorded != true
    }

    private suspend fun rescheduleFromPreferences(
        application: TraceDiaryApplication,
        recoverMissed: Boolean = false,
    ) {
        val prefs = application.userPreferencesRepository.preferences.first()
        if (!prefs.dailyReminderEnabled ||
            !application.appPasswordManager.isRememberPasswordEnabled() ||
            !application.dailyReminderScheduler.canScheduleExactAlarms()
        ) {
            application.dailyReminderScheduler.cancel()
            return
        }
        if (recoverMissed) {
            application.dailyReminderScheduler.scheduleForRecovery(
                timeOfDay = DailyReminderScheduler.parseTime(prefs.dailyReminderTime),
                boundaryTime = parseBoundaryTime(prefs.dayBoundaryTime),
            )
        } else {
            application.dailyReminderScheduler.schedule(
                timeOfDay = DailyReminderScheduler.parseTime(prefs.dailyReminderTime),
                boundaryTime = parseBoundaryTime(prefs.dayBoundaryTime),
            )
        }
    }
}
