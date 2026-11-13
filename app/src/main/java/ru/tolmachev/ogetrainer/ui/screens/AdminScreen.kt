package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.ImportStage
import ru.tolmachev.ogetrainer.data.db.BankCounts
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

@Composable
fun AdminScreen(
    profile: Profile,
    onBack: () -> Unit,
    onOpenSpike: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val importState by Graph.importState.collectAsState()
    val scope = rememberCoroutineScope()
    var counts by remember { mutableStateOf(BankCounts(0, 0, 0, 0)) }
    var attempts by remember { mutableStateOf(0) }
    var demoMessage by remember { mutableStateOf("") }

    LaunchedEffect(importState.stage) {
        counts = Graph.bank.counts()
        attempts = Graph.study.attemptsCount(profile.id)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Администрирование", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Профиль", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        "${profile.fullName} · ${profile.username} · ${profile.role}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Text(
                        "попыток: $attempts",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Банк контента", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        "предметов ${counts.subjects} · тем ${counts.topics} · конспектов ${counts.theories} · задач ${counts.tasks}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Text(
                        "в метаданных задач: ${if (importState.declaredTasks == 0) "—" else importState.declaredTasks} · экспорт ${if (importState.bankVersion.isBlank()) "—" else importState.bankVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted.copy(alpha = 0.7f),
                    )
                    Text(importState.message, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                GhostButton(
                    text = if (importState.stage == ImportStage.Running) "Импорт идёт…" else "Повторить импорт",
                    enabled = importState.stage != ImportStage.Running,
                ) { scope.launch { Graph.runImport() } }
                PrimaryButton(text = "Проверка рендера (G1)", onClick = onOpenSpike)
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Демо-данные", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        "Ученик «Иван», 9 класс, программа ОГЭ по пяти предметам, пароль ivan2027. " +
                            "Управление профилями целиком — этап 5.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    GhostButton(text = "Создать демо-профиль", enabled = true) {
                        scope.launch {
                            val result = Graph.profiles.createDemoStudent()
                            demoMessage = result.fold(
                                onSuccess = { "Демо-профиль создан: ${it.fullName}" },
                                onFailure = { it.message ?: "Не удалось" },
                            )
                        }
                    }
                    if (demoMessage.isNotBlank()) {
                        Text(demoMessage, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Провайдер", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        "Не настроен. Подключение любого OpenAI-совместимого endpoint — этап 3 плана.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}
