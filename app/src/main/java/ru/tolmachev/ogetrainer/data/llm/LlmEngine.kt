package ru.tolmachev.ogetrainer.data.llm

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Встроенный движок llama.cpp (нативная библиотека libogellm.so в APK).
 *
 * Память: модель грузится через mmap, поэтому её страницы вытесняемы системой.
 * Дополнительно [TokenSink.allow] опрашивается каждые 16 токенов — если свободной
 * памяти стало меньше порога, генерация останавливается до OOM, а не после.
 */
class LlmEngine {

    interface TokenSink {
        /** Очередной кусок текста. Вызывается из нативного потока. */
        fun onToken(text: String)

        /** Продолжать ли генерацию: false — мягкая остановка. */
        fun allow(): Boolean
    }

    private var handle: Long = 0

    val isLoaded: Boolean get() = handle != 0L

    /**
     * Загружает модель. Возвращает true при успехе; при false причина — в логе,
     * а вызывающий код должен показать бюджет памяти из [LlmResources].
     */
    fun load(path: String, contextSize: Int, threads: Int, useMmap: Boolean = true): Boolean {
        if (handle != 0L) unload()
        handle = nativeLoad(path, contextSize, threads, useMmap)
        Log.i(TAG, "load: path=$path ctx=$contextSize threads=$threads result=${handle != 0L}")
        return handle != 0L
    }

    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float = 0.6f,
        topK: Int = 40,
        topP: Float = 0.95f,
        stopFlag: AtomicBoolean? = null,
        sink: TokenSink,
    ): String {
        if (handle == 0L) return ""
        val guard = object : TokenSink {
            override fun onToken(text: String) = sink.onToken(text)
            override fun allow(): Boolean = !(stopFlag?.get() ?: false) && sink.allow()
        }
        return nativeGenerate(handle, prompt, maxTokens, temperature, topK, topP, guard)
    }

    fun unload() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0
        }
    }

    fun modelBytes(): Long = if (handle == 0L) 0 else nativeModelBytes(handle)

    private external fun nativeLoad(path: String, contextSize: Int, threads: Int, useMmap: Boolean): Long

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenSink,
    ): String

    private external fun nativeFree(handle: Long)

    private external fun nativeModelBytes(handle: Long): Long

    companion object {
        private const val TAG = "LlmEngine"

        /** Движок может отсутствовать (например, сборка без NDK) — тогда приложение работает без него. */
        val available: Boolean by lazy {
            runCatching {
                System.loadLibrary("ogellm")
                true
            }.getOrElse {
                Log.e(TAG, "нативная библиотека недоступна", it)
                false
            }
        }
    }
}
