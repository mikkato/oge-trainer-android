package ru.tolmachev.ogetrainer.data.study

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import ru.tolmachev.ogetrainer.data.db.SubjectStat
import ru.tolmachev.ogetrainer.data.db.TaskItem
import ru.tolmachev.ogetrainer.data.db.TopicRow
import ru.tolmachev.ogetrainer.data.db.doubleOf
import ru.tolmachev.ogetrainer.data.db.intOf
import ru.tolmachev.ogetrainer.data.db.longOf
import ru.tolmachev.ogetrainer.data.db.mapRows
import ru.tolmachev.ogetrainer.data.db.nullableStringOf
import ru.tolmachev.ogetrainer.data.db.stringOf
import ru.tolmachev.ogetrainer.domain.AnswerNormalizer
import ru.tolmachev.ogetrainer.domain.Difficulty
import ru.tolmachev.ogetrainer.domain.Mastery

/**
 * Учебные данные и запись попыток — всё локально.
 *
 * Освоение темы: EMA с шагом по числу попыток и сложности задания (см. [Mastery]),
 * затухание 5 % в неделю применяется при чтении, поэтому в базе хранится «сырое»
 * значение, а интерфейс видит актуальное.
 *
 * Развёрнутые ответы не оцениваются сразу: попытка получает статус pending,
 * балл от модели считается предварительным и на освоение не влияет до подтверждения
 * родителем или администратором.
 */
class StudyRepository(private val db: OgeDatabase) {

    data class AttemptResult(
        val correct: Boolean,
        val points: Int,
        val maxPoints: Int,
        val feedback: String,
        val masteryAfter: Double,
        val status: String = "checked",
        /** Сколько попыток по теме после этой — нужно для статуса «мало данных». */
        val attemptsAfter: Int? = null,
    )

    data class ReviewItem(
        val attemptId: Long,
        val profileId: Long,
        val profileName: String,
        val topicTitle: String,
        val prompt: String,
        val answer: String,
        val verdictJson: String?,
        val suggestedPoints: Int,
        val maxPoints: Int,
        val checkedBy: String,
        val createdAt: Long,
    )

    data class DiagnosticsTask(
        /** Код раздела (родительская тема). */
        val section: String,
        /** Название раздела для отчёта. */
        val sectionTitle: String,
        val subjectCode: String,
        val task: TaskItem,
    )

    data class DiagnosticsResult(
        val tasksDone: Int,
        val score: Double,
        val sections: List<Pair<String, Double>>,
    )

    // ---------- чтение ----------

    suspend fun subjectStats(profileId: Long): List<SubjectStat> = withContext(Dispatchers.IO) {
        db.read { database ->
            val now = System.currentTimeMillis()
            database.rawQuery(
                """
                SELECT s.code AS code, s.title AS title,
                       (SELECT COUNT(*) FROM topics t WHERE t.subject_code = s.code) AS topic_count,
                       (SELECT COUNT(*) FROM topics t WHERE t.subject_code = s.code AND
                           COALESCE((SELECT m.mastery FROM topic_mastery m
                                     WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0) >= 0.0) AS topic_count2
                FROM subjects s
                WHERE s.code IN (SELECT ps.subject_code FROM program_subjects ps
                                 JOIN programs p ON p.id = ps.program_id WHERE p.profile_id = ?)
                ORDER BY s.rowid
                """.trimIndent(),
                arrayOf(profileId.toString(), profileId.toString()),
            ).mapRows { cursor -> cursor.stringOf("code") to cursor.stringOf("title") }
                .map { (code, title) ->
                    val rows = topicMastery(database, profileId, code, now)
                    val completed = rows.count { Mastery.isCompleted(it.first) }
                    SubjectStat(
                        code = code,
                        title = title,
                        topicCount = rows.size,
                        completed = completed,
                        avgMastery = if (rows.isEmpty()) 0.0 else rows.sumOf { it.first } / rows.size,
                    )
                }
        }
    }

    /** mastery и attempts по темам предмета с учётом затухания. */
    private fun topicMastery(
        database: SQLiteDatabase,
        profileId: Long,
        subjectCode: String,
        now: Long,
    ): List<Pair<Double, Int>> =
        database.rawQuery(
            """
            SELECT COALESCE((SELECT m.mastery FROM topic_mastery m
                             WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0) AS mastery,
                   COALESCE((SELECT m.attempts_count FROM topic_mastery m
                             WHERE m.topic_code = t.code AND m.profile_id = ?), 0) AS attempts,
                   COALESCE((SELECT m.last_attempt_at FROM topic_mastery m
                             WHERE m.topic_code = t.code AND m.profile_id = ?), 0) AS last_at
            FROM topics t WHERE t.subject_code = ?
            """.trimIndent(),
            arrayOf(profileId.toString(), profileId.toString(), profileId.toString(), subjectCode),
        ).mapRows {
            Mastery.decay(it.getDouble(0), it.getLong(2), now) to it.getInt(1)
        }

    suspend fun topics(profileId: Long, subjectCode: String): List<TopicRow> = withContext(Dispatchers.IO) {
        db.read { database ->
            val now = System.currentTimeMillis()
            database.rawQuery(
                """
                SELECT t.code AS code, t.subject_code AS subject_code, t.parent_code AS parent_code,
                       t.title AS title, t.pos AS pos, t.grade AS grade,
                       COALESCE((SELECT m.mastery FROM topic_mastery m
                                 WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0) AS mastery,
                       COALESCE((SELECT m.attempts_count FROM topic_mastery m
                                 WHERE m.topic_code = t.code AND m.profile_id = ?), 0) AS attempts,
                       COALESCE((SELECT m.last_attempt_at FROM topic_mastery m
                                 WHERE m.topic_code = t.code AND m.profile_id = ?), 0) AS last_at
                FROM topics t
                WHERE t.subject_code = ?
                  AND t.grade <= COALESCE((SELECT MAX(p.grade) FROM programs p WHERE p.profile_id = ?), 9)
                ORDER BY t.grade, t.pos
                """.trimIndent(),
                arrayOf(
                    profileId.toString(),
                    profileId.toString(),
                    profileId.toString(),
                    subjectCode,
                    profileId.toString(),
                ),
            ).mapRows { cursor ->
                TopicRow(
                    code = cursor.stringOf("code"),
                    subjectCode = cursor.stringOf("subject_code"),
                    parentCode = cursor.nullableStringOf("parent_code"),
                    title = cursor.stringOf("title"),
                    pos = cursor.intOf("pos"),
                    grade = cursor.intOf("grade"),
                    mastery = Mastery.decay(cursor.doubleOf("mastery"), cursor.longOf("last_at"), now),
                    attempts = cursor.intOf("attempts"),
                )
            }
        }
    }

    /**
     * Следующая задача: сначала нерешённые, затем повтор. Сложность подбирается под
     * освоение темы (низкое — базовые, высокое — повышенные), проверенные задачи — первыми.
     */
    companion object {
        /** Письменные типы, как они записаны в банке: extended, izlozhenie, sochinenie. */
        val WRITTEN_TYPES = listOf("extended", "izlozhenie", "sochinenie")
    }

    suspend fun pickTask(profileId: Long, topicCode: String, type: String): TaskItem? =
        pickTask(profileId, topicCode, listOf(type))

    suspend fun pickTask(profileId: Long, topicCode: String, types: List<String>): TaskItem? =
        withContext(Dispatchers.IO) {
            db.read { database ->
                val mastery = database.rawQuery(
                    "SELECT mastery FROM topic_mastery WHERE profile_id = ? AND topic_code = ?",
                    arrayOf(profileId.toString(), topicCode),
                ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
                val target = when {
                    mastery < 0.4 -> 1
                    mastery < 0.75 -> 2
                    else -> 3
                }

                fun query(sql: String, args: Array<String>): TaskItem? = database.rawQuery(sql, args)
                    .mapRows { cursor ->
                        TaskItem(
                            id = cursor.longOf("id"),
                            subjectCode = cursor.stringOf("subject_code"),
                            topicCode = cursor.stringOf("topic_code"),
                            type = cursor.stringOf("type"),
                            prompt = cursor.stringOf("prompt"),
                            answer = cursor.stringOf("answer"),
                            solution = cursor.stringOf("solution"),
                            maxPoints = cursor.intOf("max_points"),
                            trusted = cursor.intOf("trusted") == 1,
                            origin = cursor.stringOf("origin"),
                            rubric = runCatching { cursor.stringOf("rubric") }.getOrDefault(""),
                            media = runCatching { cursor.stringOf("media") }.getOrDefault(""),
                        )
                    }.firstOrNull()

                val placeholders = types.joinToString(",") { "'" + it + "'" }
                // Только принятые задания (trusted = 1) и не выше класса профиля.
                // Порядок подстановки: тема, класс профиля, профиль (исключение решённых), сложность.
                val base =
                    "SELECT * FROM tasks WHERE topic_code = ? AND type IN ($placeholders) AND trusted = 1" +
                        " AND grade <= COALESCE((SELECT MAX(p.grade) FROM programs p WHERE p.profile_id = ?), 9)" +
                        " AND id NOT IN (SELECT task_id FROM attempts WHERE profile_id = ?)"
                val args = arrayOf(topicCode, profileId.toString(), profileId.toString())

                // 1) нерешённая задача нужной сложности, 2) любая нерешённая принятая
                query(
                    "$base AND (origin LIKE ? OR origin LIKE ?) ORDER BY id LIMIT 1",
                    args + arrayOf("local:%", "llm:%"),
                )?.takeIf { Difficulty.of(it.origin) == target }
                    ?: query("$base ORDER BY id LIMIT 1", args)
            }
        }

    suspend fun countTasks(topicCode: String, type: String): Int = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE topic_code = ? AND type = ?",
                arrayOf(topicCode, type),
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }
    }

    // ---------- короткий ответ ----------

    suspend fun submitShortAnswer(
        profileId: Long,
        task: TaskItem,
        answerText: String,
    ): AttemptResult? = withContext(Dispatchers.IO) {
        val correct = AnswerNormalizer.matches(answerText, task.answer)
        val points = if (correct) task.maxPoints else 0
        val score = if (task.maxPoints > 0) points.toDouble() / task.maxPoints else 0.0
        val feedback = buildString {
            if (correct) append("Верно! Ответ: ${task.answer}.")
            else append("Неверно. Правильный ответ: ${task.answer}.")
            if (task.solution.isNotBlank()) append("\n\nРешение:\n${task.solution}")
        }
        writeAttempt(
            profileId = profileId,
            task = task,
            answerText = answerText,
            correct = correct,
            points = points,
            score = score,
            feedback = feedback,
            status = "checked",
            checkedBy = "local",
            verdictJson = null,
            affectMastery = true,
        )
    }

    // ---------- развёрнутый ответ ----------

    /**
     * Запись письменной попытки. Даже при вердикте модели она остаётся pending:
     * освоение не меняется, пока родитель или админ не подтвердит балл.
     */
    suspend fun submitDetailedAnswer(
        profileId: Long,
        task: TaskItem,
        answerText: String,
        suggestedPoints: Int,
        checkedBy: String,
        verdictJson: String?,
    ): AttemptResult? = withContext(Dispatchers.IO) {
        val points = suggestedPoints.coerceIn(0, task.maxPoints)
        val feedback = "Ответ отправлен на проверку. Предварительная оценка: $points из ${task.maxPoints}."
        writeAttempt(
            profileId = profileId,
            task = task,
            answerText = answerText,
            correct = false,
            points = points,
            score = if (task.maxPoints > 0) points.toDouble() / task.maxPoints else 0.0,
            feedback = feedback,
            status = "pending",
            checkedBy = checkedBy,
            verdictJson = verdictJson,
            affectMastery = false,
        )
    }

    /** Очередь проверки: все письменные ответы со статусом pending. */
    suspend fun pendingReviews(profileId: Long? = null): List<ReviewItem> = withContext(Dispatchers.IO) {
        db.read { database ->
            val where = if (profileId == null) "a.status = 'pending'" else "a.status = 'pending' AND a.profile_id = ?"
            val args = if (profileId == null) null else arrayOf(profileId.toString())
            database.rawQuery(
                """
                SELECT a.id, a.profile_id, p.full_name, t.title, ta.prompt, a.answer_text,
                       a.verdict_json, a.points, a.max_points, a.checked_by, a.created_at
                FROM attempts a
                JOIN profiles p ON p.id = a.profile_id
                JOIN tasks ta ON ta.id = a.task_id
                LEFT JOIN topics t ON t.code = ta.topic_code
                WHERE $where
                ORDER BY a.created_at DESC
                """.trimIndent(),
                args,
            ).mapRows {
                ReviewItem(
                    attemptId = it.getLong(0),
                    profileId = it.getLong(1),
                    profileName = it.getString(2),
                    topicTitle = it.getString(3) ?: "",
                    prompt = it.getString(4),
                    answer = it.getString(5),
                    verdictJson = it.getString(6),
                    suggestedPoints = it.getInt(7),
                    maxPoints = it.getInt(8),
                    checkedBy = it.getString(9),
                    createdAt = it.getLong(10),
                )
            }
        }
    }

    /** Подтверждение оценки родителем или администратором: только здесь меняется освоение. */
    suspend fun approveReview(
        attemptId: Long,
        reviewer: String,
        points: Int,
    ): AttemptResult? = withContext(Dispatchers.IO) {
        db.write { database ->
            val row = database.rawQuery(
                "SELECT profile_id, task_id, max_points FROM attempts WHERE id = ?",
                arrayOf(attemptId.toString()),
            ).use { if (it.moveToFirst()) Triple(it.getLong(0), it.getLong(1), it.getInt(2)) else null }
                ?: return@write null

            val safePoints = points.coerceIn(0, row.third)
            val score = if (row.third > 0) safePoints.toDouble() / row.third else 0.0
            database.update(
                "attempts",
                ContentValues().apply {
                    put("points", safePoints)
                    put("score", score)
                    put("correct", if (score >= 0.7) 1 else 0)
                    put("status", "checked")
                    put("checked_by", reviewer)
                    put("feedback", "Проверено: $reviewer. Балл $safePoints из ${row.third}.")
                },
                "id = ?",
                arrayOf(attemptId.toString()),
            )

            val topicCode = database.rawQuery(
                "SELECT topic_code FROM tasks WHERE id = ?",
                arrayOf(row.second.toString()),
            ).use { if (it.moveToFirst()) it.getString(0) else "" }

            val masteryAfter = updateMastery(
                database = database,
                profileId = row.first,
                topicCode = topicCode,
                score = score,
                difficulty = 1,
            )
            AttemptResult(
                correct = score >= 0.7,
                points = safePoints,
                maxPoints = row.third,
                feedback = "Оценка подтверждена: $safePoints из ${row.third}.",
                masteryAfter = masteryAfter,
                status = "checked",
                attemptsAfter = attemptCount(database, row.first, topicCode),
            )
        }
    }

    // ---------- диагностика ----------

    /** План диагностики: по одной задаче на раздел, до [limit] заданий. */
    suspend fun diagnosticsPlan(profileId: Long, limit: Int = 20): List<DiagnosticsTask> =
        withContext(Dispatchers.IO) {
            db.read { database ->
                val sections = database.rawQuery(
                    """
                    SELECT t.code, t.parent_code, t.title, t.subject_code
                    FROM topics t
                    WHERE t.subject_code IN (
                        SELECT ps.subject_code FROM program_subjects ps
                        JOIN programs p ON p.id = ps.program_id WHERE p.profile_id = ?
                    )
                      AND t.grade <= COALESCE((SELECT MAX(p.grade) FROM programs p WHERE p.profile_id = ?), 9)
                    ORDER BY t.subject_code, t.grade, COALESCE(t.parent_code, t.code), t.pos
                    """.trimIndent(),
                    arrayOf(profileId.toString(), profileId.toString()),
                ).mapRows {
                    SectionTopic(
                        code = it.getString(0),
                        section = it.getString(1) ?: it.getString(0),
                        title = it.getString(2),
                        subject = it.getString(3),
                    )
                }

                val perSection = sections.groupBy { it.subject + "|" + it.section }
                val result = mutableListOf<DiagnosticsTask>()
                for ((_, topics) in perSection) {
                    if (result.size >= limit) break
                    for (topic in topics) {
                        val task = database.rawQuery(
                            "SELECT * FROM tasks WHERE topic_code = ? AND type = 'short' " +
                                "ORDER BY trusted DESC, RANDOM() LIMIT 1",
                            arrayOf(topic.code),
                        ).mapRows {
                            TaskItem(
                                id = it.longOf("id"),
                                subjectCode = it.stringOf("subject_code"),
                                topicCode = it.stringOf("topic_code"),
                                type = it.stringOf("type"),
                                prompt = it.stringOf("prompt"),
                                answer = it.stringOf("answer"),
                                solution = it.stringOf("solution"),
                                maxPoints = it.intOf("max_points"),
                                trusted = it.intOf("trusted") == 1,
                                origin = it.stringOf("origin"),
                                rubric = runCatching { it.stringOf("rubric") }.getOrDefault(""),
                        media = runCatching { it.stringOf("media") }.getOrDefault(""),
                            )
                        }.firstOrNull()
                        if (task != null) {
                            result += DiagnosticsTask(
                                section = topics.first().section,
                                sectionTitle = topics.first().title,
                                subjectCode = topic.subject,
                                task = task,
                            )
                            break
                        }
                    }
                }
                result
            }
        }

    private data class SectionTopic(
        val code: String,
        val section: String,
        val title: String,
        val subject: String,
    )

    /**
     * Сохранение диагностики: начальное освоение темы = точность по её разделу.
     * Это стартовая точка, а не «пять верных ответов подряд», поэтому m0 пишется
     * в topic_mastery напрямую с пометкой в diagnostics.
     */
    suspend fun saveDiagnostics(
        profileId: Long,
        answers: List<Triple<DiagnosticsTask, String, Boolean>>,
    ): DiagnosticsResult = withContext(Dispatchers.IO) {
        // Группируем по названию раздела: это то, что видит админ в отчёте.
        val bySection = answers.groupBy { it.first.sectionTitle }
        val sections = bySection.map { (section, list) ->
            val accuracy = if (list.isEmpty()) 0.0 else list.count { it.third }.toDouble() / list.size
            section to accuracy
        }
        val overall = if (answers.isEmpty()) 0.0 else answers.count { it.third }.toDouble() / answers.size

        db.write { database ->
            val now = System.currentTimeMillis()
            bySection.forEach { (_, list) ->
                val accuracy = if (list.isEmpty()) 0.0 else list.count { it.third }.toDouble() / list.size
                // Код раздела берём из самих ответов, а не из подписи.
                val sectionCode = list.first().first.section
                val topics = database.rawQuery(
                    "SELECT code FROM topics WHERE COALESCE(parent_code, code) = ?",
                    arrayOf(sectionCode),
                ).mapRows { it.getString(0) }
                topics.forEach { topicCode ->
                    database.insertWithOnConflict(
                        "topic_mastery",
                        null,
                        ContentValues().apply {
                            put("profile_id", profileId)
                            put("topic_code", topicCode)
                            put("mastery", accuracy.coerceIn(0.0, 1.0))
                            put("attempts_count", maxOf(1, list.size))
                            put("last_attempt_at", now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            database.insert(
                "diagnostics",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("created_at", now)
                    put("tasks_done", answers.size)
                    put("score", overall)
                    put("sections_json", JSONArray().apply {
                        sections.forEach { (name, accuracy) ->
                            put(JSONObject().put("section", name).put("accuracy", accuracy))
                        }
                    }.toString())
                },
            )
        }

        DiagnosticsResult(
            tasksDone = answers.size,
            score = overall,
            sections = sections,
        )
    }

    /** Последняя диагностика профиля. */
    suspend fun lastDiagnostics(profileId: Long): DiagnosticsResult? = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT tasks_done, score, sections_json FROM diagnostics " +
                    "WHERE profile_id = ? ORDER BY created_at DESC LIMIT 1",
                arrayOf(profileId.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val array = runCatching { JSONArray(cursor.getString(2)) }.getOrDefault(JSONArray())
                DiagnosticsResult(
                    tasksDone = cursor.getInt(0),
                    score = cursor.getDouble(1),
                    sections = buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            add(item.optString("section") to item.optDouble("accuracy", 0.0))
                        }
                    },
                )
            }
        }
    }

    // ---------- пробники ОГЭ (экзаменационный трек, решение 3) ----------

    data class ExamSubject(val code: String, val title: String, val available: Int)

    data class ExamResult(
        val id: Long,
        val subjectCode: String,
        val createdAt: Long,
        val durationS: Int,
        val total: Int,
        val correct: Int,
    )

    /** Предметы, по которым есть доверенные задания банка ФИПИ, и их количество. */
    suspend fun examSubjects(): List<ExamSubject> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                """
                SELECT s.code, s.title, COUNT(t.id) AS n
                FROM subjects s
                JOIN tasks t ON t.subject_code = s.code
                WHERE t.origin = 'bank' AND t.trusted = 1 AND t.type = 'short'
                GROUP BY s.code HAVING n > 0
                ORDER BY s.title
                """.trimIndent(),
                null,
            ).mapRows { ExamSubject(it.stringOf("code"), it.stringOf("title"), it.intOf("n")) }
        }
    }

    /** Вариант пробника: до [limit] случайных заданий банка по предмету. */
    suspend fun examPlan(subjectCode: String, limit: Int = 20): List<TaskItem> =
        withContext(Dispatchers.IO) {
            db.read { database ->
                database.rawQuery(
                    "SELECT * FROM tasks WHERE subject_code = ? AND origin = 'bank' " +
                        "AND trusted = 1 AND type = 'short' ORDER BY RANDOM() LIMIT ?",
                    arrayOf(subjectCode, limit.toString()),
                ).mapRows {
                    TaskItem(
                        id = it.longOf("id"),
                        subjectCode = it.stringOf("subject_code"),
                        topicCode = it.stringOf("topic_code"),
                        type = it.stringOf("type"),
                        prompt = it.stringOf("prompt"),
                        answer = it.stringOf("answer"),
                        solution = it.stringOf("solution"),
                        maxPoints = it.intOf("max_points"),
                        trusted = it.intOf("trusted") == 1,
                        origin = it.stringOf("origin"),
                        rubric = runCatching { it.stringOf("rubric") }.getOrDefault(""),
                        media = runCatching { it.stringOf("media") }.getOrDefault(""),
                    )
                }
            }
        }

    /**
     * Пробник не трогает topic_mastery: это отдельный экзаменационный замер
     * поверх школьной программы, а не тренировка тем.
     */
    suspend fun saveExam(
        profileId: Long,
        subjectCode: String,
        durationS: Int,
        answers: List<Pair<TaskItem, Boolean>>,
    ): Long = withContext(Dispatchers.IO) {
        db.write { database ->
            database.insert(
                "exam_results",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("subject_code", subjectCode)
                    put("created_at", System.currentTimeMillis())
                    put("duration_s", durationS)
                    put("total", answers.size)
                    put("correct", answers.count { it.second })
                },
            )
        }
    }

    suspend fun examResults(profileId: Long): List<ExamResult> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT id, subject_code, created_at, duration_s, total, correct FROM exam_results " +
                    "WHERE profile_id = ? ORDER BY created_at DESC LIMIT 30",
                arrayOf(profileId.toString()),
            ).mapRows {
                ExamResult(
                    id = it.longOf("id"),
                    subjectCode = it.stringOf("subject_code"),
                    createdAt = it.longOf("created_at"),
                    durationS = it.intOf("duration_s"),
                    total = it.intOf("total"),
                    correct = it.intOf("correct"),
                )
            }
        }
    }

    suspend fun attemptsCount(profileId: Long): Int = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                "SELECT COUNT(*) FROM attempts WHERE profile_id = ?",
                arrayOf(profileId.toString()),
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }
    }

    // ---------- внутреннее ----------

    private fun writeAttempt(
        profileId: Long,
        task: TaskItem,
        answerText: String,
        correct: Boolean,
        points: Int,
        score: Double,
        feedback: String,
        status: String,
        checkedBy: String,
        verdictJson: String?,
        affectMastery: Boolean,
    ): AttemptResult? {
        val answerHash = answerText.trim().lowercase().hashCode().toString()
        return db.write { database ->
            val inserted = database.insertWithOnConflict(
                "attempts",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("task_id", task.id)
                    put("answer_text", answerText)
                    put("answer_hash", answerHash)
                    put("correct", if (correct) 1 else 0)
                    put("score", score)
                    put("points", points)
                    put("max_points", task.maxPoints)
                    put("feedback", feedback)
                    put("status", status)
                    put("checked_by", checkedBy)
                    put("verdict_json", verdictJson)
                    put("created_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (inserted == -1L) return@write null

            val masteryAfter = if (affectMastery) {
                updateMastery(
                    database = database,
                    profileId = profileId,
                    topicCode = task.topicCode,
                    score = score,
                    difficulty = Difficulty.of(task.origin),
                )
            } else {
                readMastery(database, profileId, task.topicCode)
            }

            AttemptResult(
                correct = correct,
                points = points,
                maxPoints = task.maxPoints,
                feedback = feedback,
                masteryAfter = masteryAfter,
                status = status,
                attemptsAfter = attemptCount(database, profileId, task.topicCode),
            )
        }
    }

    private fun attemptCount(database: SQLiteDatabase, profileId: Long, topicCode: String): Int =
        database.rawQuery(
            "SELECT attempts_count FROM topic_mastery WHERE profile_id = ? AND topic_code = ?",
            arrayOf(profileId.toString(), topicCode),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun readMastery(database: SQLiteDatabase, profileId: Long, topicCode: String): Double =
        database.rawQuery(
            "SELECT mastery FROM topic_mastery WHERE profile_id = ? AND topic_code = ?",
            arrayOf(profileId.toString(), topicCode),
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

    /** Пересчёт освоения: шаг по числу попыток и сложности задания. */
    private fun updateMastery(
        database: SQLiteDatabase,
        profileId: Long,
        topicCode: String,
        score: Double,
        difficulty: Int,
    ): Double {
        val (current, attempts) = database.rawQuery(
            "SELECT mastery, attempts_count FROM topic_mastery WHERE profile_id = ? AND topic_code = ?",
            arrayOf(profileId.toString(), topicCode),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) to cursor.getInt(1) else 0.0 to 0
        }
        val next = Mastery.next(current, score, attempts, difficulty)
        database.insertWithOnConflict(
            "topic_mastery",
            null,
            ContentValues().apply {
                put("profile_id", profileId)
                put("topic_code", topicCode)
                put("mastery", next)
                put("attempts_count", attempts + 1)
                put("last_attempt_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        return next
    }
}

/** Совместимость с прежним импортом верхнего уровня. */
typealias AttemptResult = StudyRepository.AttemptResult
