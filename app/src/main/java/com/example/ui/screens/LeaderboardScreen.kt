package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GameMode
import com.example.model.GhostCodec
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardBoardKey
import com.example.model.LeaderboardEntryDoc
import com.example.model.LocalLeaderboardRecord
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.ui.components.drawRcCar
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.SilverMedal

@Composable
fun LeaderboardScreen(
    localRecords: List<LocalLeaderboardRecord>,
    isSignedIn: Boolean,
    currentUserId: String?,
    cloudEntries: List<LeaderboardEntryDoc>,
    isLoadingCloud: Boolean,
    ghostLoadingId: String?,
    cloudStatusMessage: String?,
    onSelectBoardKey: (String) -> Unit,
    onStartPureSoloForTrack: (GameMode, TrackId, Int, GhostRunPlayback?) -> Unit,
    onChallengeCloudGhost: (LeaderboardEntryDoc) -> Unit,
    onRequestSignIn: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedMode by remember { mutableStateOf(GameMode.RACE) }
    var selectedTrack by remember { mutableStateOf(TrackId.LIVING_ROOM_GP) }
    var selectedLaps by remember { mutableIntStateOf(3) }

    val boardKey = remember(selectedMode, selectedTrack, selectedLaps) {
        LeaderboardBoardKey.build(
            gameMode = selectedMode,
            trackKey = selectedTrack.name,
            totalLaps = selectedLaps
        )
    }

    LaunchedEffect(boardKey, isSignedIn) {
        if (isSignedIn) {
            onSelectBoardKey(boardKey)
        }
    }

    val localPb = remember(localRecords, boardKey) {
        localRecords.find { it.boardKey == boardKey }
    }

    val localGhostPlayback = remember(localPb) {
        if (localPb != null && selectedMode == GameMode.RACE && localPb.ghostFramesData.isNotBlank()) {
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
                            text = "LEADERBOARDS & GHOSTS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Pure Solo high scores & virtual Ghost Car challenges",
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

                // Mode Filter (Circuit Time Trial vs Stunt Gauntlet)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == GameMode.RACE,
                        onClick = { selectedMode = GameMode.RACE },
                        leadingIcon = {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Circuit Time Trial") },
                        modifier = Modifier.testTag("leaderboard_mode_RACE")
                    )
                    FilterChip(
                        selected = selectedMode == GameMode.STUNTS,
                        onClick = { selectedMode = GameMode.STUNTS },
                        leadingIcon = {
                            Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Stunt Gauntlet (4R)") },
                        modifier = Modifier.testTag("leaderboard_mode_STUNTS")
                    )
                }

                // Track & Laps Selector for Circuit Mode
                if (selectedMode == GameMode.RACE) {
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
                        onStartPureSoloForTrack(selectedMode, selectedTrack, selectedLaps, localGhostPlayback)
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
                        text = if (selectedMode == GameMode.STUNTS) {
                            "START SOLO STUNT GAUNTLET (0 BOTS)"
                        } else if (localGhostPlayback != null) {
                            "DRIVE ${selectedTrack.displayName.uppercase()} (${selectedLaps}L VS PB GHOST)"
                        } else {
                            "DRIVE ${selectedTrack.displayName.uppercase()} (${selectedLaps}L PURE SOLO)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Personal Best Card (Device Local + Ghost Replay)
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
                                tint = GoldTrophy,
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
                                text = if (selectedMode == GameMode.STUNTS) {
                                    "No Solo Stunt Gauntlet score recorded yet. Finish a 4-round solo run to post a high score!"
                                } else {
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
                                    color = GoldTrophy
                                )
                            }

                            if (localGhostPlayback != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onStartPureSoloForTrack(
                                            GameMode.RACE,
                                            selectedTrack,
                                            selectedLaps,
                                            localGhostPlayback
                                        )
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

                // Cloud Global / Family Leaderboard Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ONLINE LEADERBOARD & RIVAL GHOSTS",
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
                                text = "Compare Times & Race Brothers' Ghost Cars",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sign in with Google to automatically sync your Solo Personal Bests to the cloud leaderboard and race live against other players' Ghost RC cars.",
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
                            text = "No cloud records posted for this board yet — be the first to set the benchmark & upload a Ghost!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cloudEntries.forEachIndexed { idx, entry ->
                            val rank = idx + 1
                            val medalColor = when (rank) {
                                1 -> GoldTrophy
                                2 -> SilverMedal
                                3 -> BronzeMedal
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
                                            color = if (rank == 1) GoldTrophy else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (selectedMode == GameMode.RACE && entry.hasGhost && entry.ghostId.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { onChallengeCloudGhost(entry) },
                                            enabled = ghostLoadingId == null,
                                            border = BorderStroke(1.dp, ElectricCyan),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("challenge_ghost_button_$idx")
                                        ) {
                                            if (isThisGhostLoading) {
                                                CircularProgressIndicator(
                                                    color = ElectricCyan,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Race Ghost",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricCyan
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
