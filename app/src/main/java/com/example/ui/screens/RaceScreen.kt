package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.audio.RaceAudioEngine
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ActiveGhostCarState
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
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.WinCondition
import com.example.model.computeSpringSliderSteer
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaceScreen(
    profile: PlayerProfile,
    snapshot: RaceSnapshot?,
    activeTrack: TrackDefinition? = null,
    ghostCar: ActiveGhostCarState? = null,
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
    onSwitchControlMode: (ControlMode, Float, Boolean, Float) -> Unit,
    onUpdateAudioSettings: (AudioSettings) -> Unit = {},
    onLeaveRace: () -> Unit
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    DisposableEffect(view) {
        val oldKeep = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = oldKeep }
    }

    LaunchedEffect(view, configuration.orientation, profile.controlMode) {
        tiltController.updateDisplayRotation(view.display?.rotation)
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
    var sliderSteer by remember { mutableFloatStateOf(0f) }
    var gasPressed by remember { mutableStateOf(false) }
    var brakePressed by remember { mutableStateOf(false) }

    val tiltSteer by tiltController.steerValue.collectAsState()

    LaunchedEffect(profile.controlMode) {
        if (profile.controlMode != ControlMode.SPRING_SLIDER) {
            sliderSteer = 0f
        }
        if (profile.controlMode != ControlMode.TOUCH_OVERLAY) {
            steerLeftPressed = false
            steerRightPressed = false
        }
    }

    DisposableEffect(profile.controlMode, profile.tiltSensitivity) {
        if (profile.controlMode == ControlMode.TILT_SENSOR) {
            tiltController.updateDisplayRotation(view.display?.rotation)
            tiltController.setSensitivity(profile.tiltSensitivity)
            tiltController.start()
        } else {
            tiltController.stop()
        }
        onDispose { tiltController.stop() }
    }

    val isStuntsMode = snapshot?.gameMode == GameMode.STUNTS.name ||
        (snapshot == null && rules.gameModeEnum == GameMode.STUNTS)
    val stuntState = snapshot?.stuntState
    val isLocalActiveStuntDriver = !isStuntsMode ||
        stuntState == null ||
        stuntState.activePlayerId.isEmpty() ||
        stuntState.activePlayerId == profile.playerId

    val stuntControlsLocked = isStuntsMode && stuntState?.controlsLocked == true

    LaunchedEffect(
        profile.controlMode,
        profile.autoGasInTilt,
        rules.autoAccelerate,
        steerLeftPressed,
        steerRightPressed,
        sliderSteer,
        gasPressed,
        brakePressed,
        tiltSteer,
        isLocalActiveStuntDriver,
        stuntControlsLocked
    ) {
        if (!isLocalActiveStuntDriver || stuntControlsLocked) {
            onUpdateControlInput(0f, 0f)
            return@LaunchedEffect
        }
        val steer = when (profile.controlMode) {
            ControlMode.TILT_SENSOR -> tiltSteer
            ControlMode.SPRING_SLIDER -> sliderSteer
            ControlMode.TOUCH_OVERLAY -> (if (steerRightPressed) 1f else 0f) - (if (steerLeftPressed) 1f else 0f)
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

                    val activeStuntId = if (snap.gameMode == GameMode.STUNTS.name) {
                        snap.stuntState?.activePlayerId?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    val target = if (activeStuntId != null) {
                        rendered.find { it.playerId == activeStuntId } ?: rendered.firstOrNull()
                    } else {
                        rendered.find { it.playerId == profile.playerId } ?: rendered.firstOrNull()
                    }
                    if (target != null) {
                        val stuntSnap = snap.stuntState
                        val trackSoccerBall = snap.gameMode == GameMode.STUNTS.name &&
                            stuntSnap?.variantEnum == StuntVariant.SOCCER_BALL &&
                            stuntSnap.ballX > 1945f
                        val lookX = if (trackSoccerBall) {
                            target.x * 0.45f + stuntSnap.ballX * 0.55f
                        } else {
                            target.x + cos(target.angleRad) * 40f
                        }
                        val lookY = if (trackSoccerBall) {
                            target.y * 0.45f + stuntSnap.ballY * 0.55f
                        } else {
                            target.y + sin(target.angleRad) * 40f
                        }
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
    val track = activeTrack ?: remember(snapshot?.trackId, snapshot?.arenaShape, stuntState?.variant, isArenaMode, isStuntsMode) {
        when {
            isStuntsMode -> TrackCatalog.getStuntTrack(stuntState?.variant)
            isArenaMode -> TrackCatalog.getArenaTrack(snapshot?.arenaShape)
            else -> TrackCatalog.getTrack(snapshot?.trackId ?: "LIVING_ROOM_GP")
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(track.id.baseBgColorHex))
    ) {
        val isWideHud = maxWidth >= 560.dp
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
                        arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0,
                        stuntState = stuntState,
                        rockets = snapshot?.rockets.orEmpty(),
                        ghostCar = ghostCar
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
                        arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0,
                        stuntState = stuntState,
                        rockets = snapshot?.rockets.orEmpty(),
                        ghostCar = ghostCar
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
                        if (isStuntsMode) {
                            val turnRemSec = (((stuntState?.turnTimeRemainingMs ?: 10_000L) + 999L) / 1000L).coerceAtLeast(0L)
                            Column {
                                Text(
                                    text = "STUNT ${stuntState?.roundIndex ?: 1}/${stuntState?.totalRounds ?: 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = stuntState?.variantEnum?.shortName ?: "STUNT",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                            }
                            Column {
                                Text("PTS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "${localRacer?.racePoints ?: 0}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = AcidLime
                                )
                            }
                            Column {
                                Text("TURN", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "${turnRemSec}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (turnRemSec <= 3L) AlertRed else Color.White
                                )
                            }
                        } else if (isArenaMode) {
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

                // Hybrid Tour Stage Pill
                if (snapshot?.isHybridTour == true) {
                    Surface(
                        color = CarbonBg.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, GoldTrophy.copy(alpha = 0.75f)),
                        modifier = Modifier.testTag("hybrid_stage_badge")
                    ) {
                        Text(
                            text = "HYBRID STAGE #${snapshot.hybridStageNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldTrophy,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
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
                                    arenaSuddenDeathStage = snapshot?.arenaSuddenDeathStage ?: 0,
                                    stuntState = stuntState,
                                    ghostCar = ghostCar
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

        // Countdown Ring (only before GO)
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
        }

        // Top-Anchored Notification & Status Stack — strictly at the top of the screen so the center car is never covered
        val invertedRemMs = localRacer?.invertedSteerMs ?: 0L
        val electroParalyzedRemMs = localRacer?.electroParalyzedMs ?: 0L
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    top = if (isWideHud) 8.dp else 64.dp,
                    start = if (isWideHud) 244.dp else 12.dp,
                    end = when {
                        isWideHud -> 154.dp
                        !overviewCamera -> 154.dp
                        else -> 12.dp
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (ghostCar != null && snapshot?.phase != RacePhase.FINISHED.name) {
                val delta = ghostCar.splitDeltaMs
                val ahead = delta != null && delta <= 0L
                val deltaText = when {
                    delta == null -> "TARGET ${formatRaceTime(ghostCar.finishTimeMs)}"
                    delta <= 0L -> String.format(java.util.Locale.US, "%.2fs", delta / 1000f)
                    else -> String.format(java.util.Locale.US, "+%.2fs", delta / 1000f)
                }
                val badgeColor = when {
                    delta == null -> ElectricCyan
                    ahead -> AcidLime
                    else -> SignalOrange
                }
                Surface(
                    color = CarbonBg.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, badgeColor),
                    modifier = Modifier.testTag("ghost_delta_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GHOST • ${ghostCar.driverName.take(10).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelLarge,
                            color = badgeColor
                        )
                    }
                }
            }

            if (isStuntsMode && snapshot?.phase != RacePhase.FINISHED.name && stuntState != null) {
                Surface(
                    color = if (isLocalActiveStuntDriver) {
                        Color(0xFF1A2810).copy(alpha = 0.92f)
                    } else {
                        Color(0xFF0E1B2E).copy(alpha = 0.92f)
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.5.dp,
                        if (isLocalActiveStuntDriver) AcidLime else ElectricCyan
                    ),
                    modifier = Modifier.testTag("stunt_turn_banner")
                ) {
                    Text(
                        text = if (isLocalActiveStuntDriver) {
                            "YOUR TURN! 1x NITRO READY • ${stuntState.variantEnum.displayName.uppercase()}"
                        } else {
                            "SPECTATING: ${stuntState.activePlayerName.uppercase()} • ${stuntState.variantEnum.shortName}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLocalActiveStuntDriver) AcidLime else ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            if (electroParalyzedRemMs > 0L && snapshot?.phase == RacePhase.RACING.name) {
                val shockPulse = 0.5f + 0.5f * sin(animTimeSec * 18f)
                val shockSecText = String.format("%.1fs", electroParalyzedRemMs / 1000f)
                Surface(
                    color = Color(0xFF180A33).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(2.dp, Color(0xFF7C4DFF).copy(alpha = 0.65f + 0.35f * shockPulse)),
                    modifier = Modifier.testTag("electro_paralyzed_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ELECTRO SHOCKED!",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB388FF)
                        )
                        Text(
                            text = shockSecText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            if (invertedRemMs > 0L && snapshot?.phase == RacePhase.RACING.name) {
                val invPulse = 0.5f + 0.5f * sin(animTimeSec * 12f)
                val invSecText = String.format("%.1fs", invertedRemMs / 1000f)
                Surface(
                    color = Color(0xFF280633).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(2.dp, Color(0xFFE040FB).copy(alpha = 0.65f + 0.35f * invPulse)),
                    modifier = Modifier.testTag("inverted_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "INVERTED",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF80AB)
                        )
                        Text(
                            text = invSecText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            if (localRacer?.finished == true && snapshot?.phase == RacePhase.RACING.name && !isStuntsMode) {
                val graceSec = ((snapshot.postFinishGraceMs + 999L) / 1000L).coerceAtLeast(1L)
                Surface(
                    color = CarbonBg.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, GoldTrophy),
                    modifier = Modifier.testTag("top_event_banner")
                ) {
                    Text(
                        text = "FINISHED ${ordinal(localRacer.rank)}! Waiting for rivals (${graceSec}s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldTrophy,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            } else if (!snapshot?.eventBanner.isNullOrBlank() && snapshot?.phase != RacePhase.COUNTDOWN.name) {
                Surface(
                    color = CarbonBg.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, ElectricCyan),
                    modifier = Modifier.testTag("top_event_banner")
                ) {
                    Text(
                        text = snapshot?.eventBanner ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (rules.autoAccelerate && isLocalActiveStuntDriver) {
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
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
                if (isArenaMode && arenaFlagToDraw?.carrierId == localRacer?.playerId) {
                    StatusChip("CARRYING FLAG!", GoldTrophy)
                }
                if (stuntControlsLocked && isLocalActiveStuntDriver) StatusChip("LANDED — COASTING", ElectricCyan)
                if ((localRacer?.jumpZ ?: 0f) > 0.1f) StatusChip("AIRBORNE!", GoldTrophy)
                if ((localRacer?.boostMs ?: 0L) > 0L) StatusChip("TURBO", SignalOrange)
                if ((localRacer?.shieldMs ?: 0L) > 0L) StatusChip("SHIELD", AcidLime)
                if ((localRacer?.overdriveMs ?: 0L) > 0L) StatusChip("OVERDRIVE", Color(0xFFE040FB))
                if ((localRacer?.laserBeamMs ?: 0L) > 0L) StatusChip("RED LASER", AlertRed)
                if ((localRacer?.electroShockAuraMs ?: 0L) > 0L) StatusChip("ELECTRO AURA", Color(0xFFB388FF))
                if ((localRacer?.electroParalyzedMs ?: 0L) > 0L) StatusChip("MOTIONLESS!", Color(0xFF7C4DFF))
                if ((localRacer?.stunnedMs ?: 0L) > 0L) StatusChip("ZAPPED!", AlertRed)
            }
        }

        // Bottom Controls Overlay + Flashing Red Mine Button + Power-Up Button
        val heldPowerUp = localRacer?.heldPowerUp?.let {
            runCatching { PowerUpType.valueOf(it) }.getOrNull()
        }
        val minesAvailable = localRacer?.minesAvailable ?: 0
        val hasMine = minesAvailable > 0

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
            val sliderWidth = when {
                maxWidth >= 560.dp -> 260.dp
                maxWidth >= 440.dp -> 216.dp
                maxWidth >= 330.dp -> 164.dp
                else -> 146.dp
            }
            val mineBtnWidth = when {
                maxWidth >= 440.dp -> 96.dp
                maxWidth >= 330.dp -> 78.dp
                else -> 68.dp
            }
            val powerBtnWidth = when {
                maxWidth >= 440.dp -> 116.dp
                maxWidth >= 330.dp -> 86.dp
                else -> 74.dp
            }
            val actionRowSpacing = if (maxWidth >= 440.dp) 8.dp else 5.dp
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

            if (isStuntsMode && !isLocalActiveStuntDriver) {
                // Spectator Live Scoreboard Bar while watching another player's stunt run
                Surface(
                    color = CarbonBg.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stunt_spectator_bar")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SPECTATOR MODE • WATCHING ${stuntState?.activePlayerName?.uppercase() ?: "DRIVER"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                            if (!stuntState?.lastAttemptSummary.isNullOrBlank()) {
                                Text(
                                    text = stuntState?.lastAttemptSummary ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldTrophy,
                                    maxLines = 1
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            snapshot?.racers.orEmpty().forEach { r ->
                                val isActiveTurn = r.playerId == stuntState?.activePlayerId
                                Surface(
                                    color = if (isActiveTurn) ElectricCyan.copy(alpha = 0.22f) else PaddockSurface,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActiveTurn) ElectricCyan else PaddockOutline
                                    )
                                ) {
                                    Text(
                                        text = "${if (isActiveTurn) "▶ " else ""}${r.name.take(10)}: ${r.racePoints} pts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActiveTurn) Color.White else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                when (profile.controlMode) {
                    ControlMode.TOUCH_OVERLAY -> {
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
                    }
                    ControlMode.SPRING_SLIDER -> {
                        SpringSteeringSlider(
                            onSteerChange = { sliderSteer = it },
                            sensitivity = profile.sliderSensitivity,
                            iconSize = steerIconSize,
                            modifier = Modifier
                                .size(width = sliderWidth, height = controlHeight)
                                .testTag("spring_steering_slider")
                        )
                    }
                    ControlMode.TILT_SENSOR -> {
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
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(actionRowSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isArenaMode && !isStuntsMode) {
                            val currentLap = localRacer?.lap ?: 1
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
                            val mineLabel = when {
                                hasMine -> "MINE"
                                currentLap <= 1 -> "LAP 2"
                                else -> "USED"
                            }
                            Surface(
                                color = if (hasMine) mineActiveBg else CarbonBg.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(2.dp, mineBorder),
                                modifier = Modifier
                                    .size(width = mineBtnWidth, height = 56.dp)
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
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hasMine) {
                                                    if (mineFlash > 0.45f) Color.White else Color(0xFFFF5252)
                                                } else {
                                                    TextSecondary.copy(alpha = 0.5f)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = mineLabel,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (hasMine) Color.White else TextSecondary
                                    )
                                }
                            }
                        }

                        val canFirePowerUp = heldPowerUp != null && !stuntControlsLocked
                        val powerUpColor = if (canFirePowerUp) {
                            heldPowerUp?.let { Color(it.colorHex) } ?: PaddockOutline
                        } else {
                            PaddockOutline
                        }
                        Surface(
                            color = if (canFirePowerUp) powerUpColor.copy(alpha = 0.92f) else CarbonBg.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(2.dp, powerUpColor),
                            modifier = Modifier
                                .size(width = powerBtnWidth, height = 56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = canFirePowerUp) {
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
                                    tint = if (canFirePowerUp) CarbonBg else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (stuntControlsLocked) "LOCKED" else (heldPowerUp?.shortName ?: "EMPTY"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (canFirePowerUp) CarbonBg else TextSecondary
                                )
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
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text("Race Controls & Audio", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Switch Steering Mode Live:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControlMode.entries.forEach { mode ->
                            val active = profile.controlMode == mode
                            OutlinedButton(
                                onClick = {
                                    onSwitchControlMode(
                                        mode,
                                        profile.tiltSensitivity,
                                        profile.autoGasInTilt,
                                        profile.sliderSensitivity
                                    )
                                },
                                border = BorderStroke(
                                    1.5.dp,
                                    if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_mode_${mode.name}")
                            ) {
                                Icon(
                                    when (mode) {
                                        ControlMode.TILT_SENSOR -> Icons.Default.PhoneAndroid
                                        ControlMode.SPRING_SLIDER -> Icons.Default.SwapHoriz
                                        ControlMode.TOUCH_OVERLAY -> Icons.Default.TouchApp
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(mode.shortLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (profile.controlMode == ControlMode.SPRING_SLIDER) {
                        Column {
                            Text(
                                text = "Slider Sensitivity: ${String.format("%.2fx", profile.sliderSensitivity)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = profile.sliderSensitivity,
                                onValueChange = { newSens ->
                                    onSwitchControlMode(
                                        profile.controlMode,
                                        profile.tiltSensitivity,
                                        profile.autoGasInTilt,
                                        newSens
                                    )
                                },
                                valueRange = 0.7f..2.6f,
                                modifier = Modifier.testTag("pause_slider_sensitivity")
                            )
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
                        Column {
                            Text(
                                text = "Tilt Sensitivity: ${String.format("%.2fx", profile.tiltSensitivity)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = profile.tiltSensitivity,
                                onValueChange = { newTiltSens ->
                                    onSwitchControlMode(
                                        profile.controlMode,
                                        newTiltSens,
                                        profile.autoGasInTilt,
                                        profile.sliderSensitivity
                                    )
                                },
                                valueRange = 0.6f..2.2f,
                                modifier = Modifier.testTag("pause_tilt_sensitivity")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-Gas in Tilt Mode", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = profile.autoGasInTilt,
                                onCheckedChange = {
                                    onSwitchControlMode(
                                        profile.controlMode,
                                        profile.tiltSensitivity,
                                        it,
                                        profile.sliderSensitivity
                                    )
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
fun SpringSteeringSlider(
    onSteerChange: (Float) -> Unit,
    sensitivity: Float = 1.45f,
    iconSize: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbHalfWidthPx = with(density) { 22.dp.toPx() }
    val slideSlopPx = with(density) { 8.dp.toPx() }
    val centerPuckGrabPx = with(density) { 22.dp.toPx() }
    var sliderWidthPx by remember { mutableFloatStateOf(0f) }
    var isSliding by remember { mutableStateOf(false) }
    var tapButtonDir by remember { mutableIntStateOf(0) }
    var rawThumbOffset by remember { mutableFloatStateOf(0f) }
    var activeSteerOut by remember { mutableFloatStateOf(0f) }
    val latestOnSteerChange by androidx.compose.runtime.rememberUpdatedState(onSteerChange)
    val latestSensitivity by androidx.compose.runtime.rememberUpdatedState(sensitivity)

    DisposableEffect(Unit) {
        onDispose {
            latestOnSteerChange(0f)
        }
    }

    val isInteracting = isSliding || tapButtonDir != 0
    val animatedThumbOffset by animateFloatAsState(
        targetValue = if (isInteracting) rawThumbOffset else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "springThumbSnap"
    )
    val displayOffset = if (isInteracting) rawThumbOffset else animatedThumbOffset

    Surface(
        color = CarbonBg.copy(alpha = 0.80f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            2.2.dp,
            if (isInteracting) Color.White else ElectricCyan.copy(alpha = 0.82f)
        ),
        modifier = modifier
            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
            .pointerInput(thumbHalfWidthPx, slideSlopPx, centerPuckGrabPx) {
                awaitPointerEventScope {
                    val pointerOrder = mutableListOf<PointerId>()
                    val downXById = mutableMapOf<PointerId, Float>()
                    val slidingIds = mutableSetOf<PointerId>()

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedSet = event.changes.filter { it.pressed }.map { it.id }.toSet()

                        // Clean up lifted pointers
                        val released = pointerOrder.filter { it !in pressedSet }
                        for (rid in released) {
                            pointerOrder.remove(rid)
                            downXById.remove(rid)
                            slidingIds.remove(rid)
                        }

                        // Register newly pressed pointers
                        val centerGrab = minOf(centerPuckGrabPx, sliderWidthPx * 0.16f)
                        for (change in event.changes) {
                            if (change.pressed && change.id !in downXById) {
                                downXById[change.id] = change.position.x
                                pointerOrder.add(change.id)
                                if (sliderWidthPx > 0f &&
                                    kotlin.math.abs(change.position.x - sliderWidthPx / 2f) <= centerGrab
                                ) {
                                    slidingIds.add(change.id)
                                }
                            }
                        }

                        val primaryId = pointerOrder.lastOrNull()
                        val activePointer = event.changes.firstOrNull { it.id == primaryId && it.pressed }

                        if (activePointer != null && sliderWidthPx > 0f) {
                            val startX = downXById[activePointer.id] ?: activePointer.position.x
                            val curX = activePointer.position.x
                            if (kotlin.math.abs(curX - startX) > slideSlopPx) {
                                slidingIds.add(activePointer.id)
                            }

                            if (activePointer.id in slidingIds) {
                                val maxTravel = (sliderWidthPx / 2f - thumbHalfWidthPx).coerceAtLeast(1f)
                                val rawNorm = ((curX - sliderWidthPx / 2f) / maxTravel).coerceIn(-1f, 1f)
                                val steer = computeSpringSliderSteer(rawNorm, sensitivity = latestSensitivity)
                                isSliding = true
                                tapButtonDir = 0
                                rawThumbOffset = rawNorm
                                activeSteerOut = steer
                                latestOnSteerChange(steer)
                            } else {
                                val dir = if (curX < sliderWidthPx / 2f) -1 else 1
                                val steer = dir.toFloat()
                                isSliding = false
                                tapButtonDir = dir
                                rawThumbOffset = steer
                                activeSteerOut = steer
                                latestOnSteerChange(steer)
                            }
                        } else if (isSliding || tapButtonDir != 0 || rawThumbOffset != 0f || activeSteerOut != 0f) {
                            isSliding = false
                            tapButtonDir = 0
                            rawThumbOffset = 0f
                            activeSteerOut = 0f
                            latestOnSteerChange(0f)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f
                val centerY = h / 2f
                val maxTravel = (w / 2f - thumbHalfWidthPx).coerceAtLeast(1f)
                val thumbCenterX = centerX + displayOffset * maxTravel

                // Overlaid Left & Right Arrow Button Pad Zones
                val padInset = 6.dp.toPx()
                val padGap = 8.dp.toPx()
                val padWidth = ((w - padInset * 2f - padGap) / 2f).coerceAtLeast(24f)
                val padHeight = (h - padInset * 2f).coerceAtLeast(24f)
                val padCorner = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                val leftActive = tapButtonDir < 0 || (isSliding && activeSteerOut < -0.15f)
                val rightActive = tapButtonDir > 0 || (isSliding && activeSteerOut > 0.15f)

                drawRoundRect(
                    color = when {
                        tapButtonDir < 0 -> ElectricCyan.copy(alpha = 0.55f)
                        leftActive -> ElectricCyan.copy(alpha = 0.18f)
                        else -> PaddockSurface.copy(alpha = 0.45f)
                    },
                    topLeft = Offset(padInset, padInset),
                    size = Size(padWidth, padHeight),
                    cornerRadius = padCorner
                )
                drawRoundRect(
                    color = if (tapButtonDir < 0) Color.White else ElectricCyan.copy(alpha = 0.42f),
                    topLeft = Offset(padInset, padInset),
                    size = Size(padWidth, padHeight),
                    cornerRadius = padCorner,
                    style = Stroke(width = if (tapButtonDir < 0) 2.dp.toPx() else 1.2.dp.toPx())
                )

                val rightLeftX = w - padInset - padWidth
                drawRoundRect(
                    color = when {
                        tapButtonDir > 0 -> ElectricCyan.copy(alpha = 0.55f)
                        rightActive -> ElectricCyan.copy(alpha = 0.18f)
                        else -> PaddockSurface.copy(alpha = 0.45f)
                    },
                    topLeft = Offset(rightLeftX, padInset),
                    size = Size(padWidth, padHeight),
                    cornerRadius = padCorner
                )
                drawRoundRect(
                    color = if (tapButtonDir > 0) Color.White else ElectricCyan.copy(alpha = 0.42f),
                    topLeft = Offset(rightLeftX, padInset),
                    size = Size(padWidth, padHeight),
                    cornerRadius = padCorner,
                    style = Stroke(width = if (tapButtonDir > 0) 2.dp.toPx() else 1.2.dp.toPx())
                )

                // Center channel groove
                val railHeight = 8.dp.toPx()
                val railMargin = thumbHalfWidthPx * 0.75f
                drawRoundRect(
                    color = CarbonBg.copy(alpha = 0.85f),
                    topLeft = Offset(railMargin, centerY - railHeight / 2f),
                    size = Size((w - railMargin * 2f).coerceAtLeast(4f), railHeight),
                    cornerRadius = CornerRadius(railHeight / 2f, railHeight / 2f)
                )

                // Expo tick marks (-70%, -35%, 0%, +35%, +70%)
                val tickFracs = floatArrayOf(-0.70f, -0.35f, 0f, 0.35f, 0.70f)
                for (tf in tickFracs) {
                    val tx = centerX + tf * maxTravel
                    val isZero = tf == 0f
                    val tickHalfH = if (isZero) h * 0.32f else h * 0.14f
                    drawLine(
                        color = if (isZero) SignalOrange.copy(alpha = 0.88f) else PaddockOutline,
                        start = Offset(tx, centerY - tickHalfH),
                        end = Offset(tx, centerY + tickHalfH),
                        strokeWidth = if (isZero) 2.5.dp.toPx() else 1.4.dp.toPx()
                    )
                }

                // Active proportional deflection bar from neutral center to thumb
                val barLeft = minOf(centerX, thumbCenterX)
                val barWidth = kotlin.math.abs(thumbCenterX - centerX)
                if (barWidth > 1f) {
                    drawRoundRect(
                        color = ElectricCyan.copy(alpha = 0.90f),
                        topLeft = Offset(barLeft, centerY - railHeight / 2f),
                        size = Size(barWidth, railHeight),
                        cornerRadius = CornerRadius(railHeight / 2f, railHeight / 2f)
                    )
                }

                // Spring-loaded RC transmitter thumb puck
                val puckW = (thumbHalfWidthPx * 1.75f).coerceAtLeast(38.dp.toPx())
                val puckH = (h - 18.dp.toPx()).coerceAtLeast(42.dp.toPx())
                val puckLeft = thumbCenterX - puckW / 2f
                val puckTop = centerY - puckH / 2f
                val puckColor = if (isInteracting) ElectricCyan else ElectricCyan.copy(alpha = 0.88f)

                drawRoundRect(
                    color = puckColor,
                    topLeft = Offset(puckLeft, puckTop),
                    size = Size(puckW, puckH),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Knurled grip ridges on thumb puck
                val ridgeColor = CarbonBg.copy(alpha = 0.72f)
                val ridgeSpacing = 5.dp.toPx()
                for (ri in -1..1) {
                    val rx = thumbCenterX + ri * ridgeSpacing
                    drawLine(
                        color = ridgeColor,
                        start = Offset(rx, centerY - puckH * 0.25f),
                        end = Offset(rx, centerY + puckH * 0.25f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Overlaid Left & Right Arrow Icons (matching Touch Pedals arrow visuals)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Steer Left",
                tint = if (tapButtonDir < 0) CarbonBg else ElectricCyan,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(iconSize)
                    .testTag("slider_left_arrow_overlay")
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Steer Right",
                tint = if (tapButtonDir > 0) CarbonBg else ElectricCyan,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(iconSize)
                    .testTag("slider_right_arrow_overlay")
            )

            Text(
                text = if (isInteracting) {
                    val pct = (activeSteerOut * 100f).toInt()
                    if (pct > 0) "+$pct%" else "$pct%"
                } else {
                    "SLIDE / TAP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isInteracting) Color.White else TextSecondary.copy(alpha = 0.82f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
            )
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
