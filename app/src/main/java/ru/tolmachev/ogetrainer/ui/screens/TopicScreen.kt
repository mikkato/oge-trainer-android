package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.luminance
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

/**
 * Экран темы. Список тем — полноэкранный слой, который уезжает после выбора,
 * освобождая экран под теорию и задачи (двухколоночная вёрстка веба на телефоне не читается).
 */
@Composable
fun TopicScreen(
    profile: Profile,
    subjectCode: String,
    subjectTitle: String,
    onMenu: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val dark = colors.background.luminance() < 0.5f

    var rows by remember { mutableStateOf<List<TopicRow>>(emptyList()) }
    var selected by remember { mutableStateOf<TopicRow?>(null) }
    var listOpen by remember { mutableStateOf(true) }
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
        TopBar(
            title = subjectTitle,
            onMenu = onMenu,
            onBack = onBack,
            trailing = {
                if (!listOpen) {
                    Text(
                        text = "Темы",
                        style = MaterialTheme.typography.labelLarge,
                        color = OgeColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Space.fieldRadius))
                            .clickable { listOpen = true }
                            .padding(horizontal = Space.x2, vertical = Space.x1),
                    )
                }
            },
        )

        AnimatedContent(
            targetState = listOpen,
            transitionSpec = {
                if (targetState) {
                    // возврат к списку: он входит слева
                    (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(160)))
                } else {
                    // выбор сделан: список уезжает влево, освобождая место
                    (slideInHorizontally(tween(220)) { it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(160)))
                }
            },
            label = "topics",
            modifier = Modifier.fillMaxSize(),
        ) { showList ->
            if (showList) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.x3),
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
                                listOpen = false
                            },
                        )
                    }
                }
            } else {
                TopicContent(
                    topic = selected,
                    tab = tab,
                    onTab = { tab = it },
                    theoryTitle = theoryTitle,
                    theory = theoryContent,
                    dark = dark,
                    onOpenTopics = { listOpen = true },
                )
            }
        }
    }
}

@Composable
private fun TopicContent(
    topic: TopicRow?,
    tab: Int,
    onTab: (Int) -> Unit,
    theoryTitle: String,
    theory: String?,
    dark: Boolean,
    onOpenTopics: () -> Unit,
) {
    val colors = LocalOgeColors.current
    if (topic == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Тема не выбрана",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TabRow(tab, onTab)
        when (tab) {
            0 -> TheoryPane(topic, theoryTitle, theory, dark, onOpenTopics)
            else -> PracticePane(topic, onOpenTopics)
        }
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.material3.TabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        listOf("Теория", "Задачи").forEachIndexed { index, title ->
            androidx.compose.material3.Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TheoryPane(
    topic: TopicRow,
    theoryTitle: String,
    theory: String?,
    dark: Boolean,
    onOpenTopics: () -> Unit,
) {
    val colors = LocalOgeColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        if (theoryTitle.isNotBlank() && theoryTitle != topic.title) {
            item {
                Text(
                    text = theoryTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
        }
        item {
            when {
                theory == null -> Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                        Text(
                            "Конспекта нет в банке. Генерация через провайдера — этап 3 плана.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }
                theory.isBlank() -> Card {
                    Text("Пустой конспект", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                else -> Card { MarkdownView(markdown = theory, dark = dark) }
            }
        }
        item {
            Text(
                text = "← Другая тема",
                style = MaterialTheme.typography.labelLarge,
                color = OgeColors.Accent,
                modifier = Modifier
                    .clickable { onOpenTopics() }
                    .padding(vertical = Space.x2),
            )
        }
    }
}

@Composable
private fun PracticePane(topic: TopicRow, onOpenTopics: () -> Unit) {
    val colors = LocalOgeColors.current
    var shortCount by remember(topic.code) { mutableStateOf(-1) }
    LaunchedEffect(topic.code) {
        shortCount = Graph.study.countTasks(topic.code, "short")
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                Text(
                    "В банке по теме: ${if (shortCount < 0) "…" else shortCount} задач с кратким ответом",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
                Text(
                    "Выдача задачи, локальная проверка и разбор — этап 2 плана.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
        }
        Text(
            text = "← Другая тема",
            style = MaterialTheme.typography.labelLarge,
            color = OgeColors.Accent,
            modifier = Modifier
                .clickable { onOpenTopics() }
                .padding(vertical = Space.x2),
        )
    }
}

@Composable
private fun TopicNode(topic: TopicRow, selected: Boolean, child: Boolean, onClick: () -> Unit) {
    val colors = LocalOgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Space.listRow)
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
            .padding(start = if (child) Space.x4 else Space.x2, end = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = topic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OgeColors.Accent else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (Mastery.isCompleted(topic.mastery)) "✓" else "${(topic.mastery * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (Mastery.isCompleted(topic.mastery)) colors.okText else colors.muted,
        )
    }
}
