package ru.tolmachev.ogetrainer.ui.components

/**
 * Приведение текста из банка и от моделей к виду, который понимает KaTeX.
 *
 * Проблема, которую решает класс: в выгрузке и в ответах моделей математика встречается
 * в четырёх видах одновременно — `$x^2$`, `\(x^2\)`, голая запись `x^2` без разделителей
 * и HTML-сущности вида `&#119886;`. KaTeX видит только первый и второй вид, поэтому
 * остальное выводилось «как есть» — с крышками, обратными слэшами и амперсандами.
 *
 * Порядок обработки:
 *  1) HTML-сущности → символы;
 *  2) `\(...\)` и `\[...\]` → `$...$` / `$$...$$`;
 *  3) уже размеченные формулы временно изымаются, чтобы не завернуть их повторно;
 *  4) в обычном тексте находятся математические фрагменты (степени, индексы, дроби,
 *     равенства, знаки операций без кириллицы) и оборачиваются в `$...$`;
 *  5) формулы возвращаются на место.
 */
object MarkdownNormalizer {

    private val numericEntity = Regex("&#(\\d{2,7});")
    private val hexEntity = Regex("&#[xX]([0-9a-fA-F]{2,6});")

    private val mathBlock = Regex("\\$\\$.+?\\$\\$", RegexOption.DOT_MATCHES_ALL)
    private val mathInline = Regex("(?<!\\\\)\\$.+?(?<!\\\\)\\$", RegexOption.DOT_MATCHES_ALL)

    /** Фрагмент, похожий на формулу: без кириллицы, с математическим признаком. */
    private val mathCandidate = Regex(
        "[A-Za-z0-9^_{}()\\[\\]+\\-*/=<>·×÷.,:;%|~\\s]{3,80}",
    )

    /** Голые команды LaTeX вне формул: \frac{1}{2}, \sqrt{9}, \cdot. */
    private val bareCommand = Regex(
        "\\\\(?:[dt]?frac|sqrt|cdot|times|div|pm|mp|le|leq|ge|geq|ne|neq|approx|pi|alpha|beta|" +
            "gamma|delta|Delta|lambda|mu|rho|sigma|tau|phi|omega)(?:\\[[^\\]]*\\])?(?:\\{[^{}]*\\})*",
    )
    private val mathSignal = Regex("[\\^_=×÷·/<>]|\\d\\s*[+\\-]\\s*\\d")

    fun toKatex(source: String): String {
        if (source.isBlank()) return source

        var text = decodeEntities(source)
        text = text.replace("\\(", "$").replace("\\)", "$")
        text = text.replace("\\[", "$$").replace("\\]", "$$")

        // Голые LaTeX-команды вне формул: «\frac{1}{2}» без $...$ KaTeX не увидит.
        text = bareCommand.replace(text) { match ->
            if (match.value.isBlank()) match.value else "$" + match.value + "$"
        }

        // Всё, что уже размечено как формула, изымаем, чтобы не обернуть повторно.
        val kept = mutableListOf<String>()
        fun stash(value: String): String {
            kept += value
            return "\u0000" + (kept.size - 1) + "\u0000"
        }
        text = mathBlock.replace(text) { stash(it.value) }
        text = mathInline.replace(text) { stash(wrapLatex(it.value)) }

        // Голые математические фрагменты: только ASCII-алфавит, кириллица разрывает
        // фрагмент — поэтому русские слова внутрь формулы не попадают.
        text = mathCandidate.replace(text) { match ->
            val raw = match.value
            // Точка и запятая в конце фразы — пунктуация, а не часть формулы.
            val fragment = raw.trim().trimEnd('.', ',', ';')
            val start = raw.indexOf(fragment)
            val prefix = raw.substring(0, start.coerceAtLeast(0))
            val trailing = raw.substring(start.coerceAtLeast(0) + fragment.length)
            when {
                fragment.length < 3 -> raw
                fragment.any { it.code in 0x0400..0x04FF } -> raw
                fragment.contains('\u0000') -> raw
                !fragment.any { it.isDigit() } -> raw
                !mathSignal.containsMatchIn(fragment) && !fragment.contains('^') -> raw
                else -> prefix + "$" + fragment + "$" + trailing
            }
        }

        kept.forEachIndexed { index, value ->
            text = text.replace("\u0000$index\u0000", value)
        }
        return text
    }

    /** Вариант для простых текстовых полей: без LaTeX и markdown, с типографскими знаками. */
    fun toPlain(source: String): String = MathText.renderSafe(toKatex(source))

    private fun wrapLatex(value: String): String {
        val inner = value.removePrefix("$").removeSuffix("$")
        // \(...\) внутри $...$ уже корректно; лишние обратные слэши перед скобками убираем.
        return "$" + inner.replace("\\(", "").replace("\\)", "") + "$"
    }

    private fun decodeEntities(source: String): String {
        var text = source
        text = text.replace("&#10;", "\n").replace("&nbsp;", " ")
        text = numericEntity.replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        text = hexEntity.replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else match.value
        }
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
