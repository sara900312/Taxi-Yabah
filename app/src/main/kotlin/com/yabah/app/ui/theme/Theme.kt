package com.yabah.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yabah.app.R

object YabahColors {
    val Navy = Color(0xFF101B2C)
    val Ivory = Color(0xFFF8F7F3)
    val White = Color(0xFFFFFFFF)
    val Jade = Color(0xFF0DAA9B)
    val Gold = Color(0xFFE8B65A)
    val Slate = Color(0xFF627186)
    // Derived tones (kept close to the brand palette)
    val NavyRaised = Color(0xFF18263B)
    val NavyHigh = Color(0xFF213149)
    val JadeDeep = Color(0xFF078A7E)
    val Danger = Color(0xFFC2453D)
    val DangerDark = Color(0xFFFF8A80)
}

/** Semantic tokens not covered by Material's scheme. */
@Immutable
data class YabahExtras(
    val mapLand: Color,
    val mapGrid: Color,
    val mapOutline: Color,
    val mapPlace: Color,
    val mapLabel: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val demo: Color,
    val onDemo: Color,
)

private val LightExtras = YabahExtras(
    mapLand = Color(0xFFEDEBE4), mapGrid = Color(0x14101B2C), mapOutline = YabahColors.Jade,
    mapPlace = Color(0x66627186), mapLabel = Color(0xCC3B475A),
    warnContainer = Color(0xFFFBEFD7), onWarnContainer = Color(0xFF6B4A0E),
    dangerContainer = Color(0xFFFBE3E1), onDangerContainer = Color(0xFF7A201A),
    demo = YabahColors.Gold, onDemo = YabahColors.Navy,
)
private val DarkExtras = YabahExtras(
    mapLand = Color(0xFF142135), mapGrid = Color(0x1AF8F7F3), mapOutline = YabahColors.Jade,
    mapPlace = Color(0x80A9B4C4), mapLabel = Color(0xCCC9D1DC),
    warnContainer = Color(0xFF3A2F17), onWarnContainer = Color(0xFFF3D9A4),
    dangerContainer = Color(0xFF3D1E1E), onDangerContainer = Color(0xFFFFC9C4),
    demo = YabahColors.Gold, onDemo = YabahColors.Navy,
)

val LocalYabahExtras = staticCompositionLocalOf { LightExtras }

private val LightScheme = lightColorScheme(
    primary = YabahColors.Jade, onPrimary = YabahColors.White,
    primaryContainer = Color(0xFFD3F1ED), onPrimaryContainer = Color(0xFF00413B),
    secondary = YabahColors.Gold, onSecondary = YabahColors.Navy,
    secondaryContainer = Color(0xFFFBEFD7), onSecondaryContainer = Color(0xFF5A3D05),
    tertiary = YabahColors.Navy, onTertiary = YabahColors.Ivory,
    background = YabahColors.Ivory, onBackground = YabahColors.Navy,
    surface = YabahColors.White, onSurface = YabahColors.Navy,
    surfaceVariant = Color(0xFFF0EEE8), onSurfaceVariant = YabahColors.Slate,
    surfaceContainerLowest = YabahColors.White, surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF4F3EE), surfaceContainerHigh = Color(0xFFEFEDE7), surfaceContainerHighest = Color(0xFFE9E7E0),
    outline = Color(0xFFB9C0CB), outlineVariant = Color(0xFFE2E0D9),
    error = YabahColors.Danger, onError = YabahColors.White,
)
private val DarkScheme = darkColorScheme(
    primary = YabahColors.Jade, onPrimary = YabahColors.White,
    primaryContainer = Color(0xFF0A4A44), onPrimaryContainer = Color(0xFFB9EDE6),
    secondary = YabahColors.Gold, onSecondary = YabahColors.Navy,
    secondaryContainer = Color(0xFF3A2F17), onSecondaryContainer = Color(0xFFF3D9A4),
    tertiary = YabahColors.Ivory, onTertiary = YabahColors.Navy,
    background = YabahColors.Navy, onBackground = YabahColors.Ivory,
    surface = YabahColors.NavyRaised, onSurface = YabahColors.Ivory,
    surfaceVariant = YabahColors.NavyHigh, onSurfaceVariant = Color(0xFFA9B4C4),
    surfaceContainerLowest = Color(0xFF0C1522), surfaceContainerLow = Color(0xFF142135),
    surfaceContainer = YabahColors.NavyRaised, surfaceContainerHigh = YabahColors.NavyHigh, surfaceContainerHighest = Color(0xFF2A3B55),
    outline = Color(0xFF51617A), outlineVariant = Color(0xFF2A3B55),
    error = YabahColors.DangerDark, onError = YabahColors.Navy,
)

val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private fun s(size: Int, weight: FontWeight, line: Int) = TextStyle(fontFamily = PlexArabic, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val YabahTypography = Typography(
    displaySmall = s(32, FontWeight.Bold, 44),
    headlineMedium = s(26, FontWeight.Bold, 36),
    headlineSmall = s(22, FontWeight.SemiBold, 32),
    titleLarge = s(20, FontWeight.SemiBold, 30),
    titleMedium = s(16, FontWeight.SemiBold, 24),
    titleSmall = s(14, FontWeight.SemiBold, 20),
    bodyLarge = s(16, FontWeight.Normal, 26),
    bodyMedium = s(14, FontWeight.Normal, 22),
    bodySmall = s(12, FontWeight.Normal, 18),
    labelLarge = s(15, FontWeight.SemiBold, 22),
    labelMedium = s(13, FontWeight.Medium, 18),
    labelSmall = s(11, FontWeight.Medium, 16),
)

private val YabahShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YabahTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalYabahExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = YabahTypography,
            shapes = YabahShapes,
            content = content,
        )
    }
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum touch target. */
    val touch = 48.dp
}
