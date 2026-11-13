package ru.tolmachev.ogetrainer.ui.theme

import android.app.Activity
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** Три режима оформления. «Системная» — по настройке Android. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Палитра Material 3: все роли colorScheme заданы явно по тональной системе
 * (primary — 40/80, контейнеры — 90/30, surface-контейнеры — 100…90/10…20).
 * Брендовый оттенок #FF6B5C сохранён как источник тона, но не подменяет роли M3.
 */
private val BrandLight = lightColorScheme(
    primary = Color(0xFFA8382A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF410E05),
    inversePrimary = Color(0xFFFFB4A8),
    secondary = Color(0xFF775650),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD3),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF6B5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED9),
    onSurfaceVariant = Color(0xFF534340),
    surfaceTint = Color(0xFFA8382A),
    inverseSurface = Color(0xFF382E2C),
    inverseOnSurface = Color(0xFFFFEDEA),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceDim = Color(0xFFE8D6D3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF6E4E1),
    surfaceContainerHighest = Color(0xFFF0DEDB),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF5F150C),
    primaryContainer = Color(0xFF7F2A1E),
    onPrimaryContainer = Color(0xFFFFDAD3),
    inversePrimary = Color(0xFFA8382A),
    secondary = Color(0xFFE7BDB6),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3A),
    onSecondaryContainer = Color(0xFFFFDAD3),
    tertiary = Color(0xFFD7C58C),
    onTertiary = Color(0xFF3A3004),
    tertiaryContainer = Color(0xFF52461A),
    onTertiaryContainer = Color(0xFFF4E2A7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDC),
    surfaceVariant = Color(0xFF534340),
    onSurfaceVariant = Color(0xFFD8C2BE),
    surfaceTint = Color(0xFFFFB4A8),
    inverseSurface = Color(0xFFF1DFDC),
    inverseOnSurface = Color(0xFF382E2C),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534340),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413735),
    surfaceDim = Color(0xFF1A1110),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322826),
    surfaceContainerHighest = Color(0xFF3D3230),
)

/**
 * Семантические цвета результата. В Material 3 они выражаются контейнерами:
 * «верно» — secondary-container, «частично» — tertiary-container, «неверно» — error-container.
 * Оставлены отдельным локальом, потому что несут смысл, а не декор.
 */
@Immutable
data class OgeExtendedColors(
    val background: Color,
    val surfaceElevated: Color,
    val input: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accentSoft: Color,
    val okBg: Color,
    val okBorder: Color,
    val okText: Color,
    val badBg: Color,
    val badBorder: Color,
    val badText: Color,
    val partialBg: Color,
    val partialBorder: Color,
    val partialText: Color,
) {
    companion object {
        fun of(scheme: ColorScheme) = OgeExtendedColors(
            background = scheme.surface,
            surfaceElevated = scheme.surfaceContainerLow,
            input = scheme.surfaceContainerHighest,
            border = scheme.outlineVariant,
            text = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            accentSoft = scheme.primaryContainer,
            okBg = scheme.secondaryContainer,
            okBorder = scheme.secondary,
            okText = scheme.onSecondaryContainer,
            badBg = scheme.errorContainer,
            badBorder = scheme.error,
            badText = scheme.onErrorContainer,
            partialBg = scheme.tertiaryContainer,
            partialBorder = scheme.tertiary,
            partialText = scheme.onTertiaryContainer,
        )
    }
}

val LocalOgeColors = staticCompositionLocalOf { OgeExtendedColors.of(darkColorScheme()) }

/**
 * Совместимость с прежними именами цветов: значения берутся из ролей Material 3,
 * поэтому экраны автоматически следуют теме. Новый код должен использовать
 * MaterialTheme.colorScheme напрямую.
 */
object OgeColors {
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentHover: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val AccentDisabled: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val OkText: Color @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer
    val BadText: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
    val PartialText: Color @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer
}

/**
 * Отступы Material 3. Базовый шаг 4 dp; имена совпадают с прежними, чтобы экраны
 * не переписывать, но значения приведены к шкале M3 (4/8/12/16/24).
 */
object Space {
    val x0_5 = 4.dp
    val x1 = 4.dp
    val x1_5 = 8.dp
    val x2 = 8.dp
    val x3 = 16.dp
    val x4 = 16.dp
    val x5 = 24.dp
    val x6 = 24.dp

    val cardRadius = 12.dp
    val fieldRadius = 4.dp
    val touchTarget = 40.dp
    val listRow = 48.dp
    val drawerWidth = 320.dp
}

/** Скругления Material 3 по умолчанию (extraSmall…extraLarge). */
private val OgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun OgeTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) BrandDark else BrandLight
    val ext = OgeExtendedColors.of(scheme)
    val view = LocalView.current

    // Значки системных полос идут за темой приложения, а не за системной.
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = activity.window.insetsController ?: return@SideEffect
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(if (dark) 0 else mask, mask)
    }

    CompositionLocalProvider(LocalOgeColors provides ext) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            shapes = OgeShapes,
            content = content,
        )
    }
}
