package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MicroRCColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = CarbonBg,
    primaryContainer = Color(0xFF3D1D14),
    onPrimaryContainer = SignalOrangeLight,
    secondary = ElectricCyan,
    onSecondary = CarbonBg,
    secondaryContainer = Color(0xFF0E2F38),
    onSecondaryContainer = ElectricCyan,
    tertiary = AcidLime,
    onTertiary = CarbonBg,
    background = CarbonBg,
    onBackground = TextPrimary,
    surface = PaddockSurface,
    onSurface = TextPrimary,
    surfaceVariant = PaddockSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = PaddockOutline,
    error = AlertRed,
    onError = CarbonBg
)

private val MicroRCShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MicroRCTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MicroRCColorScheme,
        typography = MicroRCTypography,
        shapes = MicroRCShapes,
        content = content
    )
}
