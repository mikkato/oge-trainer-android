package ru.tolmachev.ogetrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tolmachev.ogetrainer.domain.AnswerNormalizer
import ru.tolmachev.ogetrainer.domain.Mastery

class AnswerNormalizerTest {

    @Test
    fun `пробелы и регистр не важны`() {
        assertTrue(AnswerNormalizer.matches("  60 ", "60"))
        assertTrue(AnswerNormalizer.matches("Наблюдение", "наблюдение"))
    }

    @Test
    fun `запятая и точка эквивалентны`() {
        assertTrue(AnswerNormalizer.matches("2,5", "2.5"))
        assertTrue(AnswerNormalizer.matches("2.5", "2,5"))
    }

    @Test
    fun `все виды тире приводятся к дефису`() {
        // В банке ответы-наборы приходят с длинным тире: «А — 1, Б — 2».
        assertTrue(AnswerNormalizer.matches("А-1,Б-2", "А — 1, Б — 2"))
        assertTrue(AnswerNormalizer.matches("А–1,Б–2", "А — 1, Б — 2"))
        assertTrue(AnswerNormalizer.matches("А−1,Б−2", "А — 1, Б — 2"))
    }

    @Test
    fun `неверный ответ не принимается`() {
        assertFalse(AnswerNormalizer.matches("61", "60"))
        assertFalse(AnswerNormalizer.matches("А-1,Б-3", "А — 1, Б — 2"))
        assertFalse(AnswerNormalizer.matches("", "60"))
    }

    @Test
    fun `неразрывный пробел удаляется`() {
        assertTrue(AnswerNormalizer.matches("12\u00A0км", "12км"))
    }

    @Test
    fun `лемматизация именной морфологии — падеж и род не ломают ответ`() {
        assertTrue(AnswerNormalizer.matches("Земского собора", "земский собор"))
        assertTrue(AnswerNormalizer.matches("собору", "собор"))
        assertTrue(AnswerNormalizer.matches("рыжее", "рыжая"))
        assertTrue(AnswerNormalizer.matches("в реке", "в река"))
    }

    @Test
    fun `лемматизация не сливает орфографически разные формы`() {
        assertFalse(AnswerNormalizer.matches("стелется", "стелится"))
        assertFalse(AnswerNormalizer.matches("клеит", "клеят"))
        assertFalse(AnswerNormalizer.matches("собака", "кошка"))
        assertFalse(AnswerNormalizer.matches("рыжий", "синий"))
    }
}

class MasteryTest {

    @Test
    fun `первые попытки двигают освоение шагом 0_3`() {
        assertEquals(0.3, Mastery.next(0.0, 1.0, attempts = 0, difficulty = 1), 1e-9)
        assertEquals(0.51, Mastery.next(0.3, 1.0, attempts = 1, difficulty = 1), 1e-9)
    }

    @Test
    fun `повторные попытки двигают мягче - промах не обнуляет тему`() {
        // Шаг 0,20: с 0,72 неверный ответ даёт 0,576, тема остаётся «в работе», а не «слабо»
        val afterMiss = Mastery.next(0.72, 0.0, attempts = 4, difficulty = 1)
        assertEquals(0.576, afterMiss, 1e-9)
        assertFalse(Mastery.isCompleted(afterMiss))
        assertFalse(Mastery.isWeak(afterMiss, 5))
    }

    @Test
    fun `сложное задание двигает освоение сильнее`() {
        val easy = Mastery.next(0.4, 1.0, attempts = 5, difficulty = 1)
        val hard = Mastery.next(0.4, 1.0, attempts = 5, difficulty = 3)
        assertTrue("сложное $hard должно быть больше простого $easy", hard > easy)
    }

    @Test
    fun `освоение ограничено единицей и нулём`() {
        assertEquals(1.0, Mastery.next(1.0, 1.0, 3, 3), 1e-9)
        assertEquals(0.0, Mastery.next(0.0, 0.0, 3, 1), 1e-9)
    }

    @Test
    fun `порог освоенной темы 0_7`() {
        assertTrue(Mastery.isCompleted(0.7))
        assertFalse(Mastery.isCompleted(0.69))
    }

    @Test
    fun `слабость только при трёх и более попытках`() {
        assertFalse(Mastery.isWeak(0.3, 1))
        assertFalse(Mastery.isWeak(0.3, 2))
        assertTrue(Mastery.isWeak(0.3, 3))
        assertFalse(Mastery.isWeak(0.6, 5))
        assertEquals("мало данных", Mastery.statusLabel(0.3, 2))
        assertEquals("слабо", Mastery.statusLabel(0.3, 4))
        assertEquals("освоено", Mastery.statusLabel(0.75, 4))
    }

    @Test
    fun `затухание пять процентов в неделю`() {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val now = 1_000_000_000_000L
        val decayed = Mastery.decay(0.8, now - 2 * weekMs, now)
        assertEquals(0.8 * 0.95 * 0.95, decayed, 1e-9)
        assertEquals(0.8, Mastery.decay(0.8, now - 3 * 24 * 60 * 60 * 1000, now), 1e-9)
    }

    @Test
    fun `пять верных ответов подряд дают освоение`() {
        var mastery = 0.0
        var attempts = 0
        repeat(5) {
            mastery = Mastery.next(mastery, 1.0, attempts, 1)
            attempts++
        }
        assertTrue("после пяти верных: $mastery", Mastery.isCompleted(mastery))
    }

    @Test
    fun `сложность берётся из источника задания`() {
        assertEquals(1, ru.tolmachev.ogetrainer.domain.Difficulty.of("bank"))
        assertEquals(3, ru.tolmachev.ogetrainer.domain.Difficulty.of("local:3"))
        assertEquals(2, ru.tolmachev.ogetrainer.domain.Difficulty.of("llm:d2"))
    }
}
