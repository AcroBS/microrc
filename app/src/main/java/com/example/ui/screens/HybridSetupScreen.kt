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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@Composable
fun HybridSetupScreen(
    initialRules: RaceRulesSettings,
    inActiveLobby: Boolean = false,
    isSoloMode: Boolean = false,
    onHostHybridWifi: (RaceRulesSettings) -> Unit,
    onStartHybridSolo: (RaceRulesSettings) -> Unit,
    onApplyToLobby: (RaceRulesSettings) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var draft by remember(initialRules) {
        mutableStateOf(initialRules.copy(gameMode = GameMode.HYBRID.name))
    }

    val enabledCount = listOf(
        draft.hybridIncludeCircuit,
        draft.hybridIncludeArena,
        draft.hybridIncludeStunts
    ).count { it }

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
            // Top Bar
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("hybrid_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hybrid Event Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Pick the game types included in your multi-stage RC tour",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Included Game Types Card
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
                        Text(
                            text = "INCLUDED GAME TYPES",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        HybridTypeToggleRow(
                            icon = Icons.Default.SportsScore,
                            title = "Circuit Race",
                            subtitle = "Closed RC circuits with power-ups, electro shock & Lap 2+ mines (multiplayer or bots)",
                            checked = draft.hybridIncludeCircuit,
                            testTag = "hybrid_toggle_circuit",
                            onToggle = { next ->
                                if (next || enabledCount > 1) {
                                    draft = draft.copy(hybridIncludeCircuit = next)
                                }
                            }
                        )

                        HybridTypeToggleRow(
                            icon = Icons.Default.Flag,
                            title = "Arena Hold-the-Flag",
                            subtitle = "Open battle arena — steal & carry the flag to bank time",
                            checked = draft.hybridIncludeArena,
                            testTag = "hybrid_toggle_arena",
                            onToggle = { next ->
                                if (next || enabledCount > 1) {
                                    draft = draft.copy(hybridIncludeArena = next)
                                }
                            }
                        )

                        HybridTypeToggleRow(
                            icon = Icons.Default.FlightTakeoff,
                            title = "Stunts Gauntlet",
                            subtitle = "Turn-based runway jumps: Long Jump, Goal Strike, Bowling & Golf",
                            checked = draft.hybridIncludeStunts,
                            testTag = "hybrid_toggle_stunts",
                            onToggle = { next ->
                                if (next || enabledCount > 1) {
                                    draft = draft.copy(hybridIncludeStunts = next)
                                }
                            }
                        )
                    }
                }

                // 2. Stage Progression Order Card
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
                        Text(
                            text = "STAGE ORDER",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OrderChoiceCard(
                                icon = Icons.Default.Autorenew,
                                title = "Rotation",
                                subtitle = "Circuit → Arena → Stunts",
                                selected = !draft.hybridRandomOrder,
                                testTag = "hybrid_order_rotation",
                                modifier = Modifier.weight(1f),
                                onClick = { draft = draft.copy(hybridRandomOrder = false) }
                            )
                            OrderChoiceCard(
                                icon = Icons.Default.Shuffle,
                                title = "Random Plays",
                                subtitle = "Surprise mode each stage",
                                selected = draft.hybridRandomOrder,
                                testTag = "hybrid_order_random",
                                modifier = Modifier.weight(1f),
                                onClick = { draft = draft.copy(hybridRandomOrder = true) }
                            )
                        }
                    }
                }

                // 3. General Stage Settings Card
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
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "GENERAL STAGE SETTINGS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (draft.hybridIncludeCircuit) {
                            Text(
                                text = "Circuit Stage Laps",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3, 5).forEach { laps ->
                                    FilterChip(
                                        selected = draft.totalLaps == laps,
                                        onClick = { draft = draft.copy(totalLaps = laps) },
                                        label = { Text("$laps Laps") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("hybrid_laps_$laps")
                                    )
                                }
                            }
                        }

                        if (draft.hybridIncludeArena) {
                            Text(
                                text = "Arena Flag Target",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3).forEach { mins ->
                                    FilterChip(
                                        selected = draft.arenaTargetMinutes == mins,
                                        onClick = { draft = draft.copy(arenaTargetMinutes = mins) },
                                        label = { Text("$mins Min") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("hybrid_arena_min_$mins")
                                    )
                                }
                            }
                        }

                        if (draft.hybridIncludeStunts) {
                            Text(
                                text = "Stunts per Hybrid Stop",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = draft.hybridStuntRounds == 1,
                                    onClick = { draft = draft.copy(hybridStuntRounds = 1) },
                                    label = { Text("1 Random Stunt") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hybrid_stunt_rounds_1")
                                )
                                FilterChip(
                                    selected = draft.hybridStuntRounds == 4,
                                    onClick = { draft = draft.copy(hybridStuntRounds = 4) },
                                    label = { Text("All 4 Stunts") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hybrid_stunt_rounds_4")
                                )
                            }
                        }

                        Text(
                            text = "Car Speed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CarSpeedPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = draft.carSpeedEnum == preset,
                                    onClick = { draft = draft.copy(carSpeed = preset.name) },
                                    label = { Text(preset.displayName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hybrid_speed_${preset.name}")
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Launch Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (inActiveLobby) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { onApplyToLobby(draft.copy(gameMode = GameMode.HYBRID.name)) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("apply_hybrid_lobby_button")
                        ) {
                            Text(
                                text = if (isSoloMode) "Apply Hybrid Setup" else "Apply Hybrid Setup to Lobby",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else if (isSoloMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { onStartHybridSolo(draft.copy(gameMode = GameMode.HYBRID.name)) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_hybrid_solo_button")
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Solo Hybrid Tour", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onStartHybridSolo(draft.copy(gameMode = GameMode.HYBRID.name)) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("start_hybrid_solo_button")
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Solo vs Bots", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { onHostHybridWifi(draft.copy(gameMode = GameMode.HYBRID.name)) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(54.dp)
                                .testTag("start_hybrid_wifi_button")
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Host Wi-Fi Tour", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HybridTypeToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
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
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun OrderChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
