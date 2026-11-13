package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.ui.components.Card
import ru.tolmachev.ogetrainer.ui.components.PrimaryButton
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val colors = LocalOgeColors.current
    var username by remember { mutableStateOf("admin") }
    var fullName by remember { mutableStateOf("Михаил") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Text("Первый запуск", style = MaterialTheme.typography.headlineSmall, color = colors.muted)
        Text(
            "Создайте администратора устройства: он заводит профили учеников и настраивает провайдера.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                Field("Логин", username) { username = it }
                Field("Имя", fullName) { fullName = it }
                Field("Пароль (мин. 6 символов)", password, password = true) { password = it }
                Field("Повторите пароль", repeat, password = true) { repeat = it }
            }
        }
        if (error != null) {
            Text(error!!, color = OgeColors.Danger, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (busy) "Создаём…" else "Создать администратора",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                error = if (password != repeat) "Пароли не совпадают" else null
                if (error != null) return@PrimaryButton
                busy = true
                scope.launch {
                    val result = Graph.profiles.create(username, fullName, "admin", password)
                    busy = false
                    result.fold(
                        onSuccess = { onDone() },
                        onFailure = { error = it.message ?: "Не удалось создать профиль" },
                    )
                }
            }
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    password: Boolean = false,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalOgeColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                numeric -> KeyboardType.Number
                password -> KeyboardType.Password
                else -> KeyboardType.Text
            },
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OgeColors.Accent,
            unfocusedBorderColor = colors.border,
            focusedLabelColor = OgeColors.Accent,
            unfocusedLabelColor = colors.muted,
            focusedTextColor = colors.muted.copy(alpha = 0.98f),
            unfocusedTextColor = colors.muted.copy(alpha = 0.98f),
            cursorColor = OgeColors.Accent,
        ),
    )
}
