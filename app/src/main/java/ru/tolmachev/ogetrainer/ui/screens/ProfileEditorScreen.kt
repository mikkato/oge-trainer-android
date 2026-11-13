package ru.tolmachev.ogetrainer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.data.auth.ProfilesRepository
import ru.tolmachev.ogetrainer.data.db.Profile
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.GhostButton
import ru.tolmachev.ogetrainer.ui.components.Hairline
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.components.ProfileAvatar
import ru.tolmachev.ogetrainer.ui.components.SectionLabel
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

private val ROLE_OPTIONS = listOf("Ученик", "Родитель", "Администратор")
private val ROLE_VALUES = mapOf("Ученик" to "student", "Родитель" to "parent", "Администратор" to "admin")
private val ROLE_LABELS = ROLE_VALUES.entries.associate { (k, v) -> v to k }
private val PROGRAM_OPTIONS = listOf("ОГЭ", "Школа")
private val PROGRAM_VALUES = mapOf("ОГЭ" to "oge", "Школа" to "school")

/**
 * Карточка профиля: фото, ФИО, возраст, класс и программа — на первой вкладке,
 * логин, роль и пароль — на второй. Один экран и для создания, и для правки.
 */
@Composable
fun ProfileEditorScreen(
    profile: Profile?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val editing = profile != null

    var tab by remember { mutableStateOf(0) }
    var fullName by remember { mutableStateOf(profile?.fullName ?: "") }
    var age by remember { mutableStateOf(if ((profile?.age ?: 0) > 0) profile!!.age.toString() else "") }
    var grade by remember { mutableStateOf(0) }
    var program by remember { mutableStateOf("oge") }
    var subjects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedSubjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var photoPath by remember { mutableStateOf(profile?.photoPath) }
    var username by remember { mutableStateOf(profile?.username ?: "") }
    var roleLabel by remember { mutableStateOf(ROLE_LABELS[profile?.role ?: "student"] ?: "Ученик") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Класс и предметы нужны только ученику: у администратора и родителя карточка — ФИО, возраст, фото.
    val showProgram = roleLabel == "Ученик"

    LaunchedEffect(Unit) {
        subjects = Graph.bank.subjects()
        if (editing) {
            val programInfo = Graph.profiles.programOf(profile!!.id)
            if (programInfo != null) {
                grade = programInfo.first
                program = programInfo.second
                selectedSubjects = programInfo.third.toSet()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = Graph.photos.save(uri)
                if (saved != null) {
                    Graph.photos.delete(photoPath)
                    photoPath = saved
                } else {
                    error = "Не удалось сохранить фото"
                }
            }
        }
    }

    fun validate(): String? = when {
        fullName.trim().length < 2 -> "Укажите ФИО"
        age.isNotBlank() && (age.toIntOrNull() ?: -1) !in 0..99 -> "Возраст от 0 до 99"
        showProgram && grade !in 1..11 -> "Выберите класс"
        showProgram && selectedSubjects.isEmpty() -> "Добавьте хотя бы один предмет"
        !editing && username.trim().length < 3 -> "Логин короче 3 символов"
        !editing && password.length < 6 -> "Пароль короче 6 символов"
        !editing && password != repeat -> "Пароли не совпадают"
        editing && password.isNotBlank() && password.length < 6 -> "Пароль короче 6 символов"
        editing && password.isNotBlank() && password != repeat -> "Пароли не совпадают"
        else -> null
    }

    fun save() {
        error = validate()
        if (error != null) return
        busy = true
        scope.launch {
            val ageValue = age.toIntOrNull() ?: 0
            val result = if (!editing) {
                Graph.profiles.create(
                    username = username,
                    fullName = fullName,
                    role = ROLE_VALUES[roleLabel] ?: "student",
                    password = password,
                    age = ageValue,
                    photoPath = photoPath,
                    grade = if (showProgram) grade else null,
                    programType = if (showProgram) program else null,
                    subjects = if (showProgram) selectedSubjects.toList() else emptyList(),
                ).map { }
            } else {
                Graph.profiles.updateCard(profile!!.id, fullName, ageValue, photoPath)
                    .onSuccess {
                        if (showProgram && grade in 1..11) {
                            Graph.profiles.setProgram(profile.id, grade, program, selectedSubjects.toList())
                        }
                    }
                    .onSuccess {
                        if (password.isNotBlank()) Graph.profiles.setPassword(profile.id, password)
                    }
                    .map { }
            }
            busy = false
            result.fold(
                onSuccess = { onDone() },
                onFailure = { error = it.message ?: "Не удалось сохранить профиль" },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (editing) "Карточка профиля" else "Новый профиль",
            onBack = onCancel,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            ChoiceRow(
                options = listOf("Карточка", "Доступ"),
                selected = if (tab == 0) "Карточка" else "Доступ",
                onSelect = { tab = if (it == "Карточка") 0 else 1 },
            )

            if (tab == 0) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.x3),
                        ) {
                            ProfileAvatar(photoPath = photoPath, name = fullName.ifBlank { "?" }, size = 72)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (photoPath == null) "Фото не выбрано" else "Фото добавлено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                    Text(
                                        text = "Выбрать фото",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = OgeColors.Accent,
                                        modifier = Modifier
                                            .clickable {
                                                picker.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                    ),
                                                )
                                            }
                                            .padding(vertical = Space.x1),
                                    )
                                    if (photoPath != null) {
                                        Text(
                                            text = "Убрать",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.muted,
                                            modifier = Modifier
                                                .clickable {
                                                    Graph.photos.delete(photoPath)
                                                    photoPath = null
                                                }
                                                .padding(vertical = Space.x1),
                                        )
                                    }
                                }
                            }
                        }
                        Field("ФИО", fullName) { fullName = it }
                        Field("Возраст", age, numeric = true) { input ->
                            age = input.filter { it.isDigit() }.take(2)
                        }
                    }
                }

                if (showProgram) {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel("КЛАСС")
                                Text(
                                    text = if (grade in 1..11) "$grade класс" else "не выбран",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (grade in 1..11) OgeColors.Accent else colors.muted,
                                    modifier = Modifier.padding(start = Space.x2, top = Space.x2, bottom = Space.x0_5),
                                )
                            }
                            Slider(
                                value = if (grade in 1..11) (grade - 1).toFloat() else 4f,
                                onValueChange = { value ->
                                    grade = (value.toInt() + 1).coerceIn(1, 11)
                                    program = if (grade == 9) "oge" else "school"
                                },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = OgeColors.Accent,
                                    activeTrackColor = OgeColors.Accent,
                                    inactiveTrackColor = colors.border,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.x1),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                Text("1", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                                Box(Modifier.weight(1f))
                                Text("11", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                            }
                            Text(
                                text = if (program == "oge") "Программа: ОГЭ" else "Программа: школьная",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                        }
                    }
                }

                if (showProgram && grade in 1..11) {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                            SectionLabel("ПРОГРАММА ${grade} КЛАССА · ФГОС")
                            val curriculum = ru.tolmachev.ogetrainer.data.school.SchoolCurriculum
                                .subjectsFor(context, grade)
                            curriculum.forEach { item ->
                                val bankCode = item.bankCode
                                val inProgram = bankCode != null && bankCode in selectedSubjects
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (bankCode != null) {
                                                Modifier.clickable {
                                                    selectedSubjects = if (inProgram) {
                                                        selectedSubjects - bankCode
                                                    } else {
                                                        selectedSubjects + bankCode
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .padding(vertical = Space.x0_5),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (inProgram) "✓" else if (bankCode != null) "·" else "—",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            inProgram -> OgeColors.Accent
                                            bankCode != null -> colors.muted
                                            else -> colors.muted.copy(alpha = 0.5f)
                                        },
                                        modifier = Modifier.width(20.dp),
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (bankCode != null) colors.text else colors.muted.copy(alpha = 0.6f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (item.isOge) {
                                        Text(
                                            "ОГЭ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (bankCode == null) {
                                        Text(
                                            "нет банка",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.muted.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(start = Space.x1),
                                        )
                                    }
                                }
                            }
                            Text(
                                "ОГЭ: русский язык и математика обязательны, остальные предметы — по выбору. " +
                                    "Предметы без банка заданий показаны справочно: их добавление станет доступно " +
                                    "после загрузки контента.",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                        }
                    }
                }

                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x0_5)) {
                        SectionLabel(
                            if (selectedSubjects.isEmpty()) {
                                "ПРЕДМЕТЫ · список пуст"
                            } else {
                                "ПРЕДМЕТЫ · выбрано ${selectedSubjects.size}"
                            },
                        )
                        if (subjects.isEmpty()) {
                            Text(
                                "Банк ещё не импортирован",
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        // Выбранные — с удалением, без повторов: множество.
                        subjects.filter { it.first in selectedSubjects }.forEach { (code, title) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OgeColors.Accent,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { selectedSubjects = selectedSubjects - code },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Убрать $title",
                                        tint = colors.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        val available = subjects.filterNot { it.first in selectedSubjects }
                        if (available.isNotEmpty()) {
                            Text(
                                "ДОБАВИТЬ",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                                modifier = Modifier.padding(top = Space.x1),
                            )
                            available.forEach { (code, title) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSubjects = selectedSubjects + code }
                                        .padding(vertical = Space.x0_5),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = colors.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.muted,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = Space.x1),
                                    )
                                }
                            }
                        }
                    }
                }

            } else {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        if (editing) {
                            Text(
                                "Логин: ${profile!!.username}" +
                                    if (profile.builtin) " · встроенный" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        } else {
                            Field("Логин", username) { username = it }
                        }
                        SectionLabel("РОЛЬ")
                        ChoiceRow(
                            options = ROLE_OPTIONS,
                            selected = roleLabel,
                            onSelect = {
                                roleLabel = it
                            },
                        )
                        if (editing && profile!!.builtin) {
                            Text(
                                "Пароль встроенного администратора меняется только полным сбросом профилей.",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted,
                            )
                        } else {
                            Field(if (editing) "Новый пароль (необязательно)" else "Пароль", password, password = true) {
                                password = it
                            }
                            Field("Повторите пароль", repeat, password = true) { repeat = it }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = OgeColors.Danger, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                PrimaryButton(
                    text = if (busy) "Сохраняем…" else "Сохранить",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { save() }
                GhostButton(text = "Отмена", enabled = !busy) { onCancel() }
            }

            if (editing && !profile!!.builtin) {
                Hairline()
                GhostButton(text = "Удалить профиль", enabled = !busy) { confirmDelete = true }
            }
            Box(Modifier.padding(bottom = Space.x6))
        }
    }

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Профиль «${profile.fullName}» будет удалён вместе с попытками и освоением. " +
                        "Отменить это нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val result = Graph.profiles.delete(profile.id)
                        result.fold(
                            onSuccess = { Graph.photos.delete(profile.photoPath); onDone() },
                            onFailure = { error = it.message },
                        )
                    }
                }) { Text("Удалить", color = OgeColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = colors.muted) }
            },
        )
    }
}

@Suppress("unused")
private val unusedPadding = 0.dp
