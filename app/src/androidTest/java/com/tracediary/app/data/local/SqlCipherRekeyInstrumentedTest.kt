package com.tracediary.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tracediary.app.data.security.CryptoBox
import com.tracediary.app.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SqlCipherRekeyInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "sqlcipher-rekey-test.db"

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun rekey_keepsDataReadableWithNewPasswordOnly() = runBlocking {
        val oldPassphrase = CryptoBox.deriveDatabasePassphrase("old-password")
        val newPassphrase = CryptoBox.deriveDatabasePassphrase("new-password")
        val date = LocalDate.of(2026, 5, 24)

        val database = openDatabase(oldPassphrase)
        try {
            database.diaryEntryDao().upsert(
                DiaryEntryEntity(
                    epochDay = date.toEpochDay(),
                    content = "改密后应能读回",
                    createdAtEpochMilli = 1_700_000_000_000L,
                    updatedAtEpochMilli = 1_700_000_001_000L,
                    syncState = SyncState.Pending,
                )
            )

            SqlCipherRekey.rekey(
                database.openHelper.writableDatabase,
                newPassphrase,
            )
        } finally {
            database.close()
        }

        assertThrows(Throwable::class.java) {
            val oldDatabase = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                context.getDatabasePath(dbName).path,
                oldPassphrase,
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null,
            )
            try {
                val cursor = oldDatabase.rawQuery("SELECT count(*) FROM diary_entries")
                try {
                    cursor.moveToFirst()
                } finally {
                    cursor.close()
                }
            } finally {
                oldDatabase.close()
            }
        }

        val newDatabase = openDatabase(newPassphrase)
        try {
            val saved = newDatabase.diaryEntryDao().getByDate(date.toEpochDay())
            assertNotNull(saved)
            assertEquals("改密后应能读回", saved?.content)
        } finally {
            newDatabase.close()
        }
    }

    private fun openDatabase(passphrase: ByteArray): TraceDiaryDatabase {
        return Room.databaseBuilder(context, TraceDiaryDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .allowMainThreadQueries()
            .build()
    }
}
