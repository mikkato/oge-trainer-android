package ru.tolmachev.ogetrainer.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.SubjectStat
import ru.tolmachev.ogetrainer.ui.components.DrawerMenu
import ru.tolmachev.ogetrainer.ui.components.EdgeBackArea
import ru.tolmachev.ogetrainer.ui.screens.AdminScreen
import ru.tolmachev.ogetrainer.ui.screens.LoginScreen
import ru.tolmachev.ogetrainer.ui.screens.ProfileEditorScreen
import ru.tolmachev.ogetrainer.ui.screens.BankScreen
import ru.tolmachev.ogetrainer.ui.screens.ModelsScreen
import ru.tolmachev.ogetrainer.ui.screens.ProviderScreen
import ru.tolmachev.ogetrainer.ui.screens.RenderSpikeScreen
import ru.tolmachev.ogetrainer.ui.screens.SetupScreen
import ru.tolmachev.ogetrainer.ui.screens.SubjectsScreen
import ru.tolmachev.ogetrainer.ui.screens.TopicScreen
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space
import ru.tolmachev.ogetrainer.ui.theme.ThemeMode

private sealed interface Route {
    data object Subjects : Route
    data class Topic(val stat: SubjectStat) : Route
    data object Admin : Route
    data object Spike : Route
    /** Карточка профиля: null — создание, профиль — правка. */
    data class ProfileEditor(val profile: Profile?) : Route
    data object Provider : Route
    data object Models : Route
    data object Bank : Route
}

private val ROLE_LABEL = mapOf("student" to "Ученик", "parent" to "Родитель", "admin" to "Администратор")

@Composable
fun OgeRoot() {
    val colors = LocalOgeColors.current
    val context = LocalContext.current
    val importState by Graph.importState.collectAsState()
    val themeMode by Graph.settings.themeMode.collectAsState()

    var profileCount by remember { mutableStateOf<Int?>(null) }
    var session by remember { mutableStateOf<Profile?>(null) }
    var route by remember { mutableStateOf<Route>(Route.Subjects) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuHintShown by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    // Счётчик профилей обновляется при старте, после импорта и при смене сессии:
    // иначе после «Выйти из профиля» экран остаётся в состоянии загрузки.
    LaunchedEffect(importState.stage, session) {
        profileCount = Graph.profiles.count()
    }
    // На новом экране первый жест «назад» снова показывает меню.
    LaunchedEffect(route) { menuHintShown = false }

    fun goUp(): Boolean = when (route) {
        is Route.Topic -> {
            route = Route.Subjects
            true
        }
        Route.Admin -> {
            route = Route.Subjects
            true
        }
        Route.Spike -> {
            route = Route.Admin
            true
        }
        is Route.ProfileEditor -> {
            route = Route.Admin
            true
        }
        Route.Provider -> {
            route = Route.Admin
            true
        }
        Route.Models -> {
            route = Route.Admin
            true
        }
        Route.Bank -> {
            route = Route.Admin
            true
        }
        Route.Subjects -> false
    }

    /**
     * Иерархия жеста «назад» с правого края (и системной кнопки):
     * 1) меню закрыто и подсказка ещё не показана на этом экране → меню выезжает;
     * 2) повторный жест → закрыть меню и подняться на уровень выше;
     * 3) на верхнем уровне → подтверждение выхода из приложения.
     */
    val onBackGesture: () -> Unit = {
        when {
            menuOpen -> {
                menuOpen = false
                menuHintShown = false
                if (!goUp()) exitRequested = true
            }
            !menuHintShown -> {
                menuHintShown = true
                menuOpen = true
            }
            else -> if (!goUp()) exitRequested = true
        }
    }

    BackHandler(enabled = session != null) { onBackGesture() }

    EdgeBackArea(onBack = onBackGesture) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            val count = profileCount
            val current = session
            when {
                // Пока банк и встроенный администратор не созданы, «нет профилей» — это ещё не сбой.
                count == 0 && importState.stage != ru.tolmachev.ogetrainer.ImportStage.Ready -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = OgeColors.Accent,
                    )
                    Text(
                        text = importState.message.ifBlank { "Готовим банк…" },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = Space.x6),
                    )
                }

                count == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = OgeColors.Accent,
                    )
                }

                // Страховка: если встроенный администратор почему-то не создан.
                count == 0 -> SetupScreen(onDone = { profileCount = 1 })

                current == null -> LoginScreen(onLoggedIn = {
                    session = it
                    route = Route.Subjects
                })

                else -> when (val r = route) {
                    Route.Subjects -> SubjectsScreen(
                        profile = current,
                        onOpenSubject = { route = Route.Topic(it) },
                        onMenu = { menuOpen = true },
                    )

                    is Route.Topic -> TopicScreen(
                        profile = current,
                        subjectCode = r.stat.code,
                        subjectTitle = r.stat.title,
                        onMenu = { menuOpen = true },
                        onBack = { route = Route.Subjects },
                    )

                    Route.Admin -> AdminScreen(
                        profile = current,
                        onMenu = { menuOpen = true },
                        onBack = { route = Route.Subjects },
                        onOpenSpike = { route = Route.Spike },
                        onEditProfile = { route = Route.ProfileEditor(it) },
                        onOpenProvider = { route = Route.Provider },
                        onOpenModels = { route = Route.Models },
                        onOpenBank = { route = Route.Bank },
                    )

                    Route.Provider -> ProviderScreen(onBack = { route = Route.Admin })

                    Route.Models -> ModelsScreen(onBack = { route = Route.Admin })

                    Route.Bank -> BankScreen(
                        onBack = { route = Route.Admin },
                        onOpenProvider = { route = Route.Provider },
                    )

                    is Route.ProfileEditor -> ProfileEditorScreen(
                        profile = r.profile,
                        onDone = { route = Route.Admin },
                        onCancel = { route = Route.Admin },
                    )

                    Route.Spike -> RenderSpikeScreen(onBack = { route = Route.Admin })
                }
            }

            if (current != null) {
                DrawerMenu(
                    open = menuOpen,
                    userName = current.fullName,
                    userRole = ROLE_LABEL[current.role] ?: current.role,
                    onClose = {
                        menuOpen = false
                        menuHintShown = false
                    },
                    canGoUp = route !is Route.Subjects,
                    onGoUp = { goUp() },
                    onSubjects = { route = Route.Subjects },
                    adminVisible = current.role == "admin",
                    onAdmin = { route = Route.Admin },
                    onModels = { route = Route.Models },
                    onBank = { route = Route.Bank },
                    themeMode = themeMode,
                    onThemeMode = { Graph.settings.setThemeMode(it) },
                    onLogout = {
                        session = null
                        route = Route.Subjects
                    },
                )
            }
        }

        if (exitRequested) {
            AlertDialog(
                onDismissRequest = { exitRequested = false },
                title = { Text("Выйти из приложения?") },
                text = {
                    Text(
                        "Прогресс сохранён на устройстве. Приложение закроется, банк и профили останутся на месте.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        exitRequested = false
                        (context as? Activity)?.finishAffinity()
                    }) { Text("Выйти", color = OgeColors.Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { exitRequested = false }) {
                        Text("Остаться", color = colors.muted)
                    }
                },
                containerColor = colors.surfaceElevated,
                titleContentColor = colors.text,
                textContentColor = colors.muted,
            )
        }
    }
}

/** Режим оформления: прокидывается в MainActivity, чтобы тема применялась до первого кадра. */
@Composable
fun currentThemeMode(): ThemeMode = Graph.settings.themeMode.collectAsState().value
