package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.provider.ProviderConfig
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.SectionLabel
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Провайдер генерации и проверки: любой OpenAI-совместимый endpoint.
 * Ключ хранится в EncryptedSharedPreferences, в поле показывается маска.
 */
@Composable
fun ProviderScreen(onBack: () -> Unit) {
    val colors = LocalOgeColors.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("deepseek-chat") }
    var apiKey by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("0.4") }
    var maxTokens by remember { mutableStateOf("1500") }
    var masked by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = Graph.provider.load()
        baseUrl = config.baseUrl
        model = config.model
        temperature = config.temperature.toString()
        maxTokens = config.maxTokens.toString()
        masked = Graph.provider.maskedKey()
        hasKey = Graph.provider.hasKey()
    }

    fun currentConfig() = ProviderConfig(
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        temperature = temperature.toDoubleOrNull() ?: 0.4,
        maxTokens = maxTokens.toIntOrNull() ?: 1500,
    )

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Провайдер", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    Text(
                        "Любой OpenAI-совместимый endpoint: DeepSeek, OpenRouter, корпоративный шлюз, " +
                            "локальный llama.cpp или Ollama.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                    Field("Адрес endpoint", baseUrl) { baseUrl = it }
                    Text(
                        "Например https://api.deepseek.com или http://192.168.1.10:8080/v1 (http — только локальная сеть)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted.copy(alpha = 0.75f),
                    )
                    Field("Модель", model) { model = it }
                    Field(
                        if (hasKey) "Ключ (сохранён: $masked; пусто — не менять)" else "Ключ API",
                        apiKey,
                        password = true,
                    ) { apiKey = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Field("Температура", temperature, numeric = true) { temperature = it }
                        Field("Лимит токенов", maxTokens, numeric = true) { maxTokens = it }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(text = if (busy) "Проверяем…" else "Проверить подключение", enabled = !busy) {
                    busy = true
                    status = null
                    scope.launch {
                        val result = Graph.provider.probe(currentConfig(), apiKey)
                        busy = false
                        result.fold(
                            onSuccess = {
                                status = "Подключение работает: «${it.reply}», ${it.latencyMs} мс"
                                statusIsError = false
                            },
                            onFailure = {
                                status = it.message
                                statusIsError = true
                            },
                        )
                    }
                }
                PrimaryButton(text = if (busy) "Сохраняем…" else "Сохранить", enabled = !busy) {
                    busy = true
                    scope.launch {
                        val result = Graph.provider.save(currentConfig(), apiKey)
                        busy = false
                        result.fold(
                            onSuccess = {
                                masked = Graph.provider.maskedKey()
                                hasKey = Graph.provider.hasKey()
                                apiKey = ""
                                status = "Настройки сохранены"
                                statusIsError = false
                            },
                            onFailure = {
                                status = it.message
                                statusIsError = true
                            },
                        )
                    }
                }
            }
            if (hasKey) {
                GhostButton(text = "Отключить провайдера", enabled = !busy) {
                    busy = true
                    scope.launch {
                        Graph.provider.disable()
                        busy = false
                        baseUrl = ""
                        model = "deepseek-chat"
                        apiKey = ""
                        hasKey = false
                        masked = ""
                        status = "Провайдер отключён, приложение работает офлайн"
                        statusIsError = false
                    }
                }
            }

            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusIsError) OgeColors.Danger else colors.okText,
                )
            }

            SectionLabel("ЧТО ЭТО ДАЁТ")
            Text(
                "С провайдером доступны генерация новых заданий и проверка развёрнутых ответов, " +
                    "изложений и сочинений. Без него работают банк задач, теория и проверка коротких ответов.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
            Text(
                "В запрос уходят текст задания и ответ ученика — это предупреждение показывается один раз.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted.copy(alpha = 0.75f),
            )
            Column(Modifier.padding(bottom = Space.x6)) {}
        }
    }
}
