package ru.tolmachev.ogetrainer.data.provider

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class ProviderConfig(
    val baseUrl: String = "",
    val model: String = "deepseek-chat",
    val temperature: Double = 0.4,
    val maxTokens: Int = 1500,
)

data class ProbeResult(
    val latencyMs: Long,
    val reply: String,
)

/**
 * Провайдер генерации и проверки ответов: любой OpenAI-совместимый endpoint.
 * Настройки — в таблице provider_config, ключ — в [ProviderStore].
 *
 * Правила безопасности: cleartext (http) разрешён только для приватных подсетей,
 * публичные адреса — только https.
 */
class ProviderRepository(
    private val db: OgeDatabase,
    private val store: ProviderStore,
) {
    suspend fun load(): ProviderConfig = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT base_url, model, temperature, max_tokens FROM provider_config WHERE id = 1",
                null,
            ).use { c ->
                if (c.moveToFirst() && !c.isNull(0)) {
                    ProviderConfig(
                        baseUrl = c.getString(0) ?: "",
                        model = c.getString(1).ifBlank { "deepseek-chat" },
                        temperature = c.getDouble(2),
                        maxTokens = c.getInt(3),
                    )
                } else {
                    ProviderConfig()
                }
            }
        }
    }

    fun maskedKey(): String = store.maskedKey()

    fun hasKey(): Boolean = store.apiKey() != null

    suspend fun save(config: ProviderConfig, apiKey: String?): Result<Unit> = withContext(Dispatchers.IO) {
        validateUrl(config.baseUrl)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        if (config.model.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Укажите модель"))
        }
        if (config.maxTokens !in 64..16000) {
            return@withContext Result.failure(IllegalArgumentException("Лимит токенов от 64 до 16000"))
        }
        if (!apiKey.isNullOrBlank()) store.saveKey(apiKey)
        val hasKey = store.apiKey() != null

        db.write { database ->
            val values = ContentValues().apply {
                put("id", 1)
                put("base_url", normalizeBase(config.baseUrl))
                put("model", config.model.trim())
                put("temperature", config.temperature)
                put("max_tokens", config.maxTokens)
                put("has_key", if (hasKey) 1 else 0)
            }
            database.insertWithOnConflict("provider_config", null, values, 1)
        }
        Result.success(Unit)
    }

    /** Полное отключение провайдера: чистим адрес и ключ, приложение возвращается в офлайн. */
    suspend fun disable(): Result<Unit> = withContext(Dispatchers.IO) {
        store.clearKey()
        db.write { database ->
            database.insertWithOnConflict(
                "provider_config",
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("base_url", "")
                    put("model", "")
                    put("temperature", 0.4)
                    put("max_tokens", 1500)
                    put("has_key", 0)
                },
                1,
            )
        }
        Result.success(Unit)
    }

    /** Проверка подключения: минимальный запрос к /chat/completions, возвращаем задержку и ответ. */
    suspend fun probe(config: ProviderConfig, apiKeyOverride: String? = null): Result<ProbeResult> =
        withContext(Dispatchers.IO) {
            validateUrl(config.baseUrl)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
            val key = apiKeyOverride?.takeIf { it.isNotBlank() } ?: store.apiKey()
            val url = endpoint(config.baseUrl)

            runCatching {
                val payload = JSONObject().apply {
                    put("model", config.model.ifBlank { "deepseek-chat" })
                    put("messages", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Ответь одним словом: работает")
                        },
                    ))
                    put("max_tokens", 16)
                    put("stream", false)
                }
                val started = System.currentTimeMillis()
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (!key.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $key")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                val latency = System.currentTimeMillis() - started

                if (code !in 200..299) {
                    val hint = when (code) {
                        401, 403 -> "проверьте ключ"
                        404 -> "проверьте адрес и модель"
                        429 -> "лимит запросов, повторите позже"
                        in 500..599 -> "ошибка на стороне провайдера"
                        else -> "неожиданный ответ"
                    }
                    return@runCatching Result.failure<ProbeResult>(
                        IllegalStateException("HTTP $code — $hint: ${body.take(160)}"),
                    )
                }

                val json = JSONObject(body)
                val reply = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (reply.isBlank()) {
                    Result.failure(IllegalStateException("Провайдер ответил без текста: ${body.take(160)}"))
                } else {
                    Result.success(ProbeResult(latency, reply.trim().take(80)))
                }
            }.getOrElse { e ->
                Result.failure(IllegalStateException("Нет связи с провайдером: ${e.message}"))
            }
        }

    /**
     * Один запрос к провайдеру: системная и пользовательская подсказки, возврат текста ответа.
     * Используется генерацией контента и проверкой развёрнутых ответов.
     */
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double = 0.5,
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = load()
        if (config.baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Провайдер не настроен"))
        }
        val key = store.apiKey()
        if (key.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Ключ провайдера не задан"))
        }
        runCatching {
            val payload = JSONObject().apply {
                put("model", config.model.ifBlank { "deepseek-chat" })
                put("messages", org.json.JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    }
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                })
                put("max_tokens", maxTokens)
                put("temperature", temperature)
                put("stream", false)
            }
            val connection = (URL(endpoint(config.baseUrl)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (code !in 200..299) {
                return@runCatching Result.failure<String>(IllegalStateException("HTTP $code: ${body.take(200)}"))
            }
            val content = JSONObject(body).optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) Result.failure(IllegalStateException("Пустой ответ модели"))
            else Result.success(content)
        }.getOrElse { Result.failure(IllegalStateException("Нет связи с провайдером: ${it.message}")) }
    }

    /** null — адрес допустим; иначе текст ошибки. */
    fun validateUrl(baseUrl: String): String? {
        val value = baseUrl.trim()
        if (value.isBlank()) return "Укажите адрес endpoint"
        val uri = runCatching { URI(value) }.getOrNull() ?: return "Некорректный адрес"
        val scheme = uri.scheme?.lowercase() ?: return "Адрес должен начинаться с http:// или https://"
        val host = uri.host ?: return "Некорректный адрес"
        if (scheme !in setOf("http", "https")) return "Поддерживаются только http и https"
        if (scheme == "http" && !isPrivateHost(host)) {
            return "По http разрешены только локальные адреса (10.x, 172.16–31.x, 192.168.x, localhost)"
        }
        return null
    }

    private fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "10.0.2.2" || h.endsWith(".local")) return true
        val parts = h.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || a == 127
        }
        return false
    }

    private fun normalizeBase(base: String): String = base.trim().trimEnd('/')

    private fun endpoint(base: String): String {
        val normalized = normalizeBase(base)
        return if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
    }
}
