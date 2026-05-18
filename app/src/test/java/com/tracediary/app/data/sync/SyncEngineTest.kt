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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    ): SyncEngine = SyncEngine(
        storageProvider = { storage },
        diaryRepository = diaryRepo,
        annualSummaryRepository = annualRepo,
        syncMetadataRepository = metaRepo,
        clock = clock,
    )

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
            body = encodeDiary(entry.copy(content = "remote")),
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
            body = encodeDiary(remoteEntry),
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
            body = encodeDiary(remoteEntry),
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
            body = encodeDiary(remoteOnly),
            lastModified = remoteOnly.updatedAt,
        )
        storage.seed(
            key = SyncObjectKeys.diaryObjectKey(date3),
            body = encodeDiary(bothRemote),
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
            body = encodeDiary(remoteEntry),
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
            body = encodeDiary(remoteEntry),
            lastModified = baseInstant,
        )
        val engine = newEngine(storage, diaryRepo, annualRepo, metaRepo)

        val result = engine.mergeAll()

        assertTrue(result is RemoteResult.Ok)
        assertEquals(1, (result as RemoteResult.Ok).value.downloaded)
        assertEquals("新远端", diaryRepo.store[date]?.content)
        assertEquals(SyncState.Synced, diaryRepo.syncStates[date])
    }
}

// ---------- Fakes ----------

private class FakeRemoteStorage(
    private val forceConflictOnPut: Boolean = false,
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

    override suspend fun getMetadata(objectKey: String): SyncMetadata? = store[objectKey]

    override suspend fun saveMetadata(metadata: SyncMetadata) {
        store[metadata.objectKey] = metadata
    }

    override suspend fun deleteMetadata(objectKey: String) {
        store.remove(objectKey)
    }
}
