package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.models.ModelCatalog
import ru.tolmachev.ogetrainer.data.models.ModelDownloads
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.DenseRow
import ru.tolmachev.ogetrainer.ui.components.SectionLabel
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Генерация заданий для профиля подключённой моделью. Вызывается кнопкой в таблице профилей.
 * Предмет и тема берутся из программы профиля; результат показывается в диалоге.
 */
@Composable
fun GenerateDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenProvider: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var subjects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var subject by remember { mutableStateOf<String?>(null) }
    var topics by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var topic by remember { mutableStateOf<Pair<String, String>?>(null) }
    var count by remember { mutableStateOf("5") }
    var grade by remember { mutableStateOf(0) }
    var gradeChosen by remember { mutableStateOf(false) }
    var providerReady by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Локально") }
    var deviceModelId by remember { mutableStateOf<String?>(null) }
    var localSupported by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile.id) {
        val selectedModel = Graph.settings.localModel.value?.let { ModelCatalog.byId(it) }
        deviceModelId = selectedModel?.id?.takeIf { ModelDownloads.isInstalled(context, selectedModel) }
        providerReady = Graph.provider.hasKey() && Graph.provider.load().baseUrl.isNotBlank()
        val all = Graph.bank.subjects()
        val program = Graph.profiles.programOf(profile.id)
        if (!gradeChosen) grade = program?.first ?: 9
        val allowed = program?.third?.toSet().orEmpty()
        subjects = if (allowed.isEmpty()) all else all.filter { it.first in allowed }
        subject = subjects.firstOrNull()?.first
    }

    LaunchedEffect(subject, grade) {
        val code = subject ?: return@LaunchedEffect
        topics = Graph.bank.topicsOf(code, grade)
        topic = topics.firstOrNull()
    }

    LaunchedEffect(topic, subject) {
        val current = topic
        val code = subject
        localSupported = current != null && code != null &&
            Graph.localGenerator.supports(code, current.second)
        if (!localSupported && providerReady) mode = "Моделью"
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Задания для «${profile.fullName}»") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x1_5),
            ) {
                val modes = buildList {
                    add("Локально")
                    if (deviceModelId != null) add("На устройстве")
                    if (providerReady) add("Моделью")
                }
                if (modes.size > 1) {
                    ChoiceRow(
                        options = modes,
                        selected = mode,
                        onSelect = { mode = it },
                    )
                }
                Text(
                    text = when (mode) {
                        "Локально" ->
                            "Без сети: числа подставляет шаблон, ответ вычисляет приложение — проверка точная."
                        "На устройстве" ->
                            "Без сети: модель работает на телефоне (встроенный llama.cpp). " +
                                "Медленнее и слабее облачной, задания требуют приёмки."
                        else ->
                            "Через подключённую модель: нужен интернет, задания разнообразнее, но требуют проверки."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
                if (!providerReady && mode == "Моделью") {
                    Text(
                        "Провайдер не настроен. Подключите модель в панели администрирования.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                } else {
                    SectionLabel("КЛАСС")
                    ChoiceRow(
                        options = listOf("5", "6", "7", "8", "9"),
                        selected = grade.toString(),
                        onSelect = {
                            gradeChosen = true
                            grade = it.toInt()
                        },
                    )
                    Text(
                        "Один и тот же предмет в разных классах — разный контент: темы и задания " +
                            "подбираются по классу, в банке они лежат отдельными группами.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                    SectionLabel("ПРЕДМЕТ")
                    subjects.forEach { (code, title) ->
                        DenseRow(
                            title = title,
                            selected = code == subject,
                            onClick = { subject = code },
                        )
                    }
                    SectionLabel("ТЕМА")
                    topics.forEach { entry ->
                        DenseRow(
                            title = entry.second,
                            selected = entry == topic,
                            onClick = { topic = entry },
                        )
                    }
                    SectionLabel("СКОЛЬКО ЗАДАНИЙ")
                    ChoiceRow(
                        options = listOf("3", "5", "10"),
                        selected = count,
                        onSelect = { count = it },
                    )
                    result?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    }
                    if (mode == "Локально" && !localSupported) {
                        Text(
                            "Для этой темы локальные шаблоны не заданы — выберите другую тему " +
                                "или режим «Моделью».",
                            style = MaterialTheme.typography.labelMedium,
                            color = OgeColors.Danger,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!providerReady && mode == "Моделью") {
                TextButton(onClick = { onDismiss(); onOpenProvider() }) {
                    Text("Настроить", color = OgeColors.Accent)
                }
            } else {
                TextButton(
                    enabled = !busy && topic != null && (mode == "Моделью" || localSupported),
                    onClick = {
                        val currentTopic = topic ?: return@TextButton
                        val subjectCode = subject ?: return@TextButton
                        busy = true
                        result = null
                        scope.launch {
                            val wanted = count.toIntOrNull() ?: 5
                            if (mode == "На устройстве") {
                                val model = deviceModelId?.let { ModelCatalog.byId(it) }
                                val batch = Graph.localLlm.generateTasks(
                                    context = context,
                                    entry = model ?: return@launch,
                                    subjectCode = subjectCode,
                                    subjectTitle = Graph.bank.subjectTitle(subjectCode),
                                    topicCode = currentTopic.first,
                                    topicTitle = currentTopic.second,
                                    grade = grade,
                                    count = wanted,
                                )
                                busy = false
                                batch.fold(
                                    onSuccess = {
                                        val text = "Добавлено ${it.added} моделью на устройстве" +
                                            if (it.rejected > 0) ", отклонено ${it.rejected}" else ""
                                        result = text
                                        onMessage("«${profile.fullName}»: $text")
                                    },
                                    onFailure = { result = it.message ?: "Ошибка локальной модели" },
                                )
                            } else if (mode == "Локально") {
                                val batch = Graph.localGenerator.generate(
                                    subjectCode = subjectCode,
                                    topicCode = currentTopic.first,
                                    topicTitle = currentTopic.second,
                                    grade = grade,
                                    count = wanted,
                                )
                                busy = false
                                batch.fold(
                                    onSuccess = {
                                        val text = "Добавлено ${it.added} локально" +
                                            if (it.skipped > 0) ", повторов ${it.skipped}" else ""
                                        result = text
                                        onMessage("«${profile.fullName}»: $text")
                                    },
                                    onFailure = { result = it.message ?: "Ошибка локальной генерации" },
                                )
                            } else {
                                val subjectTitle = Graph.bank.subjectTitle(subjectCode)
                                val batch = Graph.generator.generateTasks(
                                    subjectCode = subjectCode,
                                    subjectTitle = subjectTitle,
                                    topicCode = currentTopic.first,
                                    topicTitle = currentTopic.second,
                                    grade = grade,
                                    count = wanted,
                                )
                                busy = false
                                batch.fold(
                                    onSuccess = {
                                        val text = "Добавлено ${it.added} из ${it.requested}" +
                                            if (it.skipped > 0) ", дублей ${it.skipped}" else "" +
                                            if (it.rejected > 0) ", отклонено ${it.rejected}" else ""
                                        result = text
                                        onMessage("«${profile.fullName}»: $text")
                                    },
                                    onFailure = { result = it.message ?: "Ошибка генерации" },
                                )
                            }
                        }
                    },
                ) {
                    Text(if (busy) "Генерируем…" else "Сгенерировать", color = OgeColors.Accent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = colors.muted) }
        },
    )
}
