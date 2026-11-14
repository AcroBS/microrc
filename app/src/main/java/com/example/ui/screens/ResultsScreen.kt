package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.LobbyStatePayload
import com.example.model.RaceSnapshot
import com.example.model.TrackId
import com.example.network.SessionRole
import com.example.ui.theme.AcidLime
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.PaddockSurfaceElevated
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SilverMedal
import com.example.ui.theme.TextSecondary

@Composable
fun ResultsScreen(
    role: SessionRole,
    localPlayerId: String,
    results: RaceSnapshot?,
    lobby: LobbyStatePayload?,
    onReturnToLobby: () -> Unit,
    onExitHome: () -> Unit
) {
    val isHostOrSolo = role == SessionRole.Host || role == SessionRole.Solo
    BackHandler {
        if (isHostOrSolo) onReturnToLobby() else onExitHome()
    }

    val sortedRacers = results?.racers.orEmpty().sortedBy { it.rank }
    val leaderTime = sortedRacers.firstOrNull()?.finishTimeMs ?: 0L
    val overallBestLap = sortedRacers.mapNotNull { it.bestLapMs }.minOrNull()
    val trackName = runCatching {
        TrackId.valueOf(results?.trackId ?: TrackId.LIVING_ROOM_GP.name).displayName
    }.getOrDefault("Circuit")

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
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldTrophy, modifier = Modifier.size(28.dp))
                        }
                    }
                    Column {
                        Text("CHECKERED FLAG", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text(
                            text = "$trackName • ${results?.totalLaps ?: 3} Laps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                    }
                }

                // Race Standings Cards
                sortedRacers.forEach { racer ->
                    val medalColor = when (racer.rank) {
                        1 -> GoldTrophy
                        2 -> SilverMedal
                        3 -> BronzeMedal
                        else -> TextSecondary
                    }
                    val gapStr = if (racer.rank == 1 || racer.finishTimeMs == null) {
                        racer.finishTimeMs?.let { formatRaceTime(it) } ?: "--:--"
                    } else {
                        "+${String.format("%.2fs", ((racer.finishTimeMs - leaderTime).coerceAtLeast(0L)) / 1000f)}"
                    }
                    val isMe = racer.playerId == localPlayerId

                    Surface(
                        color = PaddockSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            if (isMe) 1.5.dp else 1.dp,
                            if (isMe) ElectricCyan else PaddockOutline
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
                                    color = Color.White
                                )
                                Text(
                                    text = "Best Lap: ${racer.bestLapMs?.let { formatRaceTime(it) } ?: "--"} • Hits: ${racer.powerUpHits}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (racer.bestLapMs != null && racer.bestLapMs == overallBestLap) AcidLime else TextSecondary
                                )
                            }
                            Text(
                                text = gapStr,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (racer.rank == 1) GoldTrophy else Color.White
                            )
                        }
                    }
                }

                // Lap Time Comparison Table
                val lapCount = results?.totalLaps ?: 3
                Card(
                    colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                    border = BorderStroke(1.dp, PaddockOutline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Text("LAP TIME COMPARISON", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("RACER", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(108.dp))
                                for (l in 1..lapCount) {
                                    Text("LAP $l", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(76.dp))
                                }
                                Text("BEST", style = MaterialTheme.typography.labelSmall, color = AcidLime, modifier = Modifier.width(78.dp))
                            }
                            HorizontalDivider(color = PaddockOutline)
                            sortedRacers.forEach { racer ->
                                Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = racer.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.width(108.dp)
                                    )
                                    for (idx in 0 until lapCount) {
                                        val lt = racer.lapTimesMs.getOrNull(idx)
                                        Text(
                                            text = lt?.let { formatRaceTime(it) } ?: "--",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (lt != null && lt == overallBestLap) AcidLime else TextSecondary,
                                            modifier = Modifier.width(76.dp)
                                        )
                                    }
                                    Text(
                                        text = racer.bestLapMs?.let { formatRaceTime(it) } ?: "--",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (racer.bestLapMs != null && racer.bestLapMs == overallBestLap) AcidLime else ElectricCyan,
                                        modifier = Modifier.width(78.dp)
                                    )
                                }
                                HorizontalDivider(color = PaddockSurfaceElevated)
                            }
                        }
                    }
                }

                // Session Score Leaderboard
                val sessionStandings = lobby?.players.orEmpty().sortedByDescending { it.sessionPoints }
                if (sessionStandings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                        border = BorderStroke(1.dp, PaddockOutline),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("CHAMPIONSHIP SCORE LEADERBOARD", style = MaterialTheme.typography.labelLarge, color = SignalOrange)
                            sessionStandings.forEachIndexed { idx, p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}.", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.width(26.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(p.primaryColorArgb))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text("${p.wins}W", style = MaterialTheme.typography.labelSmall, color = GoldTrophy, modifier = Modifier.padding(end = 10.dp))
                                    Text("${p.sessionPoints} PTS", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                }
                            }
                        }
                    }
                }

                if (!isHostOrSolo) {
                    Text(
                        text = "Stay here to automatically rejoin the paddock when the host hits Rematch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExitHome,
                        border = BorderStroke(1.dp, PaddockOutline),
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
                        Button(
                            onClick = onReturnToLobby,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalOrange, contentColor = CarbonBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.6f)
                                .height(52.dp)
                                .testTag("results_rematch_button")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back to Lobby (Rematch)", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
