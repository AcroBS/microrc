package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ControlMode
import com.example.model.PlayerProfile
import com.example.ui.components.drawRcCar
import com.example.ui.theme.AcidLime
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.PaddockSurfaceElevated
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    profile: PlayerProfile,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onSoloRace: () -> Unit,
    onOpenGarage: () -> Unit,
    onUpdateDriverName: (String) -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "carSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Artwork Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_paddock),
                        contentDescription = "MicroRC Paddock Hero Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        CarbonBg.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            color = SignalOrange.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, SignalOrange.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = SignalOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "2-4 PLAYER OFFLINE WI-FI P2P",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalOrange
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MicroRC",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Top-down local multiplayer RC racing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Driver Profile Pill Row
                Surface(
                    color = PaddockSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PaddockOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showNameDialog = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.primaryColorArgb)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CarbonBg
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DRIVER TAG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Driver Name",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = PaddockSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenGarage)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (profile.controlMode == ControlMode.TILT_SENSOR) {
                                        Icons.Default.PhoneAndroid
                                    } else {
                                        Icons.Default.TouchApp
                                    },
                                    contentDescription = "Control Mode",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = profile.controlMode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                // Active RC Car Preview + Garage Link
                Card(
                    colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                    border = BorderStroke(1.dp, PaddockOutline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PaddockSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRcCar(
                                    x = size.width / 2f,
                                    y = size.height / 2f,
                                    angleRad = spinAngle,
                                    carType = profile.carType,
                                    primaryColor = Color(profile.primaryColorArgb),
                                    accentColor = Color(profile.accentColorArgb),
                                    scale = 1.45f
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.carType.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = profile.carType.tagline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MiniStatBadge("SPD ${profile.carType.topSpeedStat}", SignalOrange)
                                MiniStatBadge("HDL ${profile.carType.handlingStat}", ElectricCyan)
                                MiniStatBadge("GRP ${profile.carType.gripStat}", AcidLime)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenGarage,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            .height(48.dp)
                            .testTag("open_garage_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = ElectricCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Garage, Colors & Controls", color = ElectricCyan)
                    }
                }

                // Primary Multiplayer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onHostRace,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalOrange,
                            contentColor = CarbonBg
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .testTag("host_race_button")
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("HOST RACE", style = MaterialTheme.typography.labelLarge)
                            Text("2-4 Players", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Button(
                        onClick = onJoinRace,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = CarbonBg
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .testTag("join_race_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("JOIN RACE", style = MaterialTheme.typography.labelLarge)
                            Text("Scan Wi-Fi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Solo Practice vs Bots
                OutlinedButton(
                    onClick = onSoloRace,
                    border = BorderStroke(1.dp, AcidLime.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("solo_race_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = AcidLime)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Solo Practice vs AI Bots", color = AcidLime, style = MaterialTheme.typography.labelLarge)
                }

                // Offline Wi-Fi / Hotspot Tip Card
                Surface(
                    color = PaddockSurfaceElevated.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PaddockOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "No internet needed: Connect all phones to the same Wi-Fi, or enable a Hotspot on one phone and connect the others before hosting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        var draft by remember(profile.name) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = PaddockSurface,
            title = { Text("Driver Callsign", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 16) draft = it },
                    label = { Text("Driver Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDriverName(draft)
                        showNameDialog = false
                    },
                    modifier = Modifier.testTag("save_driver_name_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MiniStatBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
