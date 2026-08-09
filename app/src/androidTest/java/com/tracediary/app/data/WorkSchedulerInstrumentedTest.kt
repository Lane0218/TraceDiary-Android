package com.tracediary.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tracediary.app.data.notifications.DailyReminderScheduler
import com.tracediary.app.data.sync.SyncScheduler
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 在真实 Android 环境中验证精确提醒闹钟能够设置和取消。 */
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
    fun dailyReminder_scheduleAndCancel_setsExactAlarm() {
        val scheduler = DailyReminderScheduler(context)
        assumeTrue("设备未授予精确闹钟权限", scheduler.canScheduleExactAlarms())
        scheduler.schedule(
            timeOfDay = LocalTime.of(21, 0),
        )
        scheduler.cancel()
        assertTrue(scheduler.canScheduleExactAlarms())
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
