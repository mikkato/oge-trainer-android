package ru.tolmachev.ogetrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tolmachev.ogetrainer.ui.components.MathText

/** Формулы и markdown должны превращаться в чистый текст для списков. */
class MathTextTest {

    @Test
    fun `степени и индексы становятся символами`() {
        assertEquals("Решите x² − 4x + 4 = 0.", MathText.render("Решите \$x^2 - 4x + 4 = 0\$."))
        assertEquals("aₙ = n² + 1", MathText.render("\$a_{n} = n^{2} + 1\$"))
    }

    @Test
    fun `дроби корни и знаки читаются словами`() {
        assertEquals("1/2 + √9 = 5,5", MathText.render("\$\\frac{1}{2} + \\sqrt{9} = 5{,}5\$"))
        assertEquals("a ≤ b ≠ c", MathText.render("\$a \\le b \\ne c\$"))
    }

    @Test
    fun `markdown-обвязка удаляется`() {
        val text = MathText.render("**Найдите** площадь *прямоугольника* со сторонами 3 и 4")
        assertEquals("Найдите площадь прямоугольника со сторонами 3 и 4", text)
        assertFalse(text.contains("*"))
    }

    @Test
    fun `служебные символы не остаются в выводе`() {
        val text = MathText.render("\$\\left( x + 1 \\right)^{2} \\cdot 3\$ м")
        assertFalse("в тексте остался \$ или \\\\: $text", text.contains("$") || text.contains("\\"))
        assertTrue(text.contains("·"))
    }
}
