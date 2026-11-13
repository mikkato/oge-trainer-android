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


    // ---------- раздел «Банк»: сводка, темы, задачи, приёмка ----------

    data class BankSummary(
        val subjects: Int,
        val topics: Int,
        val theories: Int,
        val tasks: Int,
        val generated: Int,
        val drafts: Int,
    )

    data class SubjectBankRow(
        val code: String,
        val title: String,
        val topics: Int,
        val withTheory: Int,
        val tasks: Int,
        val drafts: Int,
    )

    data class TopicBankRow(
        val code: String,
        val title: String,
        val tasks: Int,
        val drafts: Int,
        val hasTheory: Boolean,
    )

    data class BankTask(
        val id: Long,
        val prompt: String,
        val answer: String,
        val solution: String,
        val origin: String,
        val trusted: Boolean,
    )

    /** Сводка банка: сколько всего и сколько из этого сгенерировано и ждёт проверки. */
    suspend fun summary(): BankSummary = withContext(Dispatchers.IO) {
        db.read { database ->
            fun scalar(query: String): Int = database.rawQuery(query, null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            BankSummary(
                subjects = scalar("SELECT COUNT(*) FROM subjects"),
                topics = scalar("SELECT COUNT(*) FROM topics"),
                theories = scalar("SELECT COUNT(*) FROM theories"),
                tasks = scalar("SELECT COUNT(*) FROM tasks"),
                generated = scalar("SELECT COUNT(*) FROM tasks WHERE origin <> 'bank'"),
                drafts = scalar("SELECT COUNT(*) FROM tasks WHERE trusted = 0 AND origin <> 'bank'"),
            )
        }
    }

    /** Предметы со счётчиками тем, конспектов, задач и черновиков. */
    suspend fun subjectRows(originFilter: String? = null): List<SubjectBankRow> = withContext(Dispatchers.IO) {
        val originClause = when (originFilter) {
            null, "" -> ""
            "bank" -> " AND t.origin = 'bank'"
            "local" -> " AND t.origin LIKE 'local%'"
            "llm-local" -> " AND t.origin = 'llm-local'"
            "llm" -> " AND t.origin LIKE 'llm:d%'"
            else -> " AND t.origin = ?"
        }
        db.read { database ->
            database.rawQuery(
                """
                SELECT s.code, s.title,
                       (SELECT COUNT(*) FROM topics tp WHERE tp.subject_code = s.code) AS topics,
                       (SELECT COUNT(*) FROM topics tp JOIN theories th ON th.topic_code = tp.code
                         WHERE tp.subject_code = s.code) AS with_theory,
                       (SELECT COUNT(*) FROM tasks t WHERE t.subject_code = s.code$originClause) AS tasks,
                       (SELECT COUNT(*) FROM tasks t WHERE t.subject_code = s.code AND t.trusted = 0 AND t.origin <> 'bank'$originClause) AS drafts
                FROM subjects s ORDER BY s.rowid
                """.trimIndent(),
                if (originFilter in setOf("bank", "local", "llm-local", "llm", null, "")) null
                else arrayOf(originFilter),
            ).mapRows {
                SubjectBankRow(
                    code = it.getString(0),
                    title = it.getString(1),
                    topics = it.getInt(2),
                    withTheory = it.getInt(3),
                    tasks = it.getInt(4),
                    drafts = it.getInt(5),
                )
            }
        }
    }

    /** Темы предмета со счётчиками задач и признаком конспекта. */
    suspend fun topicRows(subjectCode: String, originFilter: String? = null): List<TopicBankRow> =
        withContext(Dispatchers.IO) {
            val originClause = when (originFilter) {
                null, "" -> ""
                "bank" -> " AND origin = 'bank'"
                "local" -> " AND origin LIKE 'local%'"
                "llm-local" -> " AND origin = 'llm-local'"
                "llm" -> " AND origin LIKE 'llm:d%'"
                else -> " AND origin = ?"
            }
            db.read { database ->
                database.rawQuery(
                    """
                    SELECT tp.code, tp.title,
                           (SELECT COUNT(*) FROM tasks t WHERE t.topic_code = tp.code$originClause) AS tasks,
                           (SELECT COUNT(*) FROM tasks t WHERE t.topic_code = tp.code AND t.trusted = 0 AND t.origin <> 'bank'$originClause) AS drafts,
                           EXISTS(SELECT 1 FROM theories th WHERE th.topic_code = tp.code) AS has_theory
                    FROM topics tp WHERE tp.subject_code = ? ORDER BY tp.pos, tp.rowid
                    """.trimIndent(),
                    if (originFilter in setOf("bank", "local", "llm-local", "llm", null, "")) {
                        arrayOf(subjectCode)
                    } else {
                        arrayOf(originFilter, subjectCode)
                    },
                ).mapRows {
                    TopicBankRow(
                        code = it.getString(0),
                        title = it.getString(1),
                        tasks = it.getInt(2),
                        drafts = it.getInt(3),
                        hasTheory = it.getInt(4) == 1,
                    )
                }
            }
        }

    /** Задачи темы для просмотра и приёмки. */
    suspend fun tasksOf(topicCode: String, originFilter: String? = null, onlyDrafts: Boolean = false): List<BankTask> =
        withContext(Dispatchers.IO) {
            val conditions = buildList {
                add("topic_code = ?")
                when (originFilter) {
                    null, "" -> Unit
                    "bank" -> add("origin = 'bank'")
                    "local" -> add("origin LIKE 'local%'")
                    "llm-local" -> add("origin = 'llm-local'")
                    "llm" -> add("origin LIKE 'llm:d%'")
                    else -> add("origin = '$originFilter'")
                }
                if (onlyDrafts) add("trusted = 0")
            }
            db.read { database ->
                database.rawQuery(
                    "SELECT id, prompt, answer, solution, origin, trusted FROM tasks " +
                        "WHERE ${conditions.joinToString(" AND ")} ORDER BY id DESC",
                    arrayOf(topicCode),
                ).mapRows {
                    BankTask(
                        id = it.getLong(0),
                        prompt = it.getString(1),
                        answer = it.getString(2),
                        solution = it.getString(3),
                        origin = it.getString(4),
                        trusted = it.getInt(5) == 1,
                    )
                }
            }
        }

    /** Приёмка одной задачи: после неё задание считается проверенным. */
    suspend fun approveTask(id: Long): Unit = withContext(Dispatchers.IO) {
        db.write { it.update("tasks", android.content.ContentValues().apply { put("trusted", 1) }, "id = ?", arrayOf(id.toString())) }
    }

    /** Принять все черновики темы. */
    suspend fun approveDrafts(topicCode: String): Int = withContext(Dispatchers.IO) {
        db.write { database ->
            database.update(
                "tasks",
                android.content.ContentValues().apply { put("trusted", 1) },
                "topic_code = ? AND trusted = 0",
                arrayOf(topicCode),
            )
        }
    }

    /** Удалить задачу (отклонённый черновик). */
    suspend fun deleteTask(id: Long): Unit = withContext(Dispatchers.IO) {
        db.write { it.delete("tasks", "id = ?", arrayOf(id.toString())) }
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
