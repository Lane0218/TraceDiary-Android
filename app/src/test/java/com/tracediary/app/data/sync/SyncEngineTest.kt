package com.tracediary.app.data.sync

import com.tracediary.app.domain.model.AnnualSummary
import com.tracediary.app.domain.model.DiaryEntry
import com.tracediary.app.domain.model.SyncMetadata
import com.tracediary.app.domain.model.SyncState
import com.tracediary.app.data.security.CryptoBox
import com.tracediary.app.domain.repository.AnnualSummaryRepository
import com.tracediary.app.domain.repository.DiaryRepository
import com.tracediary.app.domain.repository.PendingSyncDeletion
import com.tracediary.app.domain.repository.PendingSyncDeletionRepository
import com.tracediary.app.domain.repository.SyncMetadataRepository
import com.tracediary.app.domain.usecase.MergeStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class SyncEngineTest {

    private val baseInstant: Instant = Instant.parse("2025-05-18T10:00:00Z")
    private val clock: Clock = Clock.fixed(baseInstant, ZoneOffset.UTC)
    private val testPassword = "sync-password"
    private val testKey = CryptoBox.deriveCloudKey(testPassword)

    private fun newEntry(
        date: LocalDate,
        content: String = "本地内容 $date",
        createdAt: Instant = baseInstant.minusSeconds(3600),
        updatedAt: Instant = baseInstant.minusSeconds(60),
        syncState: SyncState = SyncState.LocalOnly,
    ): DiaryEntry = DiaryEntry(date, content, createdAt, updatedAt, syncState)

    private fun newSummary(
        year: Int,
        content: String = "$year 年度总结",
        createdAt: Instant = baseInstant.minusSeconds(7200),
        updatedAt: Instant = baseInstant.minusSeconds(120),
        syncState: SyncState = SyncState.LocalOnly,
    ): AnnualSummary = AnnualSummary(year, content, createdAt, updatedAt, syncState)

    private fun newEngine(
        storage: RemoteStorage?,
        diaryRepo: FakeDiaryRepository,
        annualRepo: FakeAnnualSummaryRepository,
        metaRepo: FakeSyncMetadataRepository,
        credentialStore: FakeCredentialStore = FakeCredentialStore(testPassword),
        pendingRepo: FakePendingSyncDeletionRepository = FakePendingSyncDeletionRepository(),
    ): SyncEngine = SyncEngine(
        storageProvider = { storage },
        diaryRepository = diaryRepo,
        annualSummaryRepository = annualRepo,
        syncMetadataRepository = metaRepo,
        pendingSyncDeletionRepository = pendingRepo,
        passwordProvider = { credentialStore.loadLocalPassword() },
        clock = clock,
    )

    private fun legacyDiary(entry: DiaryEntry): ByteArray =
        syncJson.encodeToString(entry.toSyncDto()).toByteArray(Charsets.UTF_8)

    @Test
    fun uploadAll_storageNotConfigured_returnsAuthError() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val engine = newEngine(null, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Err)
        assertEquals(ErrorCode.AUTH, (result as RemoteResult.Err).code)
    }

    @Test
    fun uploadAll_emptyLocal_returnsZeroSummary() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(0, summary.uploaded)
        assertEquals(0, summary.downloaded)
        assertEquals(0, summary.skipped)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.conflicts)
    }

    @Test
    fun uploadAll_missingPassword_returnsAuthError() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val engine = newEngine(
            storage,
            diaryRepo,
            annualRepo,
            metaRepo,
            credentialStore = FakeCredentialStore(null),
        )

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Err)
        assertEquals(ErrorCode.AUTH, (result as RemoteResult.Err).code)
        assertEquals("请先输入应用密码", result.message)
    }

    @Test
    fun uploadAll_uploadsDiaryAndUpdatesSyncState() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date)
        val summary = newSummary(2025)
        val diaryRepo = FakeDiaryRepository(listOf(entry))
        val annualRepo = FakeAnnualSummaryRepository(listOf(summary))
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Ok)
        val sum = (result as RemoteResult.Ok).value
        assertEquals(2, sum.uploaded)
        assertEquals(2, storage.objects.size)
        assertNotNull(storage.objects[SyncObjectKeys.diaryObjectKey(date)])
        assertNotNull(storage.objects[SyncObjectKeys.annualObjectKey(2025)])
        val uploadedBody = storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.body.toString(Charsets.UTF_8)
        assertTrue(uploadedBody.contains("\"cipher\""))
        assertFalse(uploadedBody.contains(entry.content))
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
        assertEquals(SyncState.Synced, annualRepo.syncStates[2025])
        assertNotNull(metaRepo.store[SyncObjectKeys.diaryObjectKey(date)])
        assertNotNull(metaRepo.store[SyncObjectKeys.annualObjectKey(2025)])
    }

    @Test
    fun uploadAll_skipsWhenRemoteNewer() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date, updatedAt = baseInstant.minusSeconds(600))
        val diaryRepo = FakeDiaryRepository(listOf(entry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        // 远端已存在更新的对象
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(entry.copy(content = "remote"), testKey),
            lastModified = baseInstant, // 比本地 updatedAt 新
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.skipped)
        assertEquals(0, result.value.uploaded)
    }

    @Test
    fun uploadAll_conflictMarksEntryAsConflict() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date)
        val diaryRepo = FakeDiaryRepository(listOf(entry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage(forceConflictOnPut = true)
        // 远端无对象（head 返回 NOT_FOUND），但 put 强制返回 CONFLICT
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.conflicts)
        assertEquals(SyncState.Conflict, diaryRepo.syncStates[date])
    }

    @Test
    fun downloadAll_writesEntriesAndMarksSynced() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val remoteEntry = newEntry(date, content = "远端内容", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.downloadAll()

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(1, summary.downloaded)
        val saved = diaryRepo.store[date]
        assertNotNull(saved)
        assertEquals("远端内容", saved!!.content)
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
    }

    @Test
    fun downloadAll_skipsWhenLocalNewerThanRemote() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "本地新", updatedAt = baseInstant)
        val remoteEntry = newEntry(date, content = "远端旧", updatedAt = baseInstant.minusSeconds(600))
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = baseInstant.minusSeconds(600),
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.downloadAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.skipped)
        assertEquals(0, result.value.downloaded)
        // 本地未被覆盖
        assertEquals("本地新", diaryRepo.store[date]?.content)
    }

    @Test
    fun mergeAll_uploadsLocalOnly_downloadsRemoteOnly_skipsEqual() = runTest {
        val date1 = LocalDate.of(2025, 5, 1) // 仅本地
        val date2 = LocalDate.of(2025, 5, 2) // 仅远端
        val date3 = LocalDate.of(2025, 5, 3) // 双方时间相等
        val sameInstant = baseInstant.minusSeconds(500)

        val localOnly = newEntry(date1, updatedAt = baseInstant.minusSeconds(60))
        val bothLocal = newEntry(date3, content = "both-local", updatedAt = sameInstant)
        val bothRemote = newEntry(date3, content = "both-remote", updatedAt = sameInstant)
        val remoteOnly = newEntry(date2, content = "remote-only", updatedAt = baseInstant.minusSeconds(30))

        val diaryRepo = FakeDiaryRepository(listOf(localOnly, bothLocal))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date2),
            body = encodeDiary(remoteOnly, testKey),
            lastModified = remoteOnly.updatedAt,
        )
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date3),
            body = encodeDiary(bothRemote, testKey),
            lastModified = sameInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(1, summary.uploaded)
        assertEquals(1, summary.downloaded)
        assertEquals(1, summary.skipped)
        // date1 已上传到远端
        assertNotNull(storage.objects[SyncObjectKeys.diaryObjectKey(date1)])
        // date2 已下载到本地
        assertEquals("remote-only", diaryRepo.store[date2]?.content)
        // date3 双方时间相等：未变
        assertEquals("both-local", diaryRepo.store[date3]?.content)
    }

    @Test
    fun mergeAll_localNewerOverwritesRemote() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "新本地", updatedAt = baseInstant)
        val remoteEntry = newEntry(date, content = "旧远端", updatedAt = baseInstant.minusSeconds(600))
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = baseInstant.minusSeconds(600),
        )
        val originalEtag = storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.etag
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.uploaded)
        // 远端被覆盖：etag 变化
        val newEtag = storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.etag
        assertTrue(newEtag != originalEtag)
        // 校验上传时使用了 If-Match
        assertTrue(storage.lastIfMatchEtags.contains(originalEtag))
    }

    @Test
    fun mergeAll_remoteNewerOverwritesLocal() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "旧本地", updatedAt = baseInstant.minusSeconds(600))
        val remoteEntry = newEntry(date, content = "新远端", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.downloaded)
        assertEquals("新远端", diaryRepo.store[date]?.content)
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
    }

    @Test
    fun mergeAll_keepNewer_remoteNewerAnnualOverwritesLocal() = runTest {
        val localSummary = newSummary(2025, content = "旧本地年度", updatedAt = baseInstant.minusSeconds(600))
        val remoteSummary = newSummary(2025, content = "新远端年度", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository(listOf(localSummary))
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.annualObjectKey(2025),
            body = encodeAnnual(remoteSummary, testKey),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll(MergeStrategy.KeepNewer)

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.downloaded)
        assertEquals("新远端年度", annualRepo.store[2025]?.content)
        assertEquals(SyncState.Synced, annualRepo.syncStates[2025])
    }

    @Test
    fun mergeAll_overwriteAll_localWinsForDiaryAndAnnualEvenWhenRemoteNewer() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "本地日记", updatedAt = baseInstant.minusSeconds(600))
        val remoteEntry = newEntry(date, content = "远端日记", updatedAt = baseInstant)
        val localSummary = newSummary(2025, content = "本地年度", updatedAt = baseInstant.minusSeconds(600))
        val remoteSummary = newSummary(2025, content = "远端年度", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository(listOf(localSummary))
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = baseInstant,
        )
        storage.seed(
            key = SyncObjectKeys.annualObjectKey(2025),
            body = encodeAnnual(remoteSummary, testKey),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll(MergeStrategy.OverwriteAll)

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(2, summary.uploaded)
        assertEquals("本地日记", decodeDiary(storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.body, testKey).content)
        assertEquals("本地年度", decodeAnnual(storage.objects[SyncObjectKeys.annualObjectKey(2025)]!!.body, testKey).content)
    }

    @Test
    fun mergeAll_preferRemote_remoteWinsForDiaryAndAnnualEvenWhenLocalNewer() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "本地日记", updatedAt = baseInstant)
        val remoteEntry = newEntry(date, content = "远端日记", updatedAt = baseInstant.minusSeconds(600))
        val localSummary = newSummary(2025, content = "本地年度", updatedAt = baseInstant)
        val remoteSummary = newSummary(2025, content = "远端年度", updatedAt = baseInstant.minusSeconds(600))
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository(listOf(localSummary))
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(remoteEntry, testKey),
            lastModified = remoteEntry.updatedAt,
        )
        storage.seed(
            key = SyncObjectKeys.annualObjectKey(2025),
            body = encodeAnnual(remoteSummary, testKey),
            lastModified = remoteSummary.updatedAt,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll(MergeStrategy.PreferRemote)

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(2, summary.downloaded)
        assertEquals("远端日记", diaryRepo.store[date]?.content)
        assertEquals("远端年度", annualRepo.store[2025]?.content)
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
        assertEquals(SyncState.Synced, annualRepo.syncStates[2025])
    }

    @Test
    fun mergeAll_skipExisting_skipsConflictsButSyncsMissingItems() = runTest {
        val localOnlyDate = LocalDate.of(2025, 5, 1)
        val remoteOnlyDate = LocalDate.of(2025, 5, 2)
        val bothDate = LocalDate.of(2025, 5, 3)
        val localOnly = newEntry(localOnlyDate, content = "本地独有")
        val bothLocal = newEntry(bothDate, content = "本地冲突", updatedAt = baseInstant.minusSeconds(600))
        val bothRemote = newEntry(bothDate, content = "远端冲突", updatedAt = baseInstant)
        val remoteOnly = newEntry(remoteOnlyDate, content = "远端独有")
        val bothLocalSummary = newSummary(2025, content = "本地年度冲突", updatedAt = baseInstant.minusSeconds(600))
        val bothRemoteSummary = newSummary(2025, content = "远端年度冲突", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository(listOf(localOnly, bothLocal))
        val annualRepo = FakeAnnualSummaryRepository(listOf(bothLocalSummary))
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(remoteOnlyDate),
            body = encodeDiary(remoteOnly, testKey),
            lastModified = remoteOnly.updatedAt,
        )
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(bothDate),
            body = encodeDiary(bothRemote, testKey),
            lastModified = bothRemote.updatedAt,
        )
        storage.seed(
            key = SyncObjectKeys.annualObjectKey(2025),
            body = encodeAnnual(bothRemoteSummary, testKey),
            lastModified = bothRemoteSummary.updatedAt,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll(MergeStrategy.SkipExisting)

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(1, summary.uploaded)
        assertEquals(1, summary.downloaded)
        assertEquals(2, summary.skipped)
        assertNotNull(storage.objects[SyncObjectKeys.diaryObjectKey(localOnlyDate)])
        assertEquals("远端独有", diaryRepo.store[remoteOnlyDate]?.content)
        assertEquals("本地冲突", diaryRepo.store[bothDate]?.content)
        assertEquals("本地年度冲突", annualRepo.store[2025]?.content)
        assertEquals("远端年度冲突", decodeAnnual(storage.objects[SyncObjectKeys.annualObjectKey(2025)]!!.body, testKey).content)
    }

    @Test
    fun mergeAll_legacyRemoteWithLocalEntry_returnsAuthError() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val localEntry = newEntry(date, content = "本地内容", updatedAt = baseInstant.minusSeconds(600))
        val remoteEntry = newEntry(date, content = "旧明文远端", updatedAt = baseInstant)
        val diaryRepo = FakeDiaryRepository(listOf(localEntry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = legacyDiary(remoteEntry),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Err)
        assertEquals(ErrorCode.AUTH, (result as RemoteResult.Err).code)
    }

    @Test
    fun uploadDiary_singleItem_updatesSyncState() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date)
        val diaryRepo = FakeDiaryRepository(listOf(entry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadDiary(date)

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.uploaded)
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
    }

    @Test
    fun uploadDiary_missingLocal_returnsNotFound() = runTest {
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.uploadDiary(LocalDate.of(2025, 5, 18))

        assertTrue(result is RemoteResult.Err)
        assertEquals(ErrorCode.NOT_FOUND, (result as RemoteResult.Err).code)
    }

    @Test
    fun rotateSyncPassword_success_reencryptsRemoteObjects() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date, content = "远端内容")
        val summary = newSummary(2025)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(SyncObjectKeys.diaryObjectKey(date), encodeDiary(entry, testKey), baseInstant)
        storage.seed(SyncObjectKeys.annualObjectKey(2025), encodeAnnual(summary, testKey), baseInstant)
        val credentialStore = FakeCredentialStore(testPassword)
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, credentialStore)

        val result = engine.rotateSyncPassword(testPassword, "new-password")

        assertTrue(result is RemoteResult.Ok)
        assertEquals(testPassword, credentialStore.loadLocalPassword())
        val newKey = CryptoBox.deriveCloudKey("new-password")
        assertEquals("远端内容", decodeDiary(storage.objects[SyncObjectKeys.diaryObjectKey(date)]!!.body, newKey).content)
        assertEquals("2025 年度总结", decodeAnnual(storage.objects[SyncObjectKeys.annualObjectKey(2025)]!!.body, newKey).content)
    }

    @Test
    fun rotateSyncPassword_decodeFailure_keepsPassword() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        storage.seed(SyncObjectKeys.diaryObjectKey(date), """{"v":1,"date":"2025-05-18","updatedAt":"2025-05-18T10:00:00Z","cipher":"bad"}""".toByteArray(), baseInstant)
        val credentialStore = FakeCredentialStore(testPassword)
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, credentialStore)

        val result = engine.rotateSyncPassword(testPassword, "new-password")

        assertTrue(result is RemoteResult.Err)
        assertEquals(testPassword, credentialStore.loadLocalPassword())
    }

    @Test
    fun deleteDiary_success_removesRemoteAndMetadata() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val entry = newEntry(date)
        val diaryRepo = FakeDiaryRepository(listOf(entry))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository().also {
            it.store[SyncObjectKeys.diaryObjectKey(date)] = SyncMetadata(
                objectKey = SyncObjectKeys.diaryObjectKey(date),
                localUpdatedAt = baseInstant,
                remoteUpdatedAt = baseInstant,
                lastSyncedAt = baseInstant,
                checksum = null,
            )
        }
        val storage = FakeRemoteStorage()
        storage.seed(SyncObjectKeys.diaryObjectKey(date), encodeDiary(entry, testKey), baseInstant)
        val pendingRepo = FakePendingSyncDeletionRepository()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, pendingRepo = pendingRepo)

        val result = engine.deleteDiary(date)

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.deleted)
        // 远端对象 + 元数据均已清理
        assertNull(storage.objects[SyncObjectKeys.diaryObjectKey(date)])
        assertNull(metaRepo.store[SyncObjectKeys.diaryObjectKey(date)])
        // 待重试集合空
        assertTrue(pendingRepo.store.isEmpty())
    }

    @Test
    fun deleteDiary_failure_recordsPendingForRetry() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage(forceDeleteError = true)
        val pendingRepo = FakePendingSyncDeletionRepository()
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, pendingRepo = pendingRepo)

        val result = engine.deleteDiary(date)

        assertTrue(result is RemoteResult.Err)
        assertEquals(1, pendingRepo.store.size)
        val pending = pendingRepo.store.values.first()
        assertTrue(pending is PendingSyncDeletion.Diary)
        assertEquals(SyncObjectKeys.diaryObjectKey(date), pending.objectKey)
    }

    @Test
    fun mergeAll_retriesPendingDeletionsAndClearsThemOnSuccess() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        val diaryRepo = FakeDiaryRepository()
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        // 远端仍残留对象，本地已无对应条目 → 此前删除失败的状态。
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date),
            body = encodeDiary(newEntry(date), testKey),
            lastModified = baseInstant,
        )
        val pendingRepo = FakePendingSyncDeletionRepository().also {
            it.markDiaryPending(date)
        }
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, pendingRepo = pendingRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        assertEquals(1, summary.deleted)
        assertNull(storage.objects[SyncObjectKeys.diaryObjectKey(date)])
        assertTrue(pendingRepo.store.isEmpty())
    }

    @Test
    fun uploadAll_skipsLocalEntriesPendingDeletion() = runTest {
        val date = LocalDate.of(2025, 5, 18)
        // 本地仍能查到该条目（边界场景：业务层先入队 pending 再删除本地条目，反序情况）。
        val diaryRepo = FakeDiaryRepository(listOf(newEntry(date)))
        val annualRepo = FakeAnnualSummaryRepository()
        val metaRepo = FakeSyncMetadataRepository()
        val storage = FakeRemoteStorage()
        val pendingRepo = FakePendingSyncDeletionRepository().also {
            it.markDiaryPending(date)
        }
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo, pendingRepo = pendingRepo)

        val result = engine.uploadAll()

        assertTrue(result is RemoteResult.Ok)
        val summary = (result as RemoteResult.Ok).value
        // 没有重新上传该日期；retryPendingDeletions 也会尝试删除（远端本来就没有对象，delete 返回成功）
        assertEquals(0, summary.uploaded)
        assertEquals(1, summary.deleted)
        // 远端不存在该对象
        assertNull(storage.objects[SyncObjectKeys.diaryObjectKey(date)])
        assertTrue(pendingRepo.store.isEmpty())
    }
}

// ---------- Fakes ----------

private class FakeRemoteStorage(
    private val forceConflictOnPut: Boolean = false,
    var forceDeleteError: Boolean = false,
) : RemoteStorage {

    data class StoredObject(
        val key: String,
        val body: ByteArray,
        val etag: String,
        val lastModified: Instant?,
    )

    val objects: MutableMap<String, StoredObject> = LinkedHashMap()
    private var etagSeq: Int = 0
    val lastIfMatchEtags: MutableList<String?> = mutableListOf()

    fun seed(key: String, body: ByteArray, lastModified: Instant?) {
        etagSeq++
        objects[key] = StoredObject(key, body, "etag-$etagSeq", lastModified)
    }

    override suspend fun put(
        key: String,
        body: ByteArray,
        contentType: String,
        ifMatchEtag: String?,
    ): RemoteResult<RemoteObject> {
        lastIfMatchEtags.add(ifMatchEtag)
        if (forceConflictOnPut) {
            return RemoteResult.Err(ErrorCode.CONFLICT, "forced conflict")
        }
        val current = objects[key]
        if (ifMatchEtag != null && current != null && current.etag != ifMatchEtag) {
            return RemoteResult.Err(ErrorCode.CONFLICT, "etag mismatch")
        }
        etagSeq++
        val newEtag = "etag-$etagSeq"
        val now = Instant.parse("2025-05-18T10:00:00Z")
        val stored = StoredObject(key, body, newEtag, now)
        objects[key] = stored
        return RemoteResult.Ok(RemoteObject(key, newEtag, now, body.size.toLong()))
    }

    override suspend fun get(key: String): RemoteResult<ByteArray> {
        val obj = objects[key] ?: return RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")
        return RemoteResult.Ok(obj.body)
    }

    override suspend fun head(key: String): RemoteResult<RemoteObject> {
        val obj = objects[key] ?: return RemoteResult.Err(ErrorCode.NOT_FOUND, "missing")
        return RemoteResult.Ok(RemoteObject(obj.key, obj.etag, obj.lastModified, obj.body.size.toLong()))
    }

    override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> {
        val items = objects.values
            .filter { it.key.startsWith(prefix) }
            .map { RemoteObject(it.key, it.etag, it.lastModified, it.body.size.toLong()) }
        return RemoteResult.Ok(items)
    }

    override suspend fun delete(key: String): RemoteResult<Unit> {
        if (forceDeleteError) {
            return RemoteResult.Err(ErrorCode.NETWORK, "forced delete error")
        }
        objects.remove(key)
        return RemoteResult.Ok(Unit)
    }

    override suspend fun ping(): RemoteResult<Unit> = RemoteResult.Ok(Unit)
}

private class FakeDiaryRepository(
    initial: List<DiaryEntry> = emptyList(),
) : DiaryRepository {

    val store: MutableMap<LocalDate, DiaryEntry> = LinkedHashMap<LocalDate, DiaryEntry>().apply {
        initial.forEach { put(it.date, it) }
    }
    val syncStates: MutableMap<LocalDate, SyncState> = LinkedHashMap()
    private val all: MutableStateFlow<List<DiaryEntry>> = MutableStateFlow(store.values.toList())

    private fun publish() {
        all.value = store.values.toList()
    }

    override fun observeEntry(date: LocalDate): Flow<DiaryEntry?> = flowOf(store[date])

    override fun observeEntriesInMonth(month: YearMonth): Flow<List<DiaryEntry>> =
        flowOf(store.values.filter { YearMonth.from(it.date) == month })

    override fun observeRecordedDates(): Flow<List<LocalDate>> =
        flowOf(store.keys.toList())

    override fun observeAllEntries(): Flow<List<DiaryEntry>> = all.asStateFlow()

    override suspend fun getEntry(date: LocalDate): DiaryEntry? = store[date]

    override suspend fun getEntriesBetween(
        start: LocalDate,
        endInclusive: LocalDate,
    ): List<DiaryEntry> =
        store.values.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }

    override suspend fun saveEntry(date: LocalDate, content: String): DiaryEntry {
        val now = Instant.parse("2025-05-18T10:00:00Z")
        val existing = store[date]
        val entry = DiaryEntry(
            date = date,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            syncState = SyncState.LocalOnly,
        )
        store[date] = entry
        publish()
        return entry
    }

    override suspend fun deleteEntry(date: LocalDate) {
        store.remove(date)
        syncStates.remove(date)
        publish()
    }

    override suspend fun searchEntries(
        query: String,
        year: Int?,
        recordedOnly: Boolean,
    ): List<DiaryEntry> = emptyList()

    override suspend fun getSameMonthDayEntries(date: LocalDate): List<DiaryEntry> = emptyList()

    override suspend fun setSyncState(date: LocalDate, state: SyncState) {
        syncStates[date] = state
        store[date]?.let { store[date] = it.copy(syncState = state) }
        publish()
    }
}

private class FakeAnnualSummaryRepository(
    initial: List<AnnualSummary> = emptyList(),
) : AnnualSummaryRepository {

    val store: MutableMap<Int, AnnualSummary> = LinkedHashMap<Int, AnnualSummary>().apply {
        initial.forEach { put(it.year, it) }
    }
    val syncStates: MutableMap<Int, SyncState> = LinkedHashMap()
    private val all: MutableStateFlow<List<AnnualSummary>> = MutableStateFlow(store.values.toList())

    private fun publish() {
        all.value = store.values.toList()
    }

    override fun observeSummary(year: Int): Flow<AnnualSummary?> = flowOf(store[year])

    override fun observeSummaries(): Flow<List<AnnualSummary>> = all.asStateFlow()

    override suspend fun getSummary(year: Int): AnnualSummary? = store[year]

    override suspend fun saveSummary(year: Int, content: String): AnnualSummary {
        val now = Instant.parse("2025-05-18T10:00:00Z")
        val existing = store[year]
        val summary = AnnualSummary(
            year = year,
            content = content,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            syncState = SyncState.LocalOnly,
        )
        store[year] = summary
        publish()
        return summary
    }

    override suspend fun deleteSummary(year: Int) {
        store.remove(year)
        syncStates.remove(year)
        publish()
    }

    override suspend fun setSyncState(year: Int, state: SyncState) {
        syncStates[year] = state
        store[year]?.let { store[year] = it.copy(syncState = state) }
        publish()
    }
}

private class FakeSyncMetadataRepository : SyncMetadataRepository {

    val store: MutableMap<String, SyncMetadata> = LinkedHashMap()

    override fun observeMetadata(objectKey: String): Flow<SyncMetadata?> =
        flowOf(store[objectKey])

    override fun observeLatestLastSyncedAt(): Flow<Instant?> =
        flowOf(store.values.mapNotNull { it.lastSyncedAt }.maxOrNull())

    override suspend fun getMetadata(objectKey: String): SyncMetadata? = store[objectKey]

    override suspend fun saveMetadata(metadata: SyncMetadata) {
        store[metadata.objectKey] = metadata
    }

    override suspend fun deleteMetadata(objectKey: String) {
        store.remove(objectKey)
    }
}

private class FakeCredentialStore(
    private var password: String?,
) {
    fun saveLocalPassword(value: String?) {
        password = value?.takeIf { it.isNotEmpty() }
    }

    fun loadLocalPassword(): String? = password
}

private class FakePendingSyncDeletionRepository : PendingSyncDeletionRepository {
    val store: MutableMap<String, PendingSyncDeletion> = LinkedHashMap()
    private val flow = MutableStateFlow<List<PendingSyncDeletion>>(emptyList())

    private fun publish() {
        flow.value = store.values.toList()
    }

    override fun observeAll(): Flow<List<PendingSyncDeletion>> = flow.asStateFlow()

    override suspend fun getAll(): List<PendingSyncDeletion> = store.values.toList()

    override suspend fun markDiaryPending(date: java.time.LocalDate) {
        val key = SyncObjectKeys.diaryObjectKey(date)
        store[key] = PendingSyncDeletion.Diary(objectKey = key, date = date)
        publish()
    }

    override suspend fun markAnnualPending(year: Int) {
        val key = SyncObjectKeys.annualObjectKey(year)
        store[key] = PendingSyncDeletion.Annual(objectKey = key, year = year)
        publish()
    }

    override suspend fun clearDiary(date: java.time.LocalDate) {
        store.remove(SyncObjectKeys.diaryObjectKey(date))
        publish()
    }

    override suspend fun clearAnnual(year: Int) {
        store.remove(SyncObjectKeys.annualObjectKey(year))
        publish()
    }
}
