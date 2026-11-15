package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ArenaShape
import com.example.model.GameMode
import com.example.model.LobbyStatePayload
import com.example.model.RaceSnapshot
import com.example.model.RematchCountdownState
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.SessionRole
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.SilverMedal

@Composable
fun ResultsScreen(
    role: SessionRole,
    localPlayerId: String,
    results: RaceSnapshot?,
    lobby: LobbyStatePayload?,
    rematchState: RematchCountdownState? = null,
    onVoteRematch: (String) -> Unit = {},
    onLaunchRematchNow: () -> Unit = {},
    onReturnToLobby: () -> Unit,
    onExitHome: () -> Unit
) {
    val isHostOrSolo = role == SessionRole.Host || role == SessionRole.Solo
    BackHandler {
        if (isHostOrSolo) onReturnToLobby() else onExitHome()
    }

    val isArena = results?.gameMode == GameMode.ARENA.name
    val arenaTargetMs = (results?.arenaTargetMs ?: 120_000L).coerceAtLeast(1000L)
    val sortedRacers = results?.racers.orEmpty().sortedBy { it.rank }
    val leaderTime = sortedRacers.firstOrNull()?.finishTimeMs ?: 0L
    val overallBestLap = sortedRacers.mapNotNull { it.bestLapMs }.minOrNull()
    val trackName = if (isArena) {
        results?.arenaShape?.let {
            runCatching { ArenaShape.valueOf(it).displayName }.getOrNull()
        } ?: "Battle Arena"
    } else {
        lobby?.activeCustomTrack?.name ?: runCatching {
            TrackId.valueOf(results?.trackId ?: TrackId.LIVING_ROOM_GP.name).displayName
        }.getOrDefault("Circuit")
    }
    val winCond = runCatching {
        WinCondition.valueOf(results?.winCondition ?: WinCondition.FIRST_TO_FINISH.name)
    }.getOrDefault(WinCondition.FIRST_TO_FINISH)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = GoldTrophy.copy(alpha = 0.2f),
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, GoldTrophy),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isArena) Icons.Default.Flag else Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = GoldTrophy,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isArena) "ARENA CHAMPION" else "CHECKERED FLAG",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val suddenDeathWave = results?.arenaSuddenDeathStage ?: 0
                        val modeDesc = if (isArena) {
                            if (suddenDeathWave > 0) {
                                "Sudden Death Finale (Wave $suddenDeathWave/3)"
                            } else {
                                "${arenaTargetMs / 60_000L} Min Flag Hold Target"
                            }
                        } else {
                            when (winCond) {
                                WinCondition.TIME_TRIAL_DISTANCE -> "${results?.timeLimitSec ?: 90}s Time Limit"
                                WinCondition.CHAMPIONSHIP_POINTS -> "Points & Hits (${results?.totalLaps ?: 3} Laps)"
                                WinCondition.FIRST_TO_FINISH -> "${results?.totalLaps ?: 3} Laps"
                            }
                        }
                        Text(
                            text = "$trackName • $modeDesc",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Standings Cards
                sortedRacers.forEach { racer ->
                    val medalColor = when (racer.rank) {
                        1 -> GoldTrophy
                        2 -> SilverMedal
                        3 -> BronzeMedal
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val rightMetric = if (isArena) {
                        formatRaceTime(racer.flagCarryMs)
                    } else {
                        when (winCond) {
                            WinCondition.CHAMPIONSHIP_POINTS -> "${racer.racePoints} PTS"
                            WinCondition.TIME_TRIAL_DISTANCE -> "Lap ${racer.lap}"
                            WinCondition.FIRST_TO_FINISH -> {
                                if (racer.rank == 1 || racer.finishTimeMs == null) {
                                    racer.finishTimeMs?.let { formatRaceTime(it) } ?: "--:--"
                                } else {
                                    "+${String.format("%.2fs", ((racer.finishTimeMs - leaderTime).coerceAtLeast(0L)) / 1000f)}"
                                }
                            }
                        }
                    }
                    val isMe = racer.playerId == localPlayerId

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
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ordinal(racer.rank),
                                style = MaterialTheme.typography.titleLarge,
                                color = medalColor,
                                modifier = Modifier.width(46.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(racer.primaryColorArgb))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMe) "${racer.name} (You)" else racer.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isArena) {
                                    Text(
                                        text = "Flag Carried: ${racer.flagCarryMs / 1000L}s • Steals & Hits: ${racer.powerUpHits}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Best Lap: ${racer.bestLapMs?.let { formatRaceTime(it) } ?: "--"} • Hits: ${racer.powerUpHits}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (racer.bestLapMs != null && racer.bestLapMs == overallBestLap) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            Text(
                                text = rightMetric,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (racer.rank == 1) GoldTrophy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 10-Second Auto-Rematch & Next Track/Arena Voting Loop
                if (rematchState != null) {
                    RematchCountdownCard(
                        rematchState = rematchState,
                        localPlayerId = localPlayerId,
                        isHostOrSolo = isHostOrSolo,
                        isArena = isArena,
                        onVoteRematch = onVoteRematch,
                        onLaunchRematchNow = onLaunchRematchNow
                    )
                }

                if (isArena) {
                    // Arena Flag Hold Breakdown Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = GoldTrophy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ARENA FLAG HOLD BREAKDOWN",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            sortedRacers.forEach { racer ->
                                val frac = (racer.flagCarryMs.toFloat() / arenaTargetMs.toFloat()).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = racer.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatArenaHoldClock(racer.flagCarryMs)} / ${formatArenaHoldClock(arenaTargetMs)} (${(frac * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        color = if (racer.rank == 1) GoldTrophy else Color(racer.primaryColorArgb),
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Lap Time Comparison Table
                    val lapCount = (sortedRacers.maxOfOrNull { it.lapTimesMs.size } ?: 0)
                        .coerceAtLeast(if (winCond == WinCondition.TIME_TRIAL_DISTANCE) 1 else (results?.totalLaps ?: 3))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Text("LAP TIME COMPARISON", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("RACER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(108.dp))
                                    for (l in 1..lapCount) {
                                        Text("LAP $l", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
                                    }
                                    Text("BEST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(78.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                sortedRacers.forEach { racer ->
                                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = racer.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.width(108.dp)
                                        )
                                        for (idx in 0 until lapCount) {
                                            val lt = racer.lapTimesMs.getOrNull(idx)
                                            Text(
                                                text = lt?.let { formatRaceTime(it) } ?: "--",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (lt != null && lt == overallBestLap) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.width(76.dp)
                                            )
                                        }
                                        Text(
                                            text = racer.bestLapMs?.let { formatRaceTime(it) } ?: "--",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (racer.bestLapMs != null && racer.bestLapMs == overallBestLap) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.secondary
                                            },
                                            modifier = Modifier.width(78.dp)
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                                }
                            }
                        }
                    }
                }

                // Session Score Leaderboard
                val sessionStandings = lobby?.players.orEmpty().sortedByDescending { it.sessionPoints }
                if (sessionStandings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CHAMPIONSHIP SCORE LEADERBOARD",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            sessionStandings.forEachIndexed { idx, p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(p.primaryColorArgb))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    Text("${p.wins}W", style = MaterialTheme.typography.labelSmall, color = GoldTrophy, modifier = Modifier.padding(end = 10.dp))
                                    Text("${p.sessionPoints} PTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }

                if (!isHostOrSolo && rematchState == null) {
                    Text(
                        text = "Stay here to automatically rejoin the paddock when the host starts the next match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExitHome,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("results_home_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Home")
                    }

                    if (isHostOrSolo) {
                        OutlinedButton(
                            onClick = onReturnToLobby,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.6f)
                                .height(52.dp)
                                .testTag("results_rematch_button")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Back to Lobby (Edit Rules)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RematchCountdownCard(
    rematchState: RematchCountdownState,
    localPlayerId: String,
    isHostOrSolo: Boolean,
    isArena: Boolean,
    onVoteRematch: (String) -> Unit,
    onLaunchRematchNow: () -> Unit
) {
    val totalSec = rematchState.totalSeconds.coerceAtLeast(1)
    val secLeft = rematchState.secondsLeft.coerceIn(0, totalSec)
    val progress = secLeft.toFloat() / totalSec.toFloat()
    val myVote = rematchState.votesByPlayerId[localPlayerId]
    val leadingOption = rematchState.options.find { it.id == rematchState.leadingOptionId }
        ?: rematchState.options.lastOrNull()
    val urgentAccent = if (secLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.5.dp, urgentAccent.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rematch_countdown_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            urgentAccent.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = urgentAccent.copy(alpha = 0.22f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, urgentAccent),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = urgentAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isArena) "NEXT BATTLE IN ${secLeft}S" else "NEXT RACE IN ${secLeft}S",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("rematch_countdown_timer")
                        )
                        Text(
                            text = "Up Next: ${leadingOption?.displayName ?: "Random"} • Tap below to vote!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (isHostOrSolo) {
                    Button(
                        onClick = onLaunchRematchNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("rematch_launch_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Launch next race immediately",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Go Now", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = urgentAccent,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rematchState.options.forEach { option ->
                    val voteCount = rematchState.votesByPlayerId.values.count { it == option.id }
                    val isSelected = myVote == option.id
                    val isLeading = rematchState.leadingOptionId == option.id

                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (isSelected || isLeading) 1.5.dp else 1.dp,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.secondary
                                isLeading -> GoldTrophy.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onVoteRematch(option.id) }
                            .testTag("rematch_vote_${option.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isSelected -> Icons.Default.CheckCircle
                                    option.isRandom -> Icons.Default.Shuffle
                                    else -> Icons.Default.HowToVote
                                },
                                contentDescription = null,
                                tint = when {
                                    isSelected -> MaterialTheme.colorScheme.secondary
                                    option.isRandom -> GoldTrophy
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (voteCount > 0 || isLeading) {
                                Surface(
                                    color = if (voteCount > 0) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                                    } else {
                                        GoldTrophy.copy(alpha = 0.18f)
                                    },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (voteCount > 0) {
                                            "$voteCount ${if (voteCount == 1) "VOTE" else "VOTES"}"
                                        } else {
                                            "DEFAULT"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (voteCount > 0) MaterialTheme.colorScheme.secondary else GoldTrophy,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
