package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/**
 * 同步引擎核心：上传 / 下载 / 合并三套高层方法，按对象拆分粒度同步。
 *
 * - last-write-wins：以 [DiaryEntry.updatedAt] / [AnnualSummary.updatedAt] 决胜负。
 * - SyncMetadata 表记录每个 objectKey 最近一次远端 ETag 与时间戳。
 * - 远端未配置时立刻返回 AUTH 错误，不抛异常。
 *
 * 不在内部启动协程作用域，所有 suspend 在调用方的 dispatcher 上跑。
 */
class SyncEngine(
    private val storageProvider: () -> RemoteStorage?,
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    enum class SyncStage { Idle, Uploading, Downloading, Merging, Done, Failed }

    data class SyncEngineState(
        val stage: SyncStage = SyncStage.Idle,
        val progress: Int = 0,
        val total: Int = 0,
        val message: String? = null,
    )

    data class SyncSummary(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val conflicts: Int = 0,
        val errors: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(SyncEngineState())
    val state: StateFlow<SyncEngineState> = _state.asStateFlow()

    // ---------- 高层接口 ----------

    suspend fun uploadAll(): RemoteResult<SyncSummary> {
        val storage = storageProvider() ?: return notConfigured()
        val diaries = diaryRepository.observeAllEntries().firstSnapshot()
        val summaries = annualSummaryRepository.observeSummaries().firstSnapshot()
        val total = diaries.size + summaries.size
        emit(SyncStage.Uploading, 0, total, null)

        var uploaded = 0
        var skipped = 0
        var failed = 0
        var conflicts = 0
        var progress = 0
        val errors = mutableListOf<String>()

        for (entry in diaries) {
            when (val r = uploadDiary(storage, entry)) {
                UploadOutcome.Uploaded -> uploaded++
                UploadOutcome.Skipped -> skipped++
                UploadOutcome.Conflict -> conflicts++
                is UploadOutcome.Failed -> {
                    failed++
                    errors += "diary/${entry.date}: ${r.error.message}"
                    runCatching {
                        diaryRepository.setSyncState(entry.date, SyncState.Failed)
                    }
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Uploading, progress, total, null)
        }

        for (summary in summaries) {
            when (val r = uploadAnnual(storage, summary)) {
                UploadOutcome.Uploaded -> uploaded++
                UploadOutcome.Skipped -> skipped++
                UploadOutcome.Conflict -> conflicts++
                is UploadOutcome.Failed -> {
                    failed++
                    errors += "annual/${summary.year}: ${r.error.message}"
                    runCatching {
                        annualSummaryRepository.setSyncState(summary.year, SyncState.Failed)
                    }
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Uploading, progress, total, null)
        }

        val summaryResult = SyncSummary(uploaded, 0, skipped, failed, conflicts, errors.toList())
        emit(SyncStage.Done, total, total, null)
        return RemoteResult.Ok(summaryResult)
    }

    suspend fun downloadAll(): RemoteResult<SyncSummary> {
        val storage = storageProvider() ?: return notConfigured()
        emit(SyncStage.Downloading, 0, 0, null)

        val diaryListing = storage.list(SyncObjectKeys.DIARY_PREFIX)
        if (diaryListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, diaryListing.message)
            return diaryListing
        }
        val annualListing = storage.list(SyncObjectKeys.ANNUAL_PREFIX)
        if (annualListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, annualListing.message)
            return annualListing
        }

        val diaryRemotes = (diaryListing as RemoteResult.Ok).value
            .mapNotNull { obj -> SyncObjectKeys.parseDiaryKey(obj.key)?.let { it to obj } }
        val annualRemotes = (annualListing as RemoteResult.Ok).value
            .mapNotNull { obj -> SyncObjectKeys.parseAnnualKey(obj.key)?.let { it to obj } }
        val total = diaryRemotes.size + annualRemotes.size
        emit(SyncStage.Downloading, 0, total, null)

        var downloaded = 0
        var skipped = 0
        var failed = 0
        var progress = 0

        for ((date, _) in diaryRemotes) {
            val local = diaryRepository.getEntry(date)
            when (val r = downloadDiary(storage, date, local)) {
                DownloadOutcome.Downloaded -> downloaded++
                DownloadOutcome.Skipped -> skipped++
                is DownloadOutcome.Failed -> {
                    failed++
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Downloading, progress, total, null)
        }

        for ((year, _) in annualRemotes) {
            val local = annualSummaryRepository.getSummary(year)
            when (val r = downloadAnnual(storage, year, local)) {
                DownloadOutcome.Downloaded -> downloaded++
                DownloadOutcome.Skipped -> skipped++
                is DownloadOutcome.Failed -> {
                    failed++
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Downloading, progress, total, null)
        }

        emit(SyncStage.Done, total, total, null)
        return RemoteResult.Ok(SyncSummary(0, downloaded, skipped, failed, 0))
    }

    suspend fun mergeAll(): RemoteResult<SyncSummary> {
        val storage = storageProvider() ?: return notConfigured()
        emit(SyncStage.Merging, 0, 0, null)

        val diaryListing = storage.list(SyncObjectKeys.DIARY_PREFIX)
        if (diaryListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, diaryListing.message)
            return diaryListing
        }
        val annualListing = storage.list(SyncObjectKeys.ANNUAL_PREFIX)
        if (annualListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, annualListing.message)
            return annualListing
        }
        val remoteDiaryByDate = (diaryListing as RemoteResult.Ok).value
            .mapNotNull { obj -> SyncObjectKeys.parseDiaryKey(obj.key)?.let { it to obj } }
            .toMap()
        val remoteAnnualByYear = (annualListing as RemoteResult.Ok).value
            .mapNotNull { obj -> SyncObjectKeys.parseAnnualKey(obj.key)?.let { it to obj } }
            .toMap()

        val localDiaries = diaryRepository.observeAllEntries().firstSnapshot()
        val localSummaries = annualSummaryRepository.observeSummaries().firstSnapshot()
        val localDiaryByDate = localDiaries.associateBy { it.date }
        val localAnnualByYear = localSummaries.associateBy { it.year }

        val allDiaryKeys = localDiaryByDate.keys + remoteDiaryByDate.keys
        val allAnnualKeys = localAnnualByYear.keys + remoteAnnualByYear.keys
        val total = allDiaryKeys.size + allAnnualKeys.size
        emit(SyncStage.Merging, 0, total, null)

        var uploaded = 0
        var downloaded = 0
        var skipped = 0
        var failed = 0
        var conflicts = 0
        var progress = 0

        for (date in allDiaryKeys) {
            val local = localDiaryByDate[date]
            val remote = remoteDiaryByDate[date]
            when (val r = mergeDiary(storage, date, local, remote)) {
                is MergeOutcome.Uploaded -> uploaded++
                is MergeOutcome.Downloaded -> downloaded++
                MergeOutcome.Skipped -> skipped++
                MergeOutcome.Conflict -> conflicts++
                is MergeOutcome.Failed -> {
                    failed++
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Merging, progress, total, null)
        }

        for (year in allAnnualKeys) {
            val local = localAnnualByYear[year]
            val remote = remoteAnnualByYear[year]
            when (val r = mergeAnnual(storage, year, local, remote)) {
                is MergeOutcome.Uploaded -> uploaded++
                is MergeOutcome.Downloaded -> downloaded++
                MergeOutcome.Skipped -> skipped++
                MergeOutcome.Conflict -> conflicts++
                is MergeOutcome.Failed -> {
                    failed++
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Merging, progress, total, null)
        }

        emit(SyncStage.Done, total, total, null)
        return RemoteResult.Ok(SyncSummary(uploaded, downloaded, skipped, failed, conflicts))
    }

    // ---------- 单条 Diary 处理 ----------

    private suspend fun uploadDiary(storage: RemoteStorage, entry: DiaryEntry): UploadOutcome {
        val key = SyncObjectKeys.diaryObjectKey(entry.date)
        val headResult = storage.head(key)
        val (existingEtag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> if (headResult.code == ErrorCode.NOT_FOUND) {
                null to null
            } else {
                return UploadOutcome.Failed(headResult, fatal = headResult.code == ErrorCode.AUTH)
            }
        }
        if (remoteUpdatedAt != null && remoteUpdatedAt.isAfter(entry.updatedAt)) {
            return UploadOutcome.Skipped
        }
        val body = encodeDiary(entry)
        val putResult = storage.put(key, body, "application/json", existingEtag)
        return when (putResult) {
            is RemoteResult.Ok -> {
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = key,
                        localUpdatedAt = entry.updatedAt,
                        remoteUpdatedAt = putResult.value.lastModified ?: now,
                        lastSyncedAt = now,
                        checksum = null,
                    )
                )
                diaryRepository.setSyncState(entry.date, SyncState.Synced)
                UploadOutcome.Uploaded
            }
            is RemoteResult.Err -> if (putResult.code == ErrorCode.CONFLICT) {
                runCatching { diaryRepository.setSyncState(entry.date, SyncState.Conflict) }
                UploadOutcome.Conflict
            } else {
                UploadOutcome.Failed(putResult, fatal = putResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun downloadDiary(
        storage: RemoteStorage,
        date: java.time.LocalDate,
        local: DiaryEntry?,
    ): DownloadOutcome {
        val key = SyncObjectKeys.diaryObjectKey(date)
        val headResult = storage.head(key)
        val (etag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> return DownloadOutcome.Failed(
                headResult,
                fatal = headResult.code == ErrorCode.AUTH,
            )
        }
        if (local != null && remoteUpdatedAt != null && !remoteUpdatedAt.isAfter(local.updatedAt)) {
            return DownloadOutcome.Skipped
        }
        val getResult = storage.get(key)
        return when (getResult) {
            is RemoteResult.Ok -> {
                val decoded = runCatching { decodeDiary(getResult.value) }.getOrNull()
                    ?: return DownloadOutcome.Failed(
                        RemoteResult.Err(ErrorCode.UNKNOWN, "解析远端 diary 失败"),
                        fatal = false,
                    )
                if (local != null && !decoded.updatedAt.isAfter(local.updatedAt)) {
                    return DownloadOutcome.Skipped
                }
                diaryRepository.saveEntry(decoded.date, decoded.content)
                diaryRepository.setSyncState(decoded.date, SyncState.Synced)
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = key,
                        localUpdatedAt = decoded.updatedAt,
                        remoteUpdatedAt = remoteUpdatedAt ?: decoded.updatedAt,
                        lastSyncedAt = now,
                        checksum = etag,
                    )
                )
                DownloadOutcome.Downloaded
            }
            is RemoteResult.Err -> DownloadOutcome.Failed(
                getResult,
                fatal = getResult.code == ErrorCode.AUTH,
            )
        }
    }

    private suspend fun mergeDiary(
        storage: RemoteStorage,
        date: java.time.LocalDate,
        local: DiaryEntry?,
        remote: RemoteObject?,
    ): MergeOutcome {
        return when {
            local != null && remote == null -> when (val r = uploadDiary(storage, local)) {
                UploadOutcome.Uploaded -> MergeOutcome.Uploaded
                UploadOutcome.Skipped -> MergeOutcome.Skipped
                UploadOutcome.Conflict -> MergeOutcome.Conflict
                is UploadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
            }
            local == null && remote != null -> when (val r = downloadDiary(storage, date, null)) {
                DownloadOutcome.Downloaded -> MergeOutcome.Downloaded
                DownloadOutcome.Skipped -> MergeOutcome.Skipped
                is DownloadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
            }
            local != null && remote != null -> {
                val remoteAt = remote.lastModified
                when {
                    remoteAt != null && local.updatedAt.isAfter(remoteAt) ->
                        when (val r = uploadDiary(storage, local)) {
                            UploadOutcome.Uploaded -> MergeOutcome.Uploaded
                            UploadOutcome.Skipped -> MergeOutcome.Skipped
                            UploadOutcome.Conflict -> MergeOutcome.Conflict
                            is UploadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
                        }
                    remoteAt != null && remoteAt.isAfter(local.updatedAt) ->
                        when (val r = downloadDiary(storage, date, local)) {
                            DownloadOutcome.Downloaded -> MergeOutcome.Downloaded
                            DownloadOutcome.Skipped -> MergeOutcome.Skipped
                            is DownloadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
                        }
                    else -> MergeOutcome.Skipped
                }
            }
            else -> MergeOutcome.Skipped
        }
    }

    // ---------- 单条 Annual 处理 ----------

    private suspend fun uploadAnnual(storage: RemoteStorage, summary: AnnualSummary): UploadOutcome {
        val key = SyncObjectKeys.annualObjectKey(summary.year)
        val headResult = storage.head(key)
        val (existingEtag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> if (headResult.code == ErrorCode.NOT_FOUND) {
                null to null
            } else {
                return UploadOutcome.Failed(headResult, fatal = headResult.code == ErrorCode.AUTH)
            }
        }
        if (remoteUpdatedAt != null && remoteUpdatedAt.isAfter(summary.updatedAt)) {
            return UploadOutcome.Skipped
        }
        val body = encodeAnnual(summary)
        val putResult = storage.put(key, body, "application/json", existingEtag)
        return when (putResult) {
            is RemoteResult.Ok -> {
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = key,
                        localUpdatedAt = summary.updatedAt,
                        remoteUpdatedAt = putResult.value.lastModified ?: now,
                        lastSyncedAt = now,
                        checksum = null,
                    )
                )
                annualSummaryRepository.setSyncState(summary.year, SyncState.Synced)
                UploadOutcome.Uploaded
            }
            is RemoteResult.Err -> if (putResult.code == ErrorCode.CONFLICT) {
                runCatching { annualSummaryRepository.setSyncState(summary.year, SyncState.Conflict) }
                UploadOutcome.Conflict
            } else {
                UploadOutcome.Failed(putResult, fatal = putResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun downloadAnnual(
        storage: RemoteStorage,
        year: Int,
        local: AnnualSummary?,
    ): DownloadOutcome {
        val key = SyncObjectKeys.annualObjectKey(year)
        val headResult = storage.head(key)
        val (etag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> return DownloadOutcome.Failed(
                headResult,
                fatal = headResult.code == ErrorCode.AUTH,
            )
        }
        if (local != null && remoteUpdatedAt != null && !remoteUpdatedAt.isAfter(local.updatedAt)) {
            return DownloadOutcome.Skipped
        }
        val getResult = storage.get(key)
        return when (getResult) {
            is RemoteResult.Ok -> {
                val decoded = runCatching { decodeAnnual(getResult.value) }.getOrNull()
                    ?: return DownloadOutcome.Failed(
                        RemoteResult.Err(ErrorCode.UNKNOWN, "解析远端 annual 失败"),
                        fatal = false,
                    )
                if (local != null && !decoded.updatedAt.isAfter(local.updatedAt)) {
                    return DownloadOutcome.Skipped
                }
                annualSummaryRepository.saveSummary(decoded.year, decoded.content)
                annualSummaryRepository.setSyncState(decoded.year, SyncState.Synced)
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = key,
                        localUpdatedAt = decoded.updatedAt,
                        remoteUpdatedAt = remoteUpdatedAt ?: decoded.updatedAt,
                        lastSyncedAt = now,
                        checksum = etag,
                    )
                )
                DownloadOutcome.Downloaded
            }
            is RemoteResult.Err -> DownloadOutcome.Failed(
                getResult,
                fatal = getResult.code == ErrorCode.AUTH,
            )
        }
    }

    private suspend fun mergeAnnual(
        storage: RemoteStorage,
        year: Int,
        local: AnnualSummary?,
        remote: RemoteObject?,
    ): MergeOutcome {
        return when {
            local != null && remote == null -> when (val r = uploadAnnual(storage, local)) {
                UploadOutcome.Uploaded -> MergeOutcome.Uploaded
                UploadOutcome.Skipped -> MergeOutcome.Skipped
                UploadOutcome.Conflict -> MergeOutcome.Conflict
                is UploadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
            }
            local == null && remote != null -> when (val r = downloadAnnual(storage, year, null)) {
                DownloadOutcome.Downloaded -> MergeOutcome.Downloaded
                DownloadOutcome.Skipped -> MergeOutcome.Skipped
                is DownloadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
            }
            local != null && remote != null -> {
                val remoteAt = remote.lastModified
                when {
                    remoteAt != null && local.updatedAt.isAfter(remoteAt) ->
                        when (val r = uploadAnnual(storage, local)) {
                            UploadOutcome.Uploaded -> MergeOutcome.Uploaded
                            UploadOutcome.Skipped -> MergeOutcome.Skipped
                            UploadOutcome.Conflict -> MergeOutcome.Conflict
                            is UploadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
                        }
                    remoteAt != null && remoteAt.isAfter(local.updatedAt) ->
                        when (val r = downloadAnnual(storage, year, local)) {
                            DownloadOutcome.Downloaded -> MergeOutcome.Downloaded
                            DownloadOutcome.Skipped -> MergeOutcome.Skipped
                            is DownloadOutcome.Failed -> MergeOutcome.Failed(r.error, r.fatal)
                        }
                    else -> MergeOutcome.Skipped
                }
            }
            else -> MergeOutcome.Skipped
        }
    }

    // ---------- 内部辅助 ----------

    private fun emit(stage: SyncStage, progress: Int, total: Int, message: String?) {
        _state.value = SyncEngineState(stage, progress, total, message)
    }

    private fun notConfigured(): RemoteResult<SyncSummary> {
        emit(SyncStage.Failed, 0, 0, "未配置云同步")
        return RemoteResult.Err(ErrorCode.AUTH, "未配置云同步")
    }

    private sealed interface UploadOutcome {
        data object Uploaded : UploadOutcome
        data object Skipped : UploadOutcome
        data object Conflict : UploadOutcome
        data class Failed(val error: RemoteResult.Err, val fatal: Boolean) : UploadOutcome
    }

    private sealed interface DownloadOutcome {
        data object Downloaded : DownloadOutcome
        data object Skipped : DownloadOutcome
        data class Failed(val error: RemoteResult.Err, val fatal: Boolean) : DownloadOutcome
    }

    private sealed interface MergeOutcome {
        data object Uploaded : MergeOutcome
        data object Downloaded : MergeOutcome
        data object Skipped : MergeOutcome
        data object Conflict : MergeOutcome
        data class Failed(val error: RemoteResult.Err, val fatal: Boolean) : MergeOutcome
    }
}

private suspend fun <T> Flow<T>.firstSnapshot(): T = first()
