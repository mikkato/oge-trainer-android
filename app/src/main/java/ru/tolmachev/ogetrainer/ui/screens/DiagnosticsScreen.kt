package ru.tolmachev.ogetrainer.ui.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.study.StudyRepository
import ru.tolmachev.ogetrainer.domain.AnswerNormalizer
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Диагностический срез: 15–25 коротких заданий по разделам программы, без подсказок.
 * Результат задаёт стартовое освоение тем (точность по разделу), поэтому ученик
 * не начинает с нуля, а занимается с реального уровня.
 */
@Composable
fun DiagnosticsScreen(profile: Profile, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<List<StudyRepository.DiagnosticsTask>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var answers by remember {
        mutableStateOf<List<Triple<StudyRepository.DiagnosticsTask, String, Boolean>>>(emptyList())
    }
    var summary by remember { mutableStateOf<StudyRepository.DiagnosticsResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var subjectTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(profile.id) {
        plan = Graph.study.diagnosticsPlan(profile.id, limit = 20)
        val codes = plan.map { it.subjectCode }.distinct()
        subjectTitles = codes.associateWith { Graph.bank.subjectTitle(it) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (summary == null) "Диагностика · ${profile.fullName}" else "Результат диагностики",
            onBack = onFinish,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            val done = summary
            if (done != null) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                        Text(
                            "Выполнено ${done.tasksDone} заданий",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Общая точность ${(done.score * 100).toInt()} %",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                done.sections.forEach { (section, accuracy) ->
                    Card {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                section,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(accuracy * 100).toInt()} %",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (accuracy >= 0.7) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Text(
                    "Стартовое освоение тем выставлено по этим результатам. Дальше оно меняется " +
                        "по ответам на тренировках.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(text = "Готово") { onFinish() }
            } else if (plan.isEmpty()) {
                Text(
                    "Готовим задания… Если в программе профиля нет предметов, диагностику начать нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val current = plan[index]
                Text(
                    "Задание ${index + 1} из ${plan.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                        Text(
                            subjectTitles[current.subjectCode] ?: current.subjectCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ru.tolmachev.ogetrainer.ui.components.MarkdownView(
                            markdown = ru.tolmachev.ogetrainer.ui.components.MathText.forKatex(current.task.prompt),
                            dark = androidx.compose.foundation.isSystemInDarkTheme(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Field("Ответ", answer) { answer = it }
                PrimaryButton(
                    text = if (busy) "Сохраняем…" else if (index == plan.size - 1) "Завершить" else "Дальше",
                    enabled = !busy,
                ) {
                    busy = true
                    val correct = answer.isNotBlank() &&
                        AnswerNormalizer.matches(answer, current.task.answer)
                    val updated = answers + Triple(current, answer, correct)
                    answers = updated
                    answer = ""
                    if (index == plan.size - 1) {
                        scope.launch {
                            summary = Graph.study.saveDiagnostics(profile.id, updated)
                            busy = false
                        }
                    } else {
                        index += 1
                        busy = false
                    }
                }
                Text(
                    "Подсказок нет: цель — увидеть реальный уровень. Пропускать задания нельзя, " +
                        "но можно ответить наугад.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("", modifier = Modifier.padding(bottom = Space.x5))
        }
    }
}
