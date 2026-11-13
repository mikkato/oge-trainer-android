package ru.tolmachev.ogetrainer.data.models

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Загрузчик GGUF-моделей с докачкой.
 *
 * Логика проверена на практике: файл пишется в `<имя>.part`, при обрыве связи
 * запрос повторяется с заголовком `Range: bytes=<размер part>-`; если сервер
 * отдаёт 200 вместо 206, файл начинается заново (иначе получится мусор).
 * При пропадании сети загрузка переходит в состояние «ждём сеть» и продолжается
 * сама, когда сеть возвращается.
 */
object ModelDownloads {

    enum class Status { Idle, Downloading, Paused, WaitingNetwork, Done, Failed }

    data class State(
        val entryId: String? = null,
        val status: Status = Status.Idle,
        val downloaded: Long = 0,
        val total: Long = 0,
        val bytesPerSecond: Long = 0,
        val message: String? = null,
    ) {
        val percent: Int
            get() = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var connection: HttpURLConnection? = null

    /**
     * Признак «остановлено пользователем». Без него отмена корутины попадала в блок
     * обработки обрыва и возвращала состояние в «идёт загрузка», теряя прогресс.
     */
    @Volatile
    private var stopRequested = false

    private val appContext: Context by lazy {
        requireNotNull(holder) { "ModelDownloads не инициализирован" }
    }

    @Volatile
    private var holder: Context? = null

    fun init(context: Context) {
        holder = context.applicationContext
        registerNetworkCallback()
    }

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun fileOf(context: Context, entry: ModelEntry): File = File(modelsDir(context), entry.fileName)

    fun isInstalled(context: Context, entry: ModelEntry): Boolean {
        val file = fileOf(context, entry)
        return file.isFile && file.length() >= entry.sizeBytes * 0.98
    }

    fun installedBytes(context: Context, entry: ModelEntry): Long =
        fileOf(context, entry).length().takeIf { it > 0 } ?: 0L

    fun start(context: Context, entry: ModelEntry) {
        init(context)
        stopRequested = false
        if (isInstalled(context, entry)) {
            _state.value = State(entry.id, Status.Done, entry.sizeBytes, entry.sizeBytes)
            return
        }
        job?.cancel()
        connection?.disconnect()
        job = scope.launch { runDownload(context, entry) }
    }

    fun pause() {
        stopRequested = true
        val current = _state.value
        job?.cancel()
        connection?.disconnect()
        job = null
        if (current.status == Status.Downloading || current.status == Status.WaitingNetwork) {
            _state.value = current.copy(
                status = Status.Paused,
                bytesPerSecond = 0,
                message = "Загрузка приостановлена",
            )
        }
    }

    fun resume(context: Context) {
        val entry = _state.value.entryId?.let { ModelCatalog.byId(it) } ?: return
        start(context, entry)
    }

    fun cancel(context: Context, entry: ModelEntry) {
        stopRequested = true
        job?.cancel()
        connection?.disconnect()
        File(modelsDir(context), "${entry.fileName}.part").delete()
        _state.value = State(entry.id, Status.Idle)
    }

    /** Докачка после запуска приложения: незавершённая модель продолжается сама. */
    fun resumeIfNeeded(context: Context) {
        init(context)
        val part = modelsDir(context).listFiles()?.firstOrNull { it.name.endsWith(".part") } ?: return
        val fileName = part.name.removeSuffix(".part")
        val entry = ModelCatalog.entries.firstOrNull { it.fileName == fileName } ?: return
        if (isInstalled(context, entry)) return
        _state.value = State(entry.id, Status.Paused, part.length(), entry.sizeBytes, message = "Можно продолжить")
    }

    private suspend fun runDownload(context: Context, entry: ModelEntry) {
        var attempt = 0
        while (!stopRequested) {
            val target = fileOf(context, entry)
            val part = File(modelsDir(context), "${entry.fileName}.part")
            var existing = if (part.isFile) part.length() else 0L

            if (isInstalled(context, entry)) {
                _state.value = State(entry.id, Status.Done, entry.sizeBytes, entry.sizeBytes)
                return
            }

            if (!hasNetwork(context)) {
                _state.value = State(entry.id, Status.WaitingNetwork, existing, entry.sizeBytes, message = "Ждём сеть")
                waitForNetwork()
                continue
            }

            try {
                connection = (URL(ModelCatalog.downloadUrl(entry)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "OgeTrainer/0.1")
                    if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                }
                val active = connection ?: return
                val code = active.responseCode
                val resumed = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
                if (existing > 0 && !resumed) {
                    part.delete()
                    existing = 0
                }
                if (code !in 200..299) {
                    _state.value = State(entry.id, Status.Failed, existing, entry.sizeBytes, message = "HTTP $code")
                    return
                }

                val total = if (resumed) existing + active.contentLengthLong else active.contentLengthLong
                var downloaded = existing
                var windowStart = System.currentTimeMillis()
                var windowBytes = 0L

                active.inputStream.use { input ->
                    java.io.FileOutputStream(part, resumed).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (!stopRequested) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            windowBytes += read
                            // Пауза могла прийти во время чтения: не перетираем состояние «Приостановлено».
                            if (stopRequested) break
                            val elapsed = System.currentTimeMillis() - windowStart
                            val speed = if (elapsed > 400) {
                                (windowBytes * 1000 / elapsed).also {
                                    windowStart = System.currentTimeMillis()
                                    windowBytes = 0
                                }
                            } else {
                                _state.value.bytesPerSecond
                            }
                            if (!stopRequested) {
                                _state.value = State(
                                    entryId = entry.id,
                                    status = Status.Downloading,
                                    downloaded = downloaded,
                                    total = total.takeIf { it > 0 } ?: entry.sizeBytes,
                                    bytesPerSecond = speed,
                                )
                            }
                        }
                    }
                }
                connection?.disconnect()
                connection = null

                val length = part.length()
                if (length >= entry.sizeBytes * 0.98) {
                    if (target.exists()) target.delete()
                    if (part.renameTo(target)) {
                        _state.value = State(entry.id, Status.Done, length, length, message = "Модель готова")
                        return
                    }
                    _state.value = State(entry.id, Status.Failed, length, length, message = "Не удалось переименовать файл")
                    return
                }
                // Обрыв на середине: повторяем с докачкой, но не бесконечно.
                attempt++
                if (attempt > 40) {
                    _state.value = State(entry.id, Status.Failed, length, entry.sizeBytes, message = "Слишком много обрывов")
                    return
                }
                delay(1500L * attempt.coerceAtMost(5))
            } catch (error: Throwable) {
                connection?.disconnect()
                connection = null
                if (stopRequested) return
                Log.w("ModelDownloads", "Обрыв загрузки ${entry.id}", error)
                attempt++
                if (attempt > 40) {
                    _state.value = State(entry.id, Status.Failed, existing, entry.sizeBytes, message = error.message)
                    return
                }
                _state.value = State(
                    entryId = entry.id,
                    status = if (hasNetwork(context)) Status.Downloading else Status.WaitingNetwork,
                    downloaded = existing,
                    total = entry.sizeBytes,
                    message = error.message,
                )
                delay(2000L * attempt.coerceAtMost(5))
            }
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun waitForNetwork() {
        // Ждём сигнала от callback, но не дольше 30 с за один заход, чтобы не висеть вечно.
        var waited = 0L
        while (waited < 30_000 && !stopRequested) {
            delay(500)
            waited += 500
            if (networkAvailable) return
        }
    }

    @Volatile
    private var networkAvailable = false

    private var callbackRegistered = false

    private fun registerNetworkCallback() {
        if (callbackRegistered) return
        callbackRegistered = true
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkAvailable = hasNetwork(appContext)
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        networkAvailable = true
                        // Сеть вернулась — продолжаем, если ждали именно её.
                        if (_state.value.status == Status.WaitingNetwork) {
                            resume(appContext)
                        }
                    }

                    override fun onLost(network: Network) {
                        networkAvailable = false
                    }
                },
            )
        }
    }

    suspend fun delete(context: Context, entry: ModelEntry): Boolean = withContext(Dispatchers.IO) {
        cancel(context, entry)
        fileOf(context, entry).delete()
    }
}
