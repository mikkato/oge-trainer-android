package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.ImportStage
import ru.tolmachev.ogetrainer.data.db.BankCounts
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.DenseRow
import ru.tolmachev.ogetrainer.ui.components.ProfileAvatar
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box

@Composable
fun AdminScreen(
    profile: Profile,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onOpenSpike: () -> Unit,
    onEditProfile: (Profile?) -> Unit,
    onOpenProvider: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenBank: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val importState by Graph.importState.collectAsState()
    val scope = rememberCoroutineScope()
    var counts by remember { mutableStateOf(BankCounts(0, 0, 0, 0)) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var programInfo by remember { mutableStateOf<Map<Long, Triple<Int, String, List<String>>>>(emptyMap()) }
    var providerUrl by remember { mutableStateOf("") }
    var providerModel by remember { mutableStateOf("") }
    var providerKeyMask by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var generateFor by remember { mutableStateOf<Profile?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var demoMessage by remember { mutableStateOf("") }

    LaunchedEffect(importState.stage, demoMessage) {
        counts = Graph.bank.counts()
        attempts = Graph.study.attemptsCount(profile.id)
        profiles = Graph.profiles.all()
        programInfo = profiles.mapNotNull { p ->
            Graph.profiles.programOf(p.id)?.let { p.id to it }
        }.toMap()
        val config = Graph.provider.load()
        providerUrl = config.baseUrl
        providerModel = config.model
        providerKeyMask = Graph.provider.maskedKey()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Администрирование", onMenu = onMenu, onBack = onBack)
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
                        "${profile.fullName} · ${profile.username} · ${profile.role}" +
                            if (profile.builtin) " · встроенный" else "",
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
                Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                    Text("Банк", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        "предметов ${counts.subjects} · тем ${counts.topics} · конспектов ${counts.theories} · задач ${counts.tasks}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    GhostButton(text = "Открыть банк") { onOpenBank() }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text(
                        "Профили",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.x1, vertical = Space.x0_5),
                    ) {
                        Text("ИМЯ", style = MaterialTheme.typography.labelSmall, color = colors.muted.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                        Text("РОЛЬ", style = MaterialTheme.typography.labelSmall, color = colors.muted.copy(alpha = 0.7f), modifier = Modifier.width(64.dp))
                        Text("КЛ.", style = MaterialTheme.typography.labelSmall, color = colors.muted.copy(alpha = 0.7f), modifier = Modifier.width(30.dp))
                        Box(Modifier.width(40.dp))
                        Box(Modifier.width(40.dp))
                    }
                    profiles.forEach { item ->
                        val info = programInfo[item.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Space.fieldRadius))
                                .clickable { onEditProfile(item) }
                                .padding(horizontal = Space.x1, vertical = Space.x0_5),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(item.photoPath, item.fullName, size = 26)
                            Column(Modifier.weight(1f).padding(start = Space.x1)) {
                                Text(
                                    item.fullName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                )
                                Text(
                                    buildString {
                                        append(item.username)
                                        if (item.age > 0) append(" · ${item.age}")
                                        if (item.builtin) append(" · встроенный")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.muted,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                when (item.role) {
                                    "parent" -> "родитель"
                                    "admin" -> "админ"
                                    else -> "ученик"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                info?.first?.toString() ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted,
                                modifier = Modifier.width(30.dp),
                            )
                            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (item.role == "student") {
                                    IconButton(
                                        onClick = { generateFor = item },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Сгенерировать задания",
                                            tint = OgeColors.Accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                if (!item.builtin) {
                                    IconButton(
                                        onClick = { pendingDelete = item },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Удалить профиль",
                                            tint = colors.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    GhostButton(text = "Добавить профиль") { onEditProfile(null) }
                    Text(
                        "Сброс удаляет все профили и их попытки, затем создаёт встроенного администратора.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted.copy(alpha = 0.75f),
                    )
                    GhostButton(text = "Сбросить профили", enabled = true) {
                        scope.launch {
                            val admin = Graph.profiles.resetUsers()
                            demoMessage = "Профили сброшены, встроенный администратор: ${admin.username}"
                        }
                    }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Провайдер", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        if (providerUrl.isBlank()) {
                            "Не настроен: работают банк, теория и проверка коротких ответов."
                        } else {
                            "$providerUrl · $providerModel" +
                                if (providerKeyMask.isNotBlank()) " · ключ $providerKeyMask" else " · без ключа"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (providerUrl.isBlank()) colors.muted else colors.text,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        GhostButton(
                            text = if (providerUrl.isBlank()) "Настроить провайдера" else "Изменить настройки",
                        ) { onOpenProvider() }
                        GhostButton(text = "Локальные модели") { onOpenModels() }
                    }
                }
            }
        }
    }

    generateFor?.let { target ->
        GenerateDialog(
            profile = target,
            onDismiss = { generateFor = null },
            onMessage = { demoMessage = it },
            onOpenProvider = { onOpenProvider() },
        )
    }

    pendingDelete?.let { victim ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Профиль «${victim.fullName}» и его попытки будут удалены безвозвратно.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        Graph.profiles.delete(victim.id).fold(
                            onSuccess = {
                                Graph.photos.delete(victim.photoPath)
                                profiles = Graph.profiles.all()
                                demoMessage = "Профиль «${victim.fullName}» удалён"
                            },
                            onFailure = { demoMessage = it.message ?: "Ошибка" },
                        )
                    }
                }) { Text("Удалить", color = OgeColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена", color = colors.muted) }
            },
        )
    }
}
