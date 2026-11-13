package ru.tolmachev.ogetrainer

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.data.auth.ProfilesRepository
import ru.tolmachev.ogetrainer.data.bank.BankRepository
import ru.tolmachev.ogetrainer.data.db.OgeDatabase
import ru.tolmachev.ogetrainer.data.study.StudyRepository

enum class ImportStage { Idle, Running, Ready, Failed }

data class ImportState(
    val stage: ImportStage = ImportStage.Idle,
    val message: String = "",
    val topics: Int = 0,
    val theories: Int = 0,
    val tasks: Int = 0,
    val tasksInserted: Int = 0,
    val bankVersion: String = "",
    val declaredTasks: Int = 0,
)

/** Ручной контейнер зависимостей: одно приложение, DI-фреймворк не нужен. */
object Graph {
    lateinit var db: OgeDatabase
        private set
    lateinit var profiles: ProfilesRepository
        private set
    lateinit var bank: BankRepository
        private set
    lateinit var study: StudyRepository
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState

    fun init(context: Context) {
        if (::db.isInitialized) return
        val app = context.applicationContext
        db = OgeDatabase(app)
        profiles = ProfilesRepository(db)
        bank = BankRepository(app, db)
        study = StudyRepository(db)
        scope.launch { runImport() }
    }

    suspend fun runImport() {
        _importState.value = _importState.value.copy(stage = ImportStage.Running, message = "Импорт банка…")
        try {
            val result = bank.import()
            _importState.value = ImportState(
                stage = ImportStage.Ready,
                message = if (result.alreadyImported) "Банк уже импортирован" else "Банк импортирован",
                topics = result.counts.topics,
                theories = result.counts.theories,
                tasks = result.counts.tasks,
                tasksInserted = result.tasksInserted,
                bankVersion = result.exportedAt,
                declaredTasks = result.declaredTasks,
            )
        } catch (e: Exception) {
            _importState.value = ImportState(
                stage = ImportStage.Failed,
                message = "Импорт банка не удался: ${e.message}",
            )
        }
    }
}

class OgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
