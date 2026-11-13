package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import org.json.JSONObject
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.data.study.StudyRepository
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.MathText
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Очередь проверки письменных работ. Родитель и администратор видят ответ ученика,
 * рубрику и предварительный вердикт модели; итоговый балл ставит человек,
 * и только после этого освоение темы пересчитывается.
 */
@Composable
fun ReviewScreen(reviewer: Profile, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<StudyRepository.ReviewItem>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        items = Graph.study.pendingReviews()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Проверка работ", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.x3,
                end = Space.x3,
                bottom = Space.x5,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            item {
                Text(
                    if (items.isEmpty()) {
                        "Очередь пуста: все письменные ответы проверены."
                    } else {
                        "Ожидают проверки: ${items.size}. Пока балл не подтверждён, он не влияет на освоение."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { text ->
                item {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(items, key = { it.attemptId }) { item ->
                ReviewCard(
                    item = item,
                    reviewer = reviewer,
                    onApprove = { points ->
                        scope.launch {
                            Graph.study.approveReview(item.attemptId, reviewer.role, points)
                            message = "Оценка подтверждена: ${item.profileName}"
                            reload++
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    item: StudyRepository.ReviewItem,
    reviewer: Profile,
    onApprove: (Int) -> Unit,
) {
    var points by remember(item.attemptId) { mutableStateOf(item.suggestedPoints) }
    val verdict = remember(item.verdictJson) {
        item.verdictJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    Card {
        Column(
            modifier = Modifier.padding(Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    item.profileName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    sourceLabel(item.checkedBy),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                item.topicTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                MathText.renderSafe(item.prompt),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
            )
            Text(
                "Ответ: ${MathText.renderSafe(item.answer)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
            )

            verdict?.let { json ->
                val modelName = json.optString("model").takeIf { it.isNotBlank() }
                Text(
                    "Предварительная оценка модели" + (modelName?.let { " ($it)" } ?: "") + ": " +
                        "${json.optInt("score")} из ${item.maxPoints} · " +
                        "уверенность ${(json.optDouble("confidence", 0.0) * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val criteria = json.optJSONArray("criteria")
                if (criteria != null) {
                    for (index in 0 until criteria.length()) {
                        val criterion = criteria.optJSONObject(index) ?: continue
                        Text(
                            (if (criterion.optBoolean("met")) "✓ " else "✗ ") +
                                criterion.optString("title") +
                                criterion.optString("comment").let { if (it.isBlank()) "" else " — $it" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ChoiceRow(
                options = (0..item.maxPoints).map { it.toString() },
                selected = points.toString(),
                onSelect = { points = it.toIntOrNull() ?: 0 },
            )
            PrimaryButton(text = "Подтвердить ${points} из ${item.maxPoints}") {
                onApprove(points)
            }
        }
    }
}

private fun sourceLabel(checkedBy: String): String = when (checkedBy) {
    "llm-local" -> "модель на устройстве"
    "llm-remote" -> "облачная модель"
    "none" -> "без вердикта"
    else -> checkedBy
}
