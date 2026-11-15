package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.audio.RaceAudioEngine
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.AudioSettings
import com.example.model.ControlMode
import com.example.model.ExternalEventType
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.model.PowerUpType
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RacerState
import com.example.model.TrackDefinition
import com.example.model.WinCondition
import com.example.sensors.RcHapticController
import com.example.sensors.TiltSteeringController
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.components.drawTrackWorld
import com.example.ui.components.drawWeatherScreenOverlay
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
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RaceScreen(
    profile: PlayerProfile,
    snapshot: RaceSnapshot?,
    activeTrack: TrackDefinition? = null,
    rules: RaceRulesSettings = RaceRulesSettings(),
    audioSettings: AudioSettings = AudioSettings(),
    audioEngine: RaceAudioEngine? = null,
    hapticController: RcHapticController? = null,
    overviewCamera: Boolean,
    tiltController: TiltSteeringController,
    onToggleOverviewCamera: () -> Unit,
    onUpdateControlInput: (Float, Float) -> Unit,
    onTriggerPowerUp: () -> Unit,
    onDropMine: () -> Unit = {},
    onSwitchControlMode: (ControlMode, Float, Boolean) -> Unit,
    onUpdateAudioSettings: (AudioSettings) -> Unit = {},
    onLeaveRace: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val oldKeep = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = oldKeep }
    }

    // Pick a fresh randomized race soundtrack for this run + feed per-car RC motor SFX
    DisposableEffect(audioEngine) {
        audioEngine?.startRaceRun()
        onDispose { audioEngine?.setMenuMode() }
    }
    LaunchedEffect(snapshot, profile.playerId, audioEngine) {
        audioEngine?.updateSnapshot(snapshot, profile.playerId)
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

    LaunchedEffect(
        profile.controlMode,
        profile.autoGasInTilt,
        rules.autoAccelerate,
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
            rules.autoAccelerate -> 1f
            gasPressed -> 1f
            profile.controlMode == ControlMode.TILT_SENSOR && profile.autoGasInTilt -> 0.92f
            else -> 0f
        }
        onUpdateControlInput(throttle, steer)
    }

    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var camX by remember { mutableFloatStateOf(1100f) }
    var camY by remember { mutableFloatStateOf(750f) }
    var camInitialized by remember { mutableStateOf(false) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var lastSnapshotArrivalNanos by remember { mutableLongStateOf(0L) }
    var smoothedRacers by remember { mutableStateOf<List<RacerState>>(emptyList()) }
    val smoothPoseMap = remember { mutableMapOf<String, FloatArray>() }

    val localRacer = snapshot?.racers?.find { it.playerId == profile.playerId }
        ?: snapshot?.racers?.firstOrNull()
    val latestSnapshot by androidx.compose.runtime.rememberUpdatedState(snapshot)

    // Tactile haptics for collisions, mine blasts, EMP hits, field pickups & portal warps
    var lastCollisionSeq by remember(localRacer?.playerId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(localRacer?.collisionSeq) {
        val seq = localRacer?.collisionSeq ?: return@LaunchedEffect
        val prev = lastCollisionSeq
        if (prev != null && seq > prev) {
            hapticController?.onCollision(heavy = (localRacer.stunnedMs > 0L))
        }
        lastCollisionSeq = seq
    }

    var lastPortalWarpSeq by remember(localRacer?.playerId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(localRacer?.portalWarpSeq) {
        val seq = localRacer?.portalWarpSeq ?: return@LaunchedEffect
        val prev = lastPortalWarpSeq
        if (prev != null && seq > prev) {
            hapticController?.onPortalWarp()
        }
        lastPortalWarpSeq = seq
    }

    LaunchedEffect(snapshot) {
        if (snapshot != null) {
            lastSnapshotArrivalNanos = System.nanoTime()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) lastFrameNanos = frameNanos
                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = frameNanos
                animTimeSec += dt

                val snap = latestSnapshot
                if (snap != null && snap.racers.isNotEmpty()) {
                    val sinceSnapSec = if (lastSnapshotArrivalNanos > 0L) {
                        ((frameNanos - lastSnapshotArrivalNanos) / 1_000_000_000f).coerceIn(0f, 0.075f)
                    } else {
                        0f
                    }
                    val posLerp = (dt * 22f).coerceIn(0f, 1f)
                    val angLerp = (dt * 20f).coerceIn(0f, 1f)

                    val rendered = snap.racers.map { r ->
                        val predX = r.x + r.vx * sinceSnapSec
                        val predY = r.y + r.vy * sinceSnapSec
                        val cur = smoothPoseMap[r.playerId]
                        if (cur == null || hypot(predX - cur[0], predY - cur[1]) > 220f) {
                            smoothPoseMap[r.playerId] = floatArrayOf(predX, predY, r.angleRad, r.jumpZ)
                            r.copy(x = predX, y = predY)
                        } else {
                            cur[0] += (predX - cur[0]) * posLerp
                            cur[1] += (predY - cur[1]) * posLerp
                            val angDiff = RacePhysicsEngine.normalizeAngle(r.angleRad - cur[2])
                            cur[2] = RacePhysicsEngine.normalizeAngle(cur[2] + angDiff * angLerp)
                            cur[3] += (r.jumpZ - cur[3]) * posLerp
                            r.copy(x = cur[0], y = cur[1], angleRad = cur[2], jumpZ = cur[3])
                        }
                    }
                    smoothedRacers = rendered

                    val target = rendered.find { it.playerId == profile.playerId } ?: rendered.firstOrNull()
                    if (target != null) {
                        val lookX = target.x + cos(target.angleRad) * 40f
                        val lookY = target.y + sin(target.angleRad) * 40f
                        if (!camInitialized || hypot(lookX - camX, lookY - camY) > 420f) {
                            camX = lookX
                            camY = lookY
                            camInitialized = true
                        } else {
                            val camLerp = (dt * 12f).coerceIn(0f, 1f)
                            camX += (lookX - camX) * camLerp
                            camY += (lookY - camY) * camLerp
                        }
                    }
                }
            }
        }
    }

    val isArenaMode = snapshot?.gameMode == GameMode.ARENA.name ||
        (snapshot == null && rules.gameModeEnum == GameMode.ARENA)
    val track = activeTrack ?: remember(snapshot?.trackId, snapshot?.arenaShape, isArenaMode) {
        if (isArenaMode) {
            TrackCatalog.getArenaTrack(snapshot?.arenaShape)
        } else {
            TrackCatalog.getTrack(snapshot?.trackId ?: "LIVING_ROOM_GP")
        }
    }
    val racersToDraw = smoothedRacers.ifEmpty { snapshot?.racers.orEmpty() }
    val arenaFlagToDraw = snapshot?.arenaFlag?.let { rawFlag ->
        val carrierId = rawFlag.carrierId
        if (carrierId != null) {
            val carrierPose = racersToDraw.find { it.playerId == carrierId }
            if (carrierPose != null) {
                rawFlag.copy(x = carrierPose.x, y = carrierPose.y)
            } else {
                rawFlag
            }
        } else {
            rawFlag
        }
    }
    val activeEvent = runCatching {
        ExternalEventType.valueOf(snapshot?.activeEvent ?: ExternalEventType.NONE.name)
    }.getOrDefault(ExternalEventType.NONE)
    val winCond = runCatching {
        WinCondition.valueOf(snapshot?.winCondition ?: rules.winCondition)
    }.getOrDefault(WinCondition.FIRST_TO_FINISH)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(track.id.baseBgColorHex))
    ) {
        // World + Screen Weather Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewW = size.width
            val viewH = size.height

            val shakeX = if (activeEvent == ExternalEventType.EARTHQUAKE && snapshot?.phase == RacePhase.RACING.name) {
                sin(animTimeSec * 44f) * 14f + sin(animTimeSec * 29f) * 6f
            } else {
                0f
            }
            val shakeY = if (activeEvent == ExternalEventType.EARTHQUAKE && snapshot?.phase == RacePhase.RACING.name) {
                cos(animTimeSec * 51f) * 11f + cos(animTimeSec * 33f) * 5f
            } else {
                0f
            }

            if (overviewCamera) {
                val scale = minOf(viewW / track.worldWidth, viewH / track.worldHeight) * 0.94f
                val tx = (viewW - track.worldWidth * scale) / 2f + shakeX
                val ty = (viewH - track.worldHeight * scale) / 2f + shakeY
                withTransform({
                    translate(tx, ty)
                    scale(scale, scale, Offset.Zero)
                }) {
                    drawTrackWorld(
                        track = track,
                        itemBoxes = snapshot?.itemBoxes.orEmpty(),
                        hazards = snapshot?.hazards.orEmpty(),
                        racers = racersToDraw,
                        animTimeSec = animTimeSec,
                        hardBoundary = rules.hardTrackBoundary,
                        rainPuddles = snapshot?.rainPuddles.orEmpty(),
                        activeEvent = activeEvent,
                        showPlayerNames = rules.showPlayerNames,
                        localPlayerId = profile.playerId,
                        isOverview = true,
                        arenaFlag = arenaFlagToDraw,
                        fieldPickups = snapshot?.fieldPickups.orEmpty(),
                        portalPair = snapshot?.portalPair,
                        explosions = snapshot?.explosions.orEmpty(),
                        arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0
                    )
                }
            } else {
                val zoom = (minOf(viewW, viewH) / 860f).coerceIn(0.75f, 1.5f)
                withTransform({
                    translate(viewW / 2f - camX * zoom + shakeX, viewH / 2f - camY * zoom + shakeY)
                    scale(zoom, zoom, Offset.Zero)
                }) {
                    drawTrackWorld(
                        track = track,
                        itemBoxes = snapshot?.itemBoxes.orEmpty(),
                        hazards = snapshot?.hazards.orEmpty(),
                        racers = racersToDraw,
                        animTimeSec = animTimeSec,
                        hardBoundary = rules.hardTrackBoundary,
                        rainPuddles = snapshot?.rainPuddles.orEmpty(),
                        activeEvent = activeEvent,
                        showPlayerNames = rules.showPlayerNames,
                        localPlayerId = profile.playerId,
                        isOverview = false,
                        arenaFlag = arenaFlagToDraw,
                        fieldPickups = snapshot?.fieldPickups.orEmpty(),
                        portalPair = snapshot?.portalPair,
                        explosions = snapshot?.explosions.orEmpty(),
                        arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0
                    )
                }
            }

            if (snapshot?.phase == RacePhase.RACING.name && activeEvent != ExternalEventType.NONE) {
                drawWeatherScreenOverlay(
                    activeEvent = activeEvent,
                    animTimeSec = animTimeSec,
                    windAngleRad = snapshot.windAngleRad,
                    windStrength = snapshot.windStrength
                )
            }
        }

        // Top Telemetry HUD + Corner Mini-Map
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = CarbonBg.copy(alpha = 0.86f),
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
                        if (isArenaMode) {
                            val targetMs = snapshot?.arenaTargetMs ?: (rules.arenaTargetMinutes * 60_000L)
                            val myHoldMs = localRacer?.flagCarryMs ?: 0L
                            val carrierId = arenaFlagToDraw?.carrierId
                            val iHaveFlag = carrierId != null && carrierId == localRacer?.playerId
                            val carrierRacer = snapshot?.racers?.find { it.playerId == carrierId }
                            Column {
                                Text("FLAG HOLD", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "${formatArenaHoldClock(myHoldMs)}/${formatArenaHoldClock(targetMs)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (iHaveFlag) GoldTrophy else ElectricCyan
                                )
                            }
                            Column {
                                Text("FLAG", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = when {
                                        iHaveFlag -> "YOU!"
                                        carrierRacer != null -> carrierRacer.name.take(8)
                                        else -> "LOOSE"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        iHaveFlag -> GoldTrophy
                                        carrierRacer != null -> SignalOrange
                                        else -> AcidLime
                                    }
                                )
                            }
                            Column {
                                val sdStage = snapshot?.arenaSuddenDeathStage ?: 0
                                val curClockMs = snapshot?.raceTimeMs ?: 0L
                                Text(
                                    text = if (sdStage > 0) "FINALE" else "CLOCK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (sdStage > 0) {
                                        val remSec = ((snapshot?.arenaSuddenDeathRemainingMs ?: 0L) + 999L) / 1000L
                                        "${remSec.coerceAtLeast(0L)}s"
                                    } else {
                                        formatArenaHoldClock(curClockMs)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        sdStage > 0 -> AlertRed
                                        curClockMs >= 110_000L -> SignalOrange
                                        else -> Color.White
                                    }
                                )
                            }
                        } else if (winCond == WinCondition.TIME_TRIAL_DISTANCE) {
                            val totalMs = ((snapshot?.timeLimitSec ?: rules.timeLimitSec) * 1000L)
                            val remMs = (totalMs - (snapshot?.raceTimeMs ?: 0L)).coerceAtLeast(0L)
                            Column {
                                Text("LEFT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = formatRaceTime(remMs),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (remMs < 15000L) AlertRed else ElectricCyan
                                )
                            }
                            Column {
                                Text("LAP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "${localRacer?.lap ?: 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                            }
                        } else {
                            Column {
                                Text("LAP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "${localRacer?.lap ?: 1}/${snapshot?.totalLaps ?: 3}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                            }
                            if (winCond == WinCondition.CHAMPIONSHIP_POINTS) {
                                Column {
                                    Text("PTS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(
                                        text = "${localRacer?.racePoints ?: 0}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = AcidLime
                                    )
                                }
                            } else {
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
                    }
                }

                // Arena Sudden Death Shrinking Finale Badge
                val suddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0
                if (isArenaMode && suddenDeathStage > 0 && snapshot?.phase == RacePhase.RACING.name) {
                    val sdPulse = 0.5f + 0.5f * sin(animTimeSec * 12f)
                    val remSec = (((snapshot.arenaSuddenDeathRemainingMs) + 999L) / 1000L).coerceAtLeast(1L)
                    Surface(
                        color = Color(0xFF33060E).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp, AlertRed.copy(alpha = 0.7f + 0.3f * sdPulse)),
                        modifier = Modifier.testTag("sudden_death_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "SUDDEN DEATH • WAVE $suddenDeathStage/3 • ${remSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                // Weather / Quake active pill
                if (activeEvent != ExternalEventType.NONE && snapshot?.phase == RacePhase.RACING.name) {
                    val (evIcon, evColor) = when (activeEvent) {
                        ExternalEventType.RAIN -> Icons.Default.WaterDrop to ElectricCyan
                        ExternalEventType.EARTHQUAKE -> Icons.Default.Vibration to SignalOrange
                        ExternalEventType.WIND -> Icons.Default.Air to AcidLime
                        ExternalEventType.SNOW -> Icons.Default.AcUnit to Color.White
                        ExternalEventType.NONE -> Icons.Default.WaterDrop to ElectricCyan
                    }
                    val remSec = ((snapshot.eventRemainingMs + 999L) / 1000L).coerceAtLeast(1L)
                    Surface(
                        color = CarbonBg.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, evColor),
                        modifier = Modifier.testTag("active_weather_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(evIcon, contentDescription = null, tint = evColor, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${activeEvent.displayName.uppercase()} ${remSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = evColor
                            )
                        }
                    }
                }
            }

            // Right Pause/Camera buttons + Corner Mini-Map Overlay
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onToggleOverviewCamera,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (overviewCamera) ElectricCyan else CarbonBg.copy(alpha = 0.86f))
                            .border(1.dp, PaddockOutline, CircleShape)
                            .testTag("toggle_camera_button")
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Toggle Camera",
                            tint = if (overviewCamera) CarbonBg else ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = { showPauseDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CarbonBg.copy(alpha = 0.86f))
                            .border(1.dp, PaddockOutline, CircleShape)
                            .testTag("race_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause & Controls", tint = Color.White)
                    }
                }

                if (!overviewCamera) {
                    Surface(
                        color = CarbonBg.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, ElectricCyan.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .size(width = 136.dp, height = 94.dp)
                            .testTag("race_minimap")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawTrackMiniMap(
                                    track = track,
                                    racers = racersToDraw,
                                    localPlayerId = profile.playerId,
                                    arenaFlag = arenaFlagToDraw,
                                    portalPair = snapshot?.portalPair,
                                    arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0
                                )
                            }
                            Text(
                                text = "MAP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Countdown, Finished Grace Notice, or Event Banner
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
        } else if (localRacer?.finished == true && snapshot?.phase == RacePhase.RACING.name) {
            val graceSec = ((snapshot.postFinishGraceMs + 999L) / 1000L).coerceAtLeast(1L)
            Surface(
                color = CarbonBg.copy(alpha = 0.88f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, GoldTrophy),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 86.dp)
            ) {
                Text(
                    text = "FINISHED ${ordinal(localRacer.rank)}! Waiting for rivals (${graceSec}s)",
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldTrophy,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
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

        // Prominent "INVERTED" Steering Alert Overlay (5 seconds)
        val invertedRemMs = localRacer?.invertedSteerMs ?: 0L
        if (invertedRemMs > 0L && snapshot?.phase == RacePhase.RACING.name) {
            val invPulse = 0.5f + 0.5f * sin(animTimeSec * 12f)
            val invSecText = String.format("%.1fs", invertedRemMs / 1000f)
            val topOffset = if (!snapshot.eventBanner.isNullOrBlank() || localRacer?.finished == true) {
                126.dp
            } else {
                86.dp
            }
            Surface(
                color = Color(0xFF280633).copy(alpha = 0.90f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(2.dp, Color(0xFFE040FB).copy(alpha = 0.65f + 0.35f * invPulse)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = topOffset)
                    .testTag("inverted_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INVERTED",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF80AB)
                    )
                    Text(
                        text = invSecText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom Controls Overlay + Flashing Red Mine Button + Power-Up Button
        val heldPowerUp = localRacer?.heldPowerUp?.let {
            runCatching { PowerUpType.valueOf(it) }.getOrNull()
        }
        val minesAvailable = localRacer?.minesAvailable ?: 0
        val hasMine = !isArenaMode && minesAvailable > 0

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            val steerWidth = when {
                maxWidth >= 520.dp -> 116.dp
                maxWidth >= 360.dp -> 100.dp
                else -> 88.dp
            }
            val controlHeight = when {
                maxWidth >= 520.dp -> 100.dp
                maxWidth >= 360.dp -> 94.dp
                else -> 86.dp
            }
            val steerIconSize = when {
                maxWidth >= 520.dp -> 52.dp
                maxWidth >= 360.dp -> 48.dp
                else -> 44.dp
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rules.autoAccelerate) {
                            Surface(
                                color = AcidLime.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, AcidLime.copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("auto_gas_badge")
                            ) {
                                Text(
                                    text = "AUTO GAS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AcidLime,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (isArenaMode && arenaFlagToDraw?.carrierId == localRacer?.playerId) {
                            StatusChip("CARRYING FLAG!", GoldTrophy)
                        }
                        if (invertedRemMs > 0L) StatusChip("INVERTED", Color(0xFFE040FB))
                        if ((localRacer?.jumpZ ?: 0f) > 0.1f) StatusChip("AIRBORNE!", GoldTrophy)
                        if ((localRacer?.boostMs ?: 0L) > 0L) StatusChip("TURBO", SignalOrange)
                        if ((localRacer?.shieldMs ?: 0L) > 0L) StatusChip("SHIELD", AcidLime)
                        if ((localRacer?.overdriveMs ?: 0L) > 0L) StatusChip("OVERDRIVE", Color(0xFFE040FB))
                        if ((localRacer?.stunnedMs ?: 0L) > 0L) StatusChip("ZAPPED!", AlertRed)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isArenaMode) {
                            val mineFlash = 0.5f + 0.5f * sin(animTimeSec * 14f)
                            val mineActiveBg = if (mineFlash > 0.45f) {
                                AlertRed.copy(alpha = 0.94f)
                            } else {
                                Color(0xFF7F0019).copy(alpha = 0.92f)
                            }
                            val mineBorder = if (hasMine) {
                                if (mineFlash > 0.45f) Color(0xFFFFCDD2) else AlertRed
                            } else {
                                PaddockOutline
                            }
                            Surface(
                                color = if (hasMine) mineActiveBg else CarbonBg.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(2.dp, mineBorder),
                                modifier = Modifier
                                    .size(width = 96.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(enabled = hasMine) {
                                        hapticController?.onMineDropped()
                                        onDropMine()
                                    }
                                    .testTag("drop_mine_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hasMine) {
                                                    if (mineFlash > 0.45f) Color.White else Color(0xFFFF5252)
                                                } else {
                                                    TextSecondary.copy(alpha = 0.5f)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            hasMine -> "MINE"
                                            (localRacer?.lap ?: 1) < 2 -> "LAP 2"
                                            else -> "MINE"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (hasMine) Color.White else TextSecondary
                                    )
                                }
                            }
                        }

                        val powerUpColor = heldPowerUp?.let { Color(it.colorHex) } ?: PaddockOutline
                        Surface(
                            color = if (heldPowerUp != null) powerUpColor.copy(alpha = 0.92f) else CarbonBg.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(2.dp, powerUpColor),
                            modifier = Modifier
                                .size(width = 116.dp, height = 56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = heldPowerUp != null) {
                                    hapticController?.onPowerUpUsed()
                                    onTriggerPowerUp()
                                }
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
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = heldPowerUp?.shortName ?: "EMPTY",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (heldPowerUp != null) CarbonBg else TextSecondary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (profile.controlMode == ControlMode.TOUCH_OVERLAY) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HoldControlPad(
                                label = null,
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDesc = "Steer Left",
                                activeColor = ElectricCyan,
                                iconSize = steerIconSize,
                                modifier = Modifier
                                    .size(width = steerWidth, height = controlHeight)
                                    .testTag("steer_left_button"),
                                onPressedChange = { steerLeftPressed = it }
                            )
                            HoldControlPad(
                                label = null,
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDesc = "Steer Right",
                                activeColor = ElectricCyan,
                                iconSize = steerIconSize,
                                modifier = Modifier
                                    .size(width = steerWidth, height = controlHeight)
                                    .testTag("steer_right_button"),
                                onPressedChange = { steerRightPressed = it }
                            )
                        }
                    } else {
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rules.autoAccelerate) {
                            HoldControlPad(
                                label = "BRAKE",
                                icon = null,
                                contentDesc = "Brake or Reverse",
                                activeColor = AlertRed,
                                modifier = Modifier
                                    .size(width = 116.dp, height = controlHeight)
                                    .testTag("brake_button"),
                                onPressedChange = { brakePressed = it }
                            )
                        } else {
                            HoldControlPad(
                                label = "BRK",
                                icon = null,
                                contentDesc = "Brake or Reverse",
                                activeColor = AlertRed,
                                modifier = Modifier
                                    .size(width = 68.dp, height = controlHeight)
                                    .testTag("brake_button"),
                                onPressedChange = { brakePressed = it }
                            )
                            HoldControlPad(
                                label = if (profile.controlMode == ControlMode.TILT_SENSOR && profile.autoGasInTilt) "AUTO" else "GAS",
                                icon = null,
                                contentDesc = "Accelerate",
                                activeColor = AcidLime,
                                modifier = Modifier
                                    .size(width = 88.dp, height = controlHeight)
                                    .testTag("gas_button"),
                                onPressedChange = { gasPressed = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text("Race Controls & Audio", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Switch Steering Mode Live:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControlMode.entries.forEach { mode ->
                            val active = profile.controlMode == mode
                            OutlinedButton(
                                onClick = {
                                    onSwitchControlMode(mode, profile.tiltSensitivity, profile.autoGasInTilt)
                                },
                                border = BorderStroke(
                                    1.5.dp,
                                    if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (mode == ControlMode.TILT_SENSOR) Icons.Default.PhoneAndroid else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (mode == ControlMode.TILT_SENSOR) "Tilt" else "Touch")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Music", style = MaterialTheme.typography.bodyMedium)
                            audioEngine?.activeThemeName?.let { trackTitle ->
                                Text(
                                    text = "Now playing: $trackTitle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = audioSettings.musicEnabled,
                            onCheckedChange = {
                                onUpdateAudioSettings(audioSettings.copy(musicEnabled = it))
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RC Car Motor SFX", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = audioSettings.carSfxEnabled,
                            onCheckedChange = {
                                onUpdateAudioSettings(audioSettings.copy(carSfxEnabled = it))
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic Vibration", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = audioSettings.hapticsEnabled,
                            onCheckedChange = {
                                onUpdateAudioSettings(audioSettings.copy(hapticsEnabled = it))
                            }
                        )
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
                ) { Text("Exit Race", color = MaterialTheme.colorScheme.error) }
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
    iconSize: Dp = 44.dp,
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (pressed) {
                pressed = false
                onPressedChange(false)
            }
        }
    }

    Surface(
        color = if (pressed) activeColor.copy(alpha = 0.85f) else CarbonBg.copy(alpha = 0.75f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.2.dp, if (pressed) Color.White else activeColor.copy(alpha = 0.8f)),
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isDown = event.changes.any { it.pressed }
                    if (isDown != pressed) {
                        pressed = isDown
                        onPressedChange(isDown)
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
                    modifier = Modifier.size(iconSize)
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

fun formatArenaHoldClock(ms: Long): String {
    val totalSecs = (ms.coerceAtLeast(0L)) / 1000L
    val mins = totalSecs / 60L
    val secs = totalSecs % 60L
    return String.format("%d:%02d", mins, secs)
}

fun ordinal(rank: Int): String = when (rank) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${rank}th"
}
