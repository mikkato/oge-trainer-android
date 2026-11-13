package ru.tolmachev.ogetrainer.domain

/**
 * Правила освоения темы.
 *
 * Базовая идея прежняя (EMA), но параметры откалиброваны:
 *  - порог «освоено» снижен до 0,7: при 0,8 требовалось пять безошибочных ответов подряд;
 *  - шаг зависит от числа попыток: первые две двигают сильнее (0,30), повторные — мягче (0,15),
 *    поэтому один промах не обнуляет статус;
 *  - шаг зависит от сложности задания (вес 1,0 / 1,2 / 1,4 за difficulty 1 / 2 / 3);
 *  - «слабо» и «мало данных» разделены: до трёх попыток вывод о слабости не делается;
 *  - есть затухание 5 % в неделю без практики.
 */
object Mastery {

    /** Порог «тема освоена». */
    const val COMPLETED_THRESHOLD = 0.7

    /** Порог «тема слабая» (только при достаточном числе попыток). */
    const val WEAK_THRESHOLD = 0.5

    /** С какого числа попыток вывод считается значимым. */
    const val SIGNIFICANT_ATTEMPTS = 3

    /** Порог для тем с частичными баллами: средний score последних попыток. */
    const val RECENT_SCORE_THRESHOLD = 0.7

    /** Затухание освоения без практики: 5 % в неделю. */
    const val DECAY_PER_WEEK = 0.95

    /**
     * Шаг EMA: первые две попытки — 0,30, дальше 0,20; вес сложности 1,0…1,4.
     * 0,15 давало 0,699 после пяти верных ответов — ниже порога 0,7 на тысячную,
     * поэтому повторный шаг поднят до 0,20: тема закрывается за пять верных подряд.
     */
    fun step(attempts: Int, difficulty: Int): Double {
        val base = if (attempts < 2) 0.30 else 0.20
        val weight = 0.8 + 0.2 * difficulty.coerceIn(1, 3)
        return (base * weight).coerceIn(0.10, 0.45)
    }

    /** Новое значение освоения: сдвиг к результату на величину шага. */
    fun next(current: Double, score: Double, attempts: Int, difficulty: Int): Double {
        val step = step(attempts, difficulty)
        return (current + step * (score - current)).coerceIn(0.0, 1.0)
    }

    /** Затухание: без практики освоение снижается на 5 % за каждую полную неделю. */
    fun decay(mastery: Double, lastAttemptAt: Long, now: Long = System.currentTimeMillis()): Double {
        if (lastAttemptAt <= 0L) return mastery
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val weeks = (now - lastAttemptAt) / weekMs
        if (weeks <= 0) return mastery
        var value = mastery
        repeat(weeks.coerceAtMost(52).toInt()) { value *= DECAY_PER_WEEK }
        return value.coerceIn(0.0, 1.0)
    }

    fun isCompleted(mastery: Double): Boolean = mastery >= COMPLETED_THRESHOLD

    /** Для тем, где задание даёт частичные баллы: смотрим средний результат последних попыток. */
    fun isCompletedByRecent(recentScore: Double?): Boolean =
        recentScore != null && recentScore >= RECENT_SCORE_THRESHOLD

    fun isSignificant(attempts: Int): Boolean = attempts >= SIGNIFICANT_ATTEMPTS

    fun isWeak(mastery: Double, attempts: Int): Boolean =
        isSignificant(attempts) && mastery < WEAK_THRESHOLD

    /** Текст статуса для интерфейса. */
    fun statusLabel(mastery: Double, attempts: Int): String = when {
        attempts == 0 -> "нет данных"
        !isSignificant(attempts) -> "мало данных"
        isCompleted(mastery) -> "освоено"
        isWeak(mastery, attempts) -> "слабо"
        else -> "в работе"
    }
}

/**
 * Нормализация короткого ответа: регистр, пробелы, запятая→точка, все тире к дефису.
 * matches() дополнительно сравнивает по основанию слов (лёгкая лемматизация),
 * поэтому падеж/род/спряжение ответа не ломают проверку точных ответов (решение 7).
 */
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

    /**
     * Очередь суффиксов ИМЕННОЙ морфологии (существительное/прилагательное/причастие).
     * Глагольные формы и -ся/-сь не сводим намеренно: для орфографических
     * заданий «стелется/стелится», «клеит/клеят» — разные правильные ответы.
     */
    private val SUFFIXES = listOf(
        "иями", "ями", "ами", "ими", "ыми", "ого", "его", "ому", "ему",
        "ых", "их", "ым", "им", "ом", "ём", "ем", "ов", "ев",
        "ах", "ях", "ам", "ям", "ий", "ый", "ая", "яя", "ое", "ее", "ые", "ие",
        "ой", "ей", "ую", "юю", "ь", "у", "ю", "ы", "и", "а", "я", "о", "е",
    )

    /** Основание слова для сверки: консервативно, не короче 3 букв. */
    fun stem(word: String): String {
        var w = word.lowercase()
        if (w.length < 4 || !w.all { it.isLetter() && it in 'а'..'я' || it == 'ё' }) return w
        if (w.endsWith("ся") || w.endsWith("сь")) return w
        repeat(2) {
            val cut = SUFFIXES.firstOrNull { w.length - it.length >= 3 && w.endsWith(it) }
                ?: return@repeat
            w = w.dropLast(cut.length)
        }
        return w
    }

    private fun stems(raw: String): List<String> =
        raw.lowercase().replace('ё', 'е')
            .split(Regex("[^а-яa-z0-9.+-]+"))
            .filter { it.isNotEmpty() }.map { stem(it) }

    fun matches(userAnswer: String, reference: String): Boolean {
        if (normalize(userAnswer) == normalize(reference)) return true
        val u = stems(userAnswer)
        val r = stems(reference)
        return u.isNotEmpty() && u == r
    }
}

/**
 * Сложность задания. Хранится в поле origin: шаблоны пишут "local:2",
 * модели — "llm:d2"/"llm-local"; у заданий банка сложность неизвестна и считается базовой.
 */
object Difficulty {
    fun of(origin: String): Int {
        Regex("([123])$").find(origin)?.let { return it.groupValues[1].toInt() }
        Regex("[dD]([123])").find(origin)?.let { return it.groupValues[1].toInt() }
        return 1
    }

    fun label(difficulty: Int): String = when (difficulty) {
        3 -> "повышенная"
        2 -> "средняя"
        else -> "базовая"
    }
}
