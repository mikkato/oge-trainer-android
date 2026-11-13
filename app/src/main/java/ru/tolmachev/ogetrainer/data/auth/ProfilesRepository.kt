package ru.tolmachev.ogetrainer.data.auth

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.intOf
import ru.tolmachev.ogetrainer.data.db.longOf
import ru.tolmachev.ogetrainer.data.db.mapRows
import ru.tolmachev.ogetrainer.data.db.stringOf

/**
 * Профили устройства.
 *
 * Правила (ревью 13.11.2026):
 * - роль проверяется по белому списку [ROLES], иначе в БД могло попасть любое значение;
 * - встроенный администратор `admin` закреплён: создаётся автоматически, не удаляется,
 *   не переименовывается, логин `admin` больше никому не выдаётся;
 * - пароль хранится только как PBKDF2-хеш (210 000 итераций, соль 16 байт);
 * - встроенный администратор всегда первый в списке выбора.
 */
class ProfilesRepository(private val db: OgeDatabase) {

    private fun SQLiteDatabase.rows(sql: String, args: Array<String> = emptyArray()): List<Profile> =
        rawQuery(sql, args).mapRows { cursor ->
            Profile(
                id = cursor.longOf("id"),
                username = cursor.stringOf("username"),
                fullName = cursor.stringOf("full_name"),
                role = cursor.stringOf("role"),
                salt = cursor.stringOf("salt"),
                hash = cursor.stringOf("hash"),
                active = cursor.intOf("active") == 1,
                lastLoginAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_login_at"))
                    .takeIf { !cursor.isNull(cursor.getColumnIndexOrThrow("last_login_at")) },
                builtin = cursor.intOf("builtin") == 1,
                age = cursor.intOf("age"),
                photoPath = cursor.stringOf("photo_path").ifBlank { null },
            )
        }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        db.read { it.rawQuery("SELECT COUNT(*) FROM profiles", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
    }

    suspend fun all(): List<Profile> = withContext(Dispatchers.IO) {
        db.read { it.rows("SELECT * FROM profiles ORDER BY builtin DESC, id") }
    }

    suspend fun byId(id: Long): Profile? = withContext(Dispatchers.IO) {
        db.read { it.rows("SELECT * FROM profiles WHERE id = ?", arrayOf(id.toString())).firstOrNull() }
    }

    suspend fun byUsername(username: String): Profile? = withContext(Dispatchers.IO) {
        db.read { it.rows("SELECT * FROM profiles WHERE username = ?", arrayOf(username.trim().lowercase())) }
            .firstOrNull()
    }

    /**
     * Встроенный администратор. Идемпотентно: создаёт, если отсутствует; помечает builtin,
     * если профиль `admin` был создан вручную (пароль в этом случае не меняется —
     * для полного сброса есть [resetUsers]).
     */
    suspend fun ensureBuiltinAdmin(): Profile = withContext(Dispatchers.IO) {
        val existing = byUsername(BUILTIN_USERNAME)
        if (existing != null) {
            if (!existing.builtin) {
                db.write { database ->
                    database.update(
                        "profiles",
                        ContentValues().apply { put("builtin", 1) },
                        "id = ?",
                        arrayOf(existing.id.toString()),
                    )
                }
                return@withContext existing.copy(builtin = true)
            }
            return@withContext existing
        }

        val salt = Passwords.newSalt()
        val hash = Passwords.hash(BUILTIN_PASSWORD, salt)
        val newId = db.write { database ->
            val inserted = database.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("username", BUILTIN_USERNAME)
                    put("full_name", BUILTIN_NAME)
                    put("role", "admin")
                    put("salt", salt)
                    put("hash", hash)
                    put("active", 1)
                    put("builtin", 1)
                },
            )
            inserted
        }
        byId(newId) ?: throw IllegalStateException("Встроенный администратор не создан")
    }

    /**
     * Сброс пользовательской базы: удаляются все профили и связанные с ними данные,
     * затем встроенный администратор создаётся заново.
     */
    suspend fun resetUsers(): Profile = withContext(Dispatchers.IO) {
        db.write { database ->
            database.delete("attempts", null, null)
            database.delete("topic_mastery", null, null)
            database.delete("program_subjects", null, null)
            database.delete("programs", null, null)
            database.delete("profiles", null, null)
        }
        ensureBuiltinAdmin()
    }

    suspend fun create(
        username: String,
        fullName: String,
        role: String,
        password: String,
        age: Int = 0,
        photoPath: String? = null,
        grade: Int? = null,
        programType: String? = null,
        subjects: List<String> = emptyList(),
    ): Result<Profile> = withContext(Dispatchers.IO) {
        val name = username.trim().lowercase()
        when {
            name.length < 3 ->
                return@withContext Result.failure(IllegalArgumentException("Логин короче 3 символов"))
            name.any { !it.isLetterOrDigit() && it != '_' && it != '.' } ->
                return@withContext Result.failure(
                    IllegalArgumentException("В логине допустимы буквы, цифры, «_» и «.»"),
                )
            name == BUILTIN_USERNAME ->
                return@withContext Result.failure(
                    IllegalArgumentException("Логин «admin» закреплён за встроенным администратором"),
                )
            role !in ROLES ->
                return@withContext Result.failure(IllegalArgumentException("Недопустимая роль: $role"))
        }
        Passwords.isAcceptable(password)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        if (byUsername(name) != null) {
            return@withContext Result.failure(IllegalArgumentException("Профиль «$name» уже существует"))
        }

        val salt = Passwords.newSalt()
        val hash = Passwords.hash(password, salt)
        val newId = db.write { database ->
            val inserted = database.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("username", name)
                    put("full_name", fullName.trim().ifBlank { name })
                    put("role", role)
                    put("salt", salt)
                    put("hash", hash)
                    put("active", 1)
                    put("builtin", 0)
                    put("age", age)
                    put("photo_path", photoPath)
                },
            )
            if (grade != null) {
                saveProgram(database, inserted, grade, programType, subjects)
            }
            inserted
        }
        val created = byId(newId)
            ?: return@withContext Result.failure(IllegalStateException("Профиль не сохранён"))
        Result.success(created)
    }

    suspend fun authenticate(username: String, password: String): Result<Profile> =
        withContext(Dispatchers.IO) {
            val profile = byUsername(username)
                ?: return@withContext Result.failure(IllegalArgumentException("Неверный логин или пароль"))

            if (!profile.active) return@withContext Result.failure(IllegalArgumentException("Профиль отключён"))
            if (!Passwords.verify(password, profile.salt, profile.hash)) {
                return@withContext Result.failure(IllegalArgumentException("Неверный логин или пароль"))
            }
            val now = System.currentTimeMillis()
            db.write { database ->
                database.update(
                    "profiles",
                    ContentValues().apply { put("last_login_at", now) },
                    "id = ?",
                    arrayOf(profile.id.toString()),
                )
            }
            Result.success(profile.copy(lastLoginAt = now))
        }

    /** Программа профиля: класс, тип и предметы. Заменяет существующую. */
    suspend fun setProgram(
        profileId: Long,
        grade: Int,
        programType: String,
        subjects: List<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (grade !in 1..11) return@withContext Result.failure(IllegalArgumentException("Класс от 1 до 11"))
        if (programType !in PROGRAM_TYPES) {
            return@withContext Result.failure(IllegalArgumentException("Недопустимый тип программы"))
        }
        db.write { database -> saveProgram(database, profileId, grade, programType, subjects) }
        Result.success(Unit)
    }

    private fun saveProgram(
        database: SQLiteDatabase,
        profileId: Long,
        grade: Int,
        programType: String?,
        subjects: List<String>,
    ) {
        val type = programType ?: if (grade == 9) "oge" else "school"
        val existing = database.rawQuery(
            "SELECT id FROM programs WHERE profile_id = ?",
            arrayOf(profileId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

        val programId = if (existing == null) {
            database.insertOrThrow(
                "programs",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("type", type)
                    put("grade", grade)
                },
            )
        } else {
            database.update(
                "programs",
                ContentValues().apply {
                    put("type", type)
                    put("grade", grade)
                },
                "id = ?",
                arrayOf(existing.toString()),
            )
            database.delete("program_subjects", "program_id = ?", arrayOf(existing.toString()))
            existing
        }
        subjects.distinct().forEach { code ->
            database.insertWithOnConflict(
                "program_subjects",
                null,
                ContentValues().apply {
                    put("program_id", programId)
                    put("subject_code", code)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    /** Класс и тип программы профиля — для карточки и списка. */
    suspend fun programOf(profileId: Long): Triple<Int, String, List<String>>? = withContext(Dispatchers.IO) {
        val program = db.read { database ->
            database.rawQuery(
                "SELECT id, type, grade FROM programs WHERE profile_id = ? LIMIT 1",
                arrayOf(profileId.toString()),
            ).use { c ->
                if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1), c.getInt(2)) else null
            }
        } ?: return@withContext null

        val subjects = db.read { database ->
            database.rawQuery(
                "SELECT subject_code FROM program_subjects WHERE program_id = ?",
                arrayOf(program.first.toString()),
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        }
        Triple(program.third, program.second, subjects)
    }

    /** Правка карточки: ФИО, возраст, фото. Логин и роль не меняются. */
    suspend fun updateCard(
        profileId: Long,
        fullName: String,
        age: Int,
        photoPath: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val profile = byId(profileId) ?: return@withContext Result.failure(IllegalArgumentException("Профиль не найден"))
        if (fullName.trim().length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Имя слишком короткое"))
        }
        if (age < 0 || age > 99) return@withContext Result.failure(IllegalArgumentException("Возраст от 0 до 99"))
        db.write { database ->
            database.update(
                "profiles",
                ContentValues().apply {
                    put("full_name", fullName.trim())
                    put("age", age)
                    put("photo_path", photoPath)
                },
                "id = ?",
                arrayOf(profile.id.toString()),
            )
        }
        Result.success(Unit)
    }

    /** Смена пароля. У встроенного администратора пароль меняется только полным сбросом. */
    suspend fun setPassword(profileId: Long, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val profile = byId(profileId) ?: return@withContext Result.failure(IllegalArgumentException("Профиль не найден"))
        if (profile.builtin) {
            return@withContext Result.failure(
                IllegalArgumentException("Пароль встроенного администратора меняется только полным сбросом профилей"),
            )
        }
        Passwords.isAcceptable(password)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        val salt = Passwords.newSalt()
        val hash = Passwords.hash(password, salt)
        db.write { database ->
            database.update(
                "profiles",
                ContentValues().apply {
                    put("salt", salt)
                    put("hash", hash)
                },
                "id = ?",
                arrayOf(profileId.toString()),
            )
        }
        Result.success(Unit)
    }

    /** Удаление профиля вместе с его данными. Встроенный администратор неудаляем. */
    suspend fun delete(profileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val profile = byId(profileId) ?: return@withContext Result.failure(IllegalArgumentException("Профиль не найден"))
        if (profile.builtin) {
            return@withContext Result.failure(IllegalArgumentException("Встроенного администратора удалить нельзя"))
        }
        db.write { database ->
            database.delete("attempts", "profile_id = ?", arrayOf(profileId.toString()))
            database.delete("topic_mastery", "profile_id = ?", arrayOf(profileId.toString()))
            val programs = database.rawQuery(
                "SELECT id FROM programs WHERE profile_id = ?",
                arrayOf(profileId.toString()),
            ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            programs.forEach { programId ->
                database.delete("program_subjects", "program_id = ?", arrayOf(programId.toString()))
            }
            database.delete("programs", "profile_id = ?", arrayOf(profileId.toString()))
            database.delete("profiles", "id = ?", arrayOf(profileId.toString()))
        }
        Result.success(Unit)
    }

    /**
     * Демо-профиль для проверки этапов 0–2: ученик 9 класса с программой ОГЭ
     * по пяти предметам. Полноценное управление профилями — этап 5 (FR-802).
     */
    suspend fun createDemoStudent(): Result<Profile> = withContext(Dispatchers.IO) {
        if (byUsername("ivan") != null) {
            return@withContext Result.failure(IllegalArgumentException("Демо-профиль уже существует"))
        }
        val salt = Passwords.newSalt()
        val hash = Passwords.hash(DEMO_PASSWORD, salt)
        db.write { database ->
            val profileId = database.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("username", "ivan")
                    put("full_name", "Иван")
                    put("role", "student")
                    put("salt", salt)
                    put("hash", hash)
                    put("active", 1)
                    put("builtin", 0)
                },
            )
            val programId = database.insertOrThrow(
                "programs",
                null,
                ContentValues().apply {
                    put("profile_id", profileId)
                    put("type", "oge")
                    put("grade", 9)
                },
            )
            DEMO_SUBJECTS.forEach { code ->
                database.insertWithOnConflict(
                    "program_subjects",
                    null,
                    ContentValues().apply {
                        put("program_id", programId)
                        put("subject_code", code)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
        val created = byUsername("ivan")
            ?: return@withContext Result.failure(IllegalStateException("Демо-профиль не сохранён"))
        Result.success(created)
    }

    companion object {
        val ROLES = setOf("student", "parent", "admin")
        val PROGRAM_TYPES = setOf("oge", "school")

        const val BUILTIN_USERNAME = "admin"
        const val BUILTIN_NAME = "Администратор"
        const val BUILTIN_PASSWORD = "!DoB030282"

        const val DEMO_PASSWORD = "ivan2027"
        val DEMO_SUBJECTS = listOf("math", "rus", "physics", "geography", "biology")
    }
}
