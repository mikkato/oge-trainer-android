package ru.tolmachev.ogetrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tolmachev.ogetrainer.data.provider.LocalTemplates
import kotlin.random.Random

/**
 * Проверка локального генератора: ответ пересчитывается из условия независимо
 * от кода генерации. В регулярных выражениях — сырые строки, поэтому обратный
 * слэш пишется один раз.
 */
class LocalTemplatesTest {

    private fun drafts(topic: String, count: Int = 40) =
        LocalTemplates.drafts("math", topic, count, Random(42))

    @Test
    fun `линейные уравнения решаются верно`() {
        val equation = Regex("""Решите уравнение (-?\d+) x ([+−]) (\d+) = (-?\d+)\.""")
        var checked = 0
        drafts("Линейные и квадратные уравнения").forEach { draft ->
            val match = equation.find(draft.prompt) ?: return@forEach
            val a = match.groupValues[1].toInt()
            val sign = if (match.groupValues[2] == "−") -1 else 1
            val b = sign * match.groupValues[3].toInt()
            val c = match.groupValues[4].toInt()
            val x = draft.answer.toInt()
            assertEquals("условие: ${draft.prompt}", c, a * x + b)
            checked++
        }
        assertTrue("проверено $checked заданий", checked > 10)
    }

    @Test
    fun `прогрессия считается по формуле`() {
        val regex = Regex("""\${'$'}a_1 = (-?\d+).*?\+ (\d+)\. Найдите \${'$'}a_\{?(\d+)\}?\.""")
        var checked = 0
        drafts("Арифметическая прогрессия").forEach { draft ->
            val match = regex.find(draft.prompt) ?: return@forEach
            val a1 = match.groupValues[1].toInt()
            val d = match.groupValues[2].toInt()
            val n = match.groupValues[3].toInt()
            assertEquals("условие: ${draft.prompt}", a1 + (n - 1) * d, draft.answer.toInt())
            checked++
        }
        assertTrue("проверено $checked заданий", checked > 10)
    }

    @Test
    fun `проценты и площадь не расходятся с условием`() {
        var percentChecked = 0
        drafts("Проценты").forEach { draft ->
            val match = Regex("""стоил (\d+) рублей.*?на (\d+) %""").find(draft.prompt) ?: return@forEach
            val base = match.groupValues[1].toInt()
            val percent = match.groupValues[2].toInt()
            val expected = base - base * percent / 100
            assertEquals("условие: ${draft.prompt}", expected, draft.answer.toInt())
            percentChecked++
        }
        assertTrue("проверено $percentChecked процентных задач", percentChecked > 0)

        drafts("Площади фигур").forEach { draft ->
            val match = Regex("""сторонами (\d+) см и (\d+) см""").find(draft.prompt) ?: return@forEach
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            assertEquals("условие: ${draft.prompt}", a * b, draft.answer.toInt())
        }
    }

    @Test
    fun `физика - путь и плотность считаются верно`() {
        LocalTemplates.drafts("phys", "Скорость и путь", 40, Random(7)).forEach { draft ->
            val match = Regex("""скоростью (\d+) км/ч.*?за ([\d.]+) ч""").find(draft.prompt) ?: return@forEach
            val speed = match.groupValues[1].toDouble()
            val time = match.groupValues[2].toDouble()
            assertEquals(speed * time, draft.answer.toDouble(), 0.02)
        }
        LocalTemplates.drafts("phys", "Плотность вещества", 40, Random(7)).forEach { draft ->
            val match = Regex("""объёмом (\d+) см³.*?плотность (\d+) кг/м³""").find(draft.prompt)
                ?: return@forEach
            val v = match.groupValues[1].toDouble()
            val rho = match.groupValues[2].toDouble()
            assertEquals(rho * v / 1000.0, draft.answer.toDouble(), 0.02)
        }
    }

    @Test
    fun `гуманитарные темы локально не генерируются`() {
        assertTrue(LocalTemplates.drafts("rus", "Изложение", 5).isEmpty())
        assertTrue(LocalTemplates.drafts("bio", "Клетка", 5).isEmpty())
        assertTrue(LocalTemplates.supports("math", "Проценты"))
    }

    @Test
    fun `дроби складываются и сокращаются верно`() {
        val regex = Regex("""Вычислите (\d+)/(\d+) ([+−]) (\d+)/(\d+)\.""")
        var checked = 0
        drafts("Обыкновенные дроби").forEach { draft ->
            val match = regex.find(draft.prompt) ?: return@forEach
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val sign = if (match.groupValues[3] == "−") -1 else 1
            val c = match.groupValues[4].toInt()
            val d = match.groupValues[5].toInt()
            val expected = (a * d + sign * c * b).toDouble() / (b * d)
            val parts = draft.answer.split("/")
            val actual = parts[0].toInt().toDouble() / parts[1].toInt()
            assertEquals("условие: ${draft.prompt}", expected, actual, 1e-9)
            checked++
        }
        assertTrue("проверено $checked заданий", checked > 10)
    }

    @Test
    fun `порядок действий с натуральными числами`() {
        val regex = Regex("""Вычислите значение выражения: (\d+) \+ (\d+) · (\d+)\.""")
        var checked = 0
        drafts("Натуральные числа и действия с ними").forEach { draft ->
            val match = regex.find(draft.prompt) ?: return@forEach
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val c = match.groupValues[3].toInt()
            assertEquals("условие: ${draft.prompt}", a + b * c, draft.answer.toInt())
            checked++
        }
        assertTrue("проверено $checked заданий", checked > 10)
    }

    @Test
    fun `нод и нок считаются верно`() {
        val gcdRegex = Regex("""Найдите наибольший общий делитель чисел (\d+) и (\d+)\.""")
        val lcmRegex = Regex("""Найдите наименьшее общее кратное чисел (\d+) и (\d+)\.""")
        var checked = 0
        drafts("Делимость чисел, НОД и НОК").forEach { draft ->
            gcdRegex.find(draft.prompt)?.let { match ->
                val a = match.groupValues[1].toInt()
                val b = match.groupValues[2].toInt()
                val g = draft.answer.toInt()
                assertTrue("условие: ${draft.prompt}", a % g == 0 && b % g == 0)
                // максимальность: НОД(a/g, b/g) должен быть 1
                var x = a / g
                var y = b / g
                while (y != 0) { val t = y; y = x % y; x = t }
                assertEquals("НОД не максимален: ${draft.prompt}", 1, x)
                checked++
            }
            lcmRegex.find(draft.prompt)?.let { match ->
                val a = match.groupValues[1].toInt()
                val b = match.groupValues[2].toInt()
                val l = draft.answer.toLong()
                assertTrue("условие: ${draft.prompt}", l % a == 0L && l % b == 0L)
                checked++
            }
        }
        assertTrue("проверено $checked заданий", checked > 10)
    }
}
