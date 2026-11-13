package ru.tolmachev.ogetrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tolmachev.ogetrainer.ui.theme.LocalOgeColors
import ru.tolmachev.ogetrainer.ui.theme.OgeColors
import ru.tolmachev.ogetrainer.ui.theme.Space

@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalOgeColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.cardRadius))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(Space.cardRadius))
            .padding(horizontal = Space.x3, vertical = Space.x2),
    ) { content() }
}

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
        shape = RoundedCornerShape(Space.fieldRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = OgeColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = OgeColors.AccentDisabled,
            disabledContentColor = Color(0xFFD8B5B1),
        ),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalOgeColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Space.touchTarget),
        shape = RoundedCornerShape(Space.fieldRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.muted,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.muted.copy(alpha = 0.4f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
}

@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    val colors = LocalOgeColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.border),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OgeColors.Accent),
        )
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = LocalOgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2, end = Space.x2, top = Space.x2, bottom = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("←", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = OgeColors.Accent)
            }
        }
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = colors.muted.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x1), content = trailing)
        }
    }
}

@Composable
fun Avatar(name: String, size: Int = 34) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(OgeColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = OgeColors.Accent,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DenseRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalOgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.fieldRadius))
            .then(if (selected) Modifier.background(colors.accentSoft) else Modifier)
            .then(if (selected) Modifier.border(1.dp, OgeColors.Accent, RoundedCornerShape(Space.fieldRadius)) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = if (selected) OgeColors.Accent else colors.muted.copy(alpha = 0.98f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.muted.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = if (selected) OgeColors.Accent else colors.muted,
            )
        }
    }
}

@Composable
fun BulletSeparator() {
    val colors = LocalOgeColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x2)
            .height(1.dp)
            .width(1.dp)
            .background(colors.border.copy(alpha = 0.6f)),
    )
}
