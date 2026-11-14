package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.TrackCatalog
import com.example.model.LobbyStatePayload
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.network.SessionRole
import com.example.ui.components.drawRcCar
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.theme.AcidLime
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.PaddockSurfaceElevated
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextSecondary

@Composable
fun LobbyScreen(
    role: SessionRole,
    localPlayerId: String,
    lobby: LobbyStatePayload?,
    onSelectTrack: (TrackId) -> Unit,
    onSelectLaps: (Int) -> Unit,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onToggleReady: () -> Unit,
    onSendChat: (String) -> Unit,
    onStartRace: () -> Unit,
    onOpenGarage: () -> Unit,
    onLeaveLobby: () -> Unit
) {
    BackHandler { onLeaveLobby() }

    val isHostOrSolo = role == SessionRole.Host || role == SessionRole.Solo
    var chatDraft by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    val chatHistory = lobby?.chatHistory.orEmpty()
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            chatListState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLeaveLobby,
                    modifier = Modifier.testTag("lobby_leave_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave Lobby", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lobby?.lobbyName ?: "Race Paddock",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (role != SessionRole.Solo && lobby != null) {
                        Text(
                            text = "Host IP: ${lobby.hostIp}:${lobby.tcpPort}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    } else {
                        Text(
                            text = "Offline Practice vs AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = AcidLime
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenGarage,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("lobby_garage_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Car", color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 5-Track Selector
                val selectedTrack = runCatching {
                    TrackId.valueOf(lobby?.trackId ?: TrackId.LIVING_ROOM_GP.name)
                }.getOrDefault(TrackId.LIVING_ROOM_GP)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHostOrSolo) "SELECT TRACK (5)" else "SELECTED TRACK",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    // Lap Selector
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 5).forEach { laps ->
                            val active = (lobby?.totalLaps ?: 3) == laps
                            FilterChip(
                                selected = active,
                                onClick = { if (isHostOrSolo) onSelectLaps(laps) },
                                enabled = isHostOrSolo,
                                label = { Text("${laps}L", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SignalOrange,
                                    selectedLabelColor = CarbonBg
                                )
                            )
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(TrackCatalog.allTracks(), key = { it.id.name }) { track ->
                        val active = track.id == selectedTrack
                        Surface(
                            color = if (active) Color(track.id.roadColorHex).copy(alpha = 0.7f) else PaddockSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) Color(track.id.kerbPrimaryHex) else PaddockOutline
                            ),
                            modifier = Modifier
                                .width(156.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = isHostOrSolo) { onSelectTrack(track.id) }
                                .testTag("track_card_${track.id.name}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(66.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(track.id.baseBgColorHex).copy(alpha = 0.85f))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawTrackMiniMap(track)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = track.id.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.id.surfaceLabel} • ${track.id.difficulty}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) ElectricCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Players Roster (2-4 players)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = lobby?.players?.size ?: 0
                    Text("RACERS ($count/4)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    if (isHostOrSolo) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hasBot = lobby?.players?.any { it.isBot } == true
                            if (hasBot) {
                                OutlinedButton(
                                    onClick = onRemoveBot,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bot", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (count < 4) {
                                OutlinedButton(
                                    onClick = onAddBot,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("add_bot_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Bot", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lobby?.players.orEmpty().forEach { player ->
                        val car = runCatching { RcCarType.valueOf(player.carType) }.getOrDefault(RcCarType.VOLT_BUGGY)
                        Surface(
                            color = PaddockSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (player.id == localPlayerId) ElectricCyan.copy(alpha = 0.6f) else PaddockOutline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PaddockSurfaceElevated)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRcCar(
                                            x = size.width / 2f,
                                            y = size.height / 2f,
                                            angleRad = -0.6f,
                                            carType = car,
                                            primaryColor = Color(player.primaryColorArgb),
                                            accentColor = Color(player.accentColorArgb),
                                            scale = 0.78f
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        if (player.isHost) {
                                            Icon(Icons.Default.Star, contentDescription = "Host", tint = GoldTrophy, modifier = Modifier.size(15.dp))
                                        }
                                        if (player.isBot) {
                                            Icon(Icons.Default.SmartToy, contentDescription = "AI Bot", tint = ElectricCyan, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Text(
                                        text = "${car.displayName} • ${player.sessionPoints} pts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Surface(
                                    color = if (player.isReady) AcidLime.copy(alpha = 0.16f) else PaddockSurfaceElevated,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (player.isReady) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AcidLime, modifier = Modifier.size(14.dp))
                                        }
                                        Text(
                                            text = if (player.isHost) "HOST" else if (player.isReady) "READY" else "WAITING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (player.isReady) AcidLime else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Lobby Chat Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PaddockSurface),
                    border = BorderStroke(1.dp, PaddockOutline),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("PADDOCK CHAT", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PaddockSurfaceElevated)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(chatHistory, key = { it.id }) { msg ->
                                if (msg.isSystem) {
                                    Text(
                                        text = "• ${msg.text}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                } else {
                                    Row {
                                        Text(
                                            text = "${msg.senderName}: ",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SignalOrange
                                        )
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Quick chat chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val quickPhrases = listOf("Ready!", "Good luck!", "Rematch!", "Wait up!")
                            items(quickPhrases) { phrase ->
                                Surface(
                                    color = PaddockSurfaceElevated,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, PaddockOutline),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onSendChat(phrase) }
                                ) {
                                    Text(
                                        text = phrase,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatDraft,
                                onValueChange = { if (it.length <= 120) chatDraft = it },
                                placeholder = { Text("Message lobby...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input")
                            )
                            IconButton(
                                onClick = {
                                    onSendChat(chatDraft)
                                    chatDraft = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Chat", tint = CarbonBg)
                            }
                        }
                    }
                }
            }

            // Bottom Start / Ready Bar
            Surface(
                color = PaddockSurface,
                border = BorderStroke(1.dp, PaddockOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isHostOrSolo) {
                        val playerCount = lobby?.players?.size ?: 1
                        Button(
                            onClick = onStartRace,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalOrange,
                                contentColor = CarbonBg
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_race_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (playerCount < 2) "START SOLO RACE (OR ADD BOT)" else "START RACE ($playerCount RACERS)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        val meReady = lobby?.players?.find { it.id == localPlayerId }?.isReady == true
                        Button(
                            onClick = onToggleReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (meReady) AcidLime else ElectricCyan,
                                contentColor = CarbonBg
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("ready_toggle_button")
                        ) {
                            Text(
                                text = if (meReady) "READY! WAITING FOR HOST" else "TAP WHEN READY",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
