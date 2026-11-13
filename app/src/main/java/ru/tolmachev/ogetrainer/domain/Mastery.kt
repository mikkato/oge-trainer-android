package ru.tolmachev.ogetrainer.domain

/**
 * Правила освоения — те же, что были на сервере (reports.py / attempts.py):
 * EMA 0,7·старое + 0,3·результат, «освоено» ≥ 0,8, «слабо» — есть попытки и < 0,5.
 */
object Mastery {
    const val COMPLETED_THRESHOLD = 0.8
    const val WEAK_THRESHOLD = 0.5

    fun next(current: Double, score: Double): Double =
        (0.7 * current + 0.3 * score).coerceIn(0.0, 1.0)

    fun isCompleted(mastery: Double): Boolean = mastery >= COMPLETED_THRESHOLD

    fun isWeak(mastery: Double, attemptsCount: Int): Boolean =
        attemptsCount > 0 && mastery < WEAK_THRESHOLD
}

/** Нормализация короткого ответа: регистр, пробелы, запятая→точка, все тире к дефису. */
object AnswerNormalizer {
    fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace('\u00A0', ' ')
            .replace('\u2014', '-')   // —
            .replace('\u2013', '-')   // –
            .replace('\u2212', '-')   // −
            .replace(',', '.')
            .filterNot { it.isWhitespace() }

    fun matches(userAnswer: String, reference: String): Boolean =
        normalize(userAnswer) == normalize(reference)
}
