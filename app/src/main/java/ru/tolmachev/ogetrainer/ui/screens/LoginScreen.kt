package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.ui.components.Avatar
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.DenseRow
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

private val ROLE_LABEL = mapOf("student" to "Ученик", "parent" to "Родитель", "admin" to "Администратор")

@Composable
fun LoginScreen(onLoggedIn: (Profile) -> Unit) {
    val colors = LocalOgeColors.current
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var selected by remember { mutableStateOf<Profile?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { profiles = Graph.profiles.all() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Text("Тренировочная система", style = MaterialTheme.typography.headlineSmall, color = colors.muted)
        Text(
            "Выберите профиль и введите пароль",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )

        Card {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                items(profiles, key = { it.id }) { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    ) {
                        Avatar(profile.fullName)
                        DenseRow(
                            title = profile.fullName,
                            subtitle = ROLE_LABEL[profile.role] ?: profile.role,
                            selected = selected?.id == profile.id,
                            onClick = {
                                selected = profile
                                password = ""
                                error = null
                            },
                            trailing = if (selected?.id == profile.id) "выбран" else null,
                        )
                    }
                }
            }
        }

        val current = selected
        if (current != null) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    Field("Пароль", password, password = true) { password = it }
                    error?.let {
                        Text(it, color = OgeColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                    PrimaryButton(
                        text = if (busy) "Проверяем…" else "Войти",
                        enabled = !busy && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        busy = true
                        error = null
                        scope.launch {
                            val result = Graph.profiles.authenticate(current.username, password)
                            busy = false
                            result.fold(
                                onSuccess = {
                                    password = ""
                                    onLoggedIn(it)
                                },
                                onFailure = { error = it.message ?: "Ошибка входа" },
                            )
                        }
                    }
                }
            }
        }
    }
}
