package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.SessionNotificationHelper
import com.example.data.DailyChallengeCatalog
import com.example.data.TrackCatalog
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.GhostCodec
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardBoardKey
import com.example.model.LeaderboardEntryDoc
import com.example.model.LocalLeaderboardRecord
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.ui.components.drawRcCar
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.theme.adaptiveGhostAccentColor
import com.example.ui.theme.adaptiveGoldColor

enum class LeaderboardFilterTab {
    RACE,
    CUSTOM,
    DRIFT,
    STUNTS,
    DAILY,
    DAILY_DRIFT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaderboardScreen(
    localRecords: List<LocalLeaderboardRecord>,
    isSignedIn: Boolean,
    currentUserId: String?,
    cloudEntries: List<LeaderboardEntryDoc>,
    isLoadingCloud: Boolean,
    ghostLoadingId: String?,
    cloudStatusMessage: String?,
    customTracks: List<CustomTrackData> = emptyList(),
    initialTabDaily: Boolean = false,
    initialMode: GameMode? = null,
    initialTrackKey: String? = null,
    onSelectBoardKey: (String) -> Unit,
    onStartPureSoloForTrack: (GameMode, TrackId, Int, GhostRunPlayback?) -> Unit,
    onStartPureSoloForDriftTrack: (CustomTrackData, Int, GhostRunPlayback?) -> Unit = { _, _, _ -> },
    onStartPureSoloForCustomTrack: (CustomTrackData, Int, GhostRunPlayback?) -> Unit = { _, _, _ -> },
    onStartDailyChallenge: (dateKey: String, ghost: GhostRunPlayback?) -> Unit = { _, _ -> },
    onStartDailyDriftChallenge: (dateKey: String, ghost: GhostRunPlayback?) -> Unit = { _, _ -> },
    onChallengeCloudGhost: (LeaderboardEntryDoc) -> Unit,
    onOpenTrackEditor: () -> Unit = {},
    onRequestSignIn: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val driftTracks = remember { com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks() }
    var selectedTab by remember {
        val isInitialBuiltInRace = !initialTrackKey.isNullOrBlank() &&
            runCatching { TrackId.valueOf(initialTrackKey) }.isSuccess
        mutableStateOf(
            when {
                initialTabDaily && initialMode == GameMode.DRIFT && com.example.drift.DriftFeature.ENABLED ->
                    LeaderboardFilterTab.DAILY_DRIFT
                initialTabDaily -> LeaderboardFilterTab.DAILY
                initialMode == GameMode.DRIFT && com.example.drift.DriftFeature.ENABLED ->
                    LeaderboardFilterTab.DRIFT
                initialMode == GameMode.STUNTS -> LeaderboardFilterTab.STUNTS
                initialMode == GameMode.RACE && !initialTrackKey.isNullOrBlank() && !isInitialBuiltInRace ->
                    LeaderboardFilterTab.CUSTOM
                else -> LeaderboardFilterTab.RACE
            }
        )
    }
    var selectedTrack by remember { mutableStateOf(TrackId.LIVING_ROOM_GP) }
    var selectedDriftTrack by remember(driftTracks) { mutableStateOf(driftTracks.first()) }
    var selectedCustomTrackId by remember { mutableStateOf(initialTrackKey) }
    val selectedCustomTrack = remember(customTracks, selectedCustomTrackId) {
        customTracks.firstOrNull { it.id == selectedCustomTrackId } ?: customTracks.firstOrNull()
    }
    var selectedLaps by remember { mutableIntStateOf(3) }
    var dailyGhostEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var hasNotifPerm by remember { mutableStateOf(SessionNotificationHelper.hasNotificationPermission(context)) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    LaunchedEffect(initialTabDaily, initialMode, initialTrackKey) {
        when {
            initialTabDaily && initialMode == GameMode.DRIFT && com.example.drift.DriftFeature.ENABLED ->
                selectedTab = LeaderboardFilterTab.DAILY_DRIFT
            initialTabDaily -> selectedTab = LeaderboardFilterTab.DAILY
            initialMode == GameMode.DRIFT && com.example.drift.DriftFeature.ENABLED -> {
                selectedTab = LeaderboardFilterTab.DRIFT
                if (!initialTrackKey.isNullOrBlank()) {
                    driftTracks.find { it.id == initialTrackKey }?.let {
                        selectedDriftTrack = it
                    }
                }
            }
            initialMode == GameMode.STUNTS -> selectedTab = LeaderboardFilterTab.STUNTS
            initialMode == GameMode.RACE -> {
                val builtIn = if (!initialTrackKey.isNullOrBlank()) {
                    runCatching { TrackId.valueOf(initialTrackKey) }.getOrNull()
                } else {
                    null
                }
                if (builtIn != null) {
                    selectedTab = LeaderboardFilterTab.RACE
                    selectedTrack = builtIn
                } else if (!initialTrackKey.isNullOrBlank()) {
                    selectedTab = LeaderboardFilterTab.CUSTOM
                    selectedCustomTrackId = initialTrackKey
                } else {
                    selectedTab = LeaderboardFilterTab.RACE
                }
            }
        }
    }

    val todayKey = remember { DailyChallengeCatalog.todayDateKey() }
    val dailyCustomTrack = remember(todayKey) { DailyChallengeCatalog.dailyCustomTrack(todayKey) }
    val dailyTrackDef = remember(dailyCustomTrack) { TrackCatalog.buildCustomTrack(dailyCustomTrack) }
    val dailyBoardKey = remember(todayKey) { DailyChallengeCatalog.boardKeyForDate(todayKey) }
    val dailyDriftCustomTrack = remember(todayKey) { com.example.drift.DriftDailyChallenge.dailyDriftCustomTrack(todayKey) }
    val dailyDriftTrackDef = remember(dailyDriftCustomTrack) { TrackCatalog.buildCustomTrack(dailyDriftCustomTrack) }
    val dailyDriftBoardKey = remember(todayKey) { com.example.drift.DriftDailyChallenge.boardKeyForDate(todayKey) }
    val dailyResetLabel = remember(todayKey) { DailyChallengeCatalog.formatCountdownUntilReset() }

    val selectedMode = when (selectedTab) {
        LeaderboardFilterTab.STUNTS -> GameMode.STUNTS
        LeaderboardFilterTab.DRIFT, LeaderboardFilterTab.DAILY_DRIFT -> GameMode.DRIFT
        else -> GameMode.RACE
    }

    val boardKey = remember(
        selectedTab,
        selectedTrack,
        selectedDriftTrack,
        selectedCustomTrack?.id,
        selectedLaps,
        dailyBoardKey,
        dailyDriftBoardKey
    ) {
        when (selectedTab) {
            LeaderboardFilterTab.DAILY -> dailyBoardKey
            LeaderboardFilterTab.DAILY_DRIFT -> dailyDriftBoardKey
            LeaderboardFilterTab.DRIFT -> LeaderboardBoardKey.build(
                gameMode = GameMode.DRIFT,
                trackKey = selectedDriftTrack.id,
                totalLaps = selectedLaps
            )
            LeaderboardFilterTab.CUSTOM -> LeaderboardBoardKey.build(
                gameMode = GameMode.RACE,
                trackKey = selectedCustomTrack?.id ?: "CUSTOM_NONE",
                totalLaps = selectedLaps
            )
            LeaderboardFilterTab.STUNTS -> LeaderboardBoardKey.build(
                gameMode = GameMode.STUNTS,
                trackKey = selectedTrack.name,
                totalLaps = selectedLaps
            )
            LeaderboardFilterTab.RACE -> LeaderboardBoardKey.build(
                gameMode = GameMode.RACE,
                trackKey = selectedTrack.name,
                totalLaps = selectedLaps
            )
        }
    }

    LaunchedEffect(boardKey, isSignedIn, selectedTab, selectedCustomTrack?.id) {
        if (isSignedIn && (selectedTab != LeaderboardFilterTab.CUSTOM || selectedCustomTrack != null)) {
            onSelectBoardKey(boardKey)
        }
    }

    val localPb = remember(localRecords, boardKey) {
        localRecords.find { it.boardKey == boardKey }
    }

    val localGhostPlayback = remember(localPb, selectedTab) {
        if (localPb != null && selectedTab != LeaderboardFilterTab.STUNTS && localPb.ghostFramesData.isNotBlank()) {
            val frames = GhostCodec.decodeFrames(localPb.ghostFramesData)
            if (frames.size >= 2) {
                GhostRunPlayback(
                    ghostId = "local_${localPb.boardKey}",
                    driverName = "${localPb.driverName} (PB)",
                    carType = localPb.carType,
                    primaryColorArgb = localPb.primaryColorArgb,
                    accentColorArgb = localPb.accentColorArgb,
                    boardKey = localPb.boardKey,
                    trackId = localPb.trackKey,
                    totalLaps = localPb.totalLaps,
                    finishTimeMs = localPb.bestRaceTimeMs,
                    bestLapMs = localPb.bestLapTimeMs,
                    frames = frames
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    val topCloudDailyGhostEntry = remember(cloudEntries, selectedTab, currentUserId) {
        if (selectedTab != LeaderboardFilterTab.DAILY && selectedTab != LeaderboardFilterTab.DAILY_DRIFT) {
            null
        } else {
            cloudEntries.firstOrNull { it.hasGhost && it.ghostId.isNotBlank() && it.userId != currentUserId }
                ?: cloudEntries.firstOrNull { it.hasGhost && it.ghostId.isNotBlank() }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 660.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("leaderboard_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (selectedTab) {
                                LeaderboardFilterTab.DAILY -> "DAILY CHALLENGE"
                                LeaderboardFilterTab.DAILY_DRIFT -> "DAILY DRIFT"
                                else -> "LEADERBOARDS & GHOSTS"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when (selectedTab) {
                                LeaderboardFilterTab.DAILY ->
                                    "1 fixed random track per UTC day • 0 bots • fair for every run"
                                LeaderboardFilterTab.DAILY_DRIFT ->
                                    "1 fixed drift obstacle course per UTC day • 1x Nitro/Lap • 0 powerups"
                                else ->
                                    "Pure Solo high scores & virtual Ghost Car challenges"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (!cloudStatusMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cloudStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Mode Filter (Daily GP / Daily Drift / Drifting / Circuit Time Trial / Custom Tracks / Stunt Gauntlet)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedTab == LeaderboardFilterTab.DAILY,
                        onClick = { selectedTab = LeaderboardFilterTab.DAILY },
                        leadingIcon = {
                            Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Daily Challenge") },
                        modifier = Modifier.testTag("leaderboard_mode_DAILY")
                    )
                    if (com.example.drift.DriftFeature.ENABLED) {
                        FilterChip(
                            selected = selectedTab == LeaderboardFilterTab.DAILY_DRIFT,
                            onClick = { selectedTab = LeaderboardFilterTab.DAILY_DRIFT },
                            leadingIcon = {
                                Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Daily Drift") },
                            modifier = Modifier.testTag("leaderboard_mode_DAILY_DRIFT")
                        )
                        FilterChip(
                            selected = selectedTab == LeaderboardFilterTab.DRIFT,
                            onClick = { selectedTab = LeaderboardFilterTab.DRIFT },
                            leadingIcon = {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Drifting") },
                            modifier = Modifier.testTag("leaderboard_mode_DRIFT")
                        )
                    }
                    FilterChip(
                        selected = selectedTab == LeaderboardFilterTab.RACE,
                        onClick = { selectedTab = LeaderboardFilterTab.RACE },
                        leadingIcon = {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Circuit Time Trial") },
                        modifier = Modifier.testTag("leaderboard_mode_RACE")
                    )
                    FilterChip(
                        selected = selectedTab == LeaderboardFilterTab.CUSTOM,
                        onClick = { selectedTab = LeaderboardFilterTab.CUSTOM },
                        leadingIcon = {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                if (customTracks.isNotEmpty()) {
                                    "Custom Tracks (${customTracks.size})"
                                } else {
                                    "Custom Tracks"
                                }
                            )
                        },
                        modifier = Modifier.testTag("leaderboard_mode_CUSTOM")
                    )
                    FilterChip(
                        selected = selectedTab == LeaderboardFilterTab.STUNTS,
                        onClick = { selectedTab = LeaderboardFilterTab.STUNTS },
                        leadingIcon = {
                            Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Stunt Gauntlet (4R)") },
                        modifier = Modifier.testTag("leaderboard_mode_STUNTS")
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Daily & Record Alerts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Get system notifications when new Daily tracks drop or friends post a track record.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("daily_enable_notifications_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                if (selectedTab == LeaderboardFilterTab.DAILY || selectedTab == LeaderboardFilterTab.DAILY_DRIFT) {
                    val isDailyDriftTab = selectedTab == LeaderboardFilterTab.DAILY_DRIFT
                    val activeDailyTrack = if (isDailyDriftTab) dailyDriftCustomTrack else dailyCustomTrack
                    val activeDailyTrackDef = if (isDailyDriftTab) dailyDriftTrackDef else dailyTrackDef
                    // Daily Challenge Card with Deterministic Mini-Map & Optional Ghost Car
                    val anyDailyGhostAvailable = topCloudDailyGhostEntry != null || localGhostPlayback != null
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(
                            1.5.dp,
                            if (isDailyDriftTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (isDailyDriftTab) "daily_drift_challenge_card" else "daily_challenge_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeDailyTrack.name.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = if (isDailyDriftTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isDailyDriftTab) {
                                                "Fixed Drift Course • Obstacles • 1 Nitro/Lap • 0 Powerups"
                                            } else {
                                                "Fixed Boosts, Ramps & Portals • 1 Nitro/Lap • No Mines"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = dailyResetLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }

                            // Deterministic Track Mini-Map Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B0E14))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawTrackMiniMap(track = activeDailyTrackDef)
                                }
                            }

                            // Optional Ghost Car Toggle (shown when another player or PB already placed a time)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (anyDailyGhostAvailable) {
                                    val targetGhostLabel = if (topCloudDailyGhostEntry != null) {
                                        "${topCloudDailyGhostEntry.driverName} (${formatRaceTime(topCloudDailyGhostEntry.bestRaceTimeMs)})"
                                    } else {
                                        "Your Saved PB (${formatRaceTime(localGhostPlayback!!.finishTimeMs)})"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Drive With Ghost Car",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Target: $targetGhostLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = adaptiveGhostAccentColor(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Switch(
                                            checked = dailyGhostEnabled,
                                            onCheckedChange = { dailyGhostEnabled = it },
                                            modifier = Modifier.testTag("daily_ghost_switch")
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "No ghost cars placed on today's track yet — set the first time to unlock ghosts!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val ghostToUse = when {
                                        dailyGhostEnabled && localGhostPlayback != null -> localGhostPlayback
                                        else -> null
                                    }
                                    if (dailyGhostEnabled && topCloudDailyGhostEntry != null) {
                                        onChallengeCloudGhost(topCloudDailyGhostEntry)
                                    } else if (isDailyDriftTab) {
                                        onStartDailyDriftChallenge(todayKey, ghostToUse)
                                    } else {
                                        onStartDailyChallenge(todayKey, ghostToUse)
                                    }
                                },
                                enabled = ghostLoadingId == null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag(if (isDailyDriftTab) "daily_drift_start_button" else "daily_start_button")
                            ) {
                                if (ghostLoadingId != null) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val ctaLabel = if (dailyGhostEnabled && topCloudDailyGhostEntry != null) {
                                    "START DAILY ${if (isDailyDriftTab) "DRIFT" else "RUN"} (VS ${topCloudDailyGhostEntry.driverName.uppercase()})"
                                } else if (dailyGhostEnabled && localGhostPlayback != null) {
                                    "START DAILY ${if (isDailyDriftTab) "DRIFT" else "RUN"} (VS PB GHOST)"
                                } else if (isDailyDriftTab) {
                                    "START DAILY DRIFT (PURE SOLO)"
                                } else {
                                    "START DAILY CHALLENGE (PURE SOLO)"
                                }
                                Text(
                                    text = ctaLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (selectedTab == LeaderboardFilterTab.CUSTOM) {
                    if (selectedCustomTrack == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_tracks_empty_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No Custom Tracks Synced Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isSignedIn) {
                                        "Design a circuit in Track Studio to race solo, auto-sync it with your friends, and climb custom track leaderboards."
                                    } else {
                                        "Build your own circuit in Track Studio, or sign in with Google to see and challenge your friends' self-designed tracks."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onOpenTrackEditor,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("leaderboard_open_track_editor_button")
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Track Studio")
                                    }
                                    if (!isSignedIn) {
                                        OutlinedButton(
                                            onClick = onRequestSignIn,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Text("Sign in")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(customTracks, key = { it.id }) { cTrack ->
                                val previewDef = remember(cTrack) { TrackCatalog.buildCustomTrack(cTrack) }
                                val active = selectedCustomTrack.id == cTrack.id
                                val isOwnTrack = cTrack.creatorUid.isBlank() ||
                                    (currentUserId != null && cTrack.creatorUid == currentUserId)
                                val creatorTag = if (isOwnTrack) {
                                    "By You"
                                } else {
                                    "By ${cTrack.creatorName.ifBlank { "Friend" }}"
                                }
                                Surface(
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (active) 2.dp else 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    onClick = { selectedCustomTrackId = cTrack.id },
                                    modifier = Modifier
                                        .width(152.dp)
                                        .testTag("leaderboard_custom_track_${cTrack.id}")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(previewDef.id.baseBgColorHex).copy(alpha = 0.85f))
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawTrackMiniMap(previewDef)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = cTrack.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = creatorTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RACE DISTANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 5).forEach { laps ->
                                    FilterChip(
                                        selected = selectedLaps == laps,
                                        onClick = { selectedLaps = laps },
                                        label = { Text("$laps ${if (laps == 1) "Lap" else "Laps"}", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("leaderboard_custom_laps_$laps")
                                    )
                                }
                            }
                        }

                        val selectedPreviewDef = remember(selectedCustomTrack) {
                            TrackCatalog.buildCustomTrack(selectedCustomTrack)
                        }
                        val isOwnSelected = selectedCustomTrack.creatorUid.isBlank() ||
                            (currentUserId != null && selectedCustomTrack.creatorUid == currentUserId)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedCustomTrack.name.uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isOwnSelected) {
                                                "Designed by You • ${selectedPreviewDef.ramps.size} Jumps • ${selectedPreviewDef.boostPads.size} Boosts"
                                            } else {
                                                "Designed by ${selectedCustomTrack.creatorName.ifBlank { "Friend" }} • ${selectedPreviewDef.ramps.size} Jumps • ${selectedPreviewDef.boostPads.size} Boosts"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = selectedPreviewDef.id.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(124.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(selectedPreviewDef.id.baseBgColorHex).copy(alpha = 0.9f))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawTrackMiniMap(selectedPreviewDef)
                                    }
                                }

                                Button(
                                    onClick = {
                                        onStartPureSoloForCustomTrack(selectedCustomTrack, selectedLaps, localGhostPlayback)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("leaderboard_start_custom_solo_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (localGhostPlayback != null) {
                                            "DRIVE ${selectedCustomTrack.name.uppercase()} (${selectedLaps}L VS PB GHOST)"
                                        } else {
                                            "DRIVE ${selectedCustomTrack.name.uppercase()} (${selectedLaps}L PURE SOLO)"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Track & Laps Selector for Circuit or Drift Mode
                    if (selectedTab == LeaderboardFilterTab.DRIFT) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(driftTracks, key = { it.id }) { dTrack ->
                                FilterChip(
                                    selected = selectedDriftTrack.id == dTrack.id,
                                    onClick = { selectedDriftTrack = dTrack },
                                    label = { Text(dTrack.name, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("leaderboard_drift_track_${dTrack.id}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DRIFT DISTANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 5).forEach { laps ->
                                    FilterChip(
                                        selected = selectedLaps == laps,
                                        onClick = { selectedLaps = laps },
                                        label = { Text("$laps ${if (laps == 1) "Lap" else "Laps"}", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("leaderboard_drift_laps_$laps")
                                    )
                                }
                            }
                        }
                    } else if (selectedTab == LeaderboardFilterTab.RACE) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TrackId.entries) { track ->
                                FilterChip(
                                    selected = selectedTrack == track,
                                    onClick = { selectedTrack = track },
                                    label = { Text(track.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("leaderboard_track_${track.name}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RACE DISTANCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 5).forEach { laps ->
                                    FilterChip(
                                        selected = selectedLaps == laps,
                                        onClick = { selectedLaps = laps },
                                        label = { Text("$laps ${if (laps == 1) "Lap" else "Laps"}", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("leaderboard_laps_$laps")
                                    )
                                }
                            }
                        }
                    }

                    // Quick Launch Pure Solo (0 Bots) Button
                    Button(
                        onClick = {
                            if (selectedTab == LeaderboardFilterTab.DRIFT) {
                                onStartPureSoloForDriftTrack(selectedDriftTrack, selectedLaps, localGhostPlayback)
                            } else {
                                onStartPureSoloForTrack(selectedMode, selectedTrack, selectedLaps, localGhostPlayback)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("leaderboard_start_solo_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                selectedMode == GameMode.STUNTS ->
                                    "START SOLO STUNT GAUNTLET (0 BOTS)"
                                selectedTab == LeaderboardFilterTab.DRIFT && localGhostPlayback != null ->
                                    "DRIFT ${selectedDriftTrack.name.uppercase()} (${selectedLaps}L VS PB GHOST)"
                                selectedTab == LeaderboardFilterTab.DRIFT ->
                                    "DRIFT ${selectedDriftTrack.name.uppercase()} (${selectedLaps}L PURE SOLO)"
                                localGhostPlayback != null ->
                                    "DRIVE ${selectedTrack.displayName.uppercase()} (${selectedLaps}L VS PB GHOST)"
                                else ->
                                    "DRIVE ${selectedTrack.displayName.uppercase()} (${selectedLaps}L PURE SOLO)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Personal Best Card (Device Local + Ghost Replay)
                if (selectedTab != LeaderboardFilterTab.CUSTOM || selectedCustomTrack != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = adaptiveGoldColor(),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "YOUR PERSONAL BEST",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (localPb == null) {
                                Text(
                                    text = when (selectedTab) {
                                        LeaderboardFilterTab.DAILY ->
                                            "No Daily Challenge time recorded today yet. Finish a 3-lap run on today's track to set your benchmark!"
                                        LeaderboardFilterTab.DAILY_DRIFT ->
                                            "No Daily Drift time recorded today yet. Finish a 3-lap drift run on today's obstacle course!"
                                        LeaderboardFilterTab.DRIFT ->
                                            "No time recorded on ${selectedDriftTrack.name} (${selectedLaps}L) yet. Complete a Drift run to save your Ghost car!"
                                        LeaderboardFilterTab.CUSTOM ->
                                            "No time recorded on ${selectedCustomTrack?.name ?: "this circuit"} (${selectedLaps}L) yet. Complete a Solo run to save your Ghost car!"
                                        LeaderboardFilterTab.STUNTS ->
                                            "No Solo Stunt Gauntlet score recorded yet. Finish a 4-round solo run to post a high score!"
                                        LeaderboardFilterTab.RACE ->
                                            "No time recorded on ${selectedTrack.displayName} (${selectedLaps}L) yet. Complete a Solo run to save your Ghost car!"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = localPb.driverName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (selectedMode == GameMode.STUNTS) {
                                            "4-Round Stunt Total"
                                        } else {
                                            "Best Lap: ${if (localPb.bestLapTimeMs > 0L) formatRaceTime(localPb.bestLapTimeMs) else "--"}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (selectedMode == GameMode.STUNTS) {
                                        "${localPb.bestScore} PTS"
                                    } else {
                                        formatRaceTime(localPb.bestRaceTimeMs)
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = adaptiveGoldColor()
                                )
                            }

                                if (localGhostPlayback != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            when (selectedTab) {
                                                LeaderboardFilterTab.DAILY ->
                                                    onStartDailyChallenge(todayKey, localGhostPlayback)
                                                LeaderboardFilterTab.DAILY_DRIFT ->
                                                    onStartDailyDriftChallenge(todayKey, localGhostPlayback)
                                                LeaderboardFilterTab.DRIFT ->
                                                    onStartPureSoloForDriftTrack(
                                                        selectedDriftTrack,
                                                        selectedLaps,
                                                        localGhostPlayback
                                                    )
                                                LeaderboardFilterTab.CUSTOM ->
                                                    selectedCustomTrack?.let { cTrack ->
                                                        onStartPureSoloForCustomTrack(
                                                            cTrack,
                                                            selectedLaps,
                                                            localGhostPlayback
                                                        )
                                                    }
                                                else ->
                                                    onStartPureSoloForTrack(
                                                        GameMode.RACE,
                                                        selectedTrack,
                                                        selectedLaps,
                                                        localGhostPlayback
                                                    )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("challenge_local_pb_ghost_button")
                                    ) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Race Against Your Saved Ghost Car")
                                    }
                                }
                            }
                        }
                    }
                }

                // Cloud Global / Friends Leaderboard Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == LeaderboardFilterTab.DAILY || selectedTab == LeaderboardFilterTab.DAILY_DRIFT) {
                            "TODAY'S ONLINE LEADERBOARD & GHOSTS"
                        } else {
                            "ONLINE LEADERBOARD & RIVAL GHOSTS"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLoadingCloud) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!isSignedIn) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Compare Times & Race Friends' Ghost Cars",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sign in with Google to sync Daily Challenge & Solo times to the cloud leaderboard, notify friends on new records, and race live against their Ghost RC cars.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onRequestSignIn,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("leaderboard_sign_in_button")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }
                        }
                    }
                } else if (cloudEntries.isEmpty() && !isLoadingCloud) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedTab == LeaderboardFilterTab.DAILY) {
                                "No cloud times posted for today's Daily Challenge yet — finish a run to set the #1 benchmark & upload your Ghost!"
                            } else {
                                "No cloud records posted for this board yet — be the first to set the benchmark & upload a Ghost!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cloudEntries.forEachIndexed { idx, entry ->
                            val rank = idx + 1
                            val goldTint = adaptiveGoldColor()
                            val ghostTint = adaptiveGhostAccentColor()
                            val medalColor = when (rank) {
                                1 -> goldTint
                                2 -> MaterialTheme.colorScheme.secondary
                                3 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val isMe = currentUserId != null && entry.userId == currentUserId
                            val car = runCatching { RcCarType.valueOf(entry.carType) }
                                .getOrDefault(RcCarType.VOLT_BUGGY)
                            val isThisGhostLoading = ghostLoadingId != null && ghostLoadingId == entry.ghostId

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(
                                    if (isMe) 1.5.dp else 1.dp,
                                    if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ordinal(rank),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = medalColor,
                                        modifier = Modifier.width(40.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRcCar(
                                                x = size.width / 2f,
                                                y = size.height / 2f,
                                                angleRad = -0.55f,
                                                carType = car,
                                                primaryColor = Color(entry.primaryColorArgb),
                                                accentColor = Color(entry.accentColorArgb),
                                                scale = 0.72f
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isMe) "${entry.driverName} (You)" else entry.driverName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val metricLine = if (selectedMode == GameMode.STUNTS) {
                                            "${entry.bestScore} PTS • ${car.displayName}"
                                        } else {
                                            val lapStr = if (entry.bestLapTimeMs > 0L) formatRaceTime(entry.bestLapTimeMs) else "--"
                                            "${formatRaceTime(entry.bestRaceTimeMs)} • Best Lap $lapStr"
                                        }
                                        Text(
                                            text = metricLine,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (rank == 1) goldTint else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (selectedTab != LeaderboardFilterTab.STUNTS && entry.hasGhost && entry.ghostId.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { onChallengeCloudGhost(entry) },
                                            enabled = ghostLoadingId == null,
                                            border = BorderStroke(1.dp, ghostTint),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("challenge_ghost_button_$idx")
                                        ) {
                                            if (isThisGhostLoading) {
                                                CircularProgressIndicator(
                                                    color = ghostTint,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Race Ghost",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ghostTint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
