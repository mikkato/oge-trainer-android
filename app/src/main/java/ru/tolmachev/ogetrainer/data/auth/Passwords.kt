package ru.tolmachev.ogetrainer.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Пароли профилей: PBKDF2-HMAC-SHA256, 210 000 итераций, соль 16 байт, ключ 256 бит.
 * Сравнение — в постоянное время.
 */
object Passwords {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(password: String, saltB64: String): String {
        val salt = Base64.getDecoder().decode(saltB64)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(password: String, saltB64: String, expectedB64: String): Boolean {
        val actual = hash(password, saltB64)
        return MessageDigest.isEqual(
            Base64.getDecoder().decode(actual),
            Base64.getDecoder().decode(expectedB64),
        )
    }

    fun isAcceptable(password: String): String? = when {
        password.length < 6 -> "Пароль короче 6 символов"
        else -> null
    }
}
