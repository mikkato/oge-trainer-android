package ru.tolmachev.ogetrainer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space
import ru.tolmachev.ogetrainer.ui.theme.ThemeMode

/**
 * Боковое меню слева: выезжает из-за левого края по жесту «назад» с правого края.
 * Плотные строки 38 dp, группы разделены линией, выбор сразу закрывает меню.
 */
@Composable
fun DrawerMenu(
    open: Boolean,
    userName: String,
    userRole: String,
    onClose: () -> Unit,
    canGoUp: Boolean,
    onGoUp: () -> Unit,
    onSubjects: () -> Unit,
    adminVisible: Boolean,
    onAdmin: () -> Unit,
    onModels: () -> Unit,
    onBank: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalOgeColors.current
    val duration = 220

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(duration)),
            exit = fadeOut(tween(duration)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
            )
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(duration)) { -it },
            exit = slideOutHorizontally(tween(duration)) { -it },
        ) {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(Space.drawerWidth)
                    .fillMaxHeight(),
                drawerShape = MaterialTheme.shapes.large,
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = Space.x2)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x0_5),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    Avatar(userName)
                    Column(Modifier.weight(1f)) {
                        Text(
                            userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            maxLines = 1,
                        )
                        if (userRole != userName) {
                            Text(
                                userRole,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Закрыть меню",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionLabel("НАВИГАЦИЯ")
                MenuRow(
                    title = "← На уровень выше",
                    enabled = canGoUp,
                    onClick = { onClose(); onGoUp() },
                )
                MenuRow(title = "Предметы", onClick = { onClose(); onSubjects() })
                if (adminVisible) {
                    MenuRow(title = "Администрирование", onClick = { onClose(); onAdmin() })
                    MenuRow(title = "Банк контента", onClick = { onClose(); onBank() })
                    MenuRow(title = "Локальные модели", onClick = { onClose(); onModels() })
                }

                Hairline()
                SectionLabel("ОФОРМЛЕНИЕ")
                MenuRow(
                    title = "Системная",
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeMode(ThemeMode.SYSTEM); onClose() },
                )
                MenuRow(
                    title = "Светлая",
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeMode(ThemeMode.LIGHT); onClose() },
                )
                MenuRow(
                    title = "Тёмная",
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeMode(ThemeMode.DARK); onClose() },
                )

                Hairline()
                Spacer(Modifier.padding(top = Space.x1))
                MenuRow(title = "Выйти из профиля", onClick = { onClose(); onLogout() })
            }
        }
    }
            }
}
