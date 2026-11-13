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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.llm.LocalLlm
import ru.tolmachev.ogetrainer.data.study.AttemptResult
import ru.tolmachev.ogetrainer.data.study.StudyRepository
import ru.tolmachev.ogetrainer.domain.Difficulty
import ru.tolmachev.ogetrainer.domain.Mastery
import ru.tolmachev.ogetrainer.domain.Rubric
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import androidx.compose.ui.platform.LocalContext
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.TopicRow
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
                    profile = profile,
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
    profile: ru.tolmachev.ogetrainer.data.db.Profile,
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
            else -> PracticePane(profile, topic, onOpenTopics)
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
private fun PracticePane(
    profile: ru.tolmachev.ogetrainer.data.db.Profile,
    topic: TopicRow,
    onOpenTopics: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mode by remember(topic.code) { mutableStateOf("Короткие") }
    var task by remember(topic.code) { mutableStateOf<ru.tolmachev.ogetrainer.data.db.TaskItem?>(null) }
    var answer by remember(topic.code) { mutableStateOf("") }
    var result by remember(topic.code) { mutableStateOf<StudyRepository.AttemptResult?>(null) }
    var pendingMessage by remember(topic.code) { mutableStateOf<String?>(null) }
    var busy by remember(topic.code) { mutableStateOf(false) }
    var shortCount by remember(topic.code) { mutableStateOf(-1) }
    // пропущенные в этой сессии темы не подсунуть повторно (пока не решены/не закрыт экран)
    val skipped = remember(topic.code) { mutableSetOf<Long>() }

    suspend fun loadNext(type: String) {
        val types = if (type == "short") listOf("short") else StudyRepository.WRITTEN_TYPES
        task = Graph.study.pickTask(profile.id, topic.code, types, skipped)
        answer = ""
        result = null
        pendingMessage = null
    }

    LaunchedEffect(topic.code, mode) {
        shortCount = Graph.study.countTasks(topic.code, "short")
        loadNext(if (mode == "Короткие") "short" else "detailed")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        ChoiceRow(
            options = listOf("Короткие", "Письменные"),
            selected = mode,
            onSelect = { mode = it },
        )

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                val current = task
                if (current == null) {
                    Text(
                        if (shortCount < 0) "Загружаем задание…" else "В этой теме нет заданий выбранного типа",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                } else {
                    Text(
                        "Задание · сложность ${Difficulty.label(Difficulty.of(current.origin))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    MarkdownView(
                        markdown = ru.tolmachev.ogetrainer.ui.components.MathText.promptForRender(
                            current.media, current.prompt,
                        ),
                        dark = androidx.compose.foundation.isSystemInDarkTheme(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val current = task
        if (current != null && result == null && pendingMessage == null) {
            if (mode == "Короткие") {
                Field(label = "Ответ", value = answer, onValueChange = { answer = it })
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Развёрнутый ответ") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                PrimaryButton(
                    text = if (busy) "Проверяем…" else "Ответить",
                    enabled = !busy && answer.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    busy = true
                    scope.launch {
                        result = if (mode == "Короткие") {
                            Graph.study.submitShortAnswer(profile.id, current, answer)
                        } else {
                            null
                        }
                        if (mode != "Короткие") {
                            val criteria = Rubric.parseStored(
                                current.rubricOrDefault(),
                                Rubric.forType(current.type, current.maxPoints),
                            )
                            // Для проверки — отдельная модель (если выбрана), иначе модель генерации.
                            val checkEntry = (Graph.settings.checkModel.value ?: Graph.settings.localModel.value)
                                ?.let { ru.tolmachev.ogetrainer.data.models.ModelCatalog.byId(it) }
                            val verdict = when {
                                checkEntry != null && LocalLlm.isAvailable ->
                                    Graph.answerChecker.checkLocal(
                                        context, checkEntry, current, answer, criteria,
                                    ).getOrNull()
                                Graph.provider.hasKey() ->
                                    Graph.answerChecker.checkCloud(current, answer, criteria).getOrNull()
                                else -> null
                            }
                            val points = verdict?.score?.toInt() ?: 0
                            Graph.study.submitDetailedAnswer(
                                profileId = profile.id,
                                task = current,
                                answerText = answer,
                                suggestedPoints = points,
                                checkedBy = verdict?.source ?: "none",
                                verdictJson = verdict?.let {
                                    org.json.JSONObject().apply {
                                        put("score", it.score)
                                        put("max_score", it.maxScore)
                                        put("confidence", it.confidence)
                                        put("summary", it.summary)
                                        put("source", it.source)
                                        put("model", checkEntry?.title ?: "")
                                        put(
                                            "criteria",
                                            org.json.JSONArray().apply {
                                                it.criteria.forEach { criterion ->
                                                    put(
                                                        org.json.JSONObject().apply {
                                                            put("id", criterion.id)
                                                            put("title", criterion.title)
                                                            put("met", criterion.met)
                                                            put("comment", criterion.comment)
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    }.toString()
                                },
                            )
                            pendingMessage = if (verdict != null) {
                                "Отправлено на проверку. Предварительно: ${verdict.score.toInt()} из ${current.maxPoints}, " +
                                    "уверенность ${(verdict.confidence * 100).toInt()} %. Окончательно подтвердит родитель или админ."
                            } else {
                                "Отправлено на проверку. Модель не дала вердикта — оценит родитель или админ."
                            }
                        }
                        busy = false
                    }
                }
                GhostButton(text = "Пропустить", enabled = !busy) {
                    task?.let { skipped += it.id }
                    scope.launch { loadNext(if (mode == "Короткие") "short" else "detailed") }
                }
            }
        }

        result?.let { res ->
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text(
                        if (res.correct) "Верно" else "Неверно",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (res.correct) colors.okText else colors.badText,
                    )
                    Text(
                        "Балл ${res.points} из ${res.maxPoints} · освоение темы " +
                            "${(res.masteryAfter * 100).toInt()} % · " +
                            Mastery.statusLabel(res.masteryAfter, res.attemptsAfter ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    MarkdownView(
                        markdown = ru.tolmachev.ogetrainer.ui.components.MathText.forKatex(res.feedback),
                        dark = androidx.compose.foundation.isSystemInDarkTheme(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            PrimaryButton(text = "Следующее задание") {
                scope.launch { loadNext(if (mode == "Короткие") "short" else "detailed") }
            }
        }

        pendingMessage?.let { message ->
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    Text("Ожидает проверки", style = MaterialTheme.typography.titleSmall)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            }
            PrimaryButton(text = "Следующее задание") {
                scope.launch { loadNext(if (mode == "Короткие") "short" else "detailed") }
            }
        }

        Text(
            text = "← Другая тема",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
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
        // Раздел верхнего уровня подписываем классом: 5 класс, 9 класс (ОГЭ).
        if (!child) {
            Text(
                text = "${topic.grade} класс",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                modifier = Modifier.padding(end = Space.x1),
            )
        }
        Text(
            text = if (Mastery.isCompleted(topic.mastery)) "✓" else "${(topic.mastery * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (Mastery.isCompleted(topic.mastery)) colors.okText else colors.muted,
        )
    }
}
