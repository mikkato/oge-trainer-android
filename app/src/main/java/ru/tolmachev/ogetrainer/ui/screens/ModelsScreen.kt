package ru.tolmachev.ogetrainer.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.models.DeviceProfile
import ru.tolmachev.ogetrainer.data.models.ModelCatalog
import ru.tolmachev.ogetrainer.data.models.ModelDownloads
import ru.tolmachev.ogetrainer.data.models.ModelEntry
import ru.tolmachev.ogetrainer.data.llm.LlmResources
import ru.tolmachev.ogetrainer.data.llm.LocalLlm
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.SectionLabel
import ru.tolmachev.ogetrainer.ui.components.ThinProgress
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space
import java.util.Locale

/**
 * Локальные модели (GGUF) для llama.cpp: список подобран по характеристикам устройства,
 * загрузка с Hugging Face с докачкой после обрыва сети, выбор активной модели.
 */
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val colors = LocalOgeColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var device by remember { mutableStateOf<DeviceProfile?>(null) }
    var installed by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var engineTick by remember { mutableStateOf(0) }
    var engineNote by remember { mutableStateOf<String?>(null) }
    val download by ModelDownloads.state.collectAsState()
    val activeModel by Graph.settings.localModel.collectAsState()

    val checkModel by Graph.settings.checkModel.collectAsState()

    fun refresh() {
        installed = ModelCatalog.entries.associate { it.id to ModelDownloads.installedBytes(context, it) }
    }

    LaunchedEffect(Unit) {
        device = DeviceProfile.detect(context)
        ModelDownloads.init(context)
        refresh()
    }
    LaunchedEffect(download.status, download.entryId) {
        if (download.status == ModelDownloads.Status.Done || download.status == ModelDownloads.Status.Idle) {
            refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Локальные модели", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            device?.let { profile ->
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                        Text("Устройство", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                        Text(
                            "%.1f ГБ ОЗУ · %.1f ГБ свободно · %d ядер · %s".format(
                                Locale("ru"),
                                profile.ramGb,
                                profile.freeStorageGb,
                                profile.cpuCores,
                                profile.abi,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                        )
                        Text(profile.androidVersion, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    }
                }
            }

            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                    Text("Проверка письменных", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Text(
                        ModelCatalog.byId(checkModel ?: "")?.title
                            ?: "Не выбрана — проверяет модель генерации",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    Text(
                        "Проверка сложнее генерации: здесь нужна модель крупнее (3B и выше). " +
                            "Она подгружается перед проверкой сама — держать две модели в памяти одновременно нельзя.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }

            ModelCatalog.entries.forEach { entry ->
                ModelCard(
                    entry = entry,
                    device = device,
                    installedBytes = installed[entry.id] ?: 0L,
                    isActive = activeModel == entry.id,
                    isCheck = checkModel == entry.id,
                    onSetCheck = { Graph.settings.setCheckModel(if (checkModel == entry.id) null else entry.id) },
                    download = download,
                    onDownload = {
                        ModelDownloads.start(context, entry)
                        scope.launch { }
                    },
                    onPause = { ModelDownloads.pause() },
                    onResume = { ModelDownloads.resume(context) },
                    onCancel = { ModelDownloads.cancel(context, entry) },
                    onDelete = {
                        scope.launch {
                            ModelDownloads.delete(context, entry)
                            if (activeModel == entry.id) Graph.settings.setLocalModel(null)
                            refresh()
                        }
                    },
                    onSelect = { Graph.settings.setLocalModel(entry.id) },
                    engineLoaded = LocalLlm.isLoaded && LocalLlm.currentModelId == entry.id,
                    engineAvailable = LocalLlm.isAvailable,
                    engineTick = engineTick,
                    onLoad = {
                        val result = LocalLlm.ensureLoaded(context, entry)
                        engineNote = result.message
                        engineTick++
                    },
                    onUnload = {
                        LocalLlm.unload()
                        engineNote = "Модель выгружена из памяти"
                        engineTick++
                    },
                )
            }

            Text(
                "Движок llama.cpp встроен в приложение (libogellm.so). Модель грузится через mmap: " +
                    "страницы файла остаются вытесняемыми, а перед запуском считается бюджет памяти — " +
                    "модель + KV-кеш + запас 400 МБ. Во время генерации память проверяется каждые 16 токенов.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
            )
            engineNote?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Box(Modifier.padding(bottom = Space.x6))
        }
    }
}

@Composable
private fun ModelCard(
    entry: ModelEntry,
    device: DeviceProfile?,
    installedBytes: Long,
    isActive: Boolean,
    isCheck: Boolean,
    onSetCheck: () -> Unit,
    download: ModelDownloads.State,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    engineLoaded: Boolean,
    engineAvailable: Boolean,
    engineTick: Int,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val context = LocalContext.current
    val isInstalled = installedBytes >= entry.sizeBytes * 0.98
    val plan = remember(entry.id, installedBytes, engineTick, device) {
        if (isInstalled && device != null) {
            LlmResources.plan(context, ModelDownloads.fileOf(context, entry), device)
        } else {
            null
        }
    }
    val issue = device?.let { ModelCatalog.issue(entry, it) }
    val isThis = download.entryId == entry.id
    val busy = isThis && download.status in setOf(
        ModelDownloads.Status.Downloading,
        ModelDownloads.Status.WaitingNetwork,
    )

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(entry.title)
                            if (isActive) append(" · генерация")
                            if (isCheck) append(" · проверка")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isActive) OgeColors.Accent else colors.text,
                    )
                    Text(
                        "${entry.parameters} · ${entry.quantization} · ${gb(entry.sizeBytes)} · от ${entry.minRamGb} ГБ ОЗУ",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                    )
                }
            }
            Text(entry.purpose, style = MaterialTheme.typography.bodyMedium, color = colors.muted)

            if (plan != null) {
                Text(
                    "В памяти: модель ${gb(plan.modelBytes)} + контекст ${plan.contextSize} (${gb(plan.kvBytes)}) " +
                        "+ запас ${gb(plan.reserveBytes)} = ${gb(plan.totalBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (plan.fits) colors.muted else OgeColors.Danger,
                )
                if (!plan.fits) {
                    Text(plan.reason ?: "", style = MaterialTheme.typography.labelMedium, color = OgeColors.Danger)
                }
            }

            if (issue != null && !isInstalled) {
                Text(issue, style = MaterialTheme.typography.labelMedium, color = OgeColors.Danger)
            }

            if (isThis && download.status in setOf(
                    ModelDownloads.Status.Downloading,
                    ModelDownloads.Status.Paused,
                    ModelDownloads.Status.WaitingNetwork,
                ) && download.total > 0
            ) {
                ThinProgress(progress = download.percent / 100f)
                Text(
                    "${gb(download.downloaded)} из ${gb(download.total)} · ${download.percent} %" +
                        if (download.status == ModelDownloads.Status.Downloading) {
                            " · ${speed(download.bytesPerSecond)}"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                )
            }
            if (isThis && download.status == ModelDownloads.Status.WaitingNetwork) {
                Text("Ждём сеть · докачаем автоматически", style = MaterialTheme.typography.labelMedium, color = OgeColors.Accent)
            }
            if (isThis && !download.message.isNullOrBlank() && download.status != ModelDownloads.Status.Downloading) {
                Text(download.message, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            }

            if (isInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    if (!engineAvailable) {
                        Text(
                            "Движок не собран в этой сборке",
                            style = MaterialTheme.typography.labelMedium,
                            color = OgeColors.Danger,
                        )
                    } else if (engineLoaded) {
                        GhostButton(text = "Выгрузить из памяти", modifier = Modifier.weight(1f)) { onUnload() }
                    } else {
                        GhostButton(
                            text = "Загрузить в память",
                            enabled = plan?.fits == true,
                            modifier = Modifier.weight(1f),
                        ) { onLoad() }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                when {
                    isInstalled -> {
                        if (isActive) {
                            GhostButton(text = "Для генерации") { }
                        } else {
                            PrimaryButton(text = "Выбрать", modifier = Modifier.weight(1f)) { onSelect() }
                        }
                        GhostButton(text = if (isCheck) "Проверка ✓" else "Для проверки") { onSetCheck() }
                        GhostButton(text = "Удалить") { onDelete() }
                    }
                    busy -> {
                        GhostButton(text = "Пауза", modifier = Modifier.weight(1f)) { onPause() }
                        GhostButton(text = "Отменить") { onCancel() }
                    }
                    isThis && download.status == ModelDownloads.Status.Paused -> {
                        PrimaryButton(text = "Продолжить", modifier = Modifier.weight(1f)) { onResume() }
                        GhostButton(text = "Отменить") { onCancel() }
                    }
                    else -> {
                        PrimaryButton(
                            text = if (installedBytes > 0) "Докачать" else "Скачать",
                            enabled = issue == null || installedBytes > 0,
                            modifier = Modifier.weight(1f),
                        ) { onDownload() }
                    }
                }
            }
        }
    }
}

private fun gb(bytes: Long): String =
    "%.2f ГБ".format(Locale("ru"), bytes / 1024.0 / 1024.0 / 1024.0)

private fun speed(bps: Long): String = when {
    bps <= 0 -> "…"
    bps < 1024 * 1024 -> "%d КБ/с".format(bps / 1024)
    else -> "%.1f МБ/с".format(Locale("ru"), bps / 1024.0 / 1024.0)
}
