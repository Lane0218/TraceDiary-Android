package com.tracediary.app.data.notifications

import com.tracediary.app.data.settings.resolveAppDate
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 纯逻辑层单元测试：覆盖 [DailyReminderScheduler.parseTime] 与
 * [DailyReminderScheduler.computeInitialDelayMillis]。
 *
 * Worker 的真实调度依赖 WorkManager + Android 运行时，由 instrumented 测试覆盖。
 */
class DailyReminderSchedulerTest {

    @Test
    fun `parseTime 解析常见时间字符串`() {
        assertEquals(LocalTime.of(21, 0), DailyReminderScheduler.parseTime("21:00"))
        assertEquals(LocalTime.of(8, 30), DailyReminderScheduler.parseTime("08:30"))
        assertEquals(LocalTime.of(0, 0), DailyReminderScheduler.parseTime("00:00"))
        assertEquals(LocalTime.of(23, 59), DailyReminderScheduler.parseTime("23:59"))
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun `parseTime 对非法字符串抛出异常`() {
        DailyReminderScheduler.parseTime("9:5")
    }

    @Test
    fun `target 在今天未来时返回当天差值`() {
        val today = LocalDate.of(2026, 5, 17)
        val now = LocalTime.of(8, 0)
        val target = LocalTime.of(21, 0)

        val actual = DailyReminderScheduler.computeInitialDelayMillis(today, now, target)

        val expected = Duration.ofHours(13).toMillis()
        assertEquals(expected, actual)
    }

    @Test
    fun `target 已过今天时跳到第二天`() {
        val today = LocalDate.of(2026, 5, 17)
        val now = LocalTime.of(22, 0)
        val target = LocalTime.of(21, 0)

        val actual = DailyReminderScheduler.computeInitialDelayMillis(today, now, target)

        val expected = Duration.ofHours(23).toMillis()
        assertEquals(expected, actual)
    }

    @Test
    fun `target 等于当前时间时跳到第二天`() {
        val today = LocalDate.of(2026, 5, 17)
        val now = LocalTime.of(21, 0)
        val target = LocalTime.of(21, 0)

        val actual = DailyReminderScheduler.computeInitialDelayMillis(today, now, target)

        // 严格等于时按"过去"处理，等到下一天的同一时刻。
        val expected = Duration.ofHours(24).toMillis()
        assertEquals(expected, actual)
    }

    @Test
    fun `delay 永不为负`() {
        val today = LocalDate.of(2026, 5, 17)
        val now = LocalTime.of(23, 59)
        val target = LocalTime.of(0, 0)

        val actual = DailyReminderScheduler.computeInitialDelayMillis(today, now, target)

        assertTrue("delay should be non-negative", actual >= 0L)
        // 0:00 已过当天，跳到第二天 0:00：差 1 分钟。
        assertEquals(Duration.ofMinutes(1).toMillis(), actual)
    }

    @Test
    fun `shouldShowReminder 在当天没有记录时提醒`() {
        assertTrue(DailyReminderWorker.shouldShowReminder(null))
    }

    @Test
    fun `shouldShowReminder 在当天只有空白内容时提醒`() {
        val entry = diaryEntry(content = " \n\t ")

        assertTrue(DailyReminderWorker.shouldShowReminder(entry))
    }

    @Test
    fun `shouldShowReminder 在当天已有非空内容时不提醒`() {
        val entry = diaryEntry(content = "今天已经记录过了")

        assertFalse(DailyReminderWorker.shouldShowReminder(entry))
    }

    @Test
    fun `reminder date 在次日切换时间前仍属于前一天`() {
        val now = LocalDateTime.of(2026, 5, 30, 0, 30)
        val boundary = LocalTime.of(2, 0)

        val reminderDate = resolveAppDate(now, boundary)

        assertEquals(LocalDate.of(2026, 5, 29), reminderDate)
    }

    @Test
    fun `reminder date 到达次日切换时间后属于当天`() {
        val now = LocalDateTime.of(2026, 5, 30, 2, 0)
        val boundary = LocalTime.of(2, 0)

        val reminderDate = resolveAppDate(now, boundary)

        assertEquals(LocalDate.of(2026, 5, 30), reminderDate)
    }

    @Test
    fun `reminder 在切换时间前根据前一天日记决定是否提醒`() {
        val now = LocalDateTime.of(2026, 5, 30, 0, 30)
        val boundary = LocalTime.of(2, 0)
        val reminderDate = resolveAppDate(now, boundary)

        val writtenEntry = diaryEntry(date = reminderDate, content = "5月29日已经写了")
        val blankEntry = diaryEntry(date = reminderDate, content = " \n\t ")

        assertFalse(DailyReminderWorker.shouldShowReminder(writtenEntry))
        assertTrue(DailyReminderWorker.shouldShowReminder(blankEntry))
        assertTrue(DailyReminderWorker.shouldShowReminder(null))
    }

    private fun diaryEntry(
        content: String,
        date: LocalDate = LocalDate.of(2026, 5, 17),
    ): DiaryEntry = DiaryEntry(
        date = date,
        content = content,
        createdAt = Instant.parse("2026-05-17T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-17T12:00:00Z"),
        syncState = SyncState.Pending,
    )
}
