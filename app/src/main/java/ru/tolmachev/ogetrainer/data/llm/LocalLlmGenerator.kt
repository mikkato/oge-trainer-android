package ru.tolmachev.ogetrainer.data.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import ru.tolmachev.ogetrainer.data.models.DeviceProfile
import ru.tolmachev.ogetrainer.data.models.ModelCatalog
import ru.tolmachev.ogetrainer.data.models.ModelDownloads
import ru.tolmachev.ogetrainer.data.models.ModelEntry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Держит одну загруженную модель в памяти и освобождает её, когда память нужна системе.
 * Загрузка идёт только после проверки бюджета [LlmResources.plan]: лучше показать
 * «не хватает 600 МБ», чем получить OOM на середине генерации.
 */
object LocalLlm {

    private val engine = LlmEngine()
    private var loadedId: String? = null
    private var loadedPlan: LlmResources.Plan? = null

    val isAvailable: Boolean get() = LlmEngine.available
    val isLoaded: Boolean get() = engine.isLoaded
    val currentModelId: String? get() = loadedId
    val currentPlan: LlmResources.Plan? get() = loadedPlan

    data class LoadResult(val ok: Boolean, val message: String)

    fun ensureLoaded(context: Context, entry: ModelEntry): LoadResult = synchronized(this) {
        if (!LlmEngine.available) {
            return LoadResult(false, "Движок llama.cpp не собран в этой сборке")
        }
        if (engine.isLoaded && loadedId == entry.id) {
            return LoadResult(true, "Модель уже загружена")
        }
        val file = ModelDownloads.fileOf(context, entry)
        if (!file.isFile) return LoadResult(false, "Файл модели не найден — скачайте её")

        val device = DeviceProfile.detect(context)
        val plan = LlmResources.plan(context, file, device)
        if (!plan.fits) {
            return LoadResult(false, plan.reason ?: "Недостаточно памяти для модели")
        }

        engine.unload()
        loadedId = null
        val started = System.currentTimeMillis()
        val ok = engine.load(file.absolutePath, plan.contextSize, plan.threads, useMmap = true)
        return if (ok) {
            loadedId = entry.id
            loadedPlan = plan
            Log.i("LocalLlm", "модель ${entry.id} загружена за ${System.currentTimeMillis() - started} мс")
            LoadResult(true, "Модель загружена: контекст ${plan.contextSize}, потоков ${plan.threads}")
        } else {
            LoadResult(false, "Движок не смог загрузить файл модели")
        }
    }

    fun unload() = synchronized(this) {
        engine.unload()
        loadedId = null
        loadedPlan = null
    }

    /** Генерация с контролем памяти: каждые 16 токенов проверяем свободную память. */
    suspend fun complete(
        context: Context,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onText: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        if (!engine.isLoaded) return@withContext ""
        val stop = AtomicBoolean(false)
        val threshold = 220L * 1024 * 1024
        val answer = engine.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            stopFlag = stop,
            sink = object : LlmEngine.TokenSink {
                override fun onToken(text: String) = onText(text)

                override fun allow(): Boolean {
                    val free = LlmResources.availableBytes(context)
                    if (free < threshold || LlmResources.lowMemory(context)) {
                        Log.w("LocalLlm", "остановка по памяти: свободно ${free / 1024 / 1024} МБ")
                        stop.set(true)
                        return false
                    }
                    return true
                }
            },
        )
        answer
    }
}

/**
 * Генерация заданий локальной моделью. Промпт собирается под конкретное семейство
 * (Qwen/chatml, Gemma, Llama 3), ответ разбирается как JSON и попадает в таблицу tasks
 * с origin = "llm-local" — такие задания требуют приёмки, как и облачные.
 */
class LocalLlmGenerator(private val db: ru.tolmachev.ogetrainer.data.db.OgeDatabase) {

    data class Batch(val added: Int, val skipped: Int, val rejected: Int, val truncated: Boolean)

    suspend fun generateTasks(
        context: Context,
        entry: ModelEntry,
        subjectCode: String,
        subjectTitle: String,
        topicCode: String,
        topicTitle: String,
        grade: Int,
        count: Int,
        onText: (String) -> Unit = {},
    ): Result<Batch> = withContext(Dispatchers.IO) {
        val loaded = LocalLlm.ensureLoaded(context, entry)
        if (!loaded.ok) return@withContext Result.failure(IllegalStateException(loaded.message))

        val system = "Ты методист ОГЭ по предмету «$subjectTitle» и составляешь задания для $grade класса. " +
            "Отвечай только JSON-массивом без пояснений."
        val user = "Тема: «$topicTitle». Составь $count заданий с коротким ответом разной сложности. " +
            "Формат каждого элемента: {\"prompt\":\"условие\",\"answer\":\"ответ\",\"solution\":\"решение по шагам\"}. " +
            "Ответ — число или слово без единиц измерения, формулы в LaTeX внутри \$...\$. Только JSON."

        val raw = LocalLlm.complete(
            context = context,
            prompt = promptFor(entry, system, user),
            maxTokens = minOf(1800, count * 320),
            temperature = 0.6f,
            onText = onText,
        )
        if (raw.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Модель не вернула текст"))
        }

        val array = parseArray(raw)
            ?: return@withContext Result.failure(
                IllegalStateException("Модель ответила не JSON: ${raw.take(160)}"),
            )

        var added = 0
        var skipped = 0
        var rejected = 0
        db.write { database ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                val prompt = item?.optString("prompt")?.trim().orEmpty()
                val answer = item?.optString("answer")?.trim().orEmpty()
                if (item == null || prompt.length < 12 || answer.length > 60 || answer.isBlank()) {
                    rejected++
                    continue
                }
                val solution = item.optString("solution").trim()
                val hash = sha256("llm-local|$topicCode|$prompt")
                val id = database.insertWithOnConflict(
                    "tasks",
                    null,
                    android.content.ContentValues().apply {
                        put("subject_code", subjectCode)
                        put("topic_code", topicCode)
                        put("type", "short")
                        put("prompt", prompt)
                        put("answer", answer)
                        put("solution", solution)
                        put("max_points", 1)
                        put("trusted", 0)
                        put("grade", grade)
                        put("origin", "llm-local")
                        put("content_hash", hash)
                    },
                    5,
                )
                if (id == -1L) skipped++ else added++
            }
        }
        Result.success(Batch(added, skipped, rejected, truncated = raw.length < 40))
    }

    /** Шаблон диалога зависит от семейства модели: иначе качество падает в разы. */
    private fun promptFor(entry: ModelEntry, system: String, user: String): String =
        ChatPrompt.build(entry, system, user)

    private fun parseArray(reply: String): JSONArray? {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JSONArray(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Suppress("unused")
    private fun fileOf(context: Context, entry: ModelEntry): File = ModelDownloads.fileOf(context, entry)

    @Suppress("unused")
    private fun models(): List<ModelEntry> = ModelCatalog.entries
}
