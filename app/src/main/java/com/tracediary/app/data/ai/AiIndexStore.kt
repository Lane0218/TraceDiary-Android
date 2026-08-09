package com.tracediary.app.data.ai

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class AiIndexStore(
    private val context: Context,
    private val passphraseProvider: () -> ByteArray?,
) {
    private val mutex = Mutex()
    private var database: AiIndexDatabase? = null

    suspend fun <T> read(block: suspend (AiIndexDao) -> T): T = mutex.withLock {
        block(open().aiIndexDao())
    }

    suspend fun <T> write(block: suspend (AiIndexDao) -> T): T = mutex.withLock {
        block(open().aiIndexDao())
    }

    suspend fun close() = mutex.withLock {
        database?.close()
        database = null
    }

    suspend fun delete() = mutex.withLock {
        database?.close()
        database = null
        check(context.deleteDatabase(DATABASE_NAME) || !context.getDatabasePath(DATABASE_NAME).exists()) {
            "无法删除 AI 索引"
        }
    }

    private fun open(): AiIndexDatabase {
        database?.let { return it }
        val passphrase = passphraseProvider() ?: error("应用已锁定")
        return Room.databaseBuilder(context, AiIndexDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .fallbackToDestructiveMigration(true)
            .build()
            .also { database = it }
    }

    companion object {
        const val DATABASE_NAME = "tracediary_ai.db"
    }
}
