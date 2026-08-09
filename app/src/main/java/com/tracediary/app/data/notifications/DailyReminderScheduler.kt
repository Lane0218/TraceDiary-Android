package com.tracediary.app.data.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import com.tracediary.app.data.settings.resolveAppDate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 使用 AlarmManager 设置下一次单次精确提醒。
 *
 * 与周期 WorkManager 不同，这里把目标业务日期写进 PendingIntent，Receiver 即使稍晚执行
 * 也能按原定业务日判断；如果执行时已经跨过该业务日，则由 Receiver 放弃过期提醒。
 */
class DailyReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext
    private val alarmManager = requireNotNull(appContext.getSystemService(AlarmManager::class.java))

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(
        timeOfDay: LocalTime,
        boundaryTime: LocalTime = LocalTime.MIDNIGHT,
        now: LocalDateTime = LocalDateTime.now(clock),
    ): ReminderSchedule = scheduleInternal(timeOfDay, boundaryTime, now, recoverMissed = false)

    /**
     * 进程启动、重启或系统时间变化后的恢复调度：如果本业务日的提醒时间已过，先补设一次
     * 立即闹钟；Receiver 会再次查询日记并通过 lastDailyReminderDate 防止重复通知。
     */
    fun scheduleForRecovery(
        timeOfDay: LocalTime,
        boundaryTime: LocalTime = LocalTime.MIDNIGHT,
        now: LocalDateTime = LocalDateTime.now(clock),
    ): ReminderSchedule = scheduleInternal(timeOfDay, boundaryTime, now, recoverMissed = true)

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleInternal(
        timeOfDay: LocalTime,
        boundaryTime: LocalTime,
        now: LocalDateTime,
        recoverMissed: Boolean,
    ): ReminderSchedule {
        check(canScheduleExactAlarms()) { EXACT_ALARM_PERMISSION_REQUIRED }
        val schedule = computeNextSchedule(now, timeOfDay, boundaryTime, clock.zone, recoverMissed)
        val pendingIntent = pendingIntent(schedule.targetBusinessDate)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            schedule.triggerAtMillis,
            pendingIntent,
        )
        return schedule
    }

    fun cancel() {
        val pendingIntent = pendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        cancelLegacyPeriodicWork()
    }

    /** 取消旧版本遗留的周期 WorkManager，防止升级后出现两套提醒。 */
    fun cancelLegacyPeriodicWork() {
        WorkManager.getInstance(appContext).cancelUniqueWork(LEGACY_WORK_NAME)
    }

    private fun pendingIntent(targetBusinessDate: LocalDate? = null): PendingIntent {
        val intent = Intent(appContext, DailyReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
        if (targetBusinessDate != null) {
            intent.putExtra(EXTRA_TARGET_BUSINESS_DATE, targetBusinessDate.toString())
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.tracediary.app.action.DAILY_REMINDER"
        const val EXTRA_TARGET_BUSINESS_DATE = "target_business_date"
        const val REQUEST_CODE = 4101
        const val LEGACY_WORK_NAME = "daily_reminder"
        const val UNIQUE_WORK_NAME = LEGACY_WORK_NAME
        const val EXACT_ALARM_PERMISSION_REQUIRED = "需要授予精确闹钟权限"

        fun parseTime(text: String): LocalTime = LocalTime.parse(text)

        internal fun computeNextSchedule(
            now: LocalDateTime,
            targetTime: LocalTime,
            boundaryTime: LocalTime,
            zoneId: java.time.ZoneId,
            recoverMissed: Boolean = false,
        ): ReminderSchedule {
            if (recoverMissed) {
                val currentBusinessDate = resolveAppDate(now, boundaryTime)
                val missedTarget = sequenceOf(now.toLocalDate(), now.toLocalDate().minusDays(1))
                    .map { LocalDateTime.of(it, targetTime) }
                    .filter { !it.isAfter(now) }
                    .filter { resolveAppDate(it, boundaryTime) == currentBusinessDate }
                    .maxOrNull()
                if (missedTarget != null) {
                    val immediateRun = now.plusSeconds(1)
                    return ReminderSchedule(
                        triggerAtMillis = immediateRun.atZone(zoneId).toInstant().toEpochMilli(),
                        targetBusinessDate = currentBusinessDate,
                        triggerAt = immediateRun,
                    )
                }
            }
            val todayTarget = LocalDateTime.of(now.toLocalDate(), targetTime)
            val nextRun = if (todayTarget.isAfter(now)) {
                todayTarget
            } else {
                LocalDateTime.of(now.toLocalDate().plusDays(1), targetTime)
            }
            return ReminderSchedule(
                triggerAtMillis = nextRun.atZone(zoneId).toInstant().toEpochMilli(),
                targetBusinessDate = resolveAppDate(nextRun, boundaryTime),
                triggerAt = nextRun,
            )
        }

        /** 保留旧测试和日期计算调用方使用的延迟计算辅助方法。 */
        internal fun computeInitialDelayMillis(
            now: LocalDateTime,
            target: LocalTime,
        ): Long {
            val todayTarget = LocalDateTime.of(now.toLocalDate(), target)
            val nextRun = if (todayTarget.isAfter(now)) {
                todayTarget
            } else {
                LocalDateTime.of(now.toLocalDate().plusDays(1), target)
            }
            return Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
        }

        internal fun computeInitialDelayMillis(
            nowDate: LocalDate,
            nowTime: LocalTime,
            target: LocalTime,
        ): Long = computeInitialDelayMillis(LocalDateTime.of(nowDate, nowTime), target)
    }
}

data class ReminderSchedule(
    val triggerAtMillis: Long,
    val targetBusinessDate: LocalDate,
    val triggerAt: LocalDateTime,
)
