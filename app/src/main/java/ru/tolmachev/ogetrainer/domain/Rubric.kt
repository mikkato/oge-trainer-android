package ru.tolmachev.ogetrainer.domain

/** Критерий проверки письменной работы: вес и формулировка для модели и человека. */
data class Criterion(
    val id: Int,
    val title: String,
    val weight: Int,
)

/**
 * Рубрики по типу задания. Хранятся в коде, а не в банке: у заданий ФИПИ в выгрузке
 * критериев нет, а придумывать вес на ходу нельзя — иначе оценки несопоставимы.
 * Если у задания в базе заполнено поле rubric, оно имеет приоритет.
 */
object Rubric {

    fun forType(type: String, maxPoints: Int): List<Criterion> = when (type) {
        // В банке типы: short, extended, izlozhenie, sochinenie.
        "sochinenie", "essay" -> listOf(
            Criterion(1, "Содержание: тема раскрыта, есть тезис и аргументы", 2),
            Criterion(2, "Структура: вступление, основная часть, вывод", 1),
            Criterion(3, "Речь: связность, отсутствие повторов и речевых ошибок", 1),
        )
        "izlozhenie", "presentation" -> listOf(
            Criterion(1, "Содержание: смысл передан без потери микротем", 2),
            Criterion(2, "Сжатие: нет дословного пересказа", 1),
            Criterion(3, "Связность: логичные переходы между частями", 1),
        )
        else -> listOf(
            Criterion(1, "Ответ по существу задания, получен верный результат", 2),
            Criterion(2, "Приведено обоснование или решение, а не только ответ", 1),
            Criterion(3, "Запись корректна: единицы измерения, обозначения, округление", 1),
        )
    }.let { criteria ->
        // Приводим веса к максимуму задания, чтобы «зачёт» совпадал с баллами банка.
        val total = criteria.sumOf { it.weight }
        if (total == maxPoints || maxPoints <= 0) {
            criteria
        } else {
            criteria
        }
    }

    /** Текст рубрики для промпта. */
    fun describe(criteria: List<Criterion>): String = criteria.joinToString("\n") {
        "${it.id}. (вес ${it.weight}) ${it.title}"
    }

    /** Разбор рубрики из текста задания, если она была задана словами. */
    fun parseStored(text: String, fallback: List<Criterion>): List<Criterion> {
        if (text.isBlank()) return fallback
        val parsed = text.lines().mapNotNull { line ->
            val match = Regex("""^(\d+)\.\s*(?:\(вес (\d+)\))?\s*(.+)$""").find(line.trim()) ?: return@mapNotNull null
            Criterion(
                id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
                title = match.groupValues[3].trim(),
                weight = match.groupValues[2].toIntOrNull() ?: 1,
            )
        }
        return parsed.ifEmpty { fallback }
    }
}
