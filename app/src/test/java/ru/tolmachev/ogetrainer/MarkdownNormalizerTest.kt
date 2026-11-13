package ru.tolmachev.ogetrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tolmachev.ogetrainer.ui.components.MarkdownNormalizer

/**
 * Нормализатор отдаёт KaTeX готовый текст: без HTML-сущностей, без \(...\),
 * с обёрнутой голой математикой и без повторной обёртки.
 * Доллар собирается из кода символа, чтобы не спорить с интерполяцией Kotlin.
 */
class MarkdownNormalizerTest {

    private val d = Char(36).toString()
    private val dd = d + d

    @Test
    fun `html-сущности раскрываются`() {
        val output = MarkdownNormalizer.toKatex("Найдите &#119886;^2 при a = 3")
        // 119886 — математическая курсивная «a» вне BMP: сущность должна раскрыться, текст — сохраниться.
        assertFalse("сущность осталась: $output", output.contains("&#"))
        assertTrue("текст потерян: $output", output.contains("Найдите") && output.contains("при"))
        assertTrue("нет обёртки формулы: $output", output.contains(d))
    }

    @Test
    fun `голая степень становится формулой`() {
        val output = MarkdownNormalizer.toKatex("Решите уравнение x^2 - 5x + 6 = 0.")

        assertTrue("нет обёртки: $output", output.contains(d))
        assertTrue(output.contains("x^2"))
    }

    @Test
    fun `уже размеченная формула не оборачивается повторно`() {
        val source = "Уравнение " + d + "x^2 - 1 = 0" + d + " имеет два корня"
        val output = MarkdownNormalizer.toKatex(source)
        assertFalse("двойная обёртка: $output", output.contains(dd + "x^2 - 1 = 0" + dd))
        assertEquals(2, output.count { it.toString() == d })
    }

    @Test
    fun `круглые скобки latex превращаются в доллары`() {
        val output = MarkdownNormalizer.toKatex("Значение \\(a_n = n^2 + 1\\) для n = 5")
        assertTrue(output.contains(d + "a_n = n^2 + 1" + d))
        assertFalse(output.contains("\\("))
    }

    @Test
    fun `русский текст без математики не трогается`() {
        val output = MarkdownNormalizer.toKatex("Сколько рублей стала стоить вещь?")
        assertEquals("Сколько рублей стала стоить вещь?", output)
    }

    @Test
    fun `плейсхолдеры формулы не остаются в тексте`() {
        val output = MarkdownNormalizer.toKatex(
            "Дано " + d + "x + 1 = 2" + d + " и \\(y = 3\\).",
        )
        assertFalse(output.contains(Char(0).toString()))
        assertEquals(4, output.count { it.toString() == d })
    }

    @Test
    fun `в простом тексте не остаётся latex-команд`() {
        val plain = MarkdownNormalizer.toPlain(
            "Найдите \\frac{1}{2} от 60 и сравните с " + d + "\\sqrt{9}" + d,
        )
        assertFalse(plain.contains("\\frac"))
        assertFalse(plain.contains(d))
        assertTrue(plain.contains("1/2"))
    }

    @Test
    fun `формула с дробями не получает лишних долларов`() {
        val source = "Отсюда сокращение: " + d + "\\dfrac{6}{8} = \\dfrac{3}{4}" + d + "."
        val output = MarkdownNormalizer.toKatex(source)
        assertEquals("Отсюда сокращение: $d\\dfrac{6}{8} = \\dfrac{3}{4}$.", output)
        assertFalse("двойные доллары: $output", output.contains(dd))
    }

    @Test
    fun `блочная формула становится строчной`() {
        val source = dd + "\\frac{a}{b} \\pm \\frac{c}{d}" + dd
        val output = MarkdownNormalizer.toKatex(source)
        assertEquals("$d\\frac{a}{b} \\pm \\frac{c}{d}$", output)
    }

    @Test
    fun `голая команда latex оборачивается один раз`() {
        val output = MarkdownNormalizer.toKatex("Дробь \\frac{1}{2} меньше единицы")
        assertEquals("Дробь $d\\frac{1}{2}$ меньше единицы", output)
    }

    @Test
    fun `несколько формул в тексте не склеиваются`() {
        val source = "Пример: " + d + "\\dfrac{2}{4} - \\dfrac{2}{8} = \\dfrac{1}{4}" + d + " верно."
        val output = MarkdownNormalizer.toKatex(source)
        assertEquals(2, output.count { it == Char(36) })
    }
}
