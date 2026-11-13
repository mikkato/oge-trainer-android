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
            )
        }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        db.read { it.rawQuery("SELECT COUNT(*) FROM profiles", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
    }

    suspend fun all(): List<Profile> = withContext(Dispatchers.IO) {
        db.read { it.rows("SELECT * FROM profiles ORDER BY id") }
    }

    suspend fun byId(id: Long): Profile? = withContext(Dispatchers.IO) {
        db.read { it.rows("SELECT * FROM profiles WHERE id = ?", arrayOf(id.toString())).firstOrNull() }
    }

    suspend fun create(
        username: String,
        fullName: String,
        role: String,
        password: String,
    ): Result<Profile> = withContext(Dispatchers.IO) {
        val name = username.trim().lowercase()
        when {
            name.length < 3 -> return@withContext Result.failure(IllegalArgumentException("Логин короче 3 символов"))
            name.any { !it.isLetterOrDigit() && it != '_' && it != '.' } ->
                return@withContext Result.failure(IllegalArgumentException("В логине допустимы буквы, цифры, «_» и «.»"))
        }
        Passwords.isAcceptable(password)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        val exists = db.read {
            it.rawQuery("SELECT 1 FROM profiles WHERE username = ?", arrayOf(name)).use { c -> c.moveToFirst() }
        }
        if (exists) return@withContext Result.failure(IllegalArgumentException("Профиль «$name» уже существует"))

        val salt = Passwords.newSalt()
        val hash = Passwords.hash(password, salt)
        val id = db.write { database ->
            database.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("username", name)
                    put("full_name", fullName.trim().ifBlank { name })
                    put("role", role)
                    put("salt", salt)
                    put("hash", hash)
                    put("active", 1)
                },
            )
        }
        val created = byId(id) ?: return@withContext Result.failure(IllegalStateException("Профиль не сохранён"))
        Result.success(created)
    }

    suspend fun authenticate(username: String, password: String): Result<Profile> =
        withContext(Dispatchers.IO) {
            val profile = db.read {
                it.rows("SELECT * FROM profiles WHERE username = ?", arrayOf(username.trim().lowercase()))
                    .firstOrNull()
            } ?: return@withContext Result.failure(IllegalArgumentException("Неверный логин или пароль"))

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

    /**
     * Демо-профиль для проверки этапа 0: ученик 9 класса с программой ОГЭ по пяти предметам.
     * Полноценное управление профилями — этап 5 (FR-802).
     */
    suspend fun createDemoStudent(): Result<Profile> = withContext(Dispatchers.IO) {
        if (db.read { it.rawQuery("SELECT 1 FROM profiles WHERE username = 'ivan'", null).use { c -> c.moveToFirst() } }) {
            return@withContext Result.failure(IllegalArgumentException("Демо-профиль уже существует"))
        }
        val existing = count()
        if (existing == 0) return@withContext Result.failure(IllegalArgumentException("Сначала создайте администратора"))

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
                    android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
        val created = db.read {
            it.rows("SELECT * FROM profiles WHERE username = 'ivan'").firstOrNull()
        } ?: return@withContext Result.failure(IllegalStateException("Демо-профиль не сохранён"))
        Result.success(created)
    }

    companion object {
        const val DEMO_PASSWORD = "ivan2027"
        val DEMO_SUBJECTS = listOf("math", "rus", "physics", "geography", "biology")
    }
}
