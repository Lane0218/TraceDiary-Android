package com.tracediary.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase

object SqlCipherRekey {

    fun rekey(database: SupportSQLiteDatabase, passphrase: ByteArray) {
        if (database is SQLiteDatabase) {
            database.changePassword(passphrase)
            return
        }
        rekeyWithPragma(database, passphrase.toString(Charsets.UTF_8))
    }

    internal fun rekeyWithPragma(database: SupportSQLiteDatabase, passphrase: String) {
        database.query(buildRekeyPragma(passphrase)).use { cursor ->
            cursor.moveToFirst()
        }
    }

    internal fun buildRekeyPragma(passphrase: String): String {
        val escaped = passphrase.replace("'", "''")
        return "PRAGMA rekey = '$escaped'"
    }
}
