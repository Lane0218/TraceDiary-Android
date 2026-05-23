package com.tracediary.app.data.sync

import com.tracediary.app.data.security.CryptoBox
import com.tracediary.app.data.security.CloudCipherCredentialStore
import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.PendingSyncDeletion
import com.tracediary.app.domain.repository.PendingSyncDeletionRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.MergeStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.crypto.SecretKey

class SyncEngine(
    private val storageProvider: () -> RemoteStorage?,
    private val secureCredentialStore: CloudCipherCredentialStore,
    private val diaryRepository: DiaryRepository,
    private val annualSummaryRepository: AnnualSummaryRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val pendingSyncDeletionRepository: PendingSyncDeletionRepository? = null,
    private val passwordProvider: () -> String? = { null },
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    enum class SyncStage { Idle, Uploading, Downloading, Merging, Rekeying, Deleting, Done, Failed }

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
        val deleted: Int = 0,
        val errors: List<String> = emptyList(),
    )

    private data class PasswordContext(
        val password: String,
        val salt: ByteArray,
        val key: SecretKey,
    )

    private data class RemoteDiaryPayload(
        val entry: DiaryEntry,
        val format: SyncPayloadFormat,
    )

    private data class RemoteAnnualPayload(
        val summary: AnnualSummary,
        val format: SyncPayloadFormat,
    )

    private val _state = MutableStateFlow(SyncEngineState())
    val state: StateFlow<SyncEngineState> = _state.asStateFlow()

    private val operationMutex = Mutex()

    suspend fun uploadAll(): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        val password = loadPasswordContext() ?: return@withLock missingPassword()
        // 待删除日期/年份不应被重新上传，直接跳过；后续在末尾的待重试阶段处理它们。
        val pending = pendingSyncDeletionRepository?.getAll().orEmpty()
        val pendingDates = pending.filterIsInstance<PendingSyncDeletion.Diary>().map { it.date }.toSet()
        val pendingYears = pending.filterIsInstance<PendingSyncDeletion.Annual>().map { it.year }.toSet()
        val diaries = diaryRepository.observeAllEntries().firstSnapshot()
            .filter { it.date !in pendingDates }
        val summaries = annualSummaryRepository.observeSummaries().firstSnapshot()
            .filter { it.year !in pendingYears }
        val total = diaries.size + summaries.size
        emit(SyncStage.Uploading, 0, total, null)

        var uploaded = 0
        var skipped = 0
        var failed = 0
        var conflicts = 0
        var progress = 0
        val errors = mutableListOf<String>()

        for (entry in diaries) {
            when (val r = uploadDiaryInternal(storage, password.key, entry)) {
                UploadOutcome.Uploaded -> uploaded++
                UploadOutcome.Skipped -> skipped++
                UploadOutcome.Conflict -> conflicts++
                is UploadOutcome.Failed -> {
                    failed++
                    errors += "diary/${entry.date}: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Uploading, progress, total, null)
        }

        for (summary in summaries) {
            when (val r = uploadAnnualInternal(storage, password.key, summary)) {
                UploadOutcome.Uploaded -> uploaded++
                UploadOutcome.Skipped -> skipped++
                UploadOutcome.Conflict -> conflicts++
                is UploadOutcome.Failed -> {
                    failed++
                    errors += "annual/${summary.year}: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Uploading, progress, total, null)
        }

        val baseSummary = SyncSummary(uploaded, 0, skipped, failed, conflicts, 0, errors.toList())
        val finalSummary = retryPendingDeletions(storage, baseSummary)
        emit(SyncStage.Done, total, total, null)
        RemoteResult.Ok(finalSummary)
    }

    suspend fun downloadAll(): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        val password = loadPasswordContext() ?: return@withLock missingPassword()
        emit(SyncStage.Downloading, 0, 0, null)

        val diaryListing = storage.list(SyncObjectKeys.DIARY_PREFIX)
        if (diaryListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, diaryListing.message)
            return@withLock diaryListing
        }
        val annualListing = storage.list(SyncObjectKeys.ANNUAL_PREFIX)
        if (annualListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, annualListing.message)
            return@withLock annualListing
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
        val errors = mutableListOf<String>()

        for ((date, remote) in diaryRemotes) {
            val local = diaryRepository.getEntry(date)
            when (val r = downloadDiaryInternal(storage, password.key, date, local, remote)) {
                DownloadOutcome.Downloaded -> downloaded++
                DownloadOutcome.Skipped -> skipped++
                is DownloadOutcome.Failed -> {
                    failed++
                    errors += "diary/$date: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Downloading, progress, total, null)
        }

        for ((year, remote) in annualRemotes) {
            val local = annualSummaryRepository.getSummary(year)
            when (val r = downloadAnnualInternal(storage, password.key, year, local, remote)) {
                DownloadOutcome.Downloaded -> downloaded++
                DownloadOutcome.Skipped -> skipped++
                is DownloadOutcome.Failed -> {
                    failed++
                    errors += "annual/$year: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Downloading, progress, total, null)
        }

        emit(SyncStage.Done, total, total, null)
        RemoteResult.Ok(SyncSummary(0, downloaded, skipped, failed, 0, 0, errors))
    }

    suspend fun mergeAll(strategy: MergeStrategy = MergeStrategy.KeepNewer): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        val password = loadPasswordContext() ?: return@withLock missingPassword()
        emit(SyncStage.Merging, 0, 0, null)

        val diaryListing = storage.list(SyncObjectKeys.DIARY_PREFIX)
        if (diaryListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, diaryListing.message)
            return@withLock diaryListing
        }
        val annualListing = storage.list(SyncObjectKeys.ANNUAL_PREFIX)
        if (annualListing is RemoteResult.Err) {
            emit(SyncStage.Failed, 0, 0, annualListing.message)
            return@withLock annualListing
        }

        val pending = pendingSyncDeletionRepository?.getAll().orEmpty()
        val pendingDates = pending.filterIsInstance<PendingSyncDeletion.Diary>().map { it.date }.toSet()
        val pendingYears = pending.filterIsInstance<PendingSyncDeletion.Annual>().map { it.year }.toSet()

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

        // 跳过仍处于"待删除"的目标，避免把已删除日期当作"仅远端"重新拉回。
        val allDiaryKeys = (localDiaryByDate.keys + remoteDiaryByDate.keys) - pendingDates
        val allAnnualKeys = (localAnnualByYear.keys + remoteAnnualByYear.keys) - pendingYears
        val total = allDiaryKeys.size + allAnnualKeys.size
        emit(SyncStage.Merging, 0, total, null)

        var uploaded = 0
        var downloaded = 0
        var skipped = 0
        var failed = 0
        var conflicts = 0
        var progress = 0
        val errors = mutableListOf<String>()

        for (date in allDiaryKeys) {
            when (
                val r = mergeDiaryInternal(
                    storage = storage,
                    key = password.key,
                    date = date,
                    local = localDiaryByDate[date],
                    remote = remoteDiaryByDate[date],
                    strategy = strategy,
                )
            ) {
                MergeOutcome.Uploaded -> uploaded++
                MergeOutcome.Downloaded -> downloaded++
                MergeOutcome.Skipped -> skipped++
                MergeOutcome.Conflict -> conflicts++
                is MergeOutcome.Failed -> {
                    failed++
                    errors += "diary/$date: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Merging, progress, total, null)
        }

        for (year in allAnnualKeys) {
            when (
                val r = mergeAnnualInternal(
                    storage = storage,
                    key = password.key,
                    year = year,
                    local = localAnnualByYear[year],
                    remote = remoteAnnualByYear[year],
                    strategy = strategy,
                )
            ) {
                MergeOutcome.Uploaded -> uploaded++
                MergeOutcome.Downloaded -> downloaded++
                MergeOutcome.Skipped -> skipped++
                MergeOutcome.Conflict -> conflicts++
                is MergeOutcome.Failed -> {
                    failed++
                    errors += "annual/$year: ${r.error.message}"
                    if (r.fatal) {
                        emit(SyncStage.Failed, progress, total, r.error.message)
                        return@withLock RemoteResult.Err(r.error.code, r.error.message, r.error.cause)
                    }
                }
            }
            progress++
            emit(SyncStage.Merging, progress, total, null)
        }

        val baseSummary = SyncSummary(uploaded, downloaded, skipped, failed, conflicts, 0, errors)
        val finalSummary = retryPendingDeletions(storage, baseSummary)
        emit(SyncStage.Done, total, total, null)
        RemoteResult.Ok(finalSummary)
    }

    /**
     * 单条日记远端删除：失败时把日期写入待重试表，后续 mergeAll/uploadAll 周期同步会再次尝试。
     * 本地条目此时通常已被 [DiaryRepository.deleteEntry] 删除，不需要回滚。
     */
    suspend fun deleteDiary(date: LocalDate): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        loadPasswordContext() ?: return@withLock missingPassword()
        val objectKey = SyncObjectKeys.diaryObjectKey(date)
        emit(SyncStage.Deleting, 0, 1, null)
        when (val result = storage.delete(objectKey)) {
            is RemoteResult.Ok -> {
                runCatching { syncMetadataRepository.deleteMetadata(objectKey) }
                runCatching { pendingSyncDeletionRepository?.clearDiary(date) }
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(deleted = 1))
            }
            is RemoteResult.Err -> {
                runCatching { pendingSyncDeletionRepository?.markDiaryPending(date) }
                emit(SyncStage.Failed, 0, 1, result.message)
                result
            }
        }
    }

    suspend fun deleteAnnual(year: Int): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        loadPasswordContext() ?: return@withLock missingPassword()
        val objectKey = SyncObjectKeys.annualObjectKey(year)
        emit(SyncStage.Deleting, 0, 1, null)
        when (val result = storage.delete(objectKey)) {
            is RemoteResult.Ok -> {
                runCatching { syncMetadataRepository.deleteMetadata(objectKey) }
                runCatching { pendingSyncDeletionRepository?.clearAnnual(year) }
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(deleted = 1))
            }
            is RemoteResult.Err -> {
                runCatching { pendingSyncDeletionRepository?.markAnnualPending(year) }
                emit(SyncStage.Failed, 0, 1, result.message)
                result
            }
        }
    }

    suspend fun uploadDiary(date: LocalDate): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        val password = loadPasswordContext() ?: return@withLock missingPassword()
        val entry = diaryRepository.getEntry(date)
            ?: return@withLock RemoteResult.Err(ErrorCode.NOT_FOUND, "本地不存在 ${date} 的日记")
        emit(SyncStage.Uploading, 0, 1, null)
        when (val result = uploadDiaryInternal(storage, password.key, entry)) {
            UploadOutcome.Uploaded -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(uploaded = 1))
            }
            UploadOutcome.Skipped -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(skipped = 1))
            }
            UploadOutcome.Conflict -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(conflicts = 1))
            }
            is UploadOutcome.Failed -> {
                emit(SyncStage.Failed, 0, 1, result.error.message)
                result.error
            }
        }
    }

    suspend fun uploadAnnual(year: Int): RemoteResult<SyncSummary> = operationMutex.withLock {
        val storage = storageProvider() ?: return@withLock notConfigured()
        val password = loadPasswordContext() ?: return@withLock missingPassword()
        val summary = annualSummaryRepository.getSummary(year)
            ?: return@withLock RemoteResult.Err(ErrorCode.NOT_FOUND, "本地不存在 $year 年的年度总结")
        emit(SyncStage.Uploading, 0, 1, null)
        when (val result = uploadAnnualInternal(storage, password.key, summary)) {
            UploadOutcome.Uploaded -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(uploaded = 1))
            }
            UploadOutcome.Skipped -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(skipped = 1))
            }
            UploadOutcome.Conflict -> {
                emit(SyncStage.Done, 1, 1, null)
                RemoteResult.Ok(SyncSummary(conflicts = 1))
            }
            is UploadOutcome.Failed -> {
                emit(SyncStage.Failed, 0, 1, result.error.message)
                result.error
            }
        }
    }

    suspend fun rotateSyncPassword(oldPassword: String, newPassword: String): RemoteResult<SyncSummary> =
        operationMutex.withLock {
            val storage = storageProvider() ?: return@withLock notConfigured()
            val persistedPassword = passwordProvider().takeUnless { it.isNullOrEmpty() }
                ?: secureCredentialStore.loadLocalPassword()
                ?: return@withLock missingPassword()
            if (persistedPassword != oldPassword) {
                emit(SyncStage.Failed, 0, 0, "旧同步密码不正确")
                return@withLock RemoteResult.Err(ErrorCode.AUTH, "旧同步密码不正确")
            }
            val oldSalt = secureCredentialStore.loadOrCreateCloudCipherSalt()
            val oldKey = CryptoBox.deriveKey(oldPassword, oldSalt)

            val diaryListing = storage.list(SyncObjectKeys.DIARY_PREFIX)
            if (diaryListing is RemoteResult.Err) {
                emit(SyncStage.Failed, 0, 0, diaryListing.message)
                return@withLock diaryListing
            }
            val annualListing = storage.list(SyncObjectKeys.ANNUAL_PREFIX)
            if (annualListing is RemoteResult.Err) {
                emit(SyncStage.Failed, 0, 0, annualListing.message)
                return@withLock annualListing
            }

            val diaryRemotes = (diaryListing as RemoteResult.Ok).value
                .mapNotNull { obj -> SyncObjectKeys.parseDiaryKey(obj.key)?.let { it to obj } }
            val annualRemotes = (annualListing as RemoteResult.Ok).value
                .mapNotNull { obj -> SyncObjectKeys.parseAnnualKey(obj.key)?.let { it to obj } }
            val total = diaryRemotes.size + annualRemotes.size
            emit(SyncStage.Rekeying, 0, total, null)

            val diaryPayloads = mutableListOf<Pair<RemoteObject, DiaryEntry>>()
            val annualPayloads = mutableListOf<Pair<RemoteObject, AnnualSummary>>()
            var progress = 0

            for ((date, remote) in diaryRemotes) {
                when (val payload = loadRemoteDiary(storage, oldKey, date, remote)) {
                    is RemoteResult.Ok -> diaryPayloads += remote to payload.value.entry
                    is RemoteResult.Err -> {
                        emit(SyncStage.Failed, progress, total, payload.message)
                        return@withLock payload
                    }
                }
                progress++
                emit(SyncStage.Rekeying, progress, total, null)
            }

            for ((year, remote) in annualRemotes) {
                when (val payload = loadRemoteAnnual(storage, oldKey, year, remote)) {
                    is RemoteResult.Ok -> annualPayloads += remote to payload.value.summary
                    is RemoteResult.Err -> {
                        emit(SyncStage.Failed, progress, total, payload.message)
                        return@withLock payload
                    }
                }
                progress++
                emit(SyncStage.Rekeying, progress, total, null)
            }

            val newSalt = secureCredentialStore.rotateCloudCipherSalt()
            val newKey = CryptoBox.deriveKey(newPassword, newSalt)
            progress = 0
            try {
                for ((remote, entry) in diaryPayloads) {
                    val put = storage.put(
                        key = remote.key,
                        body = encodeDiary(entry, newKey),
                        contentType = "application/json",
                        ifMatchEtag = remote.etag,
                    )
                    if (put is RemoteResult.Err) {
                        secureCredentialStore.saveCloudCipherSalt(oldSalt)
                        emit(SyncStage.Failed, progress, total, put.message)
                        return@withLock put
                    }
                    progress++
                    emit(SyncStage.Rekeying, progress, total, null)
                }
                for ((remote, summary) in annualPayloads) {
                    val put = storage.put(
                        key = remote.key,
                        body = encodeAnnual(summary, newKey),
                        contentType = "application/json",
                        ifMatchEtag = remote.etag,
                    )
                    if (put is RemoteResult.Err) {
                        secureCredentialStore.saveCloudCipherSalt(oldSalt)
                        emit(SyncStage.Failed, progress, total, put.message)
                        return@withLock put
                    }
                    progress++
                    emit(SyncStage.Rekeying, progress, total, null)
                }
            } catch (t: Throwable) {
                secureCredentialStore.saveCloudCipherSalt(oldSalt)
                throw t
            }

            secureCredentialStore.saveLocalPassword(newPassword)
            emit(SyncStage.Done, total, total, null)
            RemoteResult.Ok(SyncSummary(uploaded = total))
        }

    private suspend fun uploadDiaryInternal(
        storage: RemoteStorage,
        key: SecretKey,
        entry: DiaryEntry,
        force: Boolean = false,
    ): UploadOutcome {
        val objectKey = SyncObjectKeys.diaryObjectKey(entry.date)
        val headResult = storage.head(objectKey)
        val (existingEtag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> if (headResult.code == ErrorCode.NOT_FOUND) {
                null to null
            } else {
                markDiaryFailed(entry.date)
                return UploadOutcome.Failed(headResult, fatal = headResult.code == ErrorCode.AUTH)
            }
        }
        if (!force && remoteUpdatedAt != null && remoteUpdatedAt.isAfter(entry.updatedAt)) {
            return UploadOutcome.Skipped
        }
        val body = encodeDiary(entry, key)
        val putResult = storage.put(objectKey, body, "application/json", existingEtag)
        return when (putResult) {
            is RemoteResult.Ok -> {
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = objectKey,
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
                markDiaryFailed(entry.date)
                UploadOutcome.Failed(putResult, fatal = putResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun downloadDiaryInternal(
        storage: RemoteStorage,
        key: SecretKey,
        date: LocalDate,
        local: DiaryEntry?,
        remote: RemoteObject,
        force: Boolean = false,
    ): DownloadOutcome {
        if (!force && local != null && remote.lastModified != null && !remote.lastModified.isAfter(local.updatedAt)) {
            return DownloadOutcome.Skipped
        }
        return when (val payloadResult = loadRemoteDiary(storage, key, date, remote)) {
            is RemoteResult.Ok -> {
                val decoded = payloadResult.value.entry
                if (!force && local != null && !decoded.updatedAt.isAfter(local.updatedAt)) {
                    return DownloadOutcome.Skipped
                }
                diaryRepository.saveEntry(decoded.date, decoded.content)
                diaryRepository.setSyncState(decoded.date, SyncState.Synced)
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = remote.key,
                        localUpdatedAt = decoded.updatedAt,
                        remoteUpdatedAt = remote.lastModified ?: decoded.updatedAt,
                        lastSyncedAt = now,
                        checksum = remote.etag,
                    )
                )
                DownloadOutcome.Downloaded
            }
            is RemoteResult.Err -> {
                markDiaryFailed(date)
                DownloadOutcome.Failed(payloadResult, fatal = payloadResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun mergeDiaryInternal(
        storage: RemoteStorage,
        key: SecretKey,
        date: LocalDate,
        local: DiaryEntry?,
        remote: RemoteObject?,
        strategy: MergeStrategy,
    ): MergeOutcome {
        return when {
            local != null && remote == null -> uploadDiaryInternal(storage, key, local).toMergeOutcome()
            local == null && remote != null -> downloadDiaryInternal(storage, key, date, null, remote).toMergeOutcome()
            local != null && remote != null -> {
                if (strategy == MergeStrategy.OverwriteAll) {
                    return uploadDiaryInternal(storage, key, local, force = true).toMergeOutcome()
                }
                if (strategy == MergeStrategy.PreferRemote) {
                    return downloadDiaryInternal(storage, key, date, local, remote, force = true).toMergeOutcome()
                }
                if (strategy == MergeStrategy.SkipExisting) {
                    return MergeOutcome.Skipped
                }
                val remotePayload = when (val result = loadRemoteDiary(storage, key, date, remote)) {
                    is RemoteResult.Ok -> result.value
                    is RemoteResult.Err -> {
                        markDiaryFailed(date)
                        return MergeOutcome.Failed(result, fatal = result.code == ErrorCode.AUTH)
                    }
                }
                when {
                    remotePayload.format == SyncPayloadFormat.LegacyPlaintext ->
                        uploadDiaryInternal(storage, key, local, force = true).toMergeOutcome()
                    remotePayload.entry.updatedAt.isAfter(local.updatedAt) ->
                        downloadDiaryInternal(storage, key, date, local, remote).toMergeOutcome()
                    local.updatedAt.isAfter(remotePayload.entry.updatedAt) ->
                        uploadDiaryInternal(storage, key, local).toMergeOutcome()
                    else -> MergeOutcome.Skipped
                }
            }
            else -> MergeOutcome.Skipped
        }
    }

    private suspend fun uploadAnnualInternal(
        storage: RemoteStorage,
        key: SecretKey,
        summary: AnnualSummary,
        force: Boolean = false,
    ): UploadOutcome {
        val objectKey = SyncObjectKeys.annualObjectKey(summary.year)
        val headResult = storage.head(objectKey)
        val (existingEtag, remoteUpdatedAt) = when (headResult) {
            is RemoteResult.Ok -> headResult.value.etag to headResult.value.lastModified
            is RemoteResult.Err -> if (headResult.code == ErrorCode.NOT_FOUND) {
                null to null
            } else {
                markAnnualFailed(summary.year)
                return UploadOutcome.Failed(headResult, fatal = headResult.code == ErrorCode.AUTH)
            }
        }
        if (!force && remoteUpdatedAt != null && remoteUpdatedAt.isAfter(summary.updatedAt)) {
            return UploadOutcome.Skipped
        }
        val body = encodeAnnual(summary, key)
        val putResult = storage.put(objectKey, body, "application/json", existingEtag)
        return when (putResult) {
            is RemoteResult.Ok -> {
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = objectKey,
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
                markAnnualFailed(summary.year)
                UploadOutcome.Failed(putResult, fatal = putResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun downloadAnnualInternal(
        storage: RemoteStorage,
        key: SecretKey,
        year: Int,
        local: AnnualSummary?,
        remote: RemoteObject,
        force: Boolean = false,
    ): DownloadOutcome {
        if (!force && local != null && remote.lastModified != null && !remote.lastModified.isAfter(local.updatedAt)) {
            return DownloadOutcome.Skipped
        }
        return when (val payloadResult = loadRemoteAnnual(storage, key, year, remote)) {
            is RemoteResult.Ok -> {
                val decoded = payloadResult.value.summary
                if (!force && local != null && !decoded.updatedAt.isAfter(local.updatedAt)) {
                    return DownloadOutcome.Skipped
                }
                annualSummaryRepository.saveSummary(decoded.year, decoded.content)
                annualSummaryRepository.setSyncState(decoded.year, SyncState.Synced)
                val now = Instant.now(clock)
                syncMetadataRepository.saveMetadata(
                    SyncMetadata(
                        objectKey = remote.key,
                        localUpdatedAt = decoded.updatedAt,
                        remoteUpdatedAt = remote.lastModified ?: decoded.updatedAt,
                        lastSyncedAt = now,
                        checksum = remote.etag,
                    )
                )
                DownloadOutcome.Downloaded
            }
            is RemoteResult.Err -> {
                markAnnualFailed(year)
                DownloadOutcome.Failed(payloadResult, fatal = payloadResult.code == ErrorCode.AUTH)
            }
        }
    }

    private suspend fun mergeAnnualInternal(
        storage: RemoteStorage,
        key: SecretKey,
        year: Int,
        local: AnnualSummary?,
        remote: RemoteObject?,
        strategy: MergeStrategy,
    ): MergeOutcome {
        return when {
            local != null && remote == null -> uploadAnnualInternal(storage, key, local).toMergeOutcome()
            local == null && remote != null -> downloadAnnualInternal(storage, key, year, null, remote).toMergeOutcome()
            local != null && remote != null -> {
                if (strategy == MergeStrategy.OverwriteAll) {
                    return uploadAnnualInternal(storage, key, local, force = true).toMergeOutcome()
                }
                if (strategy == MergeStrategy.PreferRemote) {
                    return downloadAnnualInternal(storage, key, year, local, remote, force = true).toMergeOutcome()
                }
                if (strategy == MergeStrategy.SkipExisting) {
                    return MergeOutcome.Skipped
                }
                val remotePayload = when (val result = loadRemoteAnnual(storage, key, year, remote)) {
                    is RemoteResult.Ok -> result.value
                    is RemoteResult.Err -> {
                        markAnnualFailed(year)
                        return MergeOutcome.Failed(result, fatal = result.code == ErrorCode.AUTH)
                    }
                }
                when {
                    remotePayload.format == SyncPayloadFormat.LegacyPlaintext ->
                        uploadAnnualInternal(storage, key, local, force = true).toMergeOutcome()
                    remotePayload.summary.updatedAt.isAfter(local.updatedAt) ->
                        downloadAnnualInternal(storage, key, year, local, remote).toMergeOutcome()
                    local.updatedAt.isAfter(remotePayload.summary.updatedAt) ->
                        uploadAnnualInternal(storage, key, local).toMergeOutcome()
                    else -> MergeOutcome.Skipped
                }
            }
            else -> MergeOutcome.Skipped
        }
    }

    private suspend fun loadRemoteDiary(
        storage: RemoteStorage,
        key: SecretKey,
        date: LocalDate,
        remote: RemoteObject,
    ): RemoteResult<RemoteDiaryPayload> {
        val getResult = storage.get(remote.key)
        if (getResult is RemoteResult.Err) return getResult
        val decoded = runCatching { decodeDiaryPayload((getResult as RemoteResult.Ok).value, key) }.getOrNull()
            ?: return RemoteResult.Err(ErrorCode.AUTH, "同步密码错误或云端数据已损坏")
        if (decoded.entry.date != date) {
            return RemoteResult.Err(ErrorCode.UNKNOWN, "远端 diary 对象日期与文件名不一致")
        }
        return RemoteResult.Ok(RemoteDiaryPayload(decoded.entry, decoded.format))
    }

    private suspend fun loadRemoteAnnual(
        storage: RemoteStorage,
        key: SecretKey,
        year: Int,
        remote: RemoteObject,
    ): RemoteResult<RemoteAnnualPayload> {
        val getResult = storage.get(remote.key)
        if (getResult is RemoteResult.Err) return getResult
        val decoded = runCatching { decodeAnnualPayload((getResult as RemoteResult.Ok).value, key) }.getOrNull()
            ?: return RemoteResult.Err(ErrorCode.AUTH, "同步密码错误或云端数据已损坏")
        if (decoded.summary.year != year) {
            return RemoteResult.Err(ErrorCode.UNKNOWN, "远端 annual 对象年份与文件名不一致")
        }
        return RemoteResult.Ok(RemoteAnnualPayload(decoded.summary, decoded.format))
    }

    private fun loadPasswordContext(): PasswordContext? {
        // 优先用 passwordProvider（解锁后内存中的密码），保证关闭"记住密码"时仍可同步。
        val password = passwordProvider().takeUnless { it.isNullOrEmpty() }
            ?: secureCredentialStore.loadLocalPassword()
            ?: return null
        val salt = secureCredentialStore.loadOrCreateCloudCipherSalt()
        val key = CryptoBox.deriveKey(password, salt)
        return PasswordContext(password, salt, key)
    }

    /**
     * 重试本地已删除但云端删除失败的对象。成功删除一项就清掉对应记录，
     * 失败保留原记录等待下次同步。错误聚合到 [SyncSummary.errors]，但不会让整体 mergeAll/uploadAll 失败。
     */
    private suspend fun retryPendingDeletions(
        storage: RemoteStorage,
        base: SyncSummary,
    ): SyncSummary {
        val repo = pendingSyncDeletionRepository ?: return base
        val pending = runCatching { repo.getAll() }.getOrNull().orEmpty()
        if (pending.isEmpty()) return base
        emit(SyncStage.Deleting, 0, pending.size, null)
        var deleted = base.deleted
        var failed = base.failed
        val errors = base.errors.toMutableList()
        var progress = 0
        for (item in pending) {
            when (val r = storage.delete(item.objectKey)) {
                is RemoteResult.Ok -> {
                    runCatching { syncMetadataRepository.deleteMetadata(item.objectKey) }
                    when (item) {
                        is PendingSyncDeletion.Diary -> runCatching { repo.clearDiary(item.date) }
                        is PendingSyncDeletion.Annual -> runCatching { repo.clearAnnual(item.year) }
                    }
                    deleted++
                }
                is RemoteResult.Err -> {
                    failed++
                    errors += "delete/${item.objectKey}: ${r.message}"
                }
            }
            progress++
            emit(SyncStage.Deleting, progress, pending.size, null)
        }
        return base.copy(deleted = deleted, failed = failed, errors = errors)
    }

    private suspend fun markDiaryFailed(date: LocalDate) {
        runCatching { diaryRepository.setSyncState(date, SyncState.Failed) }
    }

    private suspend fun markAnnualFailed(year: Int) {
        runCatching { annualSummaryRepository.setSyncState(year, SyncState.Failed) }
    }

    private fun emit(stage: SyncStage, progress: Int, total: Int, message: String?) {
        _state.value = SyncEngineState(stage, progress, total, message)
    }

    private fun notConfigured(): RemoteResult<SyncSummary> {
        emit(SyncStage.Failed, 0, 0, "未配置云同步")
        return RemoteResult.Err(ErrorCode.AUTH, "未配置云同步")
    }

    private fun missingPassword(): RemoteResult<SyncSummary> {
        emit(SyncStage.Failed, 0, 0, "请先在设置页设置同步密码")
        return RemoteResult.Err(ErrorCode.AUTH, "请先在设置页设置同步密码")
    }

    private fun UploadOutcome.toMergeOutcome(): MergeOutcome = when (this) {
        UploadOutcome.Uploaded -> MergeOutcome.Uploaded
        UploadOutcome.Skipped -> MergeOutcome.Skipped
        UploadOutcome.Conflict -> MergeOutcome.Conflict
        is UploadOutcome.Failed -> MergeOutcome.Failed(error, fatal)
    }

    private fun DownloadOutcome.toMergeOutcome(): MergeOutcome = when (this) {
        DownloadOutcome.Downloaded -> MergeOutcome.Downloaded
        DownloadOutcome.Skipped -> MergeOutcome.Skipped
        is DownloadOutcome.Failed -> MergeOutcome.Failed(error, fatal)
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
