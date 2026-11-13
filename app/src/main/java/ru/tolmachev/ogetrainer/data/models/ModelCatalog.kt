package ru.tolmachev.ogetrainer.data.models

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Модель из каталога. Файлы — GGUF (llama.cpp), имена и размеры сверены по API Hugging Face.
 * minRamGb — практический минимум для запуска с контекстом 4 тыс. токенов.
 */
data class ModelEntry(
    val id: String,
    val repo: String,
    val fileName: String,
    val title: String,
    val parameters: String,
    val quantization: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val license: String,
    val purpose: String,
)

/**
 * Курируемый каталог локальных моделей для школьных задач.
 * Ставка на русский язык и на умение держать JSON: 1.5B — проверка коротких развёрнутых
 * ответов, 3B — генерация заданий, Gemma 1B — самый дешёвый по памяти вариант.
 */
object ModelCatalog {

    val entries: List<ModelEntry> = listOf(
        ModelEntry(
            id = "qwen2.5-1.5b-q4",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            title = "Qwen2.5 1.5B Instruct",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            sizeBytes = 1066L * 1024 * 1024,
            minRamGb = 3,
            license = "Apache-2.0",
            purpose = "Проверка коротких ответов и простых формулировок; работает даже на слабых телефонах",
        ),
        ModelEntry(
            id = "gemma3-1b-q4",
            repo = "bartowski/google_gemma-3-1b-it-GGUF",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            title = "Gemma 3 1B Instruct",
            parameters = "1B",
            quantization = "Q4_K_M",
            sizeBytes = 769L * 1024 * 1024,
            minRamGb = 3,
            license = "Gemma Terms of Use",
            purpose = "Самый компактный вариант: короткие пояснения, разбор ошибок",
        ),
        ModelEntry(
            id = "qwen2.5-3b-q4",
            repo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            title = "Qwen2.5 3B Instruct",
            parameters = "3B",
            quantization = "Q4_K_M",
            sizeBytes = 2007L * 1024 * 1024,
            minRamGb = 6,
            license = "Apache-2.0",
            purpose = "Основная рабочая модель: генерация заданий по теме, проверка развёрнутых ответов",
        ),
        ModelEntry(
            id = "llama32-3b-q4",
            repo = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            title = "Llama 3.2 3B Instruct",
            parameters = "3B",
            quantization = "Q4_K_M",
            sizeBytes = 1926L * 1024 * 1024,
            minRamGb = 6,
            license = "Llama 3.2 Community License",
            purpose = "Альтернатива Qwen 3B: устойчивее держит структуру ответа, слабее в русском",
        ),
    )

    fun byId(id: String): ModelEntry? = entries.firstOrNull { it.id == id }

    fun downloadUrl(entry: ModelEntry): String =
        "https://huggingface.co/${entry.repo}/resolve/main/${entry.fileName}?download=true"

    /** Подходит ли модель устройству: хватает RAM и места с запасом 15 %. */
    fun fits(entry: ModelEntry, device: DeviceProfile): Boolean =
        device.ramGb >= entry.minRamGb && device.availableStorageBytes > entry.sizeBytes * 1.15

    /** Текст причины, по которой модель не подходит; null — подходит. */
    fun issue(entry: ModelEntry, device: DeviceProfile): String? = when {
        device.ramGb < entry.minRamGb ->
            "Нужно ${entry.minRamGb} ГБ ОЗУ, на устройстве %.1f ГБ".format(java.util.Locale("ru"), device.ramGb)
        device.availableStorageBytes <= entry.sizeBytes * 1.15 ->
            "Нужно %.1f ГБ свободно".format(
                java.util.Locale("ru"),
                entry.sizeBytes * 1.15 / 1024.0 / 1024.0 / 1024.0,
            )
        else -> null
    }

    /** Уточняет размер по API Hugging Face; null — если сеть недоступна. */
    fun fetchSize(entry: ModelEntry): Long? = try {
        val connection = (URL("https://huggingface.co/api/models/${entry.repo}?blobs=true")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "OgeTrainer/0.1")
        }
        if (connection.responseCode !in 200..299) {
            null
        } else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val siblings = JSONObject(body).optJSONArray("siblings")
            var size: Long? = null
            if (siblings != null) {
                for (index in 0 until siblings.length()) {
                    val file = siblings.optJSONObject(index) ?: continue
                    if (file.optString("rfilename") != entry.fileName) continue
                    size = (file.optJSONObject("lfs")?.optLong("size", 0L) ?: 0L)
                        .takeIf { it > 0 } ?: file.optLong("size", 0L).takeIf { it > 0 }
                    break
                }
            }
            size
        }
    } catch (error: Throwable) {
        Log.w("ModelCatalog", "Не удалось получить размер ${entry.repo}", error)
        null
    }
}
