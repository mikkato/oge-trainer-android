package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.db.TaskItem
import ru.tolmachev.ogetrainer.data.study.StudyRepository
import ru.tolmachev.ogetrainer.domain.AnswerNormalizer
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.MarkdownView
import ru.tolmachev.ogetrainer.ui.components.MathText
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.Space
import java.text.DateFormat
import java.util.Date

/**
 * Пробник ОГЭ: экзаменационный трек поверх школьной программы. Вариант — случайная
 * выборка доверенных заданий банка ФИПИ по предмету, с таймером. Результат пишется
 * в exam_results и НЕ меняет освоение тем: экзамен — отдельный замер.
 */
@Composable
fun ExamScreen(profile: Profile, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var subjects by remember { mutableStateOf<List<StudyRepository.ExamSubject>>(emptyList()) }
    var history by remember { mutableStateOf<List<StudyRepository.ExamResult>>(emptyList()) }
    var variant by remember { mutableStateOf<List<TaskItem>?>(null) }
    var subjectCode by remember { mutableStateOf("") }
    var subjectTitle by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf<List<Pair<TaskItem, Boolean>>>(emptyList()) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf<StudyRepository.ExamResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    var subjectTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(profile.id) {
        subjects = Graph.study.examSubjects()
        history = Graph.study.examResults(profile.id)
        subjectTitles = (subjects.map { it.code } + history.map { it.subjectCode }).distinct()
            .associateWith { Graph.bank.subjectTitle(it) }
    }

    // Таймер варианта; остановка — при сохранении результата.
    LaunchedEffect(variant, saved) {
        if (variant != null && saved == null) {
            while (true) {
                delay(1_000)
                elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (variant == null) "Пробник ОГЭ" else "ОГЭ · $subjectTitle",
            onBack = if (variant == null) onBack else ({
                variant = null
                saved = null
            }),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            val plan = variant
            val result = saved
            when {
                result != null -> {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                            Text(
                                "$subjectTitle: ${result.correct} из ${result.total}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Точность ${(result.correct * 100 / result.total.coerceAtLeast(1))} % · " +
                                    "время ${result.durationS / 60} мин ${result.durationS % 60} с",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Результат сохранён в истории экзаменов. Освоение тем он не меняет — " +
                            "для подтягивания слабых мест занимайся по темам.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(text = "К списку предметов") {
                        scope.launch {
                            history = Graph.study.examResults(profile.id)
                            variant = null
                            saved = null
                        }
                    }
                }

                plan == null -> {
                    Text(
                        "Выбери предмет — тренажёр соберёт случайный вариант из " +
                            "20 заданий банка ФИПИ с таймером.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    subjects.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        busy = true
                                        val tasks = Graph.study.examPlan(item.code, limit = 20)
                                        busy = false
                                        if (tasks.isNotEmpty()) {
                                            subjectCode = item.code
                                            subjectTitle = item.title
                                            variant = tasks
                                            index = 0
                                            answers = emptyList()
                                            answer = ""
                                            startedAt = System.currentTimeMillis()
                                            elapsed = 0
                                        }
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${item.available} заданий в банке",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    if (busy) "…" else "начать",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        Text(
                            "История пробников",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        history.forEach { item ->
                            Card {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (subjectTitles[item.subjectCode] ?: item.subjectCode),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            DateFormat.getDateInstance(DateFormat.SHORT)
                                                .format(Date(item.createdAt)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${item.correct}/${item.total}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (item.correct * 100 / item.total.coerceAtLeast(1) >= 70) {
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

                else -> {
                    val current = plan[index]
                    Text(
                        "Задание ${index + 1} из ${plan.size} · " +
                            "время ${elapsed / 60}:${"%02d".format(elapsed % 60)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Card {
                        MarkdownView(
                            markdown = MathText.promptForRender(current.media, current.prompt),
                            dark = isSystemInDarkTheme(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Field("Ответ", answer) { answer = it }
                    PrimaryButton(
                        text = if (busy) "Сохраняем…"
                        else if (index == plan.size - 1) "Завершить пробник" else "Дальше",
                        enabled = !busy,
                    ) {
                        busy = true
                        val correct = answer.isNotBlank() && AnswerNormalizer.matches(answer, current.answer)
                        val updated = answers + (current to correct)
                        answers = updated
                        answer = ""
                        if (index == plan.size - 1) {
                            scope.launch {
                                val id = Graph.study.saveExam(
                                    profileId = profile.id,
                                    subjectCode = subjectCode,
                                    durationS = elapsed,
                                    answers = updated,
                                )
                                saved = StudyRepository.ExamResult(
                                    id = id,
                                    subjectCode = subjectCode,
                                    createdAt = System.currentTimeMillis(),
                                    durationS = elapsed,
                                    total = updated.size,
                                    correct = updated.count { it.second },
                                )
                                busy = false
                            }
                        } else {
                            index += 1
                            busy = false
                        }
                    }
                }
            }
            Text("", modifier = Modifier.padding(bottom = Space.x5))
        }
    }
}
