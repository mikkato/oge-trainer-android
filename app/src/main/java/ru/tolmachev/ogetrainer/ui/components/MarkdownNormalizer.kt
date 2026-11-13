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
        "[A-Za-z0-9^_{}()\\[\\]+\\-*/=<>·×÷.,:;%|~ \\t]{3,80}",
    )

    /** Голые команды LaTeX вне формул: \frac{1}{2}, \sqrt{9}, \cdot. */
    private val bareCommand = Regex(
        "\\\\(?:[dt]?frac|sqrt|cdot|times|div|pm|mp|le|leq|ge|geq|ne|neq|approx|pi|alpha|beta|" +
            "gamma|delta|Delta|lambda|mu|rho|sigma|tau|phi|omega)(?:\\[[^\\]]*\\])?(?:\\{[^{}]*\\})*",
    )
    private val mathSignal = Regex("[\\^_=×÷·/<>]|\\d\\s*[+\\-]\\s*\\d")

    /**
     * Приведение текста к разметке KaTeX.
     *
     * Разбор идёт сканером: текст делится на готовые формулы (`$...$`, `$$...$$`)
     * и обычные фрагменты. В обычных фрагментах оборачиваются голые команды LaTeX
     * и математические выражения. Раньше регулярки шли по всему тексту сразу и
     * вставляли вторую пару долларов внутрь уже размеченной формулы — отсюда были
     * артефакты вида «$$\dfrac{6}{8}$ = …» и висящие доллары в конце фразы.
     */
    fun toKatex(source: String): String {
        if (source.isBlank()) return source

        val text = decodeEntities(source)
            .replace("\\(", "$").replace("\\)", "$")
            .replace("\\[", "$$").replace("\\]", "$$")

        val out = StringBuilder(text.length + 32)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '$') {
                val block = index + 1 < text.length && text[index + 1] == '$'
                val open = if (block) 2 else 1
                val close = findClose(text, index + open, block)
                if (close > 0) {
                    val body = text.substring(index + open, close).trim()
                    if (body.isNotEmpty()) {
                        out.append('$').append(body).append('$')
                        index = close + (if (block) 2 else 1)
                        continue
                    }
                }
                // Одиночный или незакрытый доллар оставляем как есть.
                out.append(ch)
                index++
                continue
            }
            var next = index
            while (next < text.length && text[next] != '$') next++
            out.append(wrapPlain(text.substring(index, next)))
            index = next
        }
        return out.toString()
    }

    /** Позиция закрывающего делимитера или -1, если формула не закрыта. */
    private fun findClose(text: String, from: Int, block: Boolean): Int {
        var j = from
        while (j < text.length) {
            if (text[j] == '\\') {
                j += 2
                continue
            }
            if (text[j] == '$') {
                if (!block) return j
                if (j + 1 < text.length && text[j + 1] == '$') return j
            }
            j++
        }
        return -1
    }

    /** Обычный фрагмент: оборачиваем голые команды и математические выражения. */
    private fun wrapPlain(plain: String): String {
        if (plain.isBlank()) return plain
        val kept = mutableListOf<String>()
        fun stash(value: String): String {
            kept += value
            return "\u0000" + (kept.size - 1) + "\u0000"
        }

        // Markdown-картинки и ссылки изымаем целиком: путь вида
        // «media/graphs/math_lin_math8.1.31_1.png» содержит цифры и «/» и иначе
        // распознаётся как формула — обёртка в $...$ ломает ссылку и картинку.
        val markdownLink = Regex("!?\\[[^\\]\\n]*]\\([^)\\n]*\\)")
        var text = markdownLink.replace(plain) { match -> stash(match.value) }

        // Голые LaTeX-команды: «\frac{1}{2}» без $...$ KaTeX не увидит.
        text = bareCommand.replace(text) { match ->
            if (match.value.isBlank()) match.value else stash("$" + match.value + "$")
        }

        // Математические фрагменты в ASCII-алфавите; кириллица разрывает фрагмент.
        text = mathCandidate.replace(text) { match ->
            val raw = match.value
            val fragment = raw.trim().trimEnd('.', ',', ';', '*')
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

        kept.forEachIndexed { i, value -> text = text.replace("\u0000$i\u0000", value) }
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
