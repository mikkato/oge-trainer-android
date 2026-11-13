package ru.tolmachev.ogetrainer.data.db

/**
 * Схема и модели. Room/KSP на AGP 9 недоступны (KSP есть только до Kotlin 2.3),
 * поэтому работа идёт напрямую с SQLite — как в AGK-проекте. Все запросы
 * выполняются на Dispatchers.IO из репозиториев.
 */
object Schema {
    const val DB_NAME = "oge.db"
    const val VERSION = 8

    val CREATE = listOf(
        """
        CREATE TABLE IF NOT EXISTS profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            full_name TEXT NOT NULL,
            role TEXT NOT NULL,
            salt TEXT NOT NULL,
            hash TEXT NOT NULL,
            active INTEGER NOT NULL DEFAULT 1,
            last_login_at INTEGER,
            builtin INTEGER NOT NULL DEFAULT 0,
            age INTEGER NOT NULL DEFAULT 0,
            photo_path TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS programs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            profile_id INTEGER NOT NULL,
            type TEXT NOT NULL,
            grade INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS program_subjects (
            program_id INTEGER NOT NULL,
            subject_code TEXT NOT NULL,
            PRIMARY KEY (program_id, subject_code)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS subjects (
            code TEXT PRIMARY KEY,
            title TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS topics (
            code TEXT PRIMARY KEY,
            subject_code TEXT NOT NULL,
            parent_code TEXT,
            title TEXT NOT NULL,
            pos INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT 'fipi',
            grade INTEGER NOT NULL DEFAULT 9
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_topics_subject ON topics(subject_code)",
        """
        CREATE TABLE IF NOT EXISTS theories (
            topic_code TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            updated_at INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            subject_code TEXT NOT NULL,
            topic_code TEXT NOT NULL,
            type TEXT NOT NULL,
            prompt TEXT NOT NULL,
            answer TEXT NOT NULL DEFAULT '',
            solution TEXT NOT NULL DEFAULT '',
            max_points INTEGER NOT NULL DEFAULT 1,
            trusted INTEGER NOT NULL DEFAULT 0,
            origin TEXT NOT NULL DEFAULT 'bank',
            rubric TEXT NOT NULL DEFAULT '',
            grade INTEGER NOT NULL DEFAULT 9,
            media TEXT NOT NULL DEFAULT '',
            content_hash TEXT NOT NULL UNIQUE
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_tasks_topic ON tasks(topic_code, type)",
        """
        CREATE TABLE IF NOT EXISTS attempts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            profile_id INTEGER NOT NULL,
            task_id INTEGER NOT NULL,
            answer_text TEXT NOT NULL,
            answer_hash TEXT NOT NULL,
            correct INTEGER NOT NULL DEFAULT 0,
            score REAL NOT NULL DEFAULT 0,
            points INTEGER,
            max_points INTEGER,
            feedback TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'checked',
            checked_by TEXT NOT NULL DEFAULT 'local',
            created_at INTEGER NOT NULL,
            UNIQUE (profile_id, task_id, answer_hash)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS topic_mastery (
            profile_id INTEGER NOT NULL,
            topic_code TEXT NOT NULL,
            mastery REAL NOT NULL DEFAULT 0,
            attempts_count INTEGER NOT NULL DEFAULT 0,
            last_attempt_at INTEGER,
            PRIMARY KEY (profile_id, topic_code)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS diagnostics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            profile_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            tasks_done INTEGER NOT NULL DEFAULT 0,
            score REAL NOT NULL DEFAULT 0,
            sections_json TEXT NOT NULL DEFAULT '[]'
        )
        """,
        // v7: пробники ОГЭ — отдельный экзаменационный трек, вне школьного освоения тем.
        """
        CREATE TABLE IF NOT EXISTS exam_results (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            profile_id INTEGER NOT NULL,
            subject_code TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            duration_s INTEGER NOT NULL DEFAULT 0,
            total INTEGER NOT NULL DEFAULT 0,
            correct INTEGER NOT NULL DEFAULT 0
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_attempts_pending ON attempts(profile_id, status)",
        """
        CREATE TABLE IF NOT EXISTS provider_config (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            base_url TEXT NOT NULL DEFAULT '',
            model TEXT NOT NULL DEFAULT '',
            temperature REAL NOT NULL DEFAULT 0.4,
            max_tokens INTEGER NOT NULL DEFAULT 1500,
            has_key INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS sync_meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,
    )
}

data class Profile(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: String,
    val salt: String,
    val hash: String,
    val active: Boolean,
    val lastLoginAt: Long?,
    /** Встроенный администратор: нельзя удалить, переименовать или лишить роли. */
    val builtin: Boolean = false,
    /** Возраст в годах; 0 — не задан. */
    val age: Int = 0,
    /** Относительный путь к фото в filesDir (например, photos/<uuid>.jpg); null — нет фото. */
    val photoPath: String? = null,
)

data class SubjectStat(
    val code: String,
    val title: String,
    val topicCount: Int,
    val completed: Int,
    val avgMastery: Double,
)

data class TopicRow(
    val code: String,
    val subjectCode: String,
    val parentCode: String?,
    val title: String,
    val pos: Int,
    /** Класс, с которого тема изучается. */
    val grade: Int = 9,
    val mastery: Double,
    /** Число попыток по теме: до трёх вывод об уровне не делается. */
    val attempts: Int = 0,
)

data class TaskItem(
    val id: Long,
    val subjectCode: String,
    val topicCode: String,
    val type: String,
    val prompt: String,
    val answer: String,
    val solution: String,
    val maxPoints: Int,
    val trusted: Boolean,
    val origin: String,
    /** Рубрика проверки, если задана в банке; иначе берётся по типу задания. */
    val rubric: String = "",
    /** Путь картинки в assets/bank/ (например media/graphs/math8_1.png); пусто — без графики. */
    val media: String = "",
) {
    fun rubricOrDefault(): String = rubric
}

data class BankCounts(
    val subjects: Int,
    val topics: Int,
    val theories: Int,
    val tasks: Int,
)
