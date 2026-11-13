package ru.tolmachev.ogetrainer.data.provider

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import java.security.MessageDigest

/**
 * Генерация контента подключённой моделью для конкретного профиля.
 * Задания кладутся в ту же таблицу tasks с origin = "llm", то есть их можно отличить
 * от банка и впоследствии отфильтровать. Дубликаты отсекаются по content_hash.
 */
class ContentGenerator(
    private val db: OgeDatabase,
    private val provider: ProviderRepository,
) {

    data class Batch(
        val requested: Int,
        val added: Int,
        val skipped: Int,
        val rejected: Int,
    )

    suspend fun generateTasks(
        subjectCode: String,
        subjectTitle: String,
        topicCode: String,
        topicTitle: String,
        grade: Int,
        count: Int,
    ): Result<Batch> = withContext(Dispatchers.IO) {
        val system = """
            Ты методист ОГЭ по предмету «$subjectTitle». Составляешь задания для ученика $grade класса.
            Отвечай строго одним JSON-массивом без пояснений и без markdown-разметки.
            Каждый элемент: {"prompt": "условие", "answer": "краткий ответ", "solution": "решение по шагам",
            "type": "short", "difficulty": 1-3}.
            Требования: условие самодостаточно и однозначно; для короткого ответа answer — число или слово
            без единиц измерения; формулы в LaTeX внутри $...$; difficulty 1 — базовый, 3 — повышенный.
        """.trimIndent()

        val user = """
            Тема: «$topicTitle».
            Составь $count заданий типа short по этой теме, разного уровня сложности,
            без повторов и без ссылок на рисунки. Только JSON-массив из $count элементов.
        """.trimIndent()

        val reply = provider.complete(system, user, maxTokens = 2200, temperature = 0.6)
            .getOrElse { return@withContext Result.failure(it) }

        val items = parseArray(reply)
            ?: return@withContext Result.failure(IllegalStateException("Модель вернула не JSON: ${reply.take(160)}"))

        var added = 0
        var skipped = 0
        var rejected = 0
        db.write { database ->
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index)
                val prompt = item?.optString("prompt")?.trim().orEmpty()
                val answer = item?.optString("answer")?.trim().orEmpty()
                if (item == null || prompt.length < 12 || answer.isBlank()) {
                    rejected++
                    continue
                }
                val solution = item.optString("solution").trim()
                val difficulty = item.optInt("difficulty", 1).coerceIn(1, 3)
                val hash = sha256("$topicCode|$prompt")
                val rowId = database.insertWithOnConflict(
                    "tasks",
                    null,
                    ContentValues().apply {
                        put("subject_code", subjectCode)
                        put("topic_code", topicCode)
                        put("type", "short")
                        put("prompt", prompt)
                        put("answer", answer)
                        put("solution", solution)
                        put("max_points", 1)
                        put("trusted", 0)
                        put("origin", "llm:d$difficulty")
                        put("content_hash", hash)
                    },
                    5, // CONFLICT_IGNORE
                )
                if (rowId == -1L) skipped++ else added++
            }
        }
        Result.success(Batch(requested = count, added = added, skipped = skipped, rejected = rejected))
    }

    private fun parseArray(reply: String): JSONArray? {
        val cleaned = reply
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JSONArray(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Suppress("unused")
    private fun emptyObject() = JSONObject()
}
