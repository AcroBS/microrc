package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AudioSettings
import com.example.model.RaceRulesSettings
import com.example.model.WinCondition

@Composable
fun SettingsScreen(
    rules: RaceRulesSettings,
    audioSettings: AudioSettings = AudioSettings(),
    canEditRules: Boolean,
    inSession: Boolean,
    onUpdateRules: (RaceRulesSettings) -> Unit,
    onUpdateAudio: (AudioSettings) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

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
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Match Rules & Audio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = if (canEditRules) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (canEditRules) Icons.Default.Tune else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (canEditRules) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        Column {
                            Text(
                                text = when {
                                    !canEditRules -> "Host-Controlled Match Rules"
                                    inSession -> "Active Lobby & Solo Rules"
                                    else -> "Host & Solo Default Rules"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (canEditRules) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                            Text(
                                text = if (canEditRules) {
                                    "Changes apply to all players when you host or race solo."
                                } else {
                                    "Only the host can edit match rules. Audio toggles below stay local to your phone."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (canEditRules) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                }
                            )
                        }
                    }
                }

                // Local Audio Section
                Text(
                    text = "AUDIO (THIS DEVICE)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RuleToggleCard(
                    icon = Icons.Default.MusicNote,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "Hard Rock Guitar BGM",
                    description = "Play randomized high-energy electric guitar riffs & drums during races.",
                    checked = audioSettings.musicEnabled,
                    enabled = true,
                    switchTestTag = "setting_music_switch",
                    onCheckedChange = {
                        onUpdateAudio(audioSettings.copy(musicEnabled = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.DirectionsCar,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "RC Car Motor Sounds",
                    description = "Subtle speed-reactive electric RC motor sound per car on track.",
                    checked = audioSettings.carSfxEnabled,
                    enabled = true,
                    switchTestTag = "setting_sfx_switch",
                    onCheckedChange = {
                        onUpdateAudio(audioSettings.copy(carSfxEnabled = it))
                    }
                )

                // Match Rules Section
                Text(
                    text = "RACE RULES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Winning Condition",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WinCondition.entries.forEach { cond ->
                                val selected = rules.winConditionEnum == cond
                                FilterChip(
                                    selected = selected,
                                    enabled = canEditRules,
                                    onClick = { onUpdateRules(rules.copy(winCondition = cond.name)) },
                                    label = {
                                        Text(
                                            text = when (cond) {
                                                WinCondition.FIRST_TO_FINISH -> "Laps"
                                                WinCondition.TIME_TRIAL_DISTANCE -> "Timer"
                                                WinCondition.CHAMPIONSHIP_POINTS -> "Points"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (rules.winConditionEnum == WinCondition.TIME_TRIAL_DISTANCE) {
                            Text(
                                text = "Time Limit: ${rules.timeLimitSec}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(60, 90, 120, 180).forEach { secs ->
                                    FilterChip(
                                        selected = rules.timeLimitSec == secs,
                                        enabled = canEditRules,
                                        onClick = { onUpdateRules(rules.copy(timeLimitSec = secs)) },
                                        label = { Text("${secs}s", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                RuleToggleCard(
                    icon = Icons.Default.Speed,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "Automatic Acceleration",
                    description = "All cars accelerate automatically during the race. Touch controls switch to Brake/Reverse & Steering.",
                    checked = rules.autoAccelerate,
                    enabled = canEditRules,
                    switchTestTag = "setting_auto_accel_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(autoAccelerate = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.Shield,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "Hard Track Boundary",
                    description = "Solid kerb guardrails bounce cars back inside the track instead of sliding off-road.",
                    checked = rules.hardTrackBoundary,
                    enabled = canEditRules,
                    switchTestTag = "setting_hard_boundary_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(hardTrackBoundary = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.FlightTakeoff,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "Random Track Jumps",
                    description = "Spawn randomized launch ramps on tracks so RC cars catch air and clear obstacles.",
                    checked = rules.randomJumpsEnabled,
                    enabled = canEditRules,
                    switchTestTag = "setting_random_jumps_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(randomJumpsEnabled = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.Thunderstorm,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "Random Ride Conditions",
                    description = "Occasional short-lived rain puddles, earthquakes, blowing crosswinds & icy snow skids.",
                    checked = rules.randomConditionsEnabled,
                    enabled = canEditRules,
                    switchTestTag = "setting_random_conditions_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(randomConditionsEnabled = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.Badge,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "Player Names Next to Car",
                    description = "Display driver callsigns next to each RC car during races (host/server controlled).",
                    checked = rules.showPlayerNames,
                    enabled = canEditRules,
                    switchTestTag = "setting_show_player_names_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(showPlayerNames = it))
                    }
                )

                if (canEditRules) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onUpdateRules(RaceRulesSettings()) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_reset_button")
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Standard Defaults", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleToggleCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    switchTestTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = if (checked) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(switchTestTag)
            )
        }
    }
}
