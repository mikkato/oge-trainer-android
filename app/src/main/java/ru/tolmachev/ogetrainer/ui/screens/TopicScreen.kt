package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.TopicRow
import ru.tolmachev.ogetrainer.domain.Mastery
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.MarkdownView
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

@Composable
fun TopicScreen(
    profile: Profile,
    subjectCode: String,
    subjectTitle: String,
    onBack: () -> Unit,
) {
    val colors = LocalOgeColors.current
    var rows by remember { mutableStateOf<List<TopicRow>>(emptyList()) }
    var selected by remember { mutableStateOf<TopicRow?>(null) }
    var tab by remember { mutableStateOf(0) }
    var theoryTitle by remember { mutableStateOf("") }
    var theoryContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile.id, subjectCode) {
        rows = Graph.study.topics(profile.id, subjectCode)
    }

    LaunchedEffect(selected?.code) {
        val code = selected?.code
        theoryContent = null
        theoryTitle = ""
        if (code != null) {
            val loaded = Graph.bank.theory(code)
            theoryTitle = loaded?.first ?: ""
            theoryContent = loaded?.second
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = subjectTitle, onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.x3),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            LazyColumn(
                modifier = Modifier.weight(0.46f),
                verticalArrangement = Arrangement.spacedBy(Space.x0_5),
            ) {
                items(rows, key = { it.code }) { topic ->
                    TopicNode(
                        topic = topic,
                        selected = selected?.code == topic.code,
                        child = topic.parentCode != null,
                        onClick = {
                            selected = topic
                            tab = 0
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxSize(),
            ) {
                val current = selected
                if (current == null) {
                    Text(
                        "Выберите тему слева: сначала теория, затем задачи.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        modifier = Modifier.padding(top = Space.x2),
                    )
                } else {
                    TabRow(tab) { tab = it }
                    when (tab) {
                        0 -> TheoryPane(current, theoryTitle, theoryContent)
                        else -> PracticePane(current)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalOgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.fieldRadius))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(Space.fieldRadius))
            .padding(Space.x0_5),
        horizontalArrangement = Arrangement.spacedBy(Space.x0_5),
    ) {
        listOf("Теория", "Задачи").forEachIndexed { index, title ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(Space.x1))
                    .background(if (selected == index) colors.accentSoft else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == index) OgeColors.Accent else colors.muted,
                )
            }
        }
    }
}

@Composable
private fun TheoryPane(topic: TopicRow, theoryTitle: String, theory: String?) {
    val colors = LocalOgeColors.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        item {
            Text(
                text = if (theoryTitle.isBlank()) topic.title else theoryTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.muted.copy(alpha = 0.98f),
            )
        }
        item {
            when {
                theory == null -> Card {
                    Text(
                        "Конспекта нет в банке. Генерация через провайдера — этап 3 плана.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
                theory.isBlank() -> Card {
                    Text("Пустой конспект", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                else -> Card { MarkdownView(theory) }
            }
        }
    }
}

@Composable
private fun PracticePane(topic: TopicRow) {
    val colors = LocalOgeColors.current
    var shortCount by remember(topic.code) { mutableStateOf(-1) }
    LaunchedEffect(topic.code) {
        shortCount = Graph.study.countTasks(topic.code, "short")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Text(topic.title, style = MaterialTheme.typography.titleMedium, color = colors.muted.copy(alpha = 0.98f))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                Text(
                    "В банке по теме: ${if (shortCount < 0) "…" else shortCount} задач с кратким ответом",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                Text(
                    "Выдача задачи, локальная проверка и разбор — этап 2 плана.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TopicNode(topic: TopicRow, selected: Boolean, child: Boolean, onClick: () -> Unit) {
    val colors = LocalOgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.fieldRadius))
            .then(
                if (selected) {
                    Modifier
                        .background(colors.accentSoft)
                        .border(1.dp, OgeColors.Accent, RoundedCornerShape(Space.fieldRadius))
                } else {
                    Modifier
                },
            )
            .clickable { onClick() }
            .padding(start = if (child) Space.x3 else Space.x2, end = Space.x2, top = Space.x1_5, bottom = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OgeColors.Accent else colors.muted.copy(alpha = 0.95f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (Mastery.isCompleted(topic.mastery)) "✓" else "${(topic.mastery * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (Mastery.isCompleted(topic.mastery)) colors.okText else colors.muted.copy(alpha = 0.7f),
        )
    }
}
