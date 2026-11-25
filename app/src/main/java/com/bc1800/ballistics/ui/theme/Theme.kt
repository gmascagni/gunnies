package com.bc1800.ballistics.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bc1800.ballistics.model.AppThemeMode
import com.bc1800.ballistics.model.UiTextScale

// Executive Precision Optics Palette — Matte Anodized Graphite + Burnished Copper + Tactical Emerald
private val FieldDarkScheme = darkColorScheme(
    primary = Color(0xFFE59A3C),           // Burnished Copper / Brass Gold
    onPrimary = Color(0xFF1A1003),
    primaryContainer = Color(0xFF3A250B),
    onPrimaryContainer = Color(0xFFFFDFB8),
    secondary = Color(0xFF6EC484),         // Tactical Optic Emerald
    onSecondary = Color(0xFF071D0E),
    secondaryContainer = Color(0xFF172D1E),
    onSecondaryContainer = Color(0xFFC8F2D2),
    tertiary = Color(0xFF69B8E6),          // Atmospheric Sky Ice-Blue
    onTertiary = Color(0xFF051B28),
    tertiaryContainer = Color(0xFF132A38),
    onTertiaryContainer = Color(0xFFCDEAFF),
    background = Color(0xFF080B09),        // Obsidian Matte Graphite
    onBackground = Color(0xFFF3F5F2),
    surface = Color(0xFF111613),           // Machined Slate Panel
    onSurface = Color(0xFFF3F5F2),
    surfaceVariant = Color(0xFF1A211D),    // Elevated Instrument Bezel
    onSurfaceVariant = Color(0xFFA7B3AB),
    outline = Color(0xFF313D35),
    outlineVariant = Color(0xFF212A24),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000)
)

private val NightRedScheme = darkColorScheme(
    primary = Color(0xFFFF4E4E),           // True OLED Night-Vision Preserving Red
    onPrimary = Color(0xFF1A0000),
    primaryContainer = Color(0xFF3D0808),
    onPrimaryContainer = Color(0xFFFFB5B5),
    secondary = Color(0xFFD93F3F),
    onSecondary = Color(0xFF1A0000),
    secondaryContainer = Color(0xFF280606),
    onSecondaryContainer = Color(0xFFFF9E9E),
    tertiary = Color(0xFFC23838),
    onTertiary = Color(0xFF1A0000),
    tertiaryContainer = Color(0xFF220505),
    onTertiaryContainer = Color(0xFFFF9A9A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFF6B6B),
    surface = Color(0xFF0B0202),
    onSurface = Color(0xFFFF6B6B),
    surfaceVariant = Color(0xFF170505),
    onSurfaceVariant = Color(0xFFC74F4F),
    outline = Color(0xFF4D1515),
    outlineVariant = Color(0xFF2B0B0B),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF240000)
)

private val DaylightScheme = lightColorScheme(
    primary = Color(0xFFB35C07),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE5CA),
    onPrimaryContainer = Color(0xFF381A00),
    secondary = Color(0xFF1F6638),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0F2DA),
    onSecondaryContainer = Color(0xFF082914),
    tertiary = Color(0xFF155E84),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1ECFA),
    onTertiaryContainer = Color(0xFF062536),
    background = Color(0xFFF2F2ED),
    onBackground = Color(0xFF121714),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121714),
    surfaceVariant = Color(0xFFE5E8E2),
    onSurfaceVariant = Color(0xFF424D45),
    outline = Color(0xFF9AA59D),
    outlineVariant = Color(0xFFD4DCD6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

// Crisp machined instrument corners across every screen
private val InstrumentShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

// Tabular numerals ("tnum") across ALL numeric text styles so digits never jitter
private val BallisticsTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontFeatureSettings = "tnum"
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontFeatureSettings = "tnum"
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun BC1800Theme(
    themeMode: AppThemeMode,
    textScale: UiTextScale,
    content: @Composable () -> Unit
) {
    val scheme = when (themeMode) {
        AppThemeMode.FIELD_DARK -> FieldDarkScheme
        AppThemeMode.NIGHT_RED -> NightRedScheme
        AppThemeMode.DAYLIGHT -> DaylightScheme
    }

    val currentDensity = LocalDensity.current
    val adjustedFontScale = (currentDensity.fontScale * textScale.factor).coerceIn(0.85f, 1.50f)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = adjustedFontScale
        )
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BallisticsTypography,
            shapes = InstrumentShapes,
            content = content
        )
    }
}
