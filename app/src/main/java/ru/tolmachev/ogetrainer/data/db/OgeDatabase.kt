package ru.tolmachev.ogetrainer.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OgeDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, Schema.DB_NAME, null, Schema.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        Schema.CREATE.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Миграции добавляются здесь по мере роста схемы (см. docs/01-TZ раздел 8).
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    fun <T> read(block: (SQLiteDatabase) -> T): T = block(readableDatabase)

    fun <T> write(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}

fun Cursor.longOf(name: String): Long = getLong(getColumnIndexOrThrow(name))
fun Cursor.intOf(name: String): Int = getInt(getColumnIndexOrThrow(name))
fun Cursor.stringOf(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
fun Cursor.doubleOf(name: String): Double = getDouble(getColumnIndexOrThrow(name))
fun Cursor.nullableStringOf(name: String): String? = getString(getColumnIndexOrThrow(name))

inline fun <T> Cursor.mapRows(block: (Cursor) -> T): List<T> {
    val result = ArrayList<T>(count)
    use { cursor ->
        while (cursor.moveToNext()) result.add(block(cursor))
    }
    return result
}
