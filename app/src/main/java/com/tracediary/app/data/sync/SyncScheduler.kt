package com.tracediary.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 周期同步调度器：以 6 小时为间隔触发 [SyncWorker]。
 *
 * 设计取舍：
 * - 仅约束 [NetworkType.CONNECTED]；不加电池 / 充电约束，避免国产 ROM 长期不触发。
 * - 使用 [ExistingPeriodicWorkPolicy.KEEP]：用户重复打开开关时保留已有任务，不重置
 *   下一次触发时间，避免反复调度时 6 小时被无限推迟。
 */
interface SyncWorkScheduler {
    fun schedule()
    fun cancel()
}

class SyncScheduler(context: Context) : SyncWorkScheduler {

    private val appContext = context.applicationContext

    override fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = INTERVAL_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "tracediary_sync_periodic"
        private const val INTERVAL_HOURS: Long = 6
    }
}
