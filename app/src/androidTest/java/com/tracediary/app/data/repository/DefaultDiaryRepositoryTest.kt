package com.tracediary.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tracediary.app.data.local.TraceDiaryDatabase
import com.tracediary.app.data.settings.DataStoreUserPreferencesRepository
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * DefaultDiaryRepository 集成测试，对应 SPEC 6.1.2：
 * 用真实 Room（in-memory）验证日记仓储的 idempotent 落库、时间戳与流式读取语义。
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
    fun saveEntryFirstCreatePendingAndEqualTimestamps() = runBlocking {
        val date = LocalDate.of(2026, 5, 17)
        val saved = repository.saveEntry(date, "首次写入")

        assertEquals(SyncState.Pending, saved.syncState)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(clock.instant(), saved.createdAt)

        val readBack = repository.getEntry(date)
        assertEquals("首次写入", readBack?.content)
    }

    @Test
    fun saveEntryIdempotentWhenContentUnchanged() = runBlocking {
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
    fun saveEntryAdvancesUpdatedAtAndResetsSyncState() = runBlocking {
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
    fun observeAllEntriesEmitsAfterSave() = runBlocking {
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
    fun getEntryReturnsNullWhenAbsent() = runBlocking {
        assertNull(repository.getEntry(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun clearingRemindedEntryResetsDuplicateMarker() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = DataStoreUserPreferencesRepository(testContext)
        val previousReminderDate = preferences.preferences.first().lastDailyReminderDate
        val date = LocalDate.of(2026, 5, 17)
        val repositoryWithReminder = DefaultDiaryRepository(
            database.diaryEntryDao(),
            clock,
            reminderPreferences = preferences,
        )
        try {
            preferences.setLastDailyReminderDate(date.toString())
            repositoryWithReminder.saveEntry(date, "已写日记")
            repositoryWithReminder.deleteEntry(date)
            assertNull(preferences.preferences.first().lastDailyReminderDate)

            preferences.setLastDailyReminderDate(date.toString())
            repositoryWithReminder.saveEntry(date, "   ")
            assertNull(preferences.preferences.first().lastDailyReminderDate)

            preferences.setLastDailyReminderDate(date.minusDays(1).toString())
            repositoryWithReminder.deleteEntry(date)
            assertEquals(date.minusDays(1).toString(), preferences.preferences.first().lastDailyReminderDate)
        } finally {
            preferences.setLastDailyReminderDate(previousReminderDate)
        }
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
