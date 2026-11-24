package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CarSpeedPreset
import com.example.model.GameMode
import com.example.model.RaceRulesSettings
import com.example.model.WinCondition

@Composable
fun HostSetupScreen(
    initialRules: RaceRulesSettings,
    onCreateLobby: (RaceRulesSettings) -> Unit,
    onOpenHybridSetup: (RaceRulesSettings) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var draft by remember(initialRules) { mutableStateOf(initialRules) }
    val selectedMode = draft.gameModeEnum
    val selectedWin = draft.winConditionEnum

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("host_setup_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Host Match Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Configure mode & rules before opening lobby",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Play Mode Selector Card (2x2 Grid: Circuit Race, Arena, Stunts, Hybrid Tour)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(22.dp),
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
                                when (selectedMode) {
                                    GameMode.ARENA -> Icons.Default.Flag
                                    GameMode.STUNTS -> Icons.Default.FlightTakeoff
                                    GameMode.HYBRID -> Icons.Default.Shuffle
                                    GameMode.DRIFT -> Icons.Default.Speed
                                    GameMode.RACE -> Icons.Default.SportsScore
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Play Mode",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        com.example.drift.DriftFeature.selectableGameModes().chunked(2).forEach { modePair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                modePair.forEach { mode ->
                                    val isSelected = selectedMode == mode
                                    val modeIcon = when (mode) {
                                        GameMode.ARENA -> Icons.Default.Flag
                                        GameMode.STUNTS -> Icons.Default.FlightTakeoff
                                        GameMode.HYBRID -> Icons.Default.Shuffle
                                        GameMode.DRIFT -> Icons.Default.Speed
                                        GameMode.RACE -> Icons.Default.SportsScore
                                    }
                                    Surface(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        border = if (isSelected) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                draft = if (mode == GameMode.DRIFT) {
                                                    draft.copy(
                                                        gameMode = mode.name,
                                                        winCondition = WinCondition.FIRST_TO_FINISH.name,
                                                        randomJumpsEnabled = false
                                                    )
                                                } else {
                                                    draft.copy(gameMode = mode.name)
                                                }
                                            }
                                            .testTag("mode_option_${mode.name}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = modeIcon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = mode.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                            Text(
                                                text = mode.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
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
                }

                when (selectedMode) {
                    GameMode.STUNTS -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FlightTakeoff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "4-Stunt Turn-Based Gauntlet",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Drivers take turns one after another while everyone else spectates live! Each turn starts with 1x Turbo Nitro on the slalom boost runway before launching into all 4 stunts in random order: Mega Long Jump, Rocket Goal Strike, 9-Pin Bowling & RC Golf.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    GameMode.HYBRID -> {
                        val enabledCount = listOf(
                            draft.hybridIncludeCircuit,
                            draft.hybridIncludeArena,
                            draft.hybridIncludeStunts
                        ).count { it }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(22.dp),
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
                                        Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Hybrid Event Game Types",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "All players rotate through your chosen game types stage after stage — e.g. Circuit map, then Arena battle, then Stunt event!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = draft.hybridIncludeCircuit,
                                        onClick = {
                                            if (!draft.hybridIncludeCircuit || enabledCount > 1) {
                                                draft = draft.copy(hybridIncludeCircuit = !draft.hybridIncludeCircuit)
                                            }
                                        },
                                        label = { Text("Circuit") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = draft.hybridIncludeArena,
                                        onClick = {
                                            if (!draft.hybridIncludeArena || enabledCount > 1) {
                                                draft = draft.copy(hybridIncludeArena = !draft.hybridIncludeArena)
                                            }
                                        },
                                        label = { Text("Arena") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = draft.hybridIncludeStunts,
                                        onClick = {
                                            if (!draft.hybridIncludeStunts || enabledCount > 1) {
                                                draft = draft.copy(hybridIncludeStunts = !draft.hybridIncludeStunts)
                                            }
                                        },
                                        label = { Text("Stunts") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onOpenHybridSetup(draft) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_hybrid_setup_button")
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Hybrid Event Setup")
                                }
                            }
                        }
                    }
                    GameMode.ARENA -> {
                    // Arena Winning Condition: Flag Hold Duration in Minutes
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(22.dp),
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
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Flag Carry Winning Target",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Drive over the arena flag to start your hold timer. Spinning out on puddles or oil drops the flag, and car collisions steal it! First driver to carry the flag for ${draft.arenaTargetMinutes} min wins.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3, 5).forEach { mins ->
                                    val active = draft.arenaTargetMinutes == mins
                                    FilterChip(
                                        selected = active,
                                        onClick = { draft = draft.copy(arenaTargetMinutes = mins) },
                                        label = { Text("$mins Min") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("arena_minutes_$mins")
                                    )
                                }
                            }
                        }
                    }
                    }
                    GameMode.DRIFT -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(22.dp),
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
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Time-Based Drift Race",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Rear-end oversteer lets you kick the tail out into turns and drift cleanly around solid corner obstacles. No powerups — 1x Turbo Nitro per lap!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 2, 3, 5, 7).forEach { laps ->
                                    val active = draft.totalLaps == laps
                                    FilterChip(
                                        selected = active,
                                        onClick = { draft = draft.copy(totalLaps = laps) },
                                        label = { Text("${laps}L") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("setup_drift_laps_${laps}")
                                    )
                                }
                            }
                        }
                    }
                    }
                    GameMode.RACE -> {
                    // Circuit Race Winning Condition Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(22.dp),
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
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Winning Condition",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            WinCondition.entries.forEach { cond ->
                                val isSelected = selectedWin == cond
                                Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            draft = draft.copy(winCondition = cond.name)
                                        }
                                        .testTag("win_condition_${cond.name}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = cond.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = cond.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Race Length: Number of Laps OR Max Driving Time
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(22.dp),
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
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Laps or Max Driving Time",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isTimeMode = selectedWin == WinCondition.TIME_TRIAL_DISTANCE
                                FilterChip(
                                    selected = !isTimeMode,
                                    onClick = {
                                        if (isTimeMode) {
                                            draft = draft.copy(winCondition = WinCondition.FIRST_TO_FINISH.name)
                                        }
                                    },
                                    label = { Text("Number of Laps") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_length_mode_laps")
                                )
                                FilterChip(
                                    selected = isTimeMode,
                                    onClick = {
                                        draft = draft.copy(winCondition = WinCondition.TIME_TRIAL_DISTANCE.name)
                                    },
                                    label = { Text("Max Driving Time") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_length_mode_time")
                                )
                            }

                            if (selectedWin == WinCondition.TIME_TRIAL_DISTANCE) {
                                Text(
                                    text = "Race ends when the driving timer expires — furthest total distance wins.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(60, 90, 120, 180, 300).forEach { secs ->
                                        val active = draft.timeLimitSec == secs
                                        val label = if (secs >= 180 && secs % 60 == 0) "${secs / 60}m" else "${secs}s"
                                        FilterChip(
                                            selected = active,
                                            onClick = { draft = draft.copy(timeLimitSec = secs) },
                                            label = { Text(label) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("setup_time_${secs}")
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "1 flashing Mine activates per driver from Lap 2.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1, 2, 3, 5, 7).forEach { laps ->
                                        val active = draft.totalLaps == laps
                                        FilterChip(
                                            selected = active,
                                            onClick = { draft = draft.copy(totalLaps = laps) },
                                            label = { Text("${laps}L") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("setup_laps_${laps}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // General Car Speed Selector Card (Low, Medium, Fast — default Fast)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(22.dp),
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
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "General Car Speed",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = draft.carSpeedEnum.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CarSpeedPreset.entries.forEach { preset ->
                                val active = draft.carSpeedEnum == preset
                                FilterChip(
                                    selected = active,
                                    onClick = { draft = draft.copy(carSpeed = preset.name) },
                                    label = { Text(preset.displayName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_speed_${preset.name}")
                                )
                            }
                        }
                    }
                }

                // Ride Conditions & Core Rules
                SetupSwitchCard(
                    icon = Icons.Default.Thunderstorm,
                    title = "Random Ride Conditions",
                    description = "Temporary weather & quakes appear: rain puddles, screen-shaking tremors, leaf-blowing crosswinds & icy snow skids.",
                    checked = draft.randomConditionsEnabled,
                    testTag = "setup_random_conditions_switch",
                    onCheckedChange = { draft = draft.copy(randomConditionsEnabled = it) }
                )

                if (selectedMode != GameMode.DRIFT && selectedMode != GameMode.STUNTS) {
                    SetupSwitchCard(
                        icon = Icons.Default.FlightTakeoff,
                        title = "Random Jumps",
                        description = "Spawn launch ramps on the circuit or arena floor for mid-air action.",
                        checked = draft.randomJumpsEnabled,
                        testTag = "setup_random_jumps_switch",
                        onCheckedChange = { draft = draft.copy(randomJumpsEnabled = it) }
                    )
                }

                SetupSwitchCard(
                    icon = Icons.Default.Badge,
                    title = "Player Names Next to Car",
                    description = "Show driver name tags floating beside each RC car during the match for all players.",
                    checked = draft.showPlayerNames,
                    testTag = "setup_show_player_names_switch",
                    onCheckedChange = { draft = draft.copy(showPlayerNames = it) }
                )
            }

            // Bottom Launch Lobby CTA
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
                    Button(
                        onClick = {
                            val finalRules = if (selectedMode == GameMode.DRIFT) {
                                draft.copy(
                                    winCondition = WinCondition.FIRST_TO_FINISH.name,
                                    randomJumpsEnabled = false
                                )
                            } else {
                                draft
                            }
                            onCreateLobby(finalRules)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("host_setup_open_lobby_button")
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedMode) {
                                GameMode.ARENA -> "Open Arena Wi-Fi Lobby"
                                GameMode.STUNTS -> "Open Stunts Wi-Fi Lobby"
                                GameMode.HYBRID -> "Open Hybrid Tour Lobby"
                                GameMode.DRIFT -> "Open Drift Wi-Fi Lobby"
                                GameMode.RACE -> "Open Wi-Fi Lobby"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
