package com.tracediary.app.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class SqlCipherRekeyTest {

    @Test
    fun buildRekeyPragma_escapesSingleQuotes() {
        val sql = SqlCipherRekey.buildRekeyPragma("td-db-v3:abc'def")

        assertEquals("PRAGMA rekey = 'td-db-v3:abc''def'", sql)
    }

    @Test
    fun rekeyWithPragma_executesPragmaAsQueryAndClosesCursor() {
        val cursor = RecordingCursor()
        val database = RecordingDatabase(cursor)

        SqlCipherRekey.rekeyWithPragma(database.proxy, "td-db-v3:abc'def")

        assertEquals(listOf("PRAGMA rekey = 'td-db-v3:abc''def'"), database.queries)
        assertTrue(cursor.moveToFirstCalled)
        assertTrue(cursor.closeCalled)
        assertFalse(database.execSqlCalled)
    }

    private class RecordingDatabase(
        private val cursor: RecordingCursor,
    ) : InvocationHandler {
        val queries = mutableListOf<String>()
        var execSqlCalled = false

        val proxy: SupportSQLiteDatabase = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            this,
        ) as SupportSQLiteDatabase

        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "query" -> {
                    queries += args?.firstOrNull() as String
                    cursor.proxy
                }
                "execSQL" -> {
                    execSqlCalled = true
                    Unit
                }
                "isOpen", "isDatabaseIntegrityOk", "isWriteAheadLoggingEnabled", "needUpgrade", "yieldIfContendedSafely" -> false
                "getVersion", "getMaximumSize", "getPageSize", "getAttachedDbs" -> 0
                "getPath" -> ""
                "toString" -> "RecordingDatabase"
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private class RecordingCursor : InvocationHandler {
        var moveToFirstCalled = false
        var closeCalled = false

        val proxy: Cursor = Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
            this,
        ) as Cursor

        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "moveToFirst" -> {
                    moveToFirstCalled = true
                    false
                }
                "close" -> {
                    closeCalled = true
                    Unit
                }
                "isClosed" -> closeCalled
                "toString" -> "RecordingCursor"
                else -> defaultReturn(method.returnType)
            }
        }
    }

    private companion object {
        fun defaultReturn(type: Class<*>): Any? {
            return when (type) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Character.TYPE -> 0.toChar()
                java.lang.Void.TYPE -> Unit
                else -> null
            }
        }
    }
}
