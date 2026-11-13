package ru.tolmachev.ogetrainer.data.provider

import android.content.ContentValues
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import java.security.MessageDigest

/**
 * Локальная генерация: задания из [LocalTemplates] кладутся в таблицу tasks
 * с origin = "local:<сложность>" и trusted = 1 — ответ вычислен кодом, проверять не нужно.
 * Сеть и модель не участвуют.
 */
class LocalGenerator(private val db: OgeDatabase) {

    data class Batch(val added: Int, val skipped: Int)

    fun supports(subjectCode: String, topicTitle: String): Boolean =
        LocalTemplates.supports(subjectCode, topicTitle)

    suspend fun generate(
        subjectCode: String,
        topicCode: String,
        topicTitle: String,
        grade: Int,
        count: Int,
    ): Result<Batch> = withContext(Dispatchers.IO) {
        val drafts = LocalTemplates.drafts(subjectCode, topicTitle, count)
        if (drafts.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Для этой темы локальная генерация недоступна — используйте банк или модель"),
            )
        }
        var added = 0
        var skipped = 0
        db.write { database ->
            drafts.forEach { draft ->
                val hash = sha256("local|$topicCode|${draft.prompt}")
                val id = database.insertWithOnConflict(
                    "tasks",
                    null,
                    ContentValues().apply {
                        put("subject_code", subjectCode)
                        put("topic_code", topicCode)
                        put("type", "short")
                        put("prompt", draft.prompt)
                        put("answer", draft.answer)
                        put("solution", draft.solution)
                        put("max_points", 1)
                        put("trusted", 1)
                        // Класс задаётся при генерации: один и тот же предмет в 5 и 9 классе
                        // даёт разный контент, и в банке он лежит раздельно.
                        put("grade", grade)
                        put("origin", "local:${draft.difficulty}")
                        put("content_hash", hash)
                    },
                    5,
                )
                if (id == -1L) skipped++ else added++
            }
        }
        Log.i("LocalGenerator", "added=$added skipped=$skipped topic=$topicCode")
        Result.success(Batch(added, skipped))
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
