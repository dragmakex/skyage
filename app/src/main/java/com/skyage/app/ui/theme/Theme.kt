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
 * Palette and type scale taken from the original app's Assets.car design tokens and the
 * Noway / NowayRound font families it ships. See design/design-tokens.md.
 */
object Sky {
    val Background = Color(0xFF1C1C1E)
    val BackgroundElevated = Color(0xFF111111)
    val Surface = Color(0xFF232325)
    val TileBorder = Color(0xFF48484A)
    val Label = Color(0xFFFFFFFF)
    val SecondaryLabel = Color(0x99EBEBF5)
    val TertiaryLabel = Color(0x66EBEBF5)
    val Fill = Color(0x14747480)
    val Pro = Color(0xFFBF5AF2)
    val ProDeep = Color(0xFF741E9F)
    val OnTime = Color(0xFF32D74B)
    val Warn = Color(0xFFFFD60A)
    val Alert = Color(0xFFFF453A)
    val Sky = Color(0xFF4A6FE5)

    // Liquid glass: a dark veil over whatever is behind it, drawn by ui/Glass.kt.
    val GlassVeil = Color(0xA6070709)
    // Night sky used behind the world map.
    val Night = Color(0xFF05060A)
    val AuroraViolet = Color(0xFF7B2CBF)
    val AuroraBlue = Color(0xFF1E3A8A)
}

private val SkyageTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
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
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)

private val DarkScheme = darkColorScheme(
    primary = Sky.Pro,
    onPrimary = Color.Black,
    secondary = Sky.Sky,
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
    primary = Sky.ProDeep,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F7),
    outline = Color(0xFFE9E9E9),
)

@Composable
fun SkyageTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // The original is a dark-first app, so dark is the default regardless of system setting.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SkyageTypography,
        content = content,
    )
}
