package ru.tolmachev.ogetrainer.data.bank

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.tolmachev.ogetrainer.data.db.BankCounts
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import ru.tolmachev.ogetrainer.data.db.intOf
import ru.tolmachev.ogetrainer.data.db.longOf
import ru.tolmachev.ogetrainer.data.db.mapRows
import ru.tolmachev.ogetrainer.data.db.stringOf
import java.security.MessageDigest

data class ImportResult(
    val counts: BankCounts,
    val tasksInserted: Int,
    val alreadyImported: Boolean,
    val bankVersion: String,
    val exportedAt: String,
    val declaredTasks: Int,
)

/**
 * Импорт банка из assets.
 *
 * Идемпотентность: задачи дедуплицируются по content_hash (sha256 от type|topic|prompt)
 * с UNIQUE-индексом и INSERT OR IGNORE. Теории перезаписываются по topic_code.
 * Счётчики сверяются с meta.json — это и есть приёмка FR-201/AC-7.
 */
class BankRepository(
    private val context: Context,
    private val db: OgeDatabase,
) {
    private fun asset(name: String): String =
        context.assets.open("bank/$name").bufferedReader().use { it.readText() }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun counts(): BankCounts = withContext(Dispatchers.IO) {
        db.read { database ->
            BankCounts(
                subjects = scalar(database, "SELECT COUNT(*) FROM subjects"),
                topics = scalar(database, "SELECT COUNT(*) FROM topics"),
                theories = scalar(database, "SELECT COUNT(*) FROM theories"),
                tasks = scalar(database, "SELECT COUNT(*) FROM tasks"),
            )
        }
    }

    private fun scalar(database: SQLiteDatabase, sql: String): Int =
        database.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    suspend fun import(): ImportResult = withContext(Dispatchers.IO) {
        val metaJson = JSONObject(asset("meta.json"))
        val exportedAt = metaJson.optString("exported_at")
        val version = metaJson.optString("version")
        val stamp = "$version:$exportedAt"
        val declaredTasks = metaJson.optJSONObject("counts")?.optInt("tasks") ?: 0

        val subjects = JSONArray(asset("subjects.json"))
        val topics = JSONArray(asset("topics.json"))
        val theories = JSONArray(asset("theories.json"))
        val tasks = JSONArray(asset("tasks.json"))

        val previous = db.read { database ->
            database.rawQuery("SELECT value FROM sync_meta WHERE key = 'bank_stamp'", null)
                .use { if (it.moveToFirst()) it.getString(0) else null }
        }

        var inserted = 0
        db.write { database ->
            subjects.forEachObject { item ->
                database.insertWithOnConflict(
                    "subjects",
                    null,
                    ContentValues().apply {
                        put("code", item.getString("code"))
                        put("title", item.getString("title"))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }

            topics.forEachObject { item ->
                database.insertWithOnConflict(
                    "topics",
                    null,
                    ContentValues().apply {
                        put("code", item.getString("code"))
                        put("subject_code", item.getString("subject"))
                        put("parent_code", item.optString("parent").ifBlank { null })
                        put("title", item.getString("title"))
                        put("pos", item.optInt("order"))
                        put("source", item.optString("source", "fipi"))
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }

            theories.forEachObject { item ->
                database.insertWithOnConflict(
                    "theories",
                    null,
                    ContentValues().apply {
                        put("topic_code", item.getString("topic"))
                        put("title", item.getString("title"))
                        put("content", item.getString("content"))
                        put("updated_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }

            tasks.forEachObject { item ->
                val type = item.getString("type")
                val topic = item.getString("topic")
                val prompt = item.getString("prompt")
                val row = ContentValues().apply {
                    put("subject_code", item.getString("subject"))
                    put("topic_code", topic)
                    put("type", type)
                    put("prompt", prompt)
                    put("answer", item.optString("answer"))
                    put("solution", item.optString("solution"))
                    put("max_points", item.optInt("max_points", 1))
                    put("trusted", if (item.optBoolean("trusted")) 1 else 0)
                    put("origin", "bank")
                    put("content_hash", sha256("$type|$topic|$prompt"))
                }
                val id = database.insertWithOnConflict(
                    "tasks",
                    null,
                    row,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (id != -1L) inserted++
            }

            database.insertWithOnConflict(
                "sync_meta",
                null,
                ContentValues().apply {
                    put("key", "bank_stamp")
                    put("value", stamp)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        val counts = counts()
        ImportResult(
            counts = counts,
            tasksInserted = inserted,
            alreadyImported = previous == stamp,
            bankVersion = stamp,
            exportedAt = exportedAt,
            declaredTasks = declaredTasks,
        )
    }

    /** Задачи с формулами — материал гейта G1. */
    suspend fun tasksWithFormula(limit: Int): List<TaskBrief> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT id, subject_code, type, prompt FROM tasks WHERE prompt LIKE '%\$%' ORDER BY id LIMIT ?",
                arrayOf(limit.toString()),
            ).mapRows {
                TaskBrief(
                    id = it.longOf("id"),
                    subjectCode = it.stringOf("subject_code"),
                    type = it.stringOf("type"),
                    prompt = it.stringOf("prompt"),
                )
            }
        }
    }

    suspend fun theory(topicCode: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT title, content FROM theories WHERE topic_code = ? LIMIT 1",
                arrayOf(topicCode),
            ).use {
                if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
            }
        }
    }

    /** Темы предмета: код и название — для генерации контента. */
    suspend fun topicsOf(subjectCode: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT code, title FROM topics WHERE subject_code = ? ORDER BY rowid",
                arrayOf(subjectCode),
            ).mapRows { it.getString(0) to it.getString(1) }
        }
    }

    suspend fun subjectTitle(subjectCode: String): String = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery("SELECT title FROM subjects WHERE code = ?", arrayOf(subjectCode)).use { c ->
                if (c.moveToFirst()) c.getString(0) else subjectCode
            }
        }
    }

    /** Справочник предметов для формы профиля. */
    suspend fun subjects(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery("SELECT code, title FROM subjects ORDER BY rowid", null).mapRows {
                it.getString(0) to it.getString(1)
            }
        }
    }

    suspend fun countTasks(topicCode: String, type: String): Int = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE topic_code = ? AND type = ?",
                arrayOf(topicCode, type),
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }
    }
}

data class TaskBrief(
    val id: Long,
    val subjectCode: String,
    val type: String,
    val prompt: String,
)

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (index in 0 until length()) block(getJSONObject(index))
}
