package ru.tolmachev.ogetrainer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tolmachev.ogetrainer.Graph
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

/** Карточка Material 3 с токенами приложения (плоская, без тональной заливки). */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalOgeColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.padding(horizontal = Space.x3, vertical = Space.x2)) { content() }
    }
}

/** Основное действие — заполненная кнопка M3. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Space.touchTarget),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Второстепенное действие — контурная кнопка M3. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Space.touchTarget),
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Индикатор прогресса M3. */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

/**
 * Верхняя панель Material 3: навигационная иконка, заголовок, действия.
 * Меню также открывается жестом «назад» с правого края.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = LocalOgeColors.current
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            when {
                onMenu != null -> IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = colors.muted)
                }
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = colors.muted,
                    )
                }
            }
        },
        actions = { trailing?.invoke(this) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            titleContentColor = colors.text,
            navigationIconContentColor = colors.muted,
            actionIconContentColor = colors.muted,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/** Пиктограмма меню M3 — если нужна отдельно от TopAppBar. */
@Composable
fun MenuGlyph(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = LocalOgeColors.current.muted)
    }
}

/**
 * Аватар профиля: фото из filesDir, если задано, иначе первая буква имени.
 * Файл читается в IO и перерисовывается только при смене пути.
 */
@Composable
fun ProfileAvatar(
    photoPath: String?,
    name: String,
    size: Int = 34,
    onClick: (() -> Unit)? = null,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photoPath, size) {
        value = if (photoPath.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            Graph.photos.load(photoPath, targetPx = size * 3)
        }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Совместимость: аватар без фото. */
@Composable
fun Avatar(name: String, size: Int = 34) = ProfileAvatar(photoPath = null, name = name, size = size)

/** Строка списка Material 3 с необязательным выделением выбора. */
@Composable
fun DenseRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalOgeColors.current
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = trailing?.let {
            { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.muted) }
        },
        leadingContent = leading,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.accentSoft else Color.Transparent,
            headlineColor = colors.text,
            supportingColor = colors.muted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    )
}

/** Пункт меню в стиле навигационного ящика M3. */
@Composable
fun MenuRow(
    title: String,
    selected: Boolean = false,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalOgeColors.current
    NavigationDrawerItem(
        label = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        selected = selected,
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .height(Space.listRow + 8.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colors.accentSoft,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = colors.text,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = colors.muted,
        ),
        shape = MaterialTheme.shapes.small,
        badge = trailing?.let {
            { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.muted) }
        },
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalOgeColors.current.muted,
        modifier = Modifier.padding(start = Space.x2, top = Space.x2, bottom = Space.x0_5),
    )
}

@Composable
fun Hairline() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x0_5),
        thickness = 1.dp,
        color = LocalOgeColors.current.border,
    )
}

/** Выбор одного варианта — SingleChoiceSegmentedButtonRow M3. */
@Composable
fun ChoiceRow(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOgeColors.current
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accentSoft,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.muted,
                    inactiveBorderColor = colors.border,
                ),
                label = {
                    Text(option, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                },
            )
        }
    }
}
