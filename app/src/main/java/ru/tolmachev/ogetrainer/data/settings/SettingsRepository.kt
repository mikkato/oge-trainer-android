package ru.tolmachev.ogetrainer.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.tolmachev.ogetrainer.ui.theme.ThemeMode

/**
 * Настройки устройства: режим оформления. Хранятся в SharedPreferences (не секрет),
 * чтобы переживать перезапуск без DataStore-корутин в стартовом пути.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _localModel = MutableStateFlow(prefs.getString(KEY_LOCAL_MODEL, null))
    /** Выбранная локальная модель (id из [ru.tolmachev.ogetrainer.data.models.ModelCatalog]); null — не выбрана. */
    val localModel: StateFlow<String?> = _localModel

    private val _checkModel = MutableStateFlow(prefs.getString(KEY_CHECK_MODEL, null))
    /**
     * Модель для проверки письменных ответов. Отдельная от генерации: проверка требует
     * более крупной модели, генерация — быстрой. null — проверять моделью генерации.
     */
    val checkModel: StateFlow<String?> = _checkModel

    private fun readTheme(): ThemeMode =
        when (prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setLocalModel(id: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_LOCAL_MODEL) else putString(KEY_LOCAL_MODEL, id)
        }.apply()
        _localModel.value = id
    }

    fun setCheckModel(id: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_CHECK_MODEL) else putString(KEY_CHECK_MODEL, id)
        }.apply()
        _checkModel.value = id
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LOCAL_MODEL = "local_model"
        const val KEY_CHECK_MODEL = "check_model"
    }
}
