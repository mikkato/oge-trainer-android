package ru.tolmachev.ogetrainer.data.study

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import ru.tolmachev.ogetrainer.domain.Mastery

/** Чтение учебных данных и запись попыток — всё локально, без сети. */
class StudyRepository(private val db: OgeDatabase) {

    suspend fun subjectStats(profileId: Long): List<SubjectStat> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                """
                SELECT s.code AS code, s.title AS title,
                       (SELECT COUNT(*) FROM topics t WHERE t.subject_code = s.code) AS topic_count,
                       (SELECT COUNT(*) FROM topics t WHERE t.subject_code = s.code AND
                           COALESCE((SELECT m.mastery FROM topic_mastery m
                                     WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0) >= 0.8) AS completed,
                       (SELECT AVG(COALESCE((SELECT m.mastery FROM topic_mastery m
                                     WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0))
                          FROM topics t WHERE t.subject_code = s.code) AS avg_mastery
                FROM subjects s
                WHERE s.code IN (SELECT ps.subject_code FROM program_subjects ps
                                 JOIN programs p ON p.id = ps.program_id WHERE p.profile_id = ?)
                ORDER BY s.rowid
                """.trimIndent(),
                arrayOf(profileId.toString(), profileId.toString(), profileId.toString()),
            ).mapRows { cursor ->
                SubjectStat(
                    code = cursor.stringOf("code"),
                    title = cursor.stringOf("title"),
                    topicCount = cursor.intOf("topic_count"),
                    completed = cursor.intOf("completed"),
                    avgMastery = cursor.doubleOf("avg_mastery"),
                )
            }
        }
    }

    suspend fun topics(profileId: Long, subjectCode: String): List<TopicRow> = withContext(Dispatchers.IO) {
        db.read { database ->
            database.rawQuery(
                """
                SELECT t.code AS code, t.subject_code AS subject_code, t.parent_code AS parent_code,
                       t.title AS title, t.pos AS pos,
                       COALESCE((SELECT m.mastery FROM topic_mastery m
                                 WHERE m.topic_code = t.code AND m.profile_id = ?), 0.0) AS mastery
                FROM topics t WHERE t.subject_code = ? ORDER BY t.pos
                """.trimIndent(),
                arrayOf(profileId.toString(), subjectCode),
            ).mapRows { cursor ->
                TopicRow(
                    code = cursor.stringOf("code"),
                    subjectCode = cursor.stringOf("subject_code"),
                    parentCode = cursor.nullableStringOf("parent_code"),
                    title = cursor.stringOf("title"),
                    pos = cursor.intOf("pos"),
                    mastery = cursor.doubleOf("mastery"),
                )
            }
        }
    }

    /** Следующая задача: сначала нерешённые, потом повтор; доверенные — первыми. */
    suspend fun pickTask(profileId: Long, topicCode: String, type: String): TaskItem? =
        withContext(Dispatchers.IO) {
            db.read { database ->
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
                        )
                    }.firstOrNull()

                query(
                    """
                    SELECT * FROM tasks WHERE topic_code = ? AND type = ?
                      AND id NOT IN (SELECT task_id FROM attempts WHERE profile_id = ?)
                    ORDER BY trusted DESC, id LIMIT 1
                    """.trimIndent(),
                    arrayOf(topicCode, type, profileId.toString()),
                ) ?: query(
                    "SELECT * FROM tasks WHERE topic_code = ? AND type = ? ORDER BY trusted DESC, id LIMIT 1",
                    arrayOf(topicCode, type),
                )
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

    /**
     * Локальная проверка короткого ответа + запись попытки + пересчёт освоения (EMA).
     * Возвращает записанную попытку или null, если такая попытка уже была (идемпотентность).
     */
    suspend fun submitShortAnswer(
        profileId: Long,
        task: TaskItem,
        answerText: String,
    ): AttemptResult? = withContext(Dispatchers.IO) {
        val correct = ru.tolmachev.ogetrainer.domain.AnswerNormalizer.matches(answerText, task.answer)
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
        )
    }

    private fun writeAttempt(
        profileId: Long,
        task: TaskItem,
        answerText: String,
        correct: Boolean,
        points: Int,
        score: Double,
        feedback: String,
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
                    put("status", "checked")
                    put("checked_by", "local")
                    put("created_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (inserted == -1L) return@write null

            val current = database.rawQuery(
                "SELECT mastery, attempts_count FROM topic_mastery WHERE profile_id = ? AND topic_code = ?",
                arrayOf(profileId.toString(), task.topicCode),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getDouble(0) to cursor.getInt(1) else 0.0 to 0
            }
            val next = Mastery.next(current.first, score)
            database.insertWithOnConflict(
                "topic_mastery",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("topic_code", task.topicCode)
                    put("mastery", next)
                    put("attempts_count", current.second + 1)
                    put("last_attempt_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )

            AttemptResult(
                correct = correct,
                points = points,
                maxPoints = task.maxPoints,
                feedback = feedback,
                masteryAfter = next,
            )
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
}

data class AttemptResult(
    val correct: Boolean,
    val points: Int,
    val maxPoints: Int,
    val feedback: String,
    val masteryAfter: Double,
)
