package ru.tolmachev.ogetrainer.data.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt
import ru.tolmachev.ogetrainer.data.db.TaskItem
import ru.tolmachev.ogetrainer.data.models.ModelEntry
import ru.tolmachev.ogetrainer.data.provider.ProviderRepository
import ru.tolmachev.ogetrainer.domain.Criterion
import ru.tolmachev.ogetrainer.domain.Rubric

/** Результат проверки письменного ответа. */
data class AnswerVerdict(
    val score: Double,
    val maxScore: Double,
    val confidence: Double,
    val criteria: List<CriterionVerdict>,
    val summary: String,
    val source: String,
) {
    val percent: Double get() = if (maxScore > 0) score / maxScore else 0.0
}

data class CriterionVerdict(
    val id: Int,
    val title: String,
    val met: Boolean,
    val comment: String,
)

/**
 * Проверка развёрнутых ответов, изложений и сочинений по рубрике.
 *
 * Правила:
 *  - модель возвращает строгий JSON: балл, отметки по критериям, уверенность;
 *  - невалидный JSON, пустой ответ или уверенность ниже 0.6 → вердикта нет,
 *    попытка остаётся «ожидает проверки» и на освоение не влияет;
 *  - локальная модель (1–3B) даёт только предварительный вердикт, окончательно
 *    подтверждает родитель или администратор;
 *  - облачная модель помечается отдельно, чтобы в отчёте было видно, кто проверял.
 */
class AnswerChecker(private val provider: ProviderRepository) {

    suspend fun checkCloud(
        task: TaskItem,
        answer: String,
        criteria: List<Criterion>,
    ): Result<AnswerVerdict> = withContext(Dispatchers.IO) {
        val system = "Ты эксперт ОГЭ. Проверяешь письменный ответ по критериям. " +
            "Отвечай только JSON без пояснений."
        val user = buildString {
            appendLine("Задание: ${task.prompt}")
            if (task.solution.isNotBlank()) appendLine("Эталонное решение: ${task.solution}")
            appendLine("Ответ ученика: $answer")
            appendLine("Критерии:")
            appendLine(Rubric.describe(criteria))
            appendLine(
                "Верни JSON: {\"criteria\":[{\"id\":1,\"met\":true,\"comment\":\"...\"}], " +
                    "\"score\": число от 0 до ${task.maxPoints}, \"confidence\": 0..1, " +
                    "\"summary\": \"одно предложение\"}",
            )
        }
        provider.complete(system, user, maxTokens = 700, temperature = 0.2)
            .mapCatching { raw ->
                parse(raw, criteria, source = "llm-remote", maxScore = task.maxPoints.toDouble())
                    ?: throw IllegalStateException("Модель ответила не по формату")
            }
    }

    /** Проверка локальной моделью: только предварительная. */
    suspend fun checkLocal(
        context: Context,
        entry: ModelEntry,
        task: TaskItem,
        answer: String,
        criteria: List<Criterion>,
    ): Result<AnswerVerdict> = withContext(Dispatchers.Default) {
        val loaded = LocalLlm.ensureLoaded(context, entry)
        if (!loaded.ok) return@withContext Result.failure(IllegalStateException(loaded.message))

        val system = "Ты эксперт ОГЭ. Проверяешь ответ по критериям. Отвечай только JSON."
        val user = buildString {
            appendLine("Задание: ${task.prompt}")
            appendLine("Ответ ученика: $answer")
            appendLine("Критерии:")
            appendLine(Rubric.describe(criteria))
            appendLine(
                "JSON: {\"criteria\":[{\"id\":1,\"met\":true,\"comment\":\"...\"}], " +
                    "\"score\": <целое число баллов от 0 до ${task.maxPoints}, не доля>, " +
                    "\"confidence\": 0..1, \"summary\": \"...\"}",
            )
        }
        val prompt = ChatPrompt.build(entry, system, user)
        val raw = LocalLlm.complete(context, prompt, maxTokens = 500, temperature = 0.2f)
        if (raw.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Модель не вернула текст"))
        }
        Log.i("AnswerChecker", "raw ответ модели (${raw.length} симв.): " + raw.take(400).replace("\n", " "))
        val verdict = parse(raw, criteria, source = "llm-local", maxScore = task.maxPoints.toDouble())
            ?: return@withContext Result.failure(IllegalStateException("Модель ответила не по формату"))
        Result.success(verdict)
    }

    /**
     * Разбор ответа модели. Возвращает null, если JSON не разобрался, балл вне диапазона
     * или уверенность ниже порога: такой ответ должен уйти человеку, а не в оценку.
     */
    fun parse(
        raw: String,
        criteria: List<Criterion>,
        source: String,
        maxScore: Double,
    ): AnswerVerdict? {
        val jsonText = raw.substring(
            raw.indexOf('{').coerceAtLeast(0),
            (raw.lastIndexOf('}') + 1).coerceAtMost(raw.length),
        )
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null

        val confidence = json.optDouble("confidence", 0.0)
        if (confidence < MIN_CONFIDENCE) return null

        val rawScore = json.optDouble("score", -1.0)
        if (rawScore < 0) return null
        // Модели любят отвечать долей (0,6) вместо баллов. Если шкала не единичная,
        // а ответ в 0..1 — это доля, пересчитываем и округляем до целого балла.
        val score = when {
            maxScore > 1.0 && rawScore in 0.0..1.0 -> (rawScore * maxScore).roundToInt().toDouble()
            else -> rawScore
        }
        if (score < 0 || score > maxScore) return null

        val items = json.optJSONArray("criteria")
        val verdicts = buildList {
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optInt("id", 0)
                    val criterion = criteria.firstOrNull { it.id == id } ?: continue
                    add(
                        CriterionVerdict(
                            id = id,
                            title = criterion.title,
                            met = item.optBoolean("met", false),
                            comment = item.optString("comment").take(200),
                        ),
                    )
                }
            }
        }
        // Модель не разметила ни один критерий — доверять баллу нельзя.
        if (verdicts.isEmpty()) return null

        Log.i(
            "AnswerChecker",
            "verdict source=$source score=$score/$maxScore conf=$confidence raw=$rawScore",
        )
        return AnswerVerdict(
            score = score,
            maxScore = maxScore,
            confidence = confidence,
            criteria = verdicts,
            summary = json.optString("summary").take(300),
            source = source,
        )
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.6
    }
}
