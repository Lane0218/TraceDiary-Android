package com.tracediary.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tracediary.app.TraceDiaryApplication

/**
 * 周期同步 Worker：在后台调用 [SyncEngine.mergeAll] 完成 last-write-wins 合并。
 *
 * 失败处理策略：
 * - [ErrorCode.AUTH] 视为无效配置或凭证错误，立刻 [Result.success] 返回——继续重试只会
 *   反复失败，等用户在设置页修复后再触发即可。
 * - 其它错误（NETWORK / NOT_FOUND / CONFLICT / UNKNOWN）走 [Result.retry]，由 WorkManager
 *   在退避后再次尝试。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TraceDiaryApplication ?: return Result.success()
        val engine = app.syncEngine
        return when (val outcome = engine.mergeAll()) {
            is RemoteResult.Ok -> Result.success()
            is RemoteResult.Err -> if (outcome.code == ErrorCode.AUTH) {
                // AUTH 类错误重试无意义（配置缺失或凭证不对），等用户改完再调度。
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
