package ru.tolmachev.ogetrainer.data.provider

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Процедурные шаблоны тренировочных заданий. Чистый Kotlin без Android API,
 * чтобы арифметику можно было проверять обычными JVM-тестами.
 *
 * Ответ вычисляется здесь же, поэтому он верный по построению. Покрыты вычислимые
 * темы математики, физики и химии; гуманитарные темы требуют банка или модели.
 */
object LocalTemplates {

    /** Источник случайных чисел для шаблонов; в тестах подменяется через [drafts]. */
    private val rnd: Random = Random.Default

    data class Draft(
        val prompt: String,
        val answer: String,
        val solution: String,
        val difficulty: Int,
    )

    fun supports(subjectCode: String, topicTitle: String): Boolean =
        familiesFor(subjectCode, topicTitle).isNotEmpty()

    /** Готовые задания: числа подставляются случайно, ответ считается кодом. */
    fun drafts(
        subjectCode: String,
        topicTitle: String,
        count: Int,
        random: Random = Random.Default,
    ): List<Draft> {
        val families = familiesFor(subjectCode, topicTitle)
        if (families.isEmpty()) return emptyList()
        return List(count) { families[rnd.nextInt(families.size)].invoke() }
    }

    /** Семейство заданий выбирается по названию темы, а не по предмету целиком. */
    private fun familiesFor(subjectCode: String, topicTitle: String): List<() -> Draft> {
        val title = topicTitle.lowercase(Locale("ru"))
        val list = mutableListOf<() -> Draft>()

        if (subjectCode.startsWith("math")) {
            when {
                title.contains("прогресс") -> list += { arithmeticProgression() }
                title.contains("процент") -> list += { percentage() }
                title.contains("квадрат") || title.contains("уравнен") -> list += { linearEquation() }
                title.contains("площад") || title.contains("треуголь") || title.contains("прямоуг") ->
                    list += { rectangleArea() }
                title.contains("скорост") || title.contains("движен") -> list += { motion() }
                title.contains("степен") || title.contains("корн") -> list += { powers() }
                title.contains("функци") || title.contains("график") -> list += { linearFunction() }
                else -> {
                    list += { linearEquation() }
                    list += { percentage() }
                    list += { rectangleArea() }
                }
            }
        }
        if (subjectCode.startsWith("phys")) {
            when {
                title.contains("плотност") -> list += { density() }
                title.contains("сила") || title.contains("тяжест") || title.contains("вес") -> list += { force() }
                title.contains("ток") || title.contains("сопротивлен") || title.contains("напряжен") ->
                    list += { ohmLaw() }
                title.contains("работ") || title.contains("мощност") -> list += { work() }
                title.contains("давлен") -> list += { pressure() }
                title.contains("скорост") || title.contains("движен") || title.contains("путь") ->
                    list += { motion() }
                else -> {
                    list += { motion() }
                    list += { density() }
                }
            }
        }
        if (subjectCode.startsWith("chem")) {
            when {
                title.contains("доля") -> list += { massFraction() }
                title.contains("молярн") || title.contains("количеств") -> list += { molarMass() }
                else -> list += { massFraction() }
            }
        }
        return list
    }


    // ---------- математика ----------

    private fun linearEquation(): Draft {
        val a = rnd.nextInt(2, 12)
        val x = rnd.nextInt(-9, 10)
        val b = rnd.nextInt(-20, 21)
        val c = a * x + b
        val sign = if (b >= 0) "+" else "−"
        return Draft(
            prompt = "Решите уравнение $a x $sign ${kotlin.math.abs(b)} = $c.",
            answer = x.toString(),
            solution = "$a x = $c ${if (b >= 0) "−" else "+"} ${kotlin.math.abs(b)} = ${a * x}; x = ${a * x} : $a = $x.",
            difficulty = 1,
        )
    }

    private fun arithmeticProgression(): Draft {
        val a1 = rnd.nextInt(-5, 8)
        val d = rnd.nextInt(2, 6)
        val n = rnd.nextInt(8, 25)
        val an = a1 + (n - 1) * d
        return Draft(
            prompt = "Арифметическая прогрессия задана условиями \$a_1 = $a1, \$a_{n+1} = \$a_n + $d. Найдите \$a_{$n}.",
            answer = an.toString(),
            solution = "\$a_{$n} = \$a_1 + (n − 1)d = $a1 + ${n - 1} · $d = $an.",
            difficulty = 1,
        )
    }

    private fun percentage(): Draft {
        val base = rnd.nextInt(20, 90) * 10
        val percent = listOf(5, 10, 15, 20, 25, 30, 40).let { it[rnd.nextInt(it.size)] }
        val discount = base * percent / 100
        val answer = base - discount
        return Draft(
            prompt = "Товар стоил $base рублей. Цену снизили на $percent %. Сколько рублей стала стоить вещь?",
            answer = answer.toString(),
            solution = "Скидка: $base · $percent / 100 = $discount; новая цена $base − $discount = $answer.",
            difficulty = 1,
        )
    }

    private fun rectangleArea(): Draft {
        val a = rnd.nextInt(3, 15)
        val b = rnd.nextInt(3, 15)
        return Draft(
            prompt = "Найдите площадь прямоугольника со сторонами $a см и $b см. Ответ дайте в квадратных сантиметрах.",
            answer = (a * b).toString(),
            solution = "S = a · b = $a · $b = ${a * b} см².",
            difficulty = 1,
        )
    }

    private fun motion(): Draft {
        val speed = rnd.nextInt(4, 20) * 5
        val time = rnd.nextDouble(0.5, 4.0).let { (it * 2).roundToInt() / 2.0 }
        val distance = speed * time
        return Draft(
            prompt = "Автомобиль едет со скоростью $speed км/ч. Какой путь он пройдёт за ${fmt(time)} ч? Ответ дайте в километрах.",
            answer = fmt(distance),
            solution = "s = v · t = $speed · ${fmt(time)} = ${fmt(distance)} км.",
            difficulty = 1,
        )
    }

    private fun powers(): Draft {
        val base = rnd.nextInt(2, 7)
        val exp = rnd.nextInt(2, 5)
        val value = Math.pow(base.toDouble(), exp.toDouble()).toInt()
        return Draft(
            prompt = "Вычислите $base^{$exp}.",
            answer = value.toString(),
            solution = "$base^{$exp} = " + List(exp) { "$base" }.joinToString(" · ") + " = $value.",
            difficulty = 1,
        )
    }

    private fun linearFunction(): Draft {
        val k = rnd.nextInt(-4, 5).let { if (it == 0) 2 else it }
        val b = rnd.nextInt(-6, 7)
        val x = rnd.nextInt(-4, 5)
        val y = k * x + b
        val sign = if (b >= 0) "+" else "−"
        return Draft(
            prompt = "Дана функция $y = ${k}x $sign ${kotlin.math.abs(b)}. Найдите значение функции при x = $x.",
            answer = y.toString(),
            solution = "y = $k · ($x) ${if (b >= 0) "+" else "−"} ${kotlin.math.abs(b)} = $y.",
            difficulty = 1,
        )
    }

    // ---------- физика ----------

    private fun density(): Draft {
        val v = rnd.nextInt(2, 30) * 10
        val rho = listOf(800, 1000, 2500, 2700, 7800).let { it[rnd.nextInt(it.size)] }
        val mass = rho * v / 1000
        return Draft(
            prompt = "Тело объёмом $v см³ имеет плотность $rho кг/м³. Найдите его массу в килограммах.",
            answer = fmt(mass.toDouble()),
            solution = "m = ρV = $rho · ${fmt(v / 1000.0)} = ${fmt(mass.toDouble())} кг.",
            difficulty = 2,
        )
    }

    private fun force(): Draft {
        val m = rnd.nextInt(1, 20) * 10
        val g = 10
        val f = m * g / 1000.0
        return Draft(
            prompt = "Тело массой $m кг поднимают вертикально. Найдите силу тяжести, действующую на тело, в килоньютонах (g = 10 Н/кг).",
            answer = fmt(f),
            solution = "F = mg = $m · 10 = ${m * 10} Н = ${fmt(f)} кН.",
            difficulty = 1,
        )
    }

    private fun ohmLaw(): Draft {
        val r = rnd.nextInt(2, 40) * 5
        val i = rnd.nextDouble(0.1, 2.0).let { (it * 10).roundToInt() / 10.0 }
        val u = r * i
        return Draft(
            prompt = "Через резистор сопротивлением $r Ом течёт ток ${fmt(i)} А. Найдите напряжение на резисторе в вольтах.",
            answer = fmt(u),
            solution = "U = I · R = ${fmt(i)} · $r = ${fmt(u)} В.",
            difficulty = 2,
        )
    }

    private fun work(): Draft {
        val f = rnd.nextInt(5, 40) * 10
        val s = rnd.nextInt(2, 20)
        val a = f * s
        return Draft(
            prompt = "Под действием силы $f Н тело переместилось на $s м по направлению силы. Найдите работу силы в джоулях.",
            answer = a.toString(),
            solution = "A = F · s = $f · $s = $a Дж.",
            difficulty = 1,
        )
    }

    private fun pressure(): Draft {
        val f = rnd.nextInt(4, 40) * 10
        val s = rnd.nextInt(2, 20) / 100.0
        val p = f / s
        return Draft(
            prompt = "Сила $f Н действует на площадь ${fmt(s)} м². Найдите давление в паскалях.",
            answer = fmt(p),
            solution = "p = F / S = $f / ${fmt(s)} = ${fmt(p)} Па.",
            difficulty = 2,
        )
    }

    // ---------- химия ----------

    private fun massFraction(): Draft {
        val solute = rnd.nextInt(2, 30)
        val solvent = rnd.nextInt(50, 200)
        val total = solute + solvent
        val w = solute * 100.0 / total
        return Draft(
            prompt = "$solute Г соли растворили в $solvent г воды. Найдите массовую долю соли в растворе в процентах, округлите до десятых.",
            answer = fmt(w),
            solution = "m(раствора) = $solute + $solvent = $total г; w = $solute / $total · 100 % = ${fmt(w)} %.",
            difficulty = 2,
        )
    }

    private fun molarMass(): Draft {
        val options = listOf("H_2O" to 18.0, "CO_2" to 44.0, "NaCl" to 58.5, "NaOH" to 40.0, "O_2" to 32.0)
        val pair = options[rnd.nextInt(options.size)]
        val m = pair.second
        val mass = (rnd.nextInt(2, 20) * 10).toDouble()
        val n = mass / m
        return Draft(
            prompt = "Найдите количество вещества в ${fmt(mass)} г вещества ${pair.first} " +
                "(молярная масса ${fmt(m)} г/моль). Ответ в молях, округлите до сотых.",
            answer = fmt(n),
            solution = "n = m / M = ${fmt(mass)} / ${fmt(m)} = ${fmt(n)} моль.",
            difficulty = 2,
        )
    }

    private fun fmt(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }
}
