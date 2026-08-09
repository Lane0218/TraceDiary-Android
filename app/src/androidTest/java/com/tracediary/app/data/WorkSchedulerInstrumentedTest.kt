package com.tracediary.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.sync.SyncScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 在真实 Android WorkManager 中验证周期任务能够入队和取消。 */
@RunWith(AndroidJUnit4::class)
class WorkSchedulerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        workManager = WorkManager.getInstance(context)
        cancelAllTestWork()
    }

    @After
    fun tearDown() {
        cancelAllTestWork()
    }

    @Test
    fun dailyReminder_scheduleAndCancel_updatesUniqueWork() {
        val scheduler = DailyReminderScheduler(context)
        scheduler.schedule(
            timeOfDay = LocalTime.of(21, 0),
            now = LocalDateTime.of(2026, 8, 9, 10, 0),
        )

        val scheduled = workInfos(DailyReminderScheduler.UNIQUE_WORK_NAME)
        assertEquals(1, scheduled.size)
        assertTrue(scheduled.single().state == WorkInfo.State.ENQUEUED)

        scheduler.cancel()
        assertCancelled(DailyReminderScheduler.UNIQUE_WORK_NAME)
    }

    @Test
    fun syncScheduler_scheduleAndCancel_updatesUniqueWork() {
        val scheduler = SyncScheduler(context)
        scheduler.schedule()

        val scheduled = workInfos(SyncScheduler.UNIQUE_WORK_NAME)
        assertEquals(1, scheduled.size)
        assertTrue(scheduled.single().state != WorkInfo.State.CANCELLED)

        scheduler.cancel()
        assertCancelled(SyncScheduler.UNIQUE_WORK_NAME)
    }

    private fun assertCancelled(uniqueWorkName: String) {
        val infos = workInfos(uniqueWorkName)
        assertTrue(infos.isNotEmpty())
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun workInfos(uniqueWorkName: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(uniqueWorkName).get(10, TimeUnit.SECONDS)

    private fun cancelAllTestWork() {
        workManager.cancelUniqueWork(DailyReminderScheduler.UNIQUE_WORK_NAME).result.get(10, TimeUnit.SECONDS)
        workManager.cancelUniqueWork(SyncScheduler.UNIQUE_WORK_NAME).result.get(10, TimeUnit.SECONDS)
    }
}
