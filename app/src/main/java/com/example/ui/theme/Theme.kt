package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val MicroRCDarkScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = CarbonBg,
    primaryContainer = Color(0xFF4E2316),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = ElectricCyan,
    onSecondary = CarbonBg,
    secondaryContainer = Color(0xFF0E3744),
    onSecondaryContainer = Color(0xFFB3F4FF),
    tertiary = AcidLime,
    onTertiary = CarbonBg,
    tertiaryContainer = Color(0xFF283E13),
    onTertiaryContainer = Color(0xFFD9FFA8),
    background = CarbonBg,
    onBackground = TextPrimary,
    surface = PaddockSurface,
    onSurface = TextPrimary,
    surfaceVariant = PaddockSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = PaddockSurface,
    surfaceContainerHigh = PaddockSurfaceElevated,
    outline = PaddockOutline,
    outlineVariant = PaddockOutline,
    error = AlertRed,
    onError = CarbonBg
)

private val MicroRCLightScheme = lightColorScheme(
    primary = Color(0xFFD84315),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3E0D00),
    secondary = Color(0xFF006879),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFAEEFFD),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF3F6800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDF47A),
    onTertiaryContainer = Color(0xFF102000),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF171B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B22),
    surfaceVariant = Color(0xFFE7ECF4),
    onSurfaceVariant = Color(0xFF475266),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE4E8F1),
    outline = Color(0xFF778298),
    outlineVariant = Color(0xFFCAD2E0),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val MicroRCShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MicroRCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MicroRCDarkScheme
        else -> MicroRCLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MicroRCTypography,
        shapes = MicroRCShapes,
        content = content
    )
}
