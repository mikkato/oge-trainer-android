package ru.tolmachev.ogetrainer.data.llm

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import ru.tolmachev.ogetrainer.data.models.DeviceProfile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Контроль ресурсов перед запуском локальной модели.
 *
 * Задача — исключить OOM: на телефоне процесс убивается системой, и это выглядит
 * как «приложение вылетело» без сообщения. Поэтому до загрузки считаем бюджет:
 * размер модели + KV-кеш (по метаданным GGUF) + запас на работу Android,
 * и сравниваем со свободной памятью. Если не влезает — не грузим вовсе,
 * а показываем, сколько не хватает. Во время генерации память проверяется
 * каждые 16 токенов через [TokenSink.allow].
 */
object LlmResources {

    data class Plan(
        val modelBytes: Long,
        val contextSize: Int,
        val kvBytes: Long,
        val reserveBytes: Long,
        val threads: Int,
        val fits: Boolean,
        val reason: String?,
    ) {
        val totalBytes: Long get() = modelBytes + kvBytes + reserveBytes
    }

    /** Метаданные GGUF, нужные для расчёта KV-кеша. */
    data class GgufMeta(
        val blockCount: Int,
        val headCountKv: Int,
        val keyLength: Int,
        val valueLength: Int,
    ) {
        /** Байт KV-кеша на один токен: K и V, по 2 байта на элемент (f16). */
        val bytesPerToken: Long
            get() = blockCount.toLong() * (keyLength + valueLength) * 2L * headCountKv
    }

    fun availableBytes(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        return info.availMem
    }

    fun lowMemory(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        return info.lowMemory
    }

    /**
     * Бюджет под конкретную модель. Контекст уменьшается, пока KV-кеш не уложится
     * в четверть свободной памяти: 4096 → 2048 → 1024 токенов.
     */
    fun plan(context: Context, modelFile: File, device: DeviceProfile): Plan {
        val modelBytes = modelFile.length()
        val meta = readMeta(modelFile)
        val threads = threadsFor(device)
        val reserve = RESERVE_BYTES
        val available = availableBytes(context)

        val candidates = listOf(4096, 2048, 1024)
        for (ctx in candidates) {
            val kv = meta?.let { it.bytesPerToken * ctx } ?: (ctx.toLong() * 64L * 1024L)
            val total = modelBytes + kv + reserve
            val fitsMemory = total <= available * 0.85
            val fitsDevice = device.ramGb * 1024 * 1024 * 1024 >= total
            if (fitsMemory && fitsDevice) {
                return Plan(modelBytes, ctx, kv, reserve, threads, fits = true, reason = null)
            }
        }

        val ctx = candidates.last()
        val kv = meta?.let { it.bytesPerToken * ctx } ?: (ctx.toLong() * 64L * 1024L)
        val total = modelBytes + kv + reserve
        val reason = buildString {
            append("Нужно ")
            append("%.1f".format(java.util.Locale("ru"), total / 1024.0 / 1024.0 / 1024.0))
            append(" ГБ, свободно ")
            append("%.1f".format(java.util.Locale("ru"), available / 1024.0 / 1024.0 / 1024.0))
            append(" ГБ")
            if (device.ramGb * 1024 * 1024 * 1024 < total) append(". Мало ОЗУ для этой модели")
        }
        return Plan(modelBytes, ctx, kv, reserve, threads, fits = false, reason = reason)
    }

    fun threadsFor(device: DeviceProfile): Int = (device.cpuCores - 1).coerceIn(2, 4)

    /**
     * Минимальный разбор GGUF: заголовок (magic, версия, числа) и метаданные.
     * Читаем только ключи, нужные для расчёта KV-кеша; строки и массивы
     * пропускаем по их длине, чтобы не тянуть в память весь файл.
     */
    fun readMeta(file: File): GgufMeta? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic) != "GGUF") return null
            readU32(raf)                    // версия формата
            readU64(raf)                    // число тензоров
            val kvCount = readU64(raf)      // число пар ключ-значение
            if (kvCount <= 0 || kvCount > 100_000) return null

            var blockCount = 0
            var headCountKv = 0
            var keyLength = 0
            var valueLength = 0
            var embeddingLength = 0

            repeat(kvCount.toInt()) {
                val key = readString(raf)
                val type = readU32(raf)
                val value = readValue(raf, type)
                when {
                    key.endsWith("block_count") -> blockCount = (value as? Long)?.toInt() ?: blockCount
                    key.endsWith("attention.head_count_kv") -> headCountKv = asInt(value)
                    key.endsWith("attention.key_length") -> keyLength = asInt(value)
                    key.endsWith("attention.value_length") -> valueLength = asInt(value)
                    key.endsWith("embedding_length") -> embeddingLength = asInt(value)
                }
            }

            if (blockCount <= 0) return null
            if (headCountKv <= 0) headCountKv = 1
            if (keyLength <= 0) keyLength = if (embeddingLength > 0) embeddingLength / 64 else 128
            if (valueLength <= 0) valueLength = keyLength
            GgufMeta(blockCount, headCountKv, keyLength, valueLength)
        }
    }.onFailure { Log.w("LlmResources", "GGUF meta не прочитаны", it) }.getOrNull()

    private fun asInt(value: Any?): Int = when (value) {
        is Long -> value.toInt()
        is Int -> value
        is List<*> -> (value.firstOrNull() as? Long)?.toInt() ?: 0
        else -> 0
    }

    private fun readU32(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readU64(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readString(raf: RandomAccessFile): String {
        val length = readU64(raf).toInt()
        if (length < 0 || length > 1_000_000) throw IllegalStateException("длина строки $length")
        val bytes = ByteArray(length)
        raf.readFully(bytes)
        return String(bytes)
    }

    /** Значения GGUF по типам: 0..12 — скаляры, массивы и строки. */
    private fun readValue(raf: RandomAccessFile, type: Int): Any? = when (type) {
        0 -> raf.readUnsignedByte().toLong()
        1 -> raf.readByte().toLong()
        2 -> raf.readUnsignedShort().toLong()
        3 -> raf.readShort().toLong()
        4 -> readU32(raf).toLong() and 0xFFFFFFFFL
        5 -> readU32(raf).toLong()
        6 -> readU64(raf)
        7 -> readU64(raf)
        8 -> raf.readFloat().toDouble()
        9 -> raf.readDouble()
        10 -> raf.readUnsignedByte().toLong()
        11 -> readString(raf).length.toLong()
        12 -> {
            val itemType = readU32(raf)
            val count = readU64(raf).toInt()
            if (count < 0 || count > 100_000) return null
            var first: Any? = null
            repeat(count) {
                val item = readValue(raf, itemType)
                if (first == null) first = item
            }
            first
        }
        else -> null
    }

    private const val RESERVE_BYTES = 400L * 1024 * 1024
}
