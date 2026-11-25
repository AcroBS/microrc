package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.model.RcColorPresets
import com.example.ui.components.drawRcCar
import com.example.ui.theme.adaptiveGoldColor

@Composable
fun GarageScreen(
    profile: PlayerProfile,
    onSaveCarAndColors: (RcCarType, Long, Long) -> Unit,
    onBack: () -> Unit
) {
    var selectedCar by remember(profile.carType) { mutableStateOf(profile.carType) }
    var primaryColorArgb by remember(profile.primaryColorArgb) { mutableLongStateOf(profile.primaryColorArgb) }
    var accentColorArgb by remember(profile.accentColorArgb) { mutableLongStateOf(profile.accentColorArgb) }

    var showCustomHue by remember { mutableStateOf(false) }
    var customHue by remember { mutableFloatStateOf(16f) }

    val infiniteTransition = rememberInfiniteTransition(label = "garageSpin")
    val turntableAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "turntable"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("garage_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RC GARAGE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Vehicle Chassis & Custom Paint",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val gridAccent = MaterialTheme.colorScheme.secondary
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val gridColor = gridAccent.copy(alpha = 0.1f)
                                val step = 36f
                                var gx = 0f
                                while (gx < size.width) {
                                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1f)
                                    gx += step
                                }
                                var gy = 0f
                                while (gy < size.height) {
                                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1f)
                                    gy += step
                                }
                                drawRcCar(
                                    x = size.width / 2f,
                                    y = size.height / 2f,
                                    angleRad = turntableAngle,
                                    carType = selectedCar,
                                    primaryColor = Color(primaryColorArgb),
                                    accentColor = Color(accentColorArgb),
                                    steerVisual = 0.35f,
                                    scale = 2.4f
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(selectedCar.displayName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(selectedCar.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        StatBarRow("Top Speed", selectedCar.topSpeedStat, MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatBarRow("Acceleration", selectedCar.accelStat, MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatBarRow("Handling", selectedCar.handlingStat, adaptiveGoldColor())
                        Spacer(modifier = Modifier.height(6.dp))
                        StatBarRow("Off-Road Grip", selectedCar.gripStat, MaterialTheme.colorScheme.tertiary)
                    }
                }

                Text("SELECT CHASSIS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RcCarType.entries.forEach { car ->
                        val active = car == selectedCar
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedCar = car
                                    onSaveCarAndColors(car, primaryColorArgb, accentColorArgb)
                                }
                                .testTag("car_option_${car.name}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = car.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = car.displayName.substringAfter(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CUSTOM PAINT SHELL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (showCustomHue) "Hide Hue Slider" else "Custom Hue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCustomHue = !showCustomHue }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    itemsIndexed(RcColorPresets) { idx, preset ->
                        val isSelected = primaryColorArgb == preset.primaryArgb && accentColorArgb == preset.accentArgb
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(preset.primaryArgb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable {
                                    primaryColorArgb = preset.primaryArgb
                                    accentColorArgb = preset.accentArgb
                                    onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                                }
                                .testTag("color_preset_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val swatchCheckTint = if (Color(preset.primaryArgb).luminance() > 0.45f) {
                                    Color(0xFF0B0E14)
                                } else {
                                    Color.White
                                }
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = preset.name,
                                    tint = swatchCheckTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (showCustomHue) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Custom Body Hue (${customHue.toInt()} deg)", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = customHue,
                                onValueChange = { hue ->
                                    customHue = hue
                                    val c = Color.hsv(hue, 0.88f, 0.96f)
                                    primaryColorArgb = c.toArgb().toLong() and 0xFFFFFFFFL
                                    onSaveCarAndColors(selectedCar, primaryColorArgb, accentColorArgb)
                                },
                                valueRange = 0f..360f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(primaryColorArgb),
                                    activeTrackColor = Color(primaryColorArgb)
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("garage_done_button")
                ) {
                    Text("Save & Return", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StatBarRow(label: String, value: Int, color: Color) {
    val animProgress by animateFloatAsState(
        targetValue = (value / 100f).coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = label
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        LinearProgressIndicator(
            progress = { animProgress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.width(28.dp)
        )
    }
}
