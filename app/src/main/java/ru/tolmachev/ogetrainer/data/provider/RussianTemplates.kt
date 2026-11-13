package ru.tolmachev.ogetrainer.data.provider

import kotlin.random.Random
import ru.tolmachev.ogetrainer.data.provider.LocalTemplates.Draft

/**
 * Процедурные шаблоны по русскому языку для классов 5–8.
 *
 * Как и [LocalTemplates], чистый Kotlin без Android API: ответы либо вычислены
 * по правилу прямо в коде (приставки -з/-с, род прилагательного по окончанию,
 * спряжение, числительные цифрами), либо берутся из проверенных таблиц, где
 * правильный ответ зафиксирован автором. Проверка — точное совпадение после
 * [ru.tolmachev.ogetrainer.domain.AnswerNormalizer].
 *
 * Ответы-значения намеренно не содержат буквы «ё»: нормализатор её не сводит
 * к «е», и вариант ввода пользователя сломал бы сравнение.
 */
object RussianTemplates {

    private val rnd = Random.Default

    /** Семейства по коду темы (`rus5.phonetics` → секция `phonetics`); при пустом коде — по названию. */
    fun supports(topicCode: String, topicTitle: String): Boolean =
        familiesFor(topicCode, topicTitle).isNotEmpty()

    fun familiesFor(topicCode: String, topicTitle: String): List<() -> Draft> {
        val section = topicCode.substringAfter('.', topicCode).lowercase()
        val byCode: List<() -> Draft>? = when (section) {
            "phonetics" -> listOf({ soundCount() }, { moreSoundsThanLetters() })
            "lexis" -> listOf({ wordPair(true) }, { wordPair(false) })
            "morphemics" -> listOf({ rootOf() })
            "roots" -> listOf({ alternRoot() })
            "noun" -> listOf({ nounDeclension() })
            "adjective" -> listOf({ adjectiveGender() })
            "verb" -> listOf({ verbConjugation() })
            "homogeneous" -> listOf({ commaCount(HOMOGENEOUS) })
            "numeral" -> listOf({ numeralDigits() })
            "pronoun" -> listOf({ pronounPerson() })
            "participle" -> listOf({ pickWord(PARTICIPLES, PARTICIPLE_DISTRACTORS, "причастие") })
            "gerund" -> listOf({ pickWord(GERUNDS, GERUND_DISTRACTORS, "деепричастие") })
            "prefixes" -> listOf({ prefixZS() })
            "not" -> listOf({ notTogether() })
            "adverb" -> listOf({ pickWord(ADVERBS, POS_DISTRACTORS, "наречие") })
            "preposition" -> listOf({ pickWord(PREPOSITIONS, POS_DISTRACTORS, "предлог") })
            "conjunction" -> listOf({ pickWord(CONJUNCTIONS, POS_DISTRACTORS, "союз") })
            "particle" -> listOf({ pickWord(PARTICLES, POS_DISTRACTORS, "частица") })
            "neither" -> listOf({ neNi() })
            "detached" -> listOf({ commaCount(DETACHED) })
            "two_part" -> listOf({ subjectOf() })
            "one_part" -> listOf({ onePartType() })
            "minor" -> listOf({ minorMember() })
            "definitions" -> listOf({ commaCount(DETACHED) })
            "intro" -> listOf({ introWord() })
            else -> null
        }
        if (byCode != null) return byCode
        if (topicCode.isEmpty() || !topicCode.startsWith("rus")) return emptyList()
        // Запасной путь по названию темы, если кода нет или он непривычный.
        val title = topicTitle.lowercase()
        return when {
            title.contains("фонетик") || title.contains("график") -> listOf({ soundCount() }, { moreSoundsThanLetters() })
            title.contains("лексик") -> listOf({ wordPair(true) }, { wordPair(false) })
            title.contains("морфем") || title.contains("словообраз") -> listOf({ rootOf() })
            title.contains("корн") -> listOf({ alternRoot() })
            title.contains("числител") -> listOf({ numeralDigits() })
            title.contains("местоимен") -> listOf({ pronounPerson() })
            title.contains("приставк") -> listOf({ prefixZS() })
            title.contains("деепричастие") -> listOf({ pickWord(GERUNDS, GERUND_DISTRACTORS, "деепричастие") })
            title.contains("причастие") -> listOf({ pickWord(PARTICIPLES, PARTICIPLE_DISTRACTORS, "причастие") })
            title.contains("однородн") -> listOf({ commaCount(HOMOGENEOUS) })
            title.contains("обособлен") -> listOf({ commaCount(DETACHED) })
            title.contains("вводн") || title.contains("обращен") -> listOf({ introWord() })
            else -> emptyList()
        }
    }

    private fun <T> pick(list: List<T>): T = list[rnd.nextInt(list.size)]

    // ---------- фонетика ----------

    /** Слово, букв, звуков — школьный подсчёт без спорных случаев. */
    private val SOUND_WORDS: List<Triple<String, Int, Int>> = listOf(
        Triple("юла", 3, 4), Triple("ежи", 3, 4), Triple("яма", 3, 4),
        Triple("емеля", 5, 6), Triple("яблоко", 6, 7),
        Triple("коньки", 6, 5), Triple("солнце", 6, 5),
        Triple("ёлка", 4, 4), Triple("мяч", 3, 3), Triple("поёт", 4, 4),
        Triple("дом", 3, 3), Triple("лес", 3, 3), Triple("вода", 4, 4),
        Triple("гора", 4, 4), Triple("стол", 4, 4), Triple("аист", 4, 4),
    )
    private val MORE_SOUNDS = SOUND_WORDS.filter { it.third > it.second }
    private val EQUAL_SOUNDS = SOUND_WORDS.filter { it.third == it.second }

    private fun soundCount(): Draft {
        val (word, letters, sounds) = pick(SOUND_WORDS)
        return Draft(
            prompt = "Сколько звуков в слове ${word.uppercase()}? Ответ дай числом.",
            answer = sounds.toString(),
            solution = "В слове «$word» $letters букв и $sounds звуков.",
            difficulty = 1,
        )
    }

    private fun moreSoundsThanLetters(): Draft {
        val answerWord = pick(MORE_SOUNDS)
        val distractors = EQUAL_SOUNDS.shuffled(rnd).take(3)
        val options = (listOf(answerWord) + distractors).shuffled(rnd).joinToString(", ") { it.first.uppercase() }
        return Draft(
            prompt = "В каком слове звуков больше, чем букв? Выпиши это слово: $options.",
            answer = answerWord.first,
            solution = "«${answerWord.first}»: ${answerWord.second} буквы, ${answerWord.third} звука " +
                "(гласные ю, е, я в начале слова дают два звука).",
            difficulty = 2,
        )
    }

    // ---------- лексика ----------

    private val ANTONYMS: List<Pair<String, String>> = listOf(
        "горячий" to "холодный", "широкий" to "узкий", "день" to "ночь",
        "верх" to "низ", "радостный" to "грустный", "храбрый" to "трусливый",
        "далеко" to "близко", "труд" to "лень",
    )
    private val SYNONYMS: List<Pair<String, String>> = listOf(
        "смелый" to "отважный", "грустный" to "печальный", "большой" to "огромный",
        "бежать" to "мчаться", "близкий" to "родной", "маленький" to "крохотный",
    )

    private fun wordPair(antonym: Boolean): Draft {
        val pair = if (antonym) pick(ANTONYMS) else pick(SYNONYMS)
        val (query, answer) = if (rnd.nextBoolean()) pair else pair.second to pair.first
        val kind = if (antonym) "антоним" else "синоним"
        return Draft(
            prompt = "Подбери $kind к слову ${query.uppercase()}. Ответ выпиши одним словом.",
            answer = answer,
            solution = "$kind: $query — $answer.",
            difficulty = 1,
        )
    }

    // ---------- морфемика и корни ----------

    private val ROOTS: List<Pair<String, String>> = listOf(
        "лесной" to "лес", "лесник" to "лес", "водянистый" to "вод",
        "приморский" to "мор", "прибрежный" to "берег", "городской" to "город",
        "снежный" to "снеж", "домашний" to "дом", "грибной" to "гриб",
        "морозный" to "мороз", "стеклянный" to "стекл",
    )

    private fun rootOf(): Draft {
        val (word, root) = pick(ROOTS)
        return Draft(
            prompt = "Выпиши корень слова ${word.uppercase()}.",
            answer = root,
            solution = "Родственные слова содержат общую часть «$root»: $word.",
            difficulty = 1,
        )
    }

    /** Чередуемые корни: слово с пропуском, буква, само слово целиком. */
    private val ALTERN_ROOTS: List<Triple<String, String, String>> = listOf(
        Triple("р_стение", "а", "растение"), Triple("р_слый", "о", "рослый"),
        Triple("выр_сли", "о", "выросли"), Triple("взр_стить", "а", "взрастить"),
        Triple("р_сток", "о", "росток"),        Triple("к_снуться", "о", "коснуться"),
        Triple("к_сание", "а", "касание"), Triple("б_рёт", "е", "берёт"),
        Triple("заб_ру", "е", "заберу"),        Triple("ум_рать", "и", "умирать"),
        Triple("г_реть", "о", "гореть"), Triple("заг_р", "о", "загар"),
        Triple("з_ри", "о", "зори"), Triple("прибл_жение", "о", "приближение"),
        Triple("прил_гать", "а", "прилагать"), Triple("скл_нять", "о", "склонять"),
    )
    private fun alternRoot(): Draft {
        val (word, letter, full) = pick(ALTERN_ROOTS)
        return Draft(
            prompt = "Вставь пропущенную букву: ${word.uppercase()}. Ответ: одна буква.",
            answer = letter,
            solution = "Правильно: «$full». Корень с чередованием: $letter.",
            difficulty = 2,
        )
    }

    // ---------- морфология: существительное, прилагательное, глагол, местоимение, числительное ----------

    private val NOUN_DECLENSION: List<Pair<String, Int>> = listOf(
        "мама" to 1, "папа" to 1, "стена" to 1, "земля" to 1, "армия" to 1,
        "окно" to 2, "поле" to 2, "море" to 2, "платье" to 2, "космос" to 2,
        "ночь" to 3, "дверь" to 3, "любовь" to 3, "время" to 3, "путь" to 3, "новость" to 3,
    )

    private fun nounDeclension(): Draft {
        val (word, decl) = pick(NOUN_DECLENSION)
        return Draft(
            prompt = "Определи склонение имени существительного ${word.uppercase()}. Ответ дай числом.",
            answer = decl.toString(),
            solution = "«$word» — ${decl} склонение.",
            difficulty = 1,
        )
    }

    /** Род вычисляется по окончанию формы именительного падежа. */
    private val ADJECTIVE_FORMS: List<String> = listOf(
        "красивая", "зелёная", "синее", "далёкое", "новое",
        "летний", "осенний", "стальной", "простой",
    )

    private fun adjectiveGender(): Draft {
        val form = pick(ADJECTIVE_FORMS)
        val gender = when {
            form.endsWith("ая") || form.endsWith("яя") -> "женский"
            form.endsWith("ое") || form.endsWith("ее") -> "средний"
            form.endsWith("ый") || form.endsWith("ой") || form.endsWith("ий") -> "мужской"
            else -> error("нет правила для формы $form")
        }
        val suffix = when {
            gender == "женский" -> "-ая/-яя"
            gender == "средний" -> "-ое/-ее"
            else -> "-ый/-ой/-ий"
        }
        return Draft(
            prompt = "Какой род у имени прилагательного в форме ${form.uppercase()}?",
            answer = gender,
            solution = "Форма именительного падежа с окончанием $suffix — ${gender} род.",
            difficulty = 1,
        )
    }

    private val VERB_CONJUGATION: List<Pair<String, Int>> = listOf(
        "читать" to 1, "писать" to 1, "стоять" to 1, "веять" to 1, "брить" to 1,
        "стелить" to 1, "колоть" to 1, "бросать" to 1, "глядеть" to 1,
        "говорить" to 2, "строить" to 2, "красить" to 2, "пилить" to 2, "дарить" to 2,
        "бороздить" to 2, "гнать" to 2, "слышать" to 2, "обидеть" to 2, "видеть" to 2,
        "ненавидеть" to 2, "держать" to 2, "зависеть" to 2, "терпеть" to 2, "дышать" to 2,
        "обмануть" to 2,
    )

    private fun verbConjugation(): Draft {
        val (verb, conj) = pick(VERB_CONJUGATION)
        return Draft(
            prompt = "Определи спряжение глагола ${verb.uppercase()}. Ответ дай числом.",
            answer = conj.toString(),
            solution = "«$verb» — ${conj} спряжение (проверь по личным окончаниям: -у/-ю, -им, -ишь...).",
            difficulty = 1,
        )
    }

    private val PRONOUN_PERSON: List<Pair<String, Int>> = listOf(
        "я" to 1, "мы" to 1, "ты" to 2, "вы" to 2,
        "он" to 3, "она" to 3, "оно" to 3, "они" to 3,
    )

    private fun pronounPerson(): Draft {
        val (word, person) = pick(PRONOUN_PERSON)
        return Draft(
            prompt = "Определи лицо местоимения ${word.uppercase()}. Ответ дай числом.",
            answer = person.toString(),
            solution = "«$word» — ${person} лицо.",
            difficulty = 1,
        )
    }

    private val NUMERALS: List<Pair<String, Int>> = listOf(
        "три" to 3, "семь" to 7, "десять" to 10, "одиннадцать" to 11,
        "двенадцать" to 12, "тринадцать" to 13, "сорок" to 40, "шестьдесят" to 60,
        "девяносто" to 90, "сто" to 100, "двести" to 200, "пятьсот" to 500, "тысяча" to 1000,
    )

    private fun numeralDigits(): Draft {
        val (word, digits) = pick(NUMERALS)
        return Draft(
            prompt = "Запиши числительное цифрами: ${word.uppercase()}.",
            answer = digits.toString(),
            solution = "${word.uppercase()} — это $digits.",
            difficulty = 1,
        )
    }

    // ---------- орфография ----------

    /** Приставка (всегда на -з), корень, верная буква: з перед гласными и звонкими (включая в), с перед глухими. */
    private val PREFIX_ROOTS: List<Triple<String, String, String>> = listOf(
        Triple("без", "шумный", "с"), Triple("без", "спорный", "с"),
        Triple("без", "крайний", "с"), Triple("без", "честный", "с"),
        Triple("без", "цветной", "с"), Triple("раз", "писать", "с"),
        Triple("без", "конечный", "с"), Triple("раз", "стегнуть", "с"),
        Triple("воз", "сиять", "с"),
        Triple("без", "брежный", "з"), Triple("без", "донный", "з"),
        Triple("без", "мерный", "з"), Triple("без", "ветренный", "з"),
        Triple("без", "жалостный", "з"), Triple("без", "законный", "з"),
        Triple("без", "обидный", "з"), Triple("без", "образный", "з"),
        Triple("раз", "вязать", "з"), Triple("из", "лить", "з"),
        Triple("без", "удержу", "з"), Triple("воз", "наградить", "з"),
    )
    private val VOICED_OR_VOWEL = setOf(
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'б', 'в', 'г', 'д', 'ж', 'з', 'й', 'л', 'м', 'н', 'р',
    )

    /** Чистая функция правила — используется и в тестах как эталон. */
    fun prefixLetter(rootStart: Char): String =
        if (rootStart.lowercaseChar() in VOICED_OR_VOWEL) "з" else "с"

    private fun prefixZS(): Draft {
        val (prefix, root, _) = pick(PREFIX_ROOTS)
        val letter = prefixLetter(root.first())
        return Draft(
            prompt = "Вставь пропущенную букву: ${(prefix.dropLast(1) + "_" + root).uppercase()}. Ответ: одна буква.",
            answer = letter,
            solution = "Приставки на -з/-с: перед гласными и звонкими (в том числе в) — З, " +
                "перед глухими — С. Слово: «${prefix.dropLast(1)}$letter$root».",
            difficulty = 2,
        )
    }

    /** НЕ слитно/раздельно: (задание с пропуском, ответ, разбор). */
    private val NOT_WORDS: List<Triple<String, String, String>> = listOf(
        Triple("(НЕ)Большой", "слитно", "небольшой = маленький, есть синоним без НЕ"),
        Triple("(НЕ)Быль", "слитно", "небыль, слово не употребляется без НЕ"),
        Triple("(НЕ)Глупый", "слитно", "неглупый = умный, есть синоним без НЕ"),
        Triple("(НЕ)Погода", "слитно", "непогода = ненастье, есть синоним без НЕ"),
        Triple("(НЕ)Настье", "слитно", "ненастье, слово не употребляется без НЕ"),
        Triple("Это (НЕ)Правда", "раздельно", "не правда — противопоставление, пишется раздельно"),
        Triple("Вовсе (НЕ)Красивое", "раздельно", "со словом ВОВСЕ — раздельно"),
        Triple("Далеко (НЕ)Простой", "раздельно", "далеко не — усилительное сочетание, пишется раздельно"),
        Triple("Совсем (НЕ)Простой", "раздельно", "со словом СОВСЕМ — раздельно"),
    )

    private fun notTogether(): Draft {
        val (task, answer, why) = pick(NOT_WORDS)
        return Draft(
            prompt = "Слитно или раздельно? Ответ выпиши одним словом: $task.",
            answer = answer,
            solution = "«${task.uppercase().replace("(НЕ)", if (answer == "слитно") "НЕ" else "НЕ ")}» — $why.",
            difficulty = 2,
        )
    }

    /** НЕ или НИ: (предложение с пропуском, буква, восстановленное предложение). */
    private val NE_NI: List<Triple<String, String, String>> = listOf(
        Triple("Я (НЕ)ЧТО не видел.", "не", "Я НЕЧТО не видел."),
        Triple("Мне (НЕ)КОГДА идти.", "не", "Мне НЕКОГДА идти."),
        Triple("Мне (НЕ)ЗАЧЕМ спорить.", "не", "Мне НЕЗАЧЕМ спорить."),
        Triple("В комнате (НЕ)КОГО позвать.", "не", "В комнате НЕКОГО позвать."),
        Triple("Не было (НИ)ЧЕГО.", "ни", "Не было НИЧЕГО."),
        Triple("Уже (НИ)ЧУТЬ не устал.", "ни", "Уже НИЧУТЬ не устал."),
        Triple("Где (НИ) посеешь всё всходит.", "ни", "Где НИ посеешь, всё всходит."),
        Triple("Как (НИ) старайся всё получается.", "ни", "Как НИ старайся, всё получается."),
    )

    private fun neNi(): Draft {
        val (task, answer, restored) = pick(NE_NI)
        return Draft(
            prompt = "Вставь НЕ или НИ. Ответ выпиши одним словом: $task",
            answer = answer,
            solution = "«$restored» — здесь $answer.",
            difficulty = 2,
        )
    }

    // ---------- идентификация частей речи ----------

    private val PARTICIPLES = listOf("читающий", "летящий", "несомый", "горящий", "видимый", "посиневший")
    private val GERUNDS = listOf("читая", "гуляя", "решив", "улыбаясь", "сделав", "придумав")
    private val VERBS = listOf("читает", "летел", "несёт", "посеял", "горит", "увидел", "гулял", "решил", "улыбнулся", "сделал")
    private val ADVERBS = listOf("вчера", "домой", "слева", "быстро", "утром", "вечером", "направо", "пешком")
    private val PREPOSITIONS = listOf("у", "о", "возле", "через", "благодаря", "после", "перед", "над")
    private val CONJUNCTIONS = listOf("а", "но", "чтобы", "если", "зато", "или")
    private val PARTICLES = listOf("бы", "ли", "же", "даже", "только", "лишь")
    private val OTHER_WORDS = listOf("дом", "лес", "бежит", "читает", "красивый", "синий", "пять", "три")

    private val PARTICIPLE_DISTRACTORS = VERBS + GERUNDS
    private val GERUND_DISTRACTORS = VERBS + PARTICIPLES
    private val POS_DISTRACTORS = VERBS + OTHER_WORDS + PREPOSITIONS + CONJUNCTIONS + PARTICLES + ADVERBS

    private fun pickWord(targetPool: List<String>, distractorPool: List<String>, kind: String): Draft {
        val target = pick(targetPool)
        val distractors = distractorPool.filter { it != target }.shuffled(rnd).take(3)
        val options = (listOf(target) + distractors).shuffled(rnd).joinToString(", ") { it.uppercase() }
        return Draft(
            prompt = "Какое слово является $kind? Выпиши это слово: $options.",
            answer = target,
            solution = "«$target» — $kind; остальные слова — другие части речи.",
            difficulty = 1,
        )
    }

    // ---------- синтаксис и пунктуация ----------

    /** (предложение без знаков, число запятых, предложение со знаками). */
    private val HOMOGENEOUS: List<Triple<String, Int, String>> = listOf(
        Triple("В корзине лежали яблоки груши и сливы", 1, "В корзине лежали яблоки, груши и сливы"),
        Triple("Мы читали писали и рисовали", 1, "Мы читали, писали и рисовали"),
        Triple("На столе лежали тетрадь ручки карандаши и альбом", 2, "На столе лежали тетрадь, ручки, карандаши и альбом"),
        Triple("Мама купила хлеб и молоко", 0, "Мама купила хлеб и молоко"),
        Triple("Брат любит рисовать и петь", 0, "Брат любит рисовать и петь"),
        Triple("И дождь и ветер нас гоняли", 1, "И дождь, и ветер нас гоняли"),
        Triple("Мы собрали и грибы и ягоды и орехи", 2, "Мы собрали и грибы, и ягоды, и орехи"),
    )
    private val DETACHED: List<Triple<String, Int, String>> = listOf(
        Triple("Сделав уроки девочка вышла гулять", 1, "Сделав уроки, девочка вышла гулять"),
        Triple("Дорожка усыпанная хвоей вилась в лесу", 2, "Дорожка, усыпанная хвоей, вилась в лесу"),
        Triple("Усыпанные хвоей дорожки вились в лесу", 0, "Усыпанные хвоей дорожки вились в лесу"),
        Triple("Ученик решивший задачу первым поднял руку", 2, "Ученик, решивший задачу первым, поднял руку"),
        Triple("Прогуливаясь по парку мы любовались листьями", 1, "Прогуливаясь по парку, мы любовались листьями"),
        Triple("Мальчик сидел у окна читая книгу", 1, "Мальчик сидел у окна, читая книгу"),
        Triple("Не спеша туристы шли по тропе", 1, "Не спеша, туристы шли по тропе"),
        Triple("Книга лежащая на столе была забыта", 2, "Книга, лежащая на столе, была забыта"),
    )

    private fun commaCount(patterns: List<Triple<String, Int, String>>): Draft {
        val (raw, count, punct) = pick(patterns)
        return Draft(
            prompt = "Сколько запятых нужно поставить в предложении? Ответ дай числом: «$raw».",
            answer = count.toString(),
            solution = "С знаками: «$punct».",
            difficulty = 2,
        )
    }

    private val INTRO: List<Triple<String, String, String>> = listOf(
        Triple("КОНЕЧНО мы не пойдём в кино", "конечно", "Конечно, мы не пойдём в кино"),
        Triple("КАЖЕТСЯ дождь скоро кончится", "кажется", "Кажется, дождь скоро кончится"),
        Triple("ВО-ПЕРВЫХ это дешевле", "во-первых", "Во-первых, это дешевле"),
        Triple("К СОЖАЛЕНИЮ поезд опоздал", "к сожалению", "К сожалению, поезд опоздал"),
        Triple("МОЖЕТ БЫТЬ они придут позже", "может быть", "Может быть, они придут позже"),
        Triple("КОРОЧЕ ГОВОРЯ идея всем понравилась", "короче говоря", "Короче говоря, идея всем понравилась"),
    )

    private fun introWord(): Draft {
        val (task, answer, punct) = pick(INTRO)
        return Draft(
            prompt = "Выпиши вводное слово: $task.",
            answer = answer,
            solution = "Вводное слово «$answer» выделяется запятой: «$punct».",
            difficulty = 2,
        )
    }

    private val SENTENCES_SUBJECT: List<Pair<String, String>> = listOf(
        "Трескучий МРОЗ сковал реку" to "мороз",
        "Пурга заметала тропинку" to "пурга",
        "Рыжая БЕЛКА прыгала по веткам" to "белка",
        "Первый СНЕГ укрыл землю" to "снег",
        "Осенний ВЕТЕР срывает листья" to "ветер",
        "Утренний ТУМАН медленно поднимался над рекой" to "туман",
    )

    private fun subjectOf(): Draft {
        val (sentence, subject) = pick(SENTENCES_SUBJECT)
        return Draft(
            prompt = "Какое слово является подлежащим? Выпиши его: «$sentence».",
            answer = subject,
            solution = "Подлежащее — «$subject»: оно называет то, о ком говорится в предложении.",
            difficulty = 1,
        )
    }

    /** 1 — определённо-личное, 2 — неопределённо-личное, 3 — безличное. */
    private val ONE_PART: List<Pair<String, Int>> = listOf(
        "Мне не спится" to 3,
        "Хочется спать" to 3,
        "Вечереет" to 3,
        "В доме топили печку" to 2,
        "По радио передают новости" to 2,
        "Гуляем в парке" to 1,
        "Нас не догонишь" to 1,
        "Грибов в лесу не найдёшь" to 1,
    )
    private val ONE_PART_LABELS = mapOf(1 to "определённо-личное", 2 to "неопределённо-личное", 3 to "безличное")

    private fun onePartType(): Draft {
        val (sentence, type) = pick(ONE_PART)
        return Draft(
            prompt = "Определи тип односоставного предложения. Ответ дай числом: " +
                "1 — определённо-личное, 2 — неопределённо-личное, 3 — безличное. «$sentence».",
            answer = type.toString(),
            solution = "«$sentence» — ${ONE_PART_LABELS[type]} предложение.",
            difficulty = 2,
        )
    }

    /** Второстепенный член, выраженный выделенным словом. */
    private val MINOR_MEMBERS: List<Pair<String, String>> = listOf(
        "В парке мы любовались КРАСИВЫМИ клумбами" to "определение",
        "Дети рисуют КРАСКАМИ" to "дополнение",
        "Птицы улетели НА ЮГ" to "обстоятельство",
        "Мы читаем ИНТЕРЕСНУЮ книгу" to "определение",
        "Он пишет БЫСТРО" to "обстоятельство",
        "Мама купила МОЛОКО" to "дополнение",
        "Мы гуляли В ЛЕСУ" to "обстоятельство",
    )

    private fun minorMember(): Draft {
        val (sentence, member) = pick(MINOR_MEMBERS)
        return Draft(
            prompt = "Каким второстепенным членом является выделенное слово? Выпиши его название: «$sentence».",
            answer = member,
            solution = "Выделенное слово — $member.",
            difficulty = 2,
        )
    }
}
