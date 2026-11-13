package ru.tolmachev.ogetrainer.data.school

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Справочник школьных предметов по классам (assets/school/curriculum.json).
 *
 * Нужен, чтобы программа профиля собиралась не «из пяти предметов ОГЭ», а из того,
 * что реально изучается в этом классе по ФГОС. Для каждого предмета известно,
 * есть ли по нему банк заданий: если нет — предмет показывается справочно,
 * добавить его в тренировки нельзя.
 */
object SchoolCurriculum {

    data class Item(
        val code: String,
        val title: String,
        /** Код предмета в банке заданий; null — контента нет. */
        val bankCode: String?,
        val isOge: Boolean,
    )

    private var loaded = false
    private var titles: Map<String, String> = emptyMap()
    private var bankCodes: Map<String, String> = emptyMap()
    private var grades: Map<Int, List<String>> = emptyMap()
    private var oge: Set<String> = emptySet()

    fun load(context: Context) {
        if (loaded) return
        runCatching {
            val json = JSONObject(
                context.assets.open("school/curriculum.json").bufferedReader().use { it.readText() },
            )
            val subjectObject = json.getJSONObject("subjects")
            titles = subjectObject.keys().asSequence().associateWith { subjectObject.getString(it) }

            val bankObject = json.optJSONObject("bankCodes")
            bankCodes = if (bankObject == null) {
                emptyMap()
            } else {
                bankObject.keys().asSequence().associateWith { bankObject.getString(it) }
            }

            val gradeObject = json.getJSONObject("grades")
            grades = gradeObject.keys().asSequence().associate { key ->
                val list = gradeObject.getJSONArray(key)
                key.toInt() to buildList {
                    for (index in 0 until list.length()) add(list.getString(index))
                }
            }

            val ogeArray = json.optJSONArray("ogeSubjects")
            oge = buildSet {
                if (ogeArray != null) {
                    for (index in 0 until ogeArray.length()) add(ogeArray.getString(index))
                }
            }
        }.onFailure { Log.e("SchoolCurriculum", "не удалось прочитать справочник", it) }
        loaded = true
    }

    /** Предметы класса из справочника: сначала те, по которым есть банк. */
    fun subjectsFor(context: Context, grade: Int): List<Item> {
        load(context)
        val codes = grades[grade] ?: emptyList()
        return codes.map { code ->
            Item(
                code = code,
                title = titles[code] ?: code,
                bankCode = bankCodes[code],
                isOge = code in oge,
            )
        }.sortedWith(compareByDescending<Item> { it.bankCode != null }.thenBy { it.isOge.not() })
    }

    /** Все предметы класса, включая те, по которым нет контента. */
    fun hasBank(context: Context, grade: Int): Boolean =
        subjectsFor(context, grade).any { it.bankCode != null }

    fun title(context: Context, code: String): String {
        load(context)
        return titles[code] ?: code
    }
}
