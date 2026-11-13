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
}

class MasteryTest {

    @Test
    fun `EMA считается по правилу 0_7 на 0_3`() {
        assertEquals(0.3, Mastery.next(0.0, 1.0), 1e-9)
        assertEquals(0.51, Mastery.next(0.3, 1.0), 1e-9)
        assertEquals(0.357, Mastery.next(0.51, 0.0), 1e-9)
    }

    @Test
    fun `освоение ограничено единицей и нулём`() {
        assertEquals(1.0, Mastery.next(1.0, 1.0), 1e-9)
        assertEquals(0.0, Mastery.next(0.0, 0.0), 1e-9)
    }

    @Test
    fun `порог освоенной темы 0_8`() {
        assertTrue(Mastery.isCompleted(0.8))
        assertFalse(Mastery.isCompleted(0.79))
    }

    @Test
    fun `слабая тема требует попыток`() {
        assertTrue(Mastery.isWeak(0.4, 1))
        assertFalse(Mastery.isWeak(0.4, 0))
        assertFalse(Mastery.isWeak(0.5, 3))
    }

    @Test
    fun `прирост до порога за серию верных ответов`() {
        var mastery = 0.0
        repeat(5) { mastery = Mastery.next(mastery, 1.0) }
        assertTrue("после пяти верных ответов тема освоена: $mastery", Mastery.isCompleted(mastery))
    }
}
