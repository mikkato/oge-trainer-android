package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.bank.TaskBrief
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.MarkdownView
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Гейт G1: 20 реальных условий из банка с формулами — проверка, что WebView + офлайн-KaTeX
 * рендерит дроби, корни, степени и выноски так же, как web-версия.
 */
@Composable
fun RenderSpikeScreen(onBack: () -> Unit) {
    val colors = LocalOgeColors.current
    var tasks by remember { mutableStateOf<List<TaskBrief>>(emptyList()) }
    var theoryTitle by remember { mutableStateOf("") }
    var theory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        tasks = Graph.bank.tasksWithFormula(20)
        val loaded = Graph.bank.theory("math.geom.1")
        theoryTitle = loaded?.first ?: ""
        theory = loaded?.second
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "G1: рендер формул (${tasks.size})", onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            val currentTheory = theory
            if (currentTheory != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                        Text(
                            text = "Конспект: $theoryTitle",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted,
                        )
                        Card { MarkdownView(currentTheory) }
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    Text(
                        text = "#${task.id} · ${task.subjectCode} · ${task.type}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted.copy(alpha = 0.6f),
                    )
                    Card { MarkdownView(task.prompt) }
                }
            }
        }
    }
}
