package ru.tolmachev.ogetrainer.ui.components

import java.util.Locale

/**
 * LaTeX и markdown → обычный текст для плотных списков.
 *
 * В экранах ученика формулы рисует KaTeX в WebView, но держать WebView в каждой
 * строке списка дорого. Для банка и списков достаточно типографского текста:
 * $x^2$ → x², \frac{a}{b} → a/b, \sqrt{9} → √9. Сложные конструкции
 * (матрицы, интегралы, системы) остаются в режиме полного рендера KaTeX.
 *
 * Внимание: Android использует ICU-регексы и отвергает незакрытую «}»,
 * поэтому фигурные скобки в шаблонах всегда экранируются как \} и \{.
 */
object MathText {

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'k' to 'ᵏ', 'm' to 'ᵐ',
    )

    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        'n' to 'ₙ', 'i' to 'ᵢ', 'k' to 'ₖ', 'm' to 'ₘ', 'x' to 'ₓ', '+' to '₊', '-' to '₋',
    )

    /** Команды LaTeX → типографские символы. Порядок важен: длинные раньше коротких. */
    private val symbols = linkedMapOf(
        "\\leqslant" to "≤",
        "\\geqslant" to "≥",
        "\\rightarrow" to "→",
        "\\Rightarrow" to "⇒",
        "\\approx" to "≈",
        "\\infty" to "∞",
        "\\degree" to "°",
        "\\angle" to "∠",
        "\\cdot" to "·",
        "\\times" to "×",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\pi" to "π",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\Delta" to "Δ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\phi" to "φ",
        "\\omega" to "ω",
        "\\left" to "",
        "\\right" to "",
        "\\quad" to "  ",
        "\\!" to "",
        "\\," to " ",
        "\\;" to " ",
        "~" to " ",
    )

    private val boldMarkdown = Regex("\\*\\*(.+?)\\*\\*")
    private val italicMarkdown = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    private val headingMarkdown = Regex("^#{1,6}\\s*", RegexOption.MULTILINE)
    private val displayMath = Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL)
    private val inlineMath = Regex("\\$(.+?)\\$", RegexOption.DOT_MATCHES_ALL)
    private val manySpaces = Regex("[ \\t]{2,}")

    private val textCommand = Regex("\\\\text\\{([^{}]*)\\}")
    private val mathrmCommand = Regex("\\\\mathrm\\{([^{}]*)\\}")
    private val mathbfCommand = Regex("\\\\mathbf\\{([^{}]*)\\}")
    private val fracCommand = Regex("\\\\[dt]?frac\\{([^{}]*)\\}\\{([^{}]*)\\}")
    private val sqrtCommand = Regex("\\\\sqrt\\[?([0-9n]*)\\]?\\{([^{}]*)\\}")
    private val supBraced = Regex("\\^\\{([^{}]*)\\}")
    private val supSingle = Regex("\\^(-?[0-9a-zA-Z])")
    private val subBraced = Regex("_\\{([^{}]*)\\}")
    private val subSingle = Regex("_([0-9a-zA-Z])")
    private val leftoverCommands = Regex("\\\\[a-zA-Z]+")

    /** Готовая строка без $, \, ** и прочих символов разметки. */
    fun render(source: String): String {
        if (source.isBlank()) return ""
        var text = source
            .replace("```", "")
            .replace("`", "")
            .replace("---", "—")
        text = boldMarkdown.replace(text, "$1")
        text = italicMarkdown.replace(text, "$1")
        text = headingMarkdown.replace(text, "")
        text = displayMath.replace(text) { math(it.groupValues[1]) }
        text = inlineMath.replace(text) { math(it.groupValues[1]) }
        // Степени вне формул: «x^2» в тексте банка тоже должны стать x².
        text = supBraced.replace(text) { sup(it.groupValues[1]) }
        text = supSingle.replace(text) { sup(it.groupValues[1]) }
        return text
            .replace(Regex("\\$"), "")
            .replace(manySpaces, " ")
            .replace(" _", " ")
            .trim()
    }

    /** Преобразование содержимого формулы. */
    private fun math(source: String): String {
        var value = textCommand.replace(source) { it.groupValues[1] }
        value = mathrmCommand.replace(value) { it.groupValues[1] }
        value = mathbfCommand.replace(value) { it.groupValues[1] }

        value = fracCommand.replace(value) {
            val num = wrap(it.groupValues[1])
            val den = wrap(it.groupValues[2])
            "$num/$den"
        }
        value = sqrtCommand.replace(value) {
            val degree = it.groupValues[1]
            val argument = wrap(it.groupValues[2])
            if (degree.isBlank()) "√$argument" else "${sup(degree)}√$argument"
        }

        symbols.forEach { (command, replacement) -> value = value.replace(command, replacement) }

        value = supBraced.replace(value) { sup(it.groupValues[1]) }
        value = supSingle.replace(value) { sup(it.groupValues[1]) }
        value = subBraced.replace(value) { sub(it.groupValues[1]) }
        value = subSingle.replace(value) { sub(it.groupValues[1]) }

        value = value.replace(" - ", " − ")
        value = leftoverCommands.replace(value, "")
        return value.replace("{", "").replace("}", "")
    }

    private fun wrap(text: String): String =
        if (text.length > 1 && text.any { it == '+' || it == '-' || it == ' ' }) "($text)" else text

    private fun sup(text: String): String {
        if (text.isEmpty()) return ""
        val builder = StringBuilder()
        for (ch in text) {
            val mapped = superscripts[ch] ?: return "^{$text}"
            builder.append(mapped)
        }
        return builder.toString()
    }

    private fun sub(text: String): String {
        if (text.isEmpty()) return ""
        val builder = StringBuilder()
        for (ch in text) {
            val mapped = subscripts[ch] ?: return "_$text"
            builder.append(mapped)
        }
        return builder.toString()
    }

    private val numericEntity = Regex("&#(\\d{2,7});")

    private val barePower = Regex("(?<![\\w$^])([A-Za-z0-9)]+)\\^(\\{[^}]*\\}|-?\\d+)")

    /**
     * Подготовка текста к рендеру KaTeX: в банке формулы встречаются без $...$
     * («x^2 - 5x + 6 = 0»), тогда KaTeX их не видит и показывает как есть.
     * Здесь такие степени заворачиваются в формулу, а HTML-сущности переноса строки
     * превращаются в обычный перевод строки.
     */
    /**
     * Markdown условия с приложенной графикой: картинка из assets/bank/ идёт первым
     * блоком. Путь в WebView резолвится от file:///android_asset/render/ — берём ../bank/.
     */
    fun promptForRender(media: String, prompt: String): String {
        val body = forKatex(prompt)
        if (media.isBlank()) return body
        return "![Рисунок к заданию](../bank/$media)\n\n$body"
    }

    fun forKatex(source: String): String {
        if (source.isBlank()) return source
        var text = source.replace("&#10;", "\n").replace("&nbsp;", " ").replace("&quot;", "\"")
        // В выгрузке банка спецсимволы пришли HTML-сущностями: &#119886; → a.
        text = numericEntity.replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            if (code in 32..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        text = barePower.replace(text) { match ->
            val base = match.groupValues[1]
            val exponent = match.groupValues[2].removeSurrounding("{", "}")
            "${'$'}$base^{$exponent}${'$'}"
        }
        return text
    }

    /** Безопасный вариант для экранов: при любой ошибке отдаём текст без разметки. */
    fun renderSafe(source: String): String =
        runCatching { render(source) }.getOrElse {
            source.replace("\$", "").replace("\\", "").replace("*", "")
        }

    /** Дробное число в русской записи: 5,25 вместо 5.25. */
    fun number(value: Double): String =
        String.format(Locale("ru"), "%.2f", value).trimEnd('0').trimEnd(',')
}
