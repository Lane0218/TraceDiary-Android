package com.tracediary.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tracediary.app.MainActivity
import com.tracediary.app.R
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.isRecorded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 每日提醒后台任务：到指定时间向用户推送一条通知，提醒打开 TraceDiary 写日记。
 *
 * - 通知 channel: [CHANNEL_ID]，名称"每日提醒"，重要性 DEFAULT。
 * - 通知 id 固定为 [NOTIFICATION_ID]，避免堆叠多条提醒。
 * - 用户拒绝通知权限时直接 success() 退出，避免 worker 反复重试。
 * - 当天已有非空日记时直接 success() 退出，不再打扰用户。
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            // 用户系统层关闭了通知或拒绝了 POST_NOTIFICATIONS 权限，
            // 此次直接成功返回，等下个周期再尝试。
            return Result.success()
        }

        val todayEntry = runCatching { loadTodayEntry(context) }.getOrNull()
        if (!shouldShowReminder(todayEntry)) {
            return Result.success()
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天还没记录吗？")
            .setContentText("打开 TraceDiary 写下今天的故事")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 部分 ROM 在权限刚被撤销时仍可能抛出 SecurityException，吞掉避免 worker 失败。
            return Result.success()
        }
        return Result.success()
    }

    private suspend fun loadTodayEntry(context: Context): DiaryEntry? {
        val application = context.applicationContext as? TraceDiaryApplication ?: return null
        if (!application.isDataLayerReady()) return null
        return withContext(Dispatchers.IO) {
            application.diaryRepository.getEntry(LocalDate.now())
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val CHANNEL_NAME = "每日提醒"
        const val NOTIFICATION_ID = 1001

        internal fun shouldShowReminder(entry: DiaryEntry?): Boolean = entry?.isRecorded != true

        /**
         * 在 Application 启动时或 Worker 内创建一次即可；重复调用是幂等的。
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService<NotificationManager>() ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天定时提醒你写一条日记"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
