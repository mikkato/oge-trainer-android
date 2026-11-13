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
        // v2 (13.11.2026): встроенный администратор + сброс пользовательской базы.
        // Столбец builtin добавляется, профили и все их данные удаляются; встроенный
        // администратор создаётся репозиторием при первом обращении (ProfilesRepository).
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN builtin INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DELETE FROM attempts")
            db.execSQL("DELETE FROM topic_mastery")
            db.execSQL("DELETE FROM program_subjects")
            db.execSQL("DELETE FROM programs")
            db.execSQL("DELETE FROM profiles")
        }
        // v3 (13.11.2026): карточка профиля — возраст и фото.
        // v5 (13.11.2026): рубрики развёрнутых ответов, вердикт модели, диагностические срезы.
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN rubric TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE attempts ADD COLUMN verdict_json TEXT")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS diagnostics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    profile_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    tasks_done INTEGER NOT NULL DEFAULT 0,
                    score REAL NOT NULL DEFAULT 0,
                    sections_json TEXT NOT NULL DEFAULT '[]'
                )""",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_attempts_pending ON attempts(profile_id, status)")
        }
        // v4 (13.11.2026): задания банка ФИПИ — проверенный контент, а не черновики.
        if (oldVersion < 4) {
            db.execSQL("UPDATE tasks SET trusted = 1 WHERE origin = 'bank'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN age INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profiles ADD COLUMN photo_path TEXT")
        }
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
