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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * DefaultDiaryRepository 集成测试，对应 SPEC 6.1.4：
 * 用真实 Room（in-memory）验证日记仓储的 idempotent 落库语义、搜索过滤与往年今日逻辑。
 */
@RunWith(AndroidJUnit4::class)
class DefaultDiaryRepositoryTest {

    private lateinit var database: TraceDiaryDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: DefaultDiaryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TraceDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(Instant.parse("2026-05-17T08:00:00Z"))
        repository = DefaultDiaryRepository(database.diaryEntryDao(), clock)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveEntry 首次创建时 syncState 为 Pending 且 createdAt 等于 updatedAt`() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        val saved = repository.saveEntry(date, "首次写入")

        assertEquals(SyncState.Pending, saved.syncState)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(clock.instant(), saved.createdAt)

        val readBack = repository.getEntry(date)
        assertEquals("首次写入", readBack?.content)
    }

    @Test
    fun `saveEntry 内容不变时 updatedAt 保持原值 idempotent`() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        val first = repository.saveEntry(date, "原始内容")

        clock.advanceTo(Instant.parse("2026-05-17T09:30:00Z"))
        val again = repository.saveEntry(date, "原始内容")

        assertEquals(first.createdAt, again.createdAt)
        assertEquals(first.updatedAt, again.updatedAt)
        // syncState 保留原值（仍为 Pending），不会因为重复保存被强行翻转
        assertEquals(first.syncState, again.syncState)
    }

    @Test
    fun `saveEntry 内容变化时 updatedAt 推进且 syncState 重新置为 Pending`() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        val first = repository.saveEntry(date, "v1")

        val later = Instant.parse("2026-05-17T10:00:00Z")
        clock.advanceTo(later)
        val updated = repository.saveEntry(date, "v2")

        assertEquals(first.createdAt, updated.createdAt)
        assertEquals(later, updated.updatedAt)
        assertTrue(updated.updatedAt.isAfter(first.updatedAt))
        assertEquals(SyncState.Pending, updated.syncState)
    }

    @Test
    fun `searchEntries 关键词为空且 recordedOnly=true 时返回空`() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        repository.saveEntry(date, "今天有内容")

        val results = repository.searchEntries(query = "", year = null, recordedOnly = true)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getSameMonthDayEntries 仅返回早于指定日期且月日匹配的条目`() = runBlocking {
        val target = LocalDate.of(2026, 5, 17)
        // 应被命中：往年同一月日
        repository.saveEntry(LocalDate.of(2025, 5, 17), "去年五一七")
        repository.saveEntry(LocalDate.of(2024, 5, 17), "前年五一七")
        // 不应命中：同月不同日
        repository.saveEntry(LocalDate.of(2025, 5, 18), "去年五一八")
        // 不应命中：不同月
        repository.saveEntry(LocalDate.of(2025, 6, 17), "去年六一七")
        // 不应命中：等于目标日期（要求严格早于）
        repository.saveEntry(target, "今天")

        val previous = repository.getSameMonthDayEntries(target)
        assertEquals(
            listOf(LocalDate.of(2025, 5, 17), LocalDate.of(2024, 5, 17)),
            previous.map { it.date },
        )
    }

    @Test
    fun `observeAllEntries 在 saveEntry 后能收到新值`() = runBlocking {
        // 初始为空
        assertTrue(repository.observeAllEntries().first().isEmpty())

        repository.saveEntry(LocalDate.of(2026, 5, 17), "记录 A")
        val afterFirst = repository.observeAllEntries().first()
        assertEquals(1, afterFirst.size)
        assertEquals("记录 A", afterFirst.first().content)

        clock.advanceTo(Instant.parse("2026-05-18T08:00:00Z"))
        repository.saveEntry(LocalDate.of(2026, 5, 18), "记录 B")
        val afterSecond = repository.observeAllEntries().first()
        assertEquals(2, afterSecond.size)
        assertEquals(
            listOf(LocalDate.of(2026, 5, 17), LocalDate.of(2026, 5, 18)),
            afterSecond.map { it.date },
        )
    }

    @Test
    fun `searchEntries 关键词命中且按 epochDay 倒序返回`() = runBlocking {
        repository.saveEntry(LocalDate.of(2026, 5, 17), "上午散步")
        repository.saveEntry(LocalDate.of(2026, 5, 18), "工作复盘")
        repository.saveEntry(LocalDate.of(2026, 5, 19), "傍晚散步")

        val hits = repository.searchEntries("散步", year = null, recordedOnly = false)
        assertEquals(
            listOf(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 17)),
            hits.map { it.date },
        )

        // 限定 2025 年应不返回任何结果
        assertFalse(repository.searchEntries("散步", year = 2025, recordedOnly = false).any())
    }

    @Test
    fun `getEntry 在没有记录时返回 null`() = runBlocking {
        assertNull(repository.getEntry(LocalDate.of(2026, 1, 1)))
    }

    /** 可推进时间的 Clock，便于验证 idempotent 与 updatedAt 行为。 */
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
