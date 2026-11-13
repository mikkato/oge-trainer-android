package ru.tolmachev.ogetrainer.data.provider

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Ключ LLM-провайдера. Хранится только в EncryptedSharedPreferences (ключ в Android Keystore),
 * в базе — лишь флаг has_key. В логи и в UI ключ не попадает (маска вида sk-ab…yz).
 */
class ProviderStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "provider_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun apiKey(): String? = runCatching { prefs.getString(KEY, null) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun saveKey(key: String) {
        runCatching { prefs.edit().putString(KEY, key.trim()).apply() }
    }

    fun clearKey() {
        runCatching { prefs.edit().remove(KEY).apply() }
    }

    /** Маска для интерфейса: sk-ab…вните остальное скрыто. */
    fun maskedKey(): String {
        val key = apiKey() ?: return ""
        return if (key.length <= 8) "*".repeat(key.length) else "${key.take(4)}…${key.takeLast(4)}"
    }

    private companion object {
        const val KEY = "llm_api_key"
    }
}
