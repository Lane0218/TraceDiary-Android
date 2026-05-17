package com.tracediary.app.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 调度每日提醒任务：基于 WorkManager 的周期任务，间隔 24 小时。
 *
 * 备注：
 * - 使用 `enqueueUniquePeriodicWork(UPDATE)`，所以每次重新调度都会替换旧的任务。
 * - WorkManager 周期任务最小 15 分钟，24 小时绝对没问题。
 * - ColorOS / 国产 ROM 后台限制可能导致触发延迟或被杀，需在文档/SPEC 中提示用户加入电池白名单。
 */
class DailyReminderScheduler(private val context: Context) {

    fun schedule(timeOfDay: LocalTime, now: LocalDateTime = LocalDateTime.now()) {
        val initialDelayMillis = computeInitialDelayMillis(now, timeOfDay)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_reminder"

        /**
         * 把 "HH:mm" 字符串解析为 [LocalTime]。
         * 抽出 companion 方法便于测试与其他模块复用。
         */
        fun parseTime(text: String): LocalTime = LocalTime.parse(text)

        /**
         * 计算从 [now] 到下一个 [target] 时刻的毫秒数。
         * - target 还在今天未来：返回当天差值。
         * - target 已过：跳到第二天。
         */
        internal fun computeInitialDelayMillis(now: LocalDateTime, target: LocalTime): Long {
            val todayTarget = LocalDateTime.of(now.toLocalDate(), target)
            val nextRun = if (todayTarget.isAfter(now)) {
                todayTarget
            } else {
                LocalDateTime.of(now.toLocalDate().plusDays(1), target)
            }
            return Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
        }

        /** 仅供测试用的便捷构造：固定基准日期，避免依赖系统当前日期。 */
        internal fun computeInitialDelayMillis(
            nowDate: LocalDate,
            nowTime: LocalTime,
            target: LocalTime
        ): Long = computeInitialDelayMillis(LocalDateTime.of(nowDate, nowTime), target)
    }
}
