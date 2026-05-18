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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * AnnualSummaryDao 集成测试，对应 SPEC 6.1.2：覆盖年度唯一性约束、observe/getByYear 与 deleteByYear。
 */
@RunWith(AndroidJUnit4::class)
class AnnualSummaryDaoTest {

    private lateinit var database: TraceDiaryDatabase
    private lateinit var dao: AnnualSummaryDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TraceDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.annualSummaryDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertSameYearKeepsLatestOnly() = runBlocking {
        dao.upsert(summary(2025, "初稿", updatedAt = 1000L))
        dao.upsert(summary(2025, "终稿", updatedAt = 2000L))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("终稿", all.first().content)
        assertEquals(2000L, all.first().updatedAtEpochMilli)
    }

    @Test
    fun observeByYearReceivesUpsertAndDelete() = runBlocking {
        assertNull(dao.observeByYear(2026).first())

        dao.upsert(summary(2026, "进行中"))
        val afterInsert = dao.observeByYear(2026).first()
        assertNotNull(afterInsert)
        assertEquals("进行中", afterInsert?.content)

        dao.deleteByYear(2026)
        assertNull(dao.observeByYear(2026).first())
    }

    @Test
    fun getByYearReturnsNullWhenAbsentValueWhenPresent() = runBlocking {
        assertNull(dao.getByYear(2024))
        dao.upsert(summary(2024, "去年总结"))
        assertEquals("去年总结", dao.getByYear(2024)?.content)
    }

    @Test
    fun deleteByYearOnlyRemovesTargetYear() = runBlocking {
        dao.upsert(summary(2024, "2024"))
        dao.upsert(summary(2025, "2025"))
        dao.upsert(summary(2026, "2026"))

        dao.deleteByYear(2025)

        assertNull(dao.getByYear(2025))
        assertEquals("2024", dao.getByYear(2024)?.content)
        assertEquals("2026", dao.getByYear(2026)?.content)
        // observeAll 顺序按 year DESC
        val ordered = dao.observeAll().first().map(AnnualSummaryEntity::year)
        assertEquals(listOf(2026, 2024), ordered)
    }

    private fun summary(
        year: Int,
        content: String,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_000L,
        syncState: SyncState = SyncState.Pending,
    ): AnnualSummaryEntity = AnnualSummaryEntity(
        year = year,
        content = content,
        createdAtEpochMilli = createdAt,
        updatedAtEpochMilli = updatedAt,
        syncState = syncState,
    )
}
