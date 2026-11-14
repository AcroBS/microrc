package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.TrackCatalog
import com.example.model.ControlMode
import com.example.model.PlayerProfile
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.sensors.TiltSteeringController
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.components.drawTrackWorld
import com.example.ui.theme.AcidLime
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RaceScreen(
    profile: PlayerProfile,
    snapshot: RaceSnapshot?,
    overviewCamera: Boolean,
    tiltController: TiltSteeringController,
    onToggleOverviewCamera: () -> Unit,
    onUpdateControlInput: (Float, Float) -> Unit,
    onTriggerPowerUp: () -> Unit,
    onSwitchControlMode: (ControlMode, Float, Boolean) -> Unit,
    onLeaveRace: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val oldKeep = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = oldKeep }
    }

    var showPauseDialog by remember { mutableStateOf(false) }
    BackHandler { showPauseDialog = true }

    var steerLeftPressed by remember { mutableStateOf(false) }
    var steerRightPressed by remember { mutableStateOf(false) }
    var gasPressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }

    val tiltSteer by tiltController.steerValue.collectAsState()

    DisposableEffect(profile.controlMode, profile.tiltSensitivity) {
        if (profile.controlMode == ControlMode.TILT_SENSOR) {
            tiltController.setSensitivity(profile.tiltSensitivity)
            tiltController.start()
        } else {
            tiltController.stop()
        }
        onDispose { tiltController.stop() }
    }

    // Stream control inputs continuously
    LaunchedEffect(
        profile.controlMode,
        profile.autoGasInTilt,
        steerLeftPressed,
        steerRightPressed,
        gasPressed,
        brakePressed,
        tiltSteer
    ) {
        val steer = if (profile.controlMode == ControlMode.TILT_SENSOR) {
            tiltSteer
        } else {
            (if (steerRightPressed) 1f else 0f) - (if (steerLeftPressed) 1f else 0f)
        }
        val throttle = when {
            brakePressed -> -1f
            gasPressed -> 1f
            profile.controlMode == ControlMode.TILT_SENSOR && profile.autoGasInTilt -> 0.92f
            else -> 0f
        }
        onUpdateControlInput(throttle, steer)
    }

    // Smooth animation clock & camera state
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var camX by remember { mutableFloatStateOf(1100f) }
    var camY by remember { mutableFloatStateOf(750f) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    val localRacer = snapshot?.racers?.find { it.playerId == profile.playerId }
        ?: snapshot?.racers?.firstOrNull()

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) lastFrameNanos = frameNanos
                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = frameNanos
                animTimeSec += dt

                val target = localRacer
                if (target != null) {
                    val lookX = target.x + cos(target.angleRad) * 60f
                    val lookY = target.y + sin(target.angleRad) * 60f
                    val lerp = (dt * 7.5f).coerceIn(0f, 1f)
                    camX += (lookX - camX) * lerp
                    camY += (lookY - camY) * lerp
                }
            }
        }
    }

    val track = remember(snapshot?.trackId) {
        TrackCatalog.getTrack(snapshot?.trackId ?: "LIVING_ROOM_GP")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(track.id.baseBgColorHex))
    ) {
        // World Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewW = size.width
            val viewH = size.height

            if (overviewCamera) {
                val scale = minOf(viewW / track.worldWidth, viewH / track.worldHeight) * 0.94f
                val tx = (viewW - track.worldWidth * scale) / 2f
                val ty = (viewH - track.worldHeight * scale) / 2f
                withTransform({
                    translate(tx, ty)
                    scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    drawTrackWorld(
                        track = track,
                        itemBoxes = snapshot?.itemBoxes.orEmpty(),
                        hazards = snapshot?.hazards.orEmpty(),
                        racers = snapshot?.racers.orEmpty(),
                        animTimeSec = animTimeSec
                    )
                }
            } else {
                val zoom = (minOf(viewW, viewH) / 760f).coerceIn(0.78f, 1.55f)
                withTransform({
                    translate(viewW / 2f - camX * zoom, viewH / 2f - camY * zoom)
                    scale(zoom, zoom, androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    drawTrackWorld(
                        track = track,
                        itemBoxes = snapshot?.itemBoxes.orEmpty(),
                        hazards = snapshot?.hazards.orEmpty(),
                        racers = snapshot?.racers.orEmpty(),
                        animTimeSec = animTimeSec
                    )
                }
            }
        }

        // Top Telemetry HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left telemetry pill
            Surface(
                color = CarbonBg.copy(alpha = 0.84f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PaddockOutline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text("POS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${ordinal(localRacer?.rank ?: 1)}/${snapshot?.racers?.size ?: 1}",
                            style = MaterialTheme.typography.titleLarge,
                            color = GoldTrophy
                        )
                    }
                    Column {
                        Text("LAP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${localRacer?.lap ?: 1}/${snapshot?.totalLaps ?: 3}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Column {
                        Text("TIME", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        val curLapMs = ((snapshot?.raceTimeMs ?: 0L) - (localRacer?.currentLapStartMs ?: 0L)).coerceAtLeast(0L)
                        Text(
                            text = formatRaceTime(curLapMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                }
            }

            // Right Mini-Map & Camera/Pause
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CarbonBg.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PaddockOutline),
                    modifier = Modifier.size(width = 104.dp, height = 72.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawTrackMiniMap(
                            track = track,
                            racers = snapshot?.racers.orEmpty(),
                            localPlayerId = profile.playerId
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showPauseDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CarbonBg.copy(alpha = 0.85f))
                            .border(1.dp, PaddockOutline, CircleShape)
                            .testTag("race_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause & Controls", tint = Color.White)
                    }
                    IconButton(
                        onClick = onToggleOverviewCamera,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (overviewCamera) ElectricCyan else CarbonBg.copy(alpha = 0.85f))
                            .border(1.dp, PaddockOutline, CircleShape)
                            .testTag("toggle_camera_button")
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Toggle Camera",
                            tint = if (overviewCamera) CarbonBg else ElectricCyan
                        )
                    }
                }
            }
        }

        // Countdown or Event Banner
        if (snapshot?.phase == RacePhase.COUNTDOWN.name) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = CarbonBg.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = BorderStroke(3.dp, SignalOrange),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${snapshot.countdownSec}",
                            style = MaterialTheme.typography.displayLarge,
                            color = SignalOrange
                        )
                    }
                }
            }
        } else if (!snapshot?.eventBanner.isNullOrBlank()) {
            Surface(
                color = CarbonBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, ElectricCyan),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 86.dp)
            ) {
                Text(
                    text = snapshot?.eventBanner ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Controls Overlay + Power-Up Button
        val heldPowerUp = localRacer?.heldPowerUp?.let {
            runCatching { PowerUpType.valueOf(it) }.getOrNull()
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active status pills + Power-Up button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Buff status badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ((localRacer?.boostMs ?: 0L) > 0L) StatusChip("TURBO", SignalOrange)
                    if ((localRacer?.shieldMs ?: 0L) > 0L) StatusChip("SHIELD", AcidLime)
                    if ((localRacer?.overdriveMs ?: 0L) > 0L) StatusChip("OVERDRIVE", Color(0xFFE040FB))
                    if ((localRacer?.stunnedMs ?: 0L) > 0L) StatusChip("ZAPPED!", AlertRed)
                }

                // Power-up Trigger
                val powerUpColor = heldPowerUp?.let { Color(it.colorHex) } ?: PaddockOutline
                Surface(
                    color = if (heldPowerUp != null) powerUpColor.copy(alpha = 0.92f) else CarbonBg.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, powerUpColor),
                    modifier = Modifier
                        .size(width = 120.dp, height = 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = heldPowerUp != null) { onTriggerPowerUp() }
                        .testTag("use_powerup_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Power-up",
                            tint = if (heldPowerUp != null) CarbonBg else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = heldPowerUp?.shortName ?: "EMPTY",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (heldPowerUp != null) CarbonBg else TextSecondary
                        )
                    }
                }
            }

            // Steering + Pedals Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                if (profile.controlMode == ControlMode.TOUCH_OVERLAY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoldControlPad(
                            label = null,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDesc = "Steer Left",
                            activeColor = ElectricCyan,
                            modifier = Modifier
                                .size(78.dp)
                                .testTag("steer_left_button"),
                            onPressedChange = { steerLeftPressed = it }
                        )
                        HoldControlPad(
                            label = null,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDesc = "Steer Right",
                            activeColor = ElectricCyan,
                            modifier = Modifier
                                .size(78.dp)
                                .testTag("steer_right_button"),
                            onPressedChange = { steerRightPressed = it }
                        )
                    }
                } else {
                    // Tilt Gauge + Zero Calibration Button
                    Surface(
                        color = CarbonBg.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.width(172.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TILT STEER", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Calibrate Neutral Tilt",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable { tiltController.calibrateNeutral() }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(PaddockSurface)
                            ) {
                                val frac = ((tiltSteer + 1f) / 2f).coerceIn(0.05f, 0.95f)
                                Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(14.dp, 10.dp)
                                            .clip(CircleShape)
                                            .background(ElectricCyan)
                                    )
                                }
                            }
                        }
                    }
                }

                // Brake + Gas Pedals
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HoldControlPad(
                        label = "BRK",
                        icon = null,
                        contentDesc = "Brake or Reverse",
                        activeColor = AlertRed,
                        modifier = Modifier
                            .size(width = 68.dp, height = 78.dp)
                            .testTag("brake_button"),
                        onPressedChange = { brakePressed = it }
                    )
                    HoldControlPad(
                        label = if (profile.controlMode == ControlMode.TILT_SENSOR && profile.autoGasInTilt) "AUTO" else "GAS",
                        icon = null,
                        contentDesc = "Accelerate",
                        activeColor = AcidLime,
                        modifier = Modifier
                            .size(width = 88.dp, height = 78.dp)
                            .testTag("gas_button"),
                        onPressedChange = { gasPressed = it }
                    )
                }
            }
        }
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            containerColor = PaddockSurface,
            title = { Text("Race Controls & Menu", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Switch Steering Mode Live:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControlMode.entries.forEach { mode ->
                            val active = profile.controlMode == mode
                            OutlinedButton(
                                onClick = {
                                    onSwitchControlMode(mode, profile.tiltSensitivity, profile.autoGasInTilt)
                                },
                                border = BorderStroke(1.5.dp, if (active) ElectricCyan else PaddockOutline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (mode == ControlMode.TILT_SENSOR) Icons.Default.PhoneAndroid else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = if (active) ElectricCyan else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (mode == ControlMode.TILT_SENSOR) "Tilt" else "Touch",
                                    color = if (active) ElectricCyan else Color.White
                                )
                            }
                        }
                    }
                    if (profile.controlMode == ControlMode.TILT_SENSOR) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-Gas in Tilt Mode", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = profile.autoGasInTilt,
                                onCheckedChange = {
                                    onSwitchControlMode(profile.controlMode, profile.tiltSensitivity, it)
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { tiltController.calibrateNeutral() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Calibrate Neutral Tilt Now")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPauseDialog = false }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPauseDialog = false
                        onLeaveRace()
                    }
                ) { Text("Exit Race", color = AlertRed) }
            }
        )
    }
}

@Composable
private fun HoldControlPad(
    label: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    contentDesc: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Surface(
        color = if (pressed) activeColor.copy(alpha = 0.85f) else CarbonBg.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, if (pressed) Color.White else activeColor.copy(alpha = 0.75f)),
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isDown = event.changes.any { it.pressed }
                    if (isDown != pressed) {
                        pressed = isDown
                        onPressedChange(isDown)
                    }
                    if (event.type == PointerEventType.Release || !isDown) {
                        if (pressed) {
                            pressed = false
                            onPressedChange(false)
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = if (pressed) CarbonBg else activeColor,
                    modifier = Modifier.size(38.dp)
                )
            } else if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (pressed) CarbonBg else activeColor
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.24f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun formatRaceTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val mins = (clamped / 60000L)
    val secs = (clamped % 60000L) / 1000L
    val hundredths = (clamped % 1000L) / 10L
    return String.format("%02d:%02d.%02d", mins, secs, hundredths)
}

fun ordinal(rank: Int): String = when (rank) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${rank}th"
}
