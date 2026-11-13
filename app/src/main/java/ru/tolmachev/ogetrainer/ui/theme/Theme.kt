package ru.tolmachev.ogetrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Токены перенесены из web-версии (index.css) без изменений. */
object OgeColors {
    val Bg = Color(0xFF0F1115)
    val Surface = Color(0xFF1C2230)
    val Input = Color(0xFF262D3B)
    val Border = Color(0xFF384052)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFFAEB8C6)
    val Accent = Color(0xFFFF6B5C)
    val AccentHover = Color(0xFFE85A4C)
    val AccentSoft = Color(0x24FF6B5C)
    val AccentDisabled = Color(0xFF6B3A35)
    val Danger = Color(0xFFF87171)

    val OkBg = Color(0xFF0E2319)
    val OkBorder = Color(0xFF1F4D34)
    val OkText = Color(0xFF6EE7A0)

    val BadBg = Color(0xFF291417)
    val BadBorder = Color(0xFF5B1F24)
    val BadText = Color(0xFFFCA5A5)

    val PartialBg = Color(0xFF241D0E)
    val PartialBorder = Color(0xFF5B4A1F)
    val PartialText = Color(0xFFF5C76B)
}

@Immutable
data class OgeExtendedColors(
    val surfaceElevated: Color = OgeColors.Surface,
    val input: Color = OgeColors.Input,
    val border: Color = OgeColors.Border,
    val muted: Color = OgeColors.Muted,
    val accentSoft: Color = OgeColors.AccentSoft,
    val okBg: Color = OgeColors.OkBg,
    val okBorder: Color = OgeColors.OkBorder,
    val okText: Color = OgeColors.OkText,
    val badBg: Color = OgeColors.BadBg,
    val badBorder: Color = OgeColors.BadBorder,
    val badText: Color = OgeColors.BadText,
    val partialBg: Color = OgeColors.PartialBg,
    val partialBorder: Color = OgeColors.PartialBorder,
    val partialText: Color = OgeColors.PartialText,
)

val LocalOgeColors = staticCompositionLocalOf { OgeExtendedColors() }

/** Плотная сетка: базовый шаг 4 dp, значения — кратности. */
object Space {
    val x0_5 = 2.dp
    val x1 = 4.dp
    val x1_5 = 6.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp

    val cardRadius = 14.dp
    val fieldRadius = 8.dp
    val touchTarget = 48.dp
    val listRow = 38.dp
}

private val OgeTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

private val OgeDarkScheme = darkColorScheme(
    primary = OgeColors.Accent,
    onPrimary = Color.White,
    primaryContainer = OgeColors.AccentSoft,
    onPrimaryContainer = OgeColors.Accent,
    secondary = OgeColors.OkText,
    onSecondary = OgeColors.OkBg,
    secondaryContainer = OgeColors.OkBg,
    onSecondaryContainer = OgeColors.OkText,
    tertiary = OgeColors.PartialText,
    tertiaryContainer = OgeColors.PartialBg,
    onTertiaryContainer = OgeColors.PartialText,
    background = OgeColors.Bg,
    onBackground = OgeColors.Text,
    surface = OgeColors.Bg,
    onSurface = OgeColors.Text,
    surfaceVariant = OgeColors.Surface,
    onSurfaceVariant = OgeColors.Muted,
    surfaceTint = OgeColors.Accent,
    outline = OgeColors.Border,
    outlineVariant = OgeColors.Border,
    error = OgeColors.Danger,
    onError = Color.White,
    errorContainer = OgeColors.BadBg,
    onErrorContainer = OgeColors.BadText,
    inverseSurface = OgeColors.Text,
    inverseOnSurface = OgeColors.Bg,
)

@Composable
fun OgeTheme(content: @Composable () -> Unit) {
    // Тёмная тема — единственная: так было в вебе и так заказано.
    isSystemInDarkTheme()
    CompositionLocalProvider(LocalOgeColors provides OgeExtendedColors()) {
        MaterialTheme(
            colorScheme = OgeDarkScheme,
            typography = OgeTypography,
            content = content,
        )
    }
}
