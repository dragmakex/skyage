package com.skyage.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Semantic palette. Every neutral carries a blue cast so surfaces read as dark navy rather than
 * grey, and there is exactly one accent hue (blue) — nothing in the app is violet.
 */
object Sky {
    val Background = Color(0xFF0A0F1E)
    val BackgroundElevated = Color(0xFF0C1424)
    val Surface = Color(0xFF101A30)
    val TileBorder = Color(0xFF2A3A5C)
    val Label = Color(0xFFFFFFFF)
    val SecondaryLabel = Color(0x99A9BEE0)
    val TertiaryLabel = Color(0x66A9BEE0)
    val Fill = Color(0x142E5AA8)

    /** The single accent: anything interactive, in progress, or "live". */
    val Accent = Color(0xFF3E7BFA)
    val AccentDeep = Color(0xFF1B3C86)

    val OnTime = Color(0xFF32D74B)
    val Warn = Color(0xFFFFD60A)
    val Alert = Color(0xFFFF453A)

    // Liquid glass: a dark veil over whatever is behind it, drawn by ui/Glass.kt.
    val GlassVeil = Color(0xA6070C18)
    // Night sky behind the globe and the map.
    val Night = Color(0xFF04070F)
    val AuroraTop = Color(0xFF1D4ED8)
    val AuroraBlue = Color(0xFF0B2A63)
}

/**
 * Apple's type rhythm, sized down for a phone sheet: a tight large title, 17 sp nav titles,
 * and small uppercase micro-labels. System family on purpose — the platform's own font.
 */
private val SkyageTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

private val DarkScheme = darkColorScheme(
    primary = Sky.Accent,
    onPrimary = Color.White,
    secondary = Sky.Accent,
    background = Sky.Background,
    onBackground = Sky.Label,
    surface = Sky.Surface,
    onSurface = Sky.Label,
    surfaceVariant = Sky.Surface,
    onSurfaceVariant = Sky.SecondaryLabel,
    outline = Sky.TileBorder,
    error = Sky.Alert,
)

private val LightScheme = lightColorScheme(
    primary = Sky.AccentDeep,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F7FA),
    outline = Color(0xFFE3E8F0),
)

@Composable
fun SkyageTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Dark-first: the app is a night-time map, so dark is the default regardless of system setting.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SkyageTypography,
        content = content,
    )
}
