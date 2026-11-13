package ru.tolmachev.ogetrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.SubjectStat
import ru.tolmachev.ogetrainer.ui.screens.AdminScreen
import ru.tolmachev.ogetrainer.ui.screens.LoginScreen
import ru.tolmachev.ogetrainer.ui.screens.RenderSpikeScreen
import ru.tolmachev.ogetrainer.ui.screens.SetupScreen
import ru.tolmachev.ogetrainer.ui.screens.SubjectsScreen
import ru.tolmachev.ogetrainer.ui.screens.TopicScreen
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

private sealed interface Route {
    data object Subjects : Route
    data class Topic(val stat: SubjectStat) : Route
    data object Admin : Route
    data object Spike : Route
}

@Composable
fun OgeRoot() {
    val colors = LocalOgeColors.current
    val importState by Graph.importState.collectAsState()
    var profileCount by remember { mutableStateOf<Int?>(null) }
    var session by remember { mutableStateOf<Profile?>(null) }
    var route by remember { mutableStateOf<Route>(Route.Subjects) }

    LaunchedEffect(importState.stage) {
        profileCount = Graph.profiles.count()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OgeColors.Bg)
            .statusBarsPadding(),
    ) {
        val count = profileCount
        val current = session
        when {
            count == null -> {
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

            count == 0 -> SetupScreen(onDone = { profileCount = 1 })

            current == null -> LoginScreen(onLoggedIn = {
                session = it
                route = Route.Subjects
            })

            else -> when (val r = route) {
                Route.Subjects -> SubjectsScreen(
                    profile = current,
                    onOpenSubject = { route = Route.Topic(it) },
                    onOpenAdmin = { route = Route.Admin },
                    onLogout = {
                        session = null
                        route = Route.Subjects
                        profileCount = null
                    },
                )

                is Route.Topic -> TopicScreen(
                    profile = current,
                    subjectCode = r.stat.code,
                    subjectTitle = r.stat.title,
                    onBack = { route = Route.Subjects },
                )

                Route.Admin -> AdminScreen(
                    profile = current,
                    onBack = { route = Route.Subjects },
                    onOpenSpike = { route = Route.Spike },
                )

                Route.Spike -> RenderSpikeScreen(onBack = { route = Route.Admin })
            }
        }
    }
}
