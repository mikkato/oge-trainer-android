package ru.tolmachev.ogetrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tolmachev.ogetrainer.data.provider.LocalTemplates
import ru.tolmachev.ogetrainer.data.provider.RussianTemplates
import ru.tolmachev.ogetrainer.domain.AnswerNormalizer
import kotlin.random.Random

/**
 * Проверка шаблонов по русскому языку. Правило здесь пересчитывается независимо
 * от кода генерации: по букве из условия, по окончанию формы или по эталонному
 * предложению из разбора. Ответ не должен содержать «ё» — нормализатор её не сводит.
 */
class RussianTemplatesTest {

    /** Все темы русского 5–8 классов из assets/bank/grades.json. */
    private val rusTopics = listOf(
        "rus5.phonetics", "rus5.lexis", "rus5.morphemics", "rus5.noun", "rus5.adjective",
        "rus5.verb", "rus5.roots", "rus5.homogeneous",
        "rus6.numeral", "rus6.pronoun", "rus6.participle", "rus6.gerund", "rus6.prefixes", "rus6.not",
        "rus7.adverb", "rus7.preposition", "rus7.conjunction", "rus7.particle", "rus7.neither", "rus7.detached",
        "rus8.two_part", "rus8.one_part", "rus8.minor", "rus8.homogeneous", "rus8.definitions", "rus8.intro",
    )

    private fun drafts(topicCode: String, count: Int = 60) =
        LocalTemplates.drafts("rus", topicCode, count, Random(7), topicCode)

    @Test
    fun `каждая тема русского 5-8 имеет шаблоны`() {
        for (code in rusTopics) {
            val drafts = drafts(code)
            assertTrue("тема $code без шаблонов", drafts.isNotEmpty())
            drafts.forEach { draft ->
                assertTrue("пустой ответ у $code", draft.answer.isNotBlank())
                // Ответ должен сходиться с самим собой через нормализатор сверки.
                assertTrue(AnswerNormalizer.matches(draft.answer, draft.answer))
                // Буква ё ломает точное сравнение: пользователь вводит е.
                assertTrue(
                    "ответ с ё: ${draft.answer}",
                    !draft.answer.lowercase().contains('ё'),
                )
                assertTrue("пустой разбор у $code: ${draft.prompt}", draft.solution.isNotBlank())
            }
        }
    }

    @Test
    fun `приставки з-с пересчитываются из первой буквы корня`() {
        val word = Regex("""([А-Я]+)_([А-Я][А-Я]+)""")
        val voicedOrVowel = setOf(
            'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
            'б', 'в', 'г', 'д', 'ж', 'з', 'й', 'л', 'м', 'н', 'р',
        )
        var checked = 0
        drafts("rus6.prefixes").forEach { draft ->
            val m = word.find(draft.prompt) ?: return@forEach
            val rootStart = m.groupValues[2].first().lowercaseChar()
            val expected = if (rootStart in voicedOrVowel) "з" else "с"
            assertEquals("условие: ${draft.prompt}", expected, draft.answer)
            checked++
        }
        assertTrue("проверено $checked", checked > 10)
    }

    @Test
    fun `число запятых сходится с эталонным предложением`() {
        val quoted = Regex("""«(.+?)»""")
        val commaTopics = listOf("rus5.homogeneous", "rus7.detached", "rus8.homogeneous", "rus8.definitions")
        var checked = 0
        commaTopics.forEach { code ->
            drafts(code).forEach { draft ->
                if (!draft.prompt.startsWith("Сколько запятых")) return@forEach
                val raw = quoted.find(draft.prompt)?.groupValues?.get(1) ?: return@forEach
                val punct = quoted.find(draft.solution)?.groupValues?.get(1) ?: return@forEach
                assertEquals("эталон без знаков равен условию: $raw", raw, punct.replace(",", ""))
                assertEquals("эталон: $punct", draft.answer.toInt(), punct.count { it == ',' })
                checked++
            }
        }
        assertTrue("проверено $checked", checked > 10)
    }

    @Test
    fun `спряжение глаголов соответствует правилу с исключениями`() {
        val secondExceptions = setOf(
            "гнать", "дышать", "держать", "обидеть", "видеть", "ненавидеть",
            "слышать", "зависеть", "терпеть", "обмануть", "заманить",
        )
        val firstExceptions = setOf("брить", "стелить", "веять")
        var checked = 0
        drafts("rus5.verb").forEach { draft ->
            val m = Regex("""Определи спряжение глагола ([А-ЯЁ]+)\.""").find(draft.prompt) ?: return@forEach
            val verb = m.groupValues[1].lowercase()
            val expected = when {
                verb in firstExceptions -> "1"
                verb in secondExceptions -> "2"
                verb.endsWith("ить") -> "2"
                else -> "1"
            }
            assertEquals("глагол $verb", expected, draft.answer)
            checked++
        }
        assertTrue("проверено $checked", checked > 10)
    }

    @Test
    fun `род прилагательного выводится из окончания`() {
        var checked = 0
        drafts("rus5.adjective").forEach { draft ->
            val m = Regex("""в форме ([А-ЯЁ]+)\?""").find(draft.prompt) ?: return@forEach
            val form = m.groupValues[1].lowercase()
            val expected = when {
                form.endsWith("ая") || form.endsWith("яя") -> "женский"
                form.endsWith("ое") || form.endsWith("ее") -> "средний"
                form.endsWith("ый") || form.endsWith("ой") || form.endsWith("ий") -> "мужской"
                else -> error("вне правила: $form")
            }
            assertEquals("форма $form", expected, draft.answer)
            checked++
        }
        assertTrue("проверено $checked", checked > 10)
    }

    @Test
    fun `числительные записываются корректными числами`() {
        drafts("rus6.numeral").forEach { draft ->
            val digits = draft.answer.toIntOrNull()
            assertTrue("не число: ${draft.answer}", digits != null && digits in 1..1000)
        }
    }

    @Test
    fun `ответ поиска слова присутствует в списке вариантов`() {
        val codes = listOf("rus5.phonetics", "rus6.participle", "rus6.gerund", "rus7.adverb", "rus7.preposition", "rus7.conjunction", "rus7.particle")
        var checked = 0
        codes.forEach { code ->
            drafts(code).forEach { draft ->
                val m = Regex("""вариантов|: (.+)\.""").find(draft.prompt) ?: return@forEach
                val options = m.groupValues[1].split(",").map { AnswerNormalizer.normalize(it) }
                if (options.isNotEmpty() && options.all { it.isNotBlank() } && !draft.prompt.contains("числом")) {
                    assertTrue(
                        "ответ ${draft.answer} не в вариантах: ${draft.prompt}",
                        AnswerNormalizer.normalize(draft.answer) in options ||
                            options.any { it == AnswerNormalizer.normalize(draft.answer) },
                    )
                    checked++
                }
            }
        }
        assertTrue("проверено $checked", checked > 20)
    }

    @Test
    fun `вводное слово и тип односоставного предложения в допустимых значениях`() {
        val introAnswers = setOf("конечно", "кажется", "во-первых", "к сожалению", "может быть", "короче говоря")
        drafts("rus8.intro").forEach { assertTrue("вводное: ${it.answer}", it.answer in introAnswers) }
        drafts("rus8.one_part").forEach { assertTrue("тип: ${it.answer}", it.answer in setOf("1", "2", "3")) }
        drafts("rus8.minor").forEach {
            assertTrue("член: ${it.answer}", it.answer in setOf("дополнение", "обстоятельство", "определение"))
        }
        drafts("rus6.not").forEach { assertTrue(it.answer in setOf("слитно", "раздельно")) }
        drafts("rus7.neither").forEach { assertTrue(it.answer in setOf("не", "ни")) }
    }
}
