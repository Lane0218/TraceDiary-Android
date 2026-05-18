package com.tracediary.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * DefaultAnnualSummaryRepository 集成测试，对应 SPEC 6.1.4：
 * 验证年度总结的 idempotent 写入语义、删除与流式订阅。
 */
@RunWith(AndroidJUnit4::class)
class DefaultAnnualSummaryRepositoryTest {

    private lateinit var database: TraceDiaryDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: DefaultAnnualSummaryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TraceDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(Instant.parse("2026-05-17T08:00:00Z"))
        repository = DefaultAnnualSummaryRepository(database.annualSummaryDao(), clock)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveSummaryFirstCreatePendingAndEqualTimestamps() = runBlocking {
        val saved = repository.saveSummary(2025, "去年总结")

        assertEquals(2025, saved.year)
        assertEquals("去年总结", saved.content)
        assertEquals(SyncState.Pending, saved.syncState)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(clock.instant(), saved.createdAt)
    }

    @Test
    fun saveSummaryIdempotentWhenContentUnchanged() = runBlocking {
        val first = repository.saveSummary(2025, "稳定内容")

        clock.advanceTo(Instant.parse("2026-05-18T08:00:00Z"))
        val again = repository.saveSummary(2025, "稳定内容")

        assertEquals(first.createdAt, again.createdAt)
        assertEquals(first.updatedAt, again.updatedAt)
        assertEquals(first.syncState, again.syncState)
    }

    @Test
    fun saveSummaryAdvancesUpdatedAtAndResetsSyncState() = runBlocking {
        val first = repository.saveSummary(2025, "v1")

        val later = Instant.parse("2026-05-17T10:00:00Z")
        clock.advanceTo(later)
        val updated = repository.saveSummary(2025, "v2")

        assertEquals(first.createdAt, updated.createdAt)
        assertEquals(later, updated.updatedAt)
        assertTrue(updated.updatedAt.isAfter(first.updatedAt))
        assertEquals(SyncState.Pending, updated.syncState)
    }

    @Test
    fun deleteSummaryReturnsNullAfterRemoval() = runBlocking {
        repository.saveSummary(2025, "待删除")
        assertNotNull(repository.getSummary(2025))

        repository.deleteSummary(2025)
        assertNull(repository.getSummary(2025))
        assertNull(repository.observeSummary(2025).first())
    }

    @Test
    fun observeSummaryEmitsNewValueAfterSave() = runBlocking {
        assertNull(repository.observeSummary(2026).first())

        repository.saveSummary(2026, "今年总结草稿")
        val emitted = repository.observeSummary(2026).first()
        assertNotNull(emitted)
        assertEquals("今年总结草稿", emitted?.content)

        clock.advanceTo(Instant.parse("2026-06-01T08:00:00Z"))
        repository.saveSummary(2026, "今年总结终稿")
        val updated = repository.observeSummary(2026).first()
        assertEquals("今年总结终稿", updated?.content)
        assertTrue(updated!!.updatedAt.isAfter(emitted!!.updatedAt))
    }

    @Test
    fun observeSummariesOrderedByYearDesc() = runBlocking {
        repository.saveSummary(2024, "2024")
        repository.saveSummary(2026, "2026")
        repository.saveSummary(2025, "2025")

        val ordered = repository.observeSummaries().first().map { it.year }
        assertEquals(listOf(2026, 2025, 2024), ordered)
    }

    private class MutableClock(initial: Instant) : Clock() {
        @Volatile
        private var current: Instant = initial

        fun advanceTo(instant: Instant) {
            current = instant
        }

        override fun instant(): Instant = current
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
    }
}
