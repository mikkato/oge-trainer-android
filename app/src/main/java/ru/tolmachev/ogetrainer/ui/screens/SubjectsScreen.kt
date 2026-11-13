package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.SubjectStat
import ru.tolmachev.ogetrainer.ui.components.ProfileAvatar
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.ThinProgress
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

private val SUBJECT_ICON = mapOf(
    "math" to "∑",
    "physics" to "⚛",
    "rus" to "Я",
    "geography" to "🌍",
    "biology" to "🧬",
)

@Composable
fun SubjectsScreen(
    profile: Profile,
    onOpenSubject: (SubjectStat) -> Unit,
    onOpenExam: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalOgeColors.current
    var stats by remember { mutableStateOf<List<SubjectStat>>(emptyList()) }
    var examMode by remember { mutableStateOf(false) }

    LaunchedEffect(profile.id) {
        stats = Graph.study.subjectStats(profile.id)
        examMode = runCatching { Graph.profiles.programOf(profile.id)?.second == "oge" }.getOrDefault(false)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Мои предметы",
            onMenu = onMenu,
            trailing = { ProfileAvatar(profile.photoPath, profile.fullName) },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            if (stats.isEmpty()) {
                item {
                    Text(
                        text = "Предметы ещё не назначены",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        modifier = Modifier.padding(top = Space.x4),
                    )
                }
            }
            if (examMode) {
                item {
                    Card(modifier = Modifier.clickable { onOpenExam() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                                Text("Пробник ОГЭ", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Случайный вариант из банка ФИПИ с таймером — отдельный экзамен",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.muted,
                                )
                            }
                            Text("→", color = OgeColors.Accent, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            items(stats, key = { it.code }) { stat -> SubjectCard(stat) { onOpenSubject(stat) } }
        }
    }
}

@Composable
private fun SubjectCard(stat: SubjectStat, onClick: () -> Unit) {
    val colors = LocalOgeColors.current
    Card(modifier = Modifier.clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = SUBJECT_ICON[stat.code] ?: "•",
                        color = OgeColors.Accent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = stat.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.muted.copy(alpha = 0.98f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Space.x2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stat.topicCount} тем",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
            ThinProgress(stat.avgMastery.toFloat())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${(stat.avgMastery * 100).toInt()}% освоено · ${stat.completed} из ${stat.topicCount} тем",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                Text("Начать →", style = MaterialTheme.typography.labelLarge, color = OgeColors.Accent)
            }
        }
    }
}
