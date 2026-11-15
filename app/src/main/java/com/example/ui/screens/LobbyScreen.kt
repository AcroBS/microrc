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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.example.model.ArenaFlagState
import com.example.model.ArenaShape
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.LobbyStatePayload
import com.example.model.RaceRulesSettings
import com.example.model.RcCarType
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.SessionRole
import com.example.ui.components.drawRcCar
import com.example.ui.components.drawTrackMiniMap
import com.example.ui.theme.GoldTrophy

@Composable
fun LobbyScreen(
    role: SessionRole,
    localPlayerId: String,
    lobby: LobbyStatePayload?,
    customTracks: List<CustomTrackData>,
    onSelectTrack: (TrackId) -> Unit,
    onSelectArenaShape: (ArenaShape) -> Unit = {},
    onSelectCustomTrack: (CustomTrackData) -> Unit,
    onGenerateRandomTrack: () -> Unit,
    onSelectRandomArena: () -> Unit = {},
    onUpdateRules: (RaceRulesSettings) -> Unit = {},
    onOpenTrackEditor: () -> Unit,
    onOpenSettings: () -> Unit,
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

    val activeRules = lobby?.rules
    val isArenaMode = activeRules?.gameModeEnum == GameMode.ARENA

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave Lobby",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lobby?.lobbyName ?: "Race Paddock",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (role != SessionRole.Solo && lobby != null) {
                        Text(
                            text = "Host IP: ${lobby.hostIp}:${lobby.tcpPort}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = if (isArenaMode) "Offline Arena vs AI" else "Offline Practice vs AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lobby_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Match Rules & Audio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenGarage,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("lobby_garage_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Car", style = MaterialTheme.typography.labelMedium)
                    }
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
                // Active Match Rules Badges
                if (activeRules != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val label = when {
                                isArenaMode -> "ARENA • ${activeRules.arenaTargetMinutes}M FLAG"
                                activeRules.winConditionEnum == WinCondition.TIME_TRIAL_DISTANCE ->
                                    "${activeRules.timeLimitSec}s LIMIT"
                                else -> activeRules.winConditionEnum.displayName.uppercase()
                            }
                            LobbyRuleBadge(label, MaterialTheme.colorScheme.primary, onOpenSettings)
                        }
                        if (activeRules.autoAccelerate) {
                            item { LobbyRuleBadge("AUTO-GAS", MaterialTheme.colorScheme.tertiary, onOpenSettings) }
                        }
                        if (activeRules.hardTrackBoundary) {
                            item { LobbyRuleBadge("BOUNCE WALLS", MaterialTheme.colorScheme.primary, onOpenSettings) }
                        }
                        if (activeRules.randomJumpsEnabled) {
                            item { LobbyRuleBadge("JUMPS", MaterialTheme.colorScheme.secondary, onOpenSettings) }
                        }
                        if (activeRules.randomConditionsEnabled) {
                            item { LobbyRuleBadge("RANDOM WEATHER", MaterialTheme.colorScheme.secondary, onOpenSettings) }
                        }
                        if (activeRules.showPlayerNames) {
                            item { LobbyRuleBadge("NAME TAGS", MaterialTheme.colorScheme.tertiary, onOpenSettings) }
                        }
                    }
                }

                if (isArenaMode) {
                    // Arena Shape Selector + Flag Hold Minutes
                    val selectedArena = runCatching {
                        ArenaShape.valueOf(lobby?.arenaShape ?: ArenaShape.SQUARE.name)
                    }.getOrDefault(ArenaShape.SQUARE)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHostOrSolo) "SELECT ARENA SHAPE" else "SELECTED ARENA",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 5).forEach { mins ->
                                val active = (activeRules?.arenaTargetMinutes ?: 2) == mins
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        if (isHostOrSolo && activeRules != null) {
                                            onUpdateRules(activeRules.copy(arenaTargetMinutes = mins))
                                        }
                                    },
                                    enabled = isHostOrSolo,
                                    label = { Text("${mins}m", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("lobby_arena_min_$mins")
                                )
                            }
                        }
                    }

                    if (isHostOrSolo) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSelectRandomArena,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lobby_random_arena_button")
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Random Arena", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (activeRules != null) {
                                        onUpdateRules(activeRules.copy(gameMode = GameMode.RACE.name))
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lobby_switch_mode_button")
                            ) {
                                Icon(Icons.Default.SportsScore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Circuit Mode", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(TrackCatalog.allArenas(), key = { "arena_${it.arenaShape?.name}" }) { arenaTrack ->
                            val shape = arenaTrack.arenaShape ?: ArenaShape.SQUARE
                            val active = shape == selectedArena
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
                                modifier = Modifier
                                    .width(164.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = isHostOrSolo) { onSelectArenaShape(shape) }
                                    .testTag("arena_card_${shape.name}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(arenaTrack.id.baseBgColorHex).copy(alpha = 0.88f))
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawTrackMiniMap(
                                                track = arenaTrack,
                                                arenaFlag = ArenaFlagState(
                                                    x = arenaTrack.worldWidth * 0.5f,
                                                    y = arenaTrack.worldHeight * 0.5f,
                                                    carrierId = null
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = shape.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${shape.shapeLabel} • Jumps & Boosts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Circuit Track Selector + Laps
                    val activeCustom = lobby?.activeCustomTrack
                    val selectedTrack = runCatching {
                        TrackId.valueOf(lobby?.trackId ?: TrackId.LIVING_ROOM_GP.name)
                    }.getOrDefault(TrackId.LIVING_ROOM_GP)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHostOrSolo) "SELECT TRACK" else "SELECTED TRACK",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activeRules?.winConditionEnum == WinCondition.TIME_TRIAL_DISTANCE) {
                            Text(
                                text = "TIME: ${activeRules.timeLimitSec}s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 3, 5).forEach { laps ->
                                    val active = (lobby?.totalLaps ?: 3) == laps
                                    FilterChip(
                                        selected = active,
                                        onClick = { if (isHostOrSolo) onSelectLaps(laps) },
                                        enabled = isHostOrSolo,
                                        label = { Text("${laps}L", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    if (isHostOrSolo) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onGenerateRandomTrack,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lobby_random_track_button")
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Random", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onOpenTrackEditor,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lobby_track_editor_button")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editor", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    val base = activeRules ?: RaceRulesSettings()
                                    onUpdateRules(base.copy(gameMode = GameMode.ARENA.name))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("lobby_switch_mode_button")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Arena", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    val carouselCustomTracks = remember(customTracks, activeCustom) {
                        if (activeCustom != null && customTracks.none { it.id == activeCustom.id }) {
                            listOf(activeCustom) + customTracks
                        } else {
                            customTracks
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(carouselCustomTracks, key = { "custom_${it.id}" }) { cTrack ->
                            val previewDef = remember(cTrack) { TrackCatalog.buildCustomTrack(cTrack) }
                            val active = activeCustom?.id == cTrack.id
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
                                modifier = Modifier
                                    .width(156.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = isHostOrSolo) { onSelectCustomTrack(cTrack) }
                                    .testTag("custom_track_card_${cTrack.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(66.dp)
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
                                        text = "Custom • ${previewDef.ramps.size} Jumps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        items(TrackCatalog.allTracks(), key = { it.id.name }) { track ->
                            val active = activeCustom == null && track.id == selectedTrack
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
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.id.surfaceLabel} • ${track.id.difficulty}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
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
                    Text(
                        text = "RACERS ($count/4)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isHostOrSolo) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hasBot = lobby?.players?.any { it.isBot } == true
                            if (hasBot) {
                                OutlinedButton(
                                    onClick = onRemoveBot,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(36.dp)
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
                                        .height(36.dp)
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
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (player.id == localPlayerId) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
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
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                                        Text(
                                            text = player.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        if (player.isHost) {
                                            Icon(Icons.Default.Star, contentDescription = "Host", tint = GoldTrophy, modifier = Modifier.size(15.dp))
                                        }
                                        if (player.isBot) {
                                            Icon(Icons.Default.SmartToy, contentDescription = "AI Bot", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Text(
                                        text = "${car.displayName} • ${player.sessionPoints} pts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (player.isReady) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (player.isReady) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = if (player.isHost) "HOST" else if (player.isReady) "READY" else "WAITING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (player.isReady) {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Lobby Chat Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PADDOCK CHAT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(chatHistory, key = { it.id }) { msg ->
                                if (msg.isSystem) {
                                    Text(
                                        text = "• ${msg.text}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Row {
                                        Text(
                                            text = "${msg.senderName}: ",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val quickPhrases = listOf("Ready!", "Good luck!", "Watch the flag!", "Rematch!")
                            items(quickPhrases) { phrase ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onSendChat(phrase) }
                                ) {
                                    Text(
                                        text = phrase,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Chat",
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Start / Ready Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
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
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_race_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isArenaMode && playerCount < 2 -> "START SOLO ARENA (OR ADD BOT)"
                                    isArenaMode -> "START ARENA ($playerCount RACERS)"
                                    playerCount < 2 -> "START SOLO RACE (OR ADD BOT)"
                                    else -> "START RACE ($playerCount RACERS)"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        val meReady = lobby?.players?.find { it.id == localPlayerId }?.isReady == true
                        Button(
                            onClick = onToggleReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (meReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (meReady) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(16.dp),
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

@Composable
private fun LobbyRuleBadge(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
