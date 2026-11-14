package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.RaceRulesSettings
import com.example.ui.theme.AcidLime
import com.example.ui.theme.CarbonBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PaddockOutline
import com.example.ui.theme.PaddockSurface
import com.example.ui.theme.PaddockSurfaceElevated
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    rules: RaceRulesSettings,
    canEditRules: Boolean,
    inSession: Boolean,
    onUpdateRules: (RaceRulesSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                        tint = Color.White
                    )
                }
                Text("Match Rules & Settings", style = MaterialTheme.typography.headlineMedium)
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
                    color = if (canEditRules) ElectricCyan.copy(alpha = 0.12f) else SignalOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (canEditRules) ElectricCyan.copy(alpha = 0.45f) else SignalOrange.copy(alpha = 0.55f)
                    )
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
                            tint = if (canEditRules) ElectricCyan else SignalOrange
                        )
                        Column {
                            Text(
                                text = when {
                                    !canEditRules -> "Host-Controlled Match Rules"
                                    inSession -> "Active Lobby & Solo Rules"
                                    else -> "Host & Solo Default Rules"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = if (canEditRules) {
                                    "Changes apply equally to all players (or solo play) when you host or start a race."
                                } else {
                                    "Only the lobby host can edit these settings. Everyone races under the host's rules."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                RuleToggleCard(
                    icon = Icons.Default.Speed,
                    accent = AcidLime,
                    title = "Automatic Acceleration",
                    description = "All cars accelerate automatically during the race. Touch controls switch to Brake/Reverse & Steering only.",
                    checked = rules.autoAccelerate,
                    enabled = canEditRules,
                    switchTestTag = "setting_auto_accel_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(autoAccelerate = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.Shield,
                    accent = SignalOrange,
                    title = "Hard Track Boundary",
                    description = "Solid kerb guardrails keep every racer inside the circuit — cars bounce off the track edges instead of drifting off-road.",
                    checked = rules.hardTrackBoundary,
                    enabled = canEditRules,
                    switchTestTag = "setting_hard_boundary_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(hardTrackBoundary = it))
                    }
                )

                RuleToggleCard(
                    icon = Icons.Default.FlightTakeoff,
                    accent = ElectricCyan,
                    title = "Random Track Jumps",
                    description = "Spawn randomized launch ramps on tracks so RC cars catch air and soar over mud, glue slicks, and rivals.",
                    checked = rules.randomJumpsEnabled,
                    enabled = canEditRules,
                    switchTestTag = "setting_random_jumps_switch",
                    onCheckedChange = {
                        onUpdateRules(rules.copy(randomJumpsEnabled = it))
                    }
                )

                if (canEditRules) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onUpdateRules(RaceRulesSettings()) },
                        border = BorderStroke(1.dp, PaddockOutline),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_reset_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Standard Rules", color = TextSecondary)
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
        colors = CardDefaults.cardColors(containerColor = PaddockSurface),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) accent.copy(alpha = 0.7f) else PaddockOutline
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
                color = if (checked) accent.copy(alpha = 0.2f) else PaddockSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) accent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CarbonBg,
                    checkedTrackColor = accent
                ),
                modifier = Modifier.testTag(switchTestTag)
            )
        }
    }
}
