package com.tracediary.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.time.LocalDate

/**
 * DiaryEntryDao 集成测试，对应 SPEC 6.1.2：用真实 Room（in-memory）验证
 * upsert 唯一性、流式订阅、范围查询、模糊搜索、删除等关键路径。
 */
@RunWith(AndroidJUnit4::class)
class DiaryEntryDaoTest {

    private lateinit var database: TraceDiaryDatabase
    private lateinit var dao: DiaryEntryDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TraceDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.diaryEntryDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertSameEpochDayKeepsLatestOnly() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        val first = entity(date = date, content = "初版", createdAt = 1000L, updatedAt = 1000L)
        val second = entity(date = date, content = "覆盖版", createdAt = 1000L, updatedAt = 2000L)

        dao.upsert(first)
        dao.upsert(second)

        val between = dao.getBetween(date.toEpochDay(), date.toEpochDay())
        assertEquals(1, between.size)
        val saved = between.first()
        assertEquals("覆盖版", saved.content)
        assertEquals(2000L, saved.updatedAtEpochMilli)
    }

    @Test
    fun observeByDateReceivesUpsertAndDelete() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        // 初始无记录
        assertNull(dao.observeByDate(date.toEpochDay()).first())

        dao.upsert(entity(date = date, content = "新写入"))
        val afterInsert = dao.observeByDate(date.toEpochDay()).first()
        assertNotNull(afterInsert)
        assertEquals("新写入", afterInsert?.content)

        dao.upsert(entity(date = date, content = "再次更新", updatedAt = 5000L))
        val afterUpdate = dao.observeByDate(date.toEpochDay()).first()
        assertEquals("再次更新", afterUpdate?.content)
        assertEquals(5000L, afterUpdate?.updatedAtEpochMilli)

        dao.deleteByDate(date.toEpochDay())
        assertNull(dao.observeByDate(date.toEpochDay()).first())
    }

    @Test
    fun getBetweenInclusiveBoundsExcludesOutOfRange() = runBlocking {
        val d1 = LocalDate.of(2026, 5, 1)
        val d2 = LocalDate.of(2026, 5, 15)
        val d3 = LocalDate.of(2026, 5, 31)
        val d4 = LocalDate.of(2026, 6, 1)
        listOf(d1, d2, d3, d4).forEach { dao.upsert(entity(date = it, content = it.toString())) }

        val inRange = dao.getBetween(d1.toEpochDay(), d3.toEpochDay())
        assertEquals(listOf(d1, d2, d3), inRange.map { LocalDate.ofEpochDay(it.epochDay) })

        // 边界单点
        val singleDay = dao.getBetween(d2.toEpochDay(), d2.toEpochDay())
        assertEquals(listOf(d2), singleDay.map { LocalDate.ofEpochDay(it.epochDay) })
    }

    @Test
    fun searchFuzzyMatchesContent() = runBlocking {
        val d1 = LocalDate.of(2026, 5, 17)
        val d2 = LocalDate.of(2026, 5, 18)
        val d3 = LocalDate.of(2026, 5, 19)
        dao.upsert(entity(date = d1, content = "上午散步 下午阅读"))
        dao.upsert(entity(date = d2, content = "工作复盘"))
        dao.upsert(entity(date = d3, content = "傍晚再次散步"))

        val matched = dao.search("散步", null, null)
        // ORDER BY epochDay DESC，所以 d3 在前
        assertEquals(listOf(d3, d1), matched.map { LocalDate.ofEpochDay(it.epochDay) })

        val noMatch = dao.search("骑行", null, null)
        assertTrue(noMatch.isEmpty())

        // 限定年份范围（2025 年），应过滤掉 2026 年数据
        val rangeMiss = dao.search(
            "散步",
            LocalDate.of(2025, 1, 1).toEpochDay(),
            LocalDate.of(2025, 12, 31).toEpochDay(),
        )
        assertTrue(rangeMiss.isEmpty())
    }

    @Test
    fun deleteByDateRemovesEntry() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        dao.upsert(entity(date = date, content = "待删除"))
        assertNotNull(dao.getByDate(date.toEpochDay()))

        dao.deleteByDate(date.toEpochDay())
        assertNull(dao.getByDate(date.toEpochDay()))
    }

    private fun entity(
        date: LocalDate,
        content: String,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L,
        syncState: SyncState = SyncState.Pending,
    ): DiaryEntryEntity = DiaryEntryEntity(
        epochDay = date.toEpochDay(),
        content = content,
        createdAtEpochMilli = createdAt,
        updatedAtEpochMilli = updatedAt,
        syncState = syncState,
    )
}
