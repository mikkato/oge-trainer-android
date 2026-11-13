package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.bank.BankRepository
import ru.tolmachev.ogetrainer.ui.components.Card as AppCard
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.SectionLabel
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Раздел «Банк контента»: сводка, предметы, темы, задачи.
 *
 * Задачи приходят из четырёх источников: банк ФИПИ, локальные шаблоны (ответ считается кодом),
 * локальная модель на устройстве и облачная модель. Сгенерированные помечаются как черновики
 * и попадают в приёмку: ученику уходят только принятые.
 */
@Composable
fun BankScreen(onBack: () -> Unit, onOpenProvider: () -> Unit) {
    val scope = rememberCoroutineScope()

    var summary by remember { mutableStateOf<BankRepository.BankSummary?>(null) }
    var subjects by remember { mutableStateOf<List<BankRepository.SubjectBankRow>>(emptyList()) }
    var topics by remember { mutableStateOf<List<BankRepository.TopicBankRow>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<BankRepository.BankTask>>(emptyList()) }

    var filter by remember { mutableStateOf("Все") }
    var subject by remember { mutableStateOf<Pair<String, String>?>(null) }
    var topic by remember { mutableStateOf<BankRepository.TopicBankRow?>(null) }
    var generateFor by remember { mutableStateOf<BankRepository.TopicBankRow?>(null) }
    var openedTask by remember { mutableStateOf<BankRepository.BankTask?>(null) }
    var reload by remember { mutableStateOf(0) }

    val originFilter: String? = when (filter) {
        "Банк" -> "bank"
        "Шаблоны" -> "local"
        "Локально" -> "llm-local"
        "Облако" -> "llm"
        else -> null
    }

    fun reloadAll() {
        scope.launch {
            summary = Graph.bank.summary()
            subjects = Graph.bank.subjectRows(originFilter)
            subject?.let { selected -> topics = Graph.bank.topicRows(selected.first, originFilter) }
            topic?.let { selected -> tasks = Graph.bank.tasksOf(selected.code, originFilter) }
        }
    }

    LaunchedEffect(filter, subject, topic, reload) { reloadAll() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = when {
                topic != null -> "Задачи темы"
                subject != null -> subject!!.second
                else -> "Банк контента"
            },
            onBack = {
                when {
                    topic != null -> topic = null
                    subject != null -> subject = null
                    else -> onBack()
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.x3,
                end = Space.x3,
                bottom = Space.x5,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            // Сводка — только на корневом уровне; фильтр остаётся и на уровне предмета.
            if (subject == null && topic == null) {
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                            summary?.let { s ->
                                Text(
                                    "${plural(s.subjects, "предмет", "предмета", "предметов")} · " +
                                        "${plural(s.topics, "тема", "темы", "тем")} · " +
                                        "${plural(s.theories, "конспект", "конспекта", "конспектов")} · " +
                                        plural(s.tasks, "задача", "задачи", "задач"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "сгенерировано ${s.generated} · ждут проверки ${s.drafts}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (s.drafts > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (topic == null) {
                item {
                    // Один компактный фильтр M3: чип с выпадающим списком источников.
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        FilterChip(
                            selected = filter != "Все",
                            onClick = { menuOpen = true },
                            label = {
                                Text(
                                    "Источник: $filter",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                    contentDescription = "Выбрать источник",
                                )
                            },
                            shape = MaterialTheme.shapes.small,
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            listOf("Все", "Банк", "Шаблоны", "Локально", "Облако").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        filter = option
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            when {
                topic != null -> {
                    item {
                        val drafts = tasks.count { !it.trusted }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.x2),
                        ) {
                            Text(
                                "${plural(tasks.size, "задача", "задачи", "задач")} · черновиков $drafts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (drafts > 0) {
                                TextButton(onClick = {
                                    scope.launch {
                                        Graph.bank.approveDrafts(topic!!.code)
                                        reload++
                                    }
                                }) { Text("Принять все") }
                            }
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onOpen = { openedTask = task },
                            onApprove = {
                                scope.launch {
                                    Graph.bank.approveTask(task.id)
                                    reload++
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    Graph.bank.deleteTask(task.id)
                                    reload++
                                }
                            },
                        )
                    }
                    if (tasks.isEmpty()) {
                        item {
                            Text(
                                "По этому фильтру задач нет. Добавьте: «+» рядом с темой в списке тем.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                subject == null -> {
                    items(subjects, key = { it.code }) { row ->
                        ListItem(
                            headlineContent = { Text(row.title) },
                            supportingContent = {
                                Text(
                                    "${plural(row.topics, "тема", "темы", "тем")} · " +
                                        "${row.withTheory} с конспектом · " +
                                        plural(row.tasks, "задача", "задачи", "задач") +
                                        if (row.drafts > 0) " · черновиков ${row.drafts}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            trailingContent = { Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                subject = row.code to row.title
                                topic = null
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                else -> {
                    items(topics, key = { it.code }) { row ->
                        ListItem(
                            headlineContent = {
                                Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(plural(row.tasks, "задача", "задачи", "задач"))
                                        if (row.drafts > 0) append(" · черновиков ${row.drafts}")
                                        if (!row.hasTheory) append(" · нет конспекта")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { generateFor = row }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Сгенерировать задания",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { topic = row },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    openedTask?.let { task ->
        AlertDialog(
            onDismissRequest = { openedTask = null },
            title = { Text("Задание") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    ru.tolmachev.ogetrainer.ui.components.MarkdownView(
                        markdown = buildString {
                            append(task.prompt)
                            if (task.solution.isNotBlank()) {
                                append("\n\n**Решение.** ")
                                append(task.solution)
                            }
                            append("\n\n**Ответ:** ")
                            append(task.answer.ifBlank { "—" })
                        },
                        dark = androidx.compose.foundation.isSystemInDarkTheme(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { openedTask = null }) { Text("Закрыть") }
            },
        )
    }

    generateFor?.let { target ->
        BankGenerateDialog(
            topic = target,
            subjectTitle = subject?.second ?: "",
            onDismiss = { generateFor = null },
            onDone = {
                generateFor = null
                reload++
            },
            onOpenProvider = onOpenProvider,
        )
    }
}

@Composable
private fun TaskCard(
    task: BankRepository.BankTask,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.clickable { onOpen() }) {
        Column(
            modifier = Modifier.padding(Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sourceLabel(task.origin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (task.trusted) "проверено" else "черновик",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (task.trusted) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                ru.tolmachev.ogetrainer.ui.components.MathText.renderSafe(task.prompt),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Ответ: " + ru.tolmachev.ogetrainer.ui.components.MathText.renderSafe(
                    task.answer.ifBlank { "—" },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!task.trusted) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    IconButton(onClick = onApprove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Принять",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BankGenerateDialog(
    topic: BankRepository.TopicBankRow,
    subjectTitle: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onOpenProvider: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf("5") }
    var mode by remember { mutableStateOf("Шаблоны") }
    var providerReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        providerReady = Graph.provider.hasKey() && Graph.provider.load().baseUrl.isNotBlank()
    }

    val localSupported = remember(topic.code) {
        // Шаблоны покрывают вычислимые темы: проверяем по названию.
        ru.tolmachev.ogetrainer.data.provider.LocalTemplates.supports("math", topic.title) ||
            ru.tolmachev.ogetrainer.data.provider.LocalTemplates.supports("phys", topic.title) ||
            ru.tolmachev.ogetrainer.data.provider.LocalTemplates.supports("chem", topic.title)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Задания: ${topic.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                if (mode == "Шаблоны" && !localSupported) {
                    Text(
                        "Для этой темы шаблонов нет — доступны только модели.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (providerReady) {
                    ChoiceRow(
                        options = listOf("Шаблоны", "Облако"),
                        selected = mode,
                        onSelect = { mode = it },
                    )
                }
                Text(
                    if (mode == "Шаблоны") {
                        "Без сети: числа подставляет шаблон, ответ вычисляет приложение — приёмка не нужна."
                    } else {
                        "Через подключённую модель: нужен интернет, задания попадут в черновики."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceRow(
                    options = listOf("3", "5", "10"),
                    selected = count,
                    onSelect = { count = it },
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (mode == "Облако" || localSupported),
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val wanted = count.toIntOrNull() ?: 5
                        val result = if (mode == "Шаблоны") {
                            Graph.localGenerator.generate(
                                subjectCode = subjectCodeFor(topic),
                                topicCode = topic.code,
                                topicTitle = topic.title,
                                count = wanted,
                            ).map { "добавлено ${it.added}" }
                        } else {
                            Graph.generator.generateTasks(
                                subjectCode = subjectCodeFor(topic),
                                subjectTitle = subjectTitle,
                                topicCode = topic.code,
                                topicTitle = topic.title,
                                grade = 9,
                                count = wanted,
                            ).map { "добавлено ${it.added}, отклонено ${it.rejected}" }
                        }
                        busy = false
                        message = result.fold(
                            onSuccess = { "Готово: $it" },
                            onFailure = { it.message ?: "Не удалось сгенерировать" },
                        )
                    }
                },
            ) { Text(if (busy) "Генерируем…" else "Сгенерировать") }
        },
        dismissButton = {
            Row {
                if (!providerReady) {
                    TextButton(onClick = { onDismiss(); onOpenProvider() }) { Text("Провайдер") }
                }
                TextButton(onClick = { if (!busy) onDone() }) { Text("Готово") }
            }
        },
    )
}

/** Код предмета по коду темы: банк хранит коды вида math.eq.1 / phys.mech.2. */
private fun subjectCodeFor(topic: BankRepository.TopicBankRow): String =
    topic.code.substringBefore('.')

private fun sourceLabel(origin: String): String = when {
    origin == "bank" -> "Банк ФИПИ"
    origin.startsWith("local") -> "Шаблон"
    origin == "llm-local" -> "Модель на устройстве"
    origin.startsWith("llm") -> "Облачная модель"
    else -> origin
}

/** Русские числительные: 1 задача, 2 задачи, 5 задач. */
private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$count $word"
}
