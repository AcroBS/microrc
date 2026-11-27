package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.model.AppThemeMode
import com.example.model.AudioSettings
import com.example.model.CarSpeedPreset
import com.example.model.ControlButtonSize
import com.example.model.ControlMode
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.model.ThemeSettings
import com.example.model.WinCondition
import com.example.sensors.TiltSteeringController
import com.example.util.AppUpdateUiState
import com.example.util.GitHubReleaseInfo
import com.example.util.SessionNotificationHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    rules: RaceRulesSettings,
    profile: PlayerProfile = PlayerProfile(playerId = "local"),
    tiltController: TiltSteeringController? = null,
    audioSettings: AudioSettings = AudioSettings(),
    themeSettings: ThemeSettings = ThemeSettings(),
    dailyNotificationsEnabled: Boolean = true,
    canEditRules: Boolean,
    inSession: Boolean,
    hasBottomNav: Boolean = false,
    onUpdateRules: (RaceRulesSettings) -> Unit,
    onSaveControlSettings: (ControlMode, Float, Boolean, Float) -> Unit = { _, _, _, _ -> },
    onUpdateSwapControls: (Boolean) -> Unit = {},
    onUpdateControlButtonSize: (ControlButtonSize) -> Unit = {},
    onUpdateShowLiveLapDelta: (Boolean) -> Unit = {},
    onUpdateShowTrackMiniMap: (Boolean) -> Unit = {},
    onUpdateAudio: (AudioSettings) -> Unit = {},
    onUpdateThemeSettings: (ThemeSettings) -> Unit = {},
    onToggleDailyNotifications: (Boolean) -> Unit = {},
    appVersionName: String = BuildConfig.VERSION_NAME,
    appVersionCode: Int = BuildConfig.VERSION_CODE,
    updateState: AppUpdateUiState = AppUpdateUiState.Idle,
    isPlayStoreInstall: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onOpenUpdateUrl: (String) -> Unit = {},
    onStartInAppUpdate: ((GitHubReleaseInfo) -> Unit)? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleDailyNotifications(granted)
    }

    val topLevelInsets = if (hasBottomNav) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(topLevelInsets),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (hasBottomNav) 16.dp else 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!hasBottomNav) {
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
                }
                Text(
                    text = if (inSession) "Match Rules" else "Settings",
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
                if (!inSession) {
                    // 1. Appearance (Light / Dark / System + Material You Dynamic Accent)
                    Text(
                        text = "APPEARANCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppThemeMode.entries.forEachIndexed { index, modeOption ->
                                    val icon = when (modeOption) {
                                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                                        AppThemeMode.DARK -> Icons.Default.DarkMode
                                    }
                                    SegmentedButton(
                                        selected = themeSettings.mode == modeOption,
                                        onClick = {
                                            onUpdateThemeSettings(themeSettings.copy(mode = modeOption))
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = AppThemeMode.entries.size
                                        ),
                                        icon = {
                                            if (themeSettings.mode != modeOption) {
                                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                            } else {
                                                SegmentedButtonDefaults.Icon(active = true)
                                            }
                                        },
                                        modifier = Modifier.testTag("settings_theme_${modeOption.name}")
                                    ) {
                                        Text(
                                            text = modeOption.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                GroupedSwitchRow(
                                    icon = Icons.Default.Palette,
                                    accent = MaterialTheme.colorScheme.primary,
                                    title = "Material You Accent",
                                    subtitle = "Match your device wallpaper colors",
                                    checked = themeSettings.dynamicColor,
                                    enabled = true,
                                    switchTestTag = "setting_dynamic_color_switch",
                                    onCheckedChange = {
                                        onUpdateThemeSettings(themeSettings.copy(dynamicColor = it))
                                    }
                                )
                            }
                        }
                    }

                    // 2. Steering Controls (moved out of Garage & Driver Tag)
                    Text(
                        text = "STEERING CONTROLS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SteeringControlsSection(
                        profile = profile,
                        tiltController = tiltController,
                        onSaveControlSettings = onSaveControlSettings,
                        onUpdateSwapControls = onUpdateSwapControls,
                        onUpdateControlButtonSize = onUpdateControlButtonSize,
                        onUpdateShowLiveLapDelta = onUpdateShowLiveLapDelta,
                        onUpdateShowTrackMiniMap = onUpdateShowTrackMiniMap
                    )

                    // 3. Audio & Alerts (this device)
                    Text(
                        text = "AUDIO & ALERTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            GroupedSwitchRow(
                                icon = Icons.Default.MusicNote,
                                accent = MaterialTheme.colorScheme.primary,
                                title = "Music",
                                subtitle = "Menu ambience & race soundtracks",
                                checked = audioSettings.musicEnabled,
                                enabled = true,
                                switchTestTag = "setting_music_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(musicEnabled = it)) }
                            )
                            if (audioSettings.musicEnabled) {
                                AudioVolumeSliderRow(
                                    label = "Music Volume",
                                    value = audioSettings.musicVolume,
                                    accent = MaterialTheme.colorScheme.primary,
                                    sliderTestTag = "setting_music_volume_slider",
                                    onValueChange = { vol ->
                                        onUpdateAudio(audioSettings.copy(musicVolume = vol))
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            GroupedSwitchRow(
                                icon = Icons.Default.DirectionsCar,
                                accent = MaterialTheme.colorScheme.secondary,
                                title = "Car Motor SFX",
                                subtitle = "Speed-reactive RC motor audio",
                                checked = audioSettings.carSfxEnabled,
                                enabled = true,
                                switchTestTag = "setting_sfx_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(carSfxEnabled = it)) }
                            )
                            if (audioSettings.carSfxEnabled) {
                                AudioVolumeSliderRow(
                                    label = "Motor SFX Volume",
                                    value = audioSettings.sfxVolume,
                                    accent = MaterialTheme.colorScheme.secondary,
                                    sliderTestTag = "setting_sfx_volume_slider",
                                    onValueChange = { vol ->
                                        onUpdateAudio(audioSettings.copy(sfxVolume = vol))
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            GroupedSwitchRow(
                                icon = Icons.Default.Vibration,
                                accent = MaterialTheme.colorScheme.tertiary,
                                title = "Haptic Vibration",
                                subtitle = "Collisions, boosts & portal warps",
                                checked = audioSettings.hapticsEnabled,
                                enabled = true,
                                switchTestTag = "setting_haptics_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(hapticsEnabled = it)) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            GroupedSwitchRow(
                                icon = Icons.Default.NotificationsActive,
                                accent = MaterialTheme.colorScheme.secondary,
                                title = "Daily & Friend Alerts",
                                subtitle = "Daily GP drops & new friend records",
                                checked = dailyNotificationsEnabled,
                                enabled = true,
                                switchTestTag = "setting_daily_notifications_switch",
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !SessionNotificationHelper.hasNotificationPermission(context)
                                    ) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onToggleDailyNotifications(enabled)
                                    }
                                }
                            )
                        }
                    }

                    // 3. Whole-Game Defaults (PvP match setup lives in Host Setup / active lobby)
                    Text(
                        text = "GAMEPLAY DEFAULTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
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
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "General Car Speed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = rules.carSpeedEnum.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Applies to Solo Play & Custom Lobbies. Daily Challenge & Daily Drift use standardized spec speed for fair leaderboards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.testTag("settings_speed_helper_note")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CarSpeedPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = rules.carSpeedEnum == preset,
                                        enabled = true,
                                        onClick = { onUpdateRules(rules.copy(carSpeed = preset.name)) },
                                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_speed_${preset.name}")
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                            GroupedSwitchRow(
                                icon = Icons.Default.Badge,
                                accent = MaterialTheme.colorScheme.primary,
                                title = "Driver Name Tags",
                                subtitle = "Show callsigns beside cars",
                                checked = rules.showPlayerNames,
                                enabled = true,
                                switchTestTag = "setting_show_player_names_switch",
                                onCheckedChange = { onUpdateRules(rules.copy(showPlayerNames = it)) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onUpdateRules(RaceRulesSettings()) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_reset_button")
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Gameplay Defaults", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 4. About & Version Section
                    Text(
                        text = "ABOUT & UPDATES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AboutAndVersionSection(
                        appVersionName = appVersionName,
                        appVersionCode = appVersionCode,
                        dynamicAccentEnabled = themeSettings.dynamicColor,
                        updateState = updateState,
                        isPlayStoreInstall = isPlayStoreInstall,
                        onCheckForUpdates = onCheckForUpdates,
                        onOpenUpdateUrl = onOpenUpdateUrl,
                        onStartInAppUpdate = onStartInAppUpdate
                    )
                } else {
                    // In-session Match Rules & Local Audio (accessible from Lobby)
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
                                    text = if (canEditRules) "Active Lobby Rules" else "Host-Controlled Match Rules",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (canEditRules) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                                Text(
                                    text = if (canEditRules) {
                                        "Changes sync to everyone in this lobby."
                                    } else {
                                        "Only the host can edit match rules. Audio toggles below stay local."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canEditRules) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "MATCH MODE & RULES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Play Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            com.example.drift.DriftFeature.selectableGameModes().chunked(2).forEach { rowModes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowModes.forEach { mode ->
                                        FilterChip(
                                            selected = rules.gameModeEnum == mode,
                                            enabled = canEditRules,
                                            onClick = {
                                                val updated = if (mode == GameMode.DRIFT) {
                                                    rules.copy(
                                                        gameMode = mode.name,
                                                        winCondition = WinCondition.FIRST_TO_FINISH.name
                                                    )
                                                } else {
                                                    rules.copy(gameMode = mode.name)
                                                }
                                                onUpdateRules(updated)
                                            },
                                            label = { Text(mode.displayName, style = MaterialTheme.typography.labelMedium) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_mode_${mode.name}")
                                        )
                                    }
                                    if (rowModes.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            when (rules.gameModeEnum) {
                                GameMode.ARENA -> {
                                    Text(
                                        text = "Flag Hold Target: ${rules.arenaTargetMinutes} min",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(1, 2, 3, 5).forEach { mins ->
                                            FilterChip(
                                                selected = rules.arenaTargetMinutes == mins,
                                                enabled = canEditRules,
                                                onClick = { onUpdateRules(rules.copy(arenaTargetMinutes = mins)) },
                                                label = { Text("${mins}m", style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("settings_arena_min_$mins")
                                            )
                                        }
                                    }
                                }
                                GameMode.STUNTS -> {
                                    Text(
                                        text = "Turn-based 4-stunt gauntlet — 1x starting Nitro every turn.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                GameMode.HYBRID -> {
                                    val enabledCount = listOf(
                                        rules.hybridIncludeCircuit,
                                        rules.hybridIncludeArena,
                                        rules.hybridIncludeStunts
                                    ).count { it }
                                    Text(
                                        text = "Included Hybrid Event Types:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = rules.hybridIncludeCircuit,
                                            enabled = canEditRules,
                                            onClick = {
                                                if (!rules.hybridIncludeCircuit || enabledCount > 1) {
                                                    onUpdateRules(rules.copy(hybridIncludeCircuit = !rules.hybridIncludeCircuit))
                                                }
                                            },
                                            label = { Text("Circuit", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = rules.hybridIncludeArena,
                                            enabled = canEditRules,
                                            onClick = {
                                                if (!rules.hybridIncludeArena || enabledCount > 1) {
                                                    onUpdateRules(rules.copy(hybridIncludeArena = !rules.hybridIncludeArena))
                                                }
                                            },
                                            label = { Text("Arena", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = rules.hybridIncludeStunts,
                                            enabled = canEditRules,
                                            onClick = {
                                                if (!rules.hybridIncludeStunts || enabledCount > 1) {
                                                    onUpdateRules(rules.copy(hybridIncludeStunts = !rules.hybridIncludeStunts))
                                                }
                                            },
                                            label = { Text("Stunts", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                GameMode.DRIFT -> {
                                    Text(
                                        text = "Rear-loose drifting with corner obstacles. Time-based, 0 powerups, 1x Nitro per lap.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Number of Laps: ${rules.totalLaps}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(1, 2, 3, 5, 7).forEach { laps ->
                                            FilterChip(
                                                selected = rules.totalLaps == laps,
                                                enabled = canEditRules,
                                                onClick = { onUpdateRules(rules.copy(totalLaps = laps)) },
                                                label = { Text("${laps}L", style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                GameMode.RACE -> {
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
                                                            WinCondition.TIME_TRIAL_DISTANCE -> "Max Time"
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
                                            text = "Max Driving Time: ${rules.timeLimitSec}s",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(60, 90, 120, 180, 300).forEach { secs ->
                                                val label = if (secs >= 180 && secs % 60 == 0) "${secs / 60}m" else "${secs}s"
                                                FilterChip(
                                                    selected = rules.timeLimitSec == secs,
                                                    enabled = canEditRules,
                                                    onClick = { onUpdateRules(rules.copy(timeLimitSec = secs)) },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Number of Laps: ${rules.totalLaps}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1, 2, 3, 5, 7).forEach { laps ->
                                                FilterChip(
                                                    selected = rules.totalLaps == laps,
                                                    enabled = canEditRules,
                                                    onClick = { onUpdateRules(rules.copy(totalLaps = laps)) },
                                                    label = { Text("${laps}L", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "General Car Speed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Applies to Solo Play & Custom Lobbies. Daily Challenge & Daily Drift use standardized spec speed for fair leaderboards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("settings_session_speed_helper_note")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CarSpeedPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = rules.carSpeedEnum == preset,
                                        enabled = canEditRules,
                                        onClick = { onUpdateRules(rules.copy(carSpeed = preset.name)) },
                                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_speed_${preset.name}")
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                            GroupedSwitchRow(
                                icon = Icons.Default.Badge,
                                accent = MaterialTheme.colorScheme.primary,
                                title = "Driver Name Tags",
                                subtitle = "Show callsigns beside cars",
                                checked = rules.showPlayerNames,
                                enabled = canEditRules,
                                switchTestTag = "setting_show_player_names_switch",
                                onCheckedChange = { onUpdateRules(rules.copy(showPlayerNames = it)) }
                            )
                        }
                    }

                    Text(
                        text = "STEERING CONTROLS (THIS DEVICE)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SteeringControlsSection(
                        profile = profile,
                        tiltController = tiltController,
                        onSaveControlSettings = onSaveControlSettings,
                        onUpdateSwapControls = onUpdateSwapControls,
                        onUpdateControlButtonSize = onUpdateControlButtonSize,
                        onUpdateShowLiveLapDelta = onUpdateShowLiveLapDelta,
                        onUpdateShowTrackMiniMap = onUpdateShowTrackMiniMap
                    )

                    Text(
                        text = "AUDIO & HAPTICS (THIS DEVICE)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            GroupedSwitchRow(
                                icon = Icons.Default.MusicNote,
                                accent = MaterialTheme.colorScheme.primary,
                                title = "Music",
                                subtitle = "Menu ambience & race soundtracks",
                                checked = audioSettings.musicEnabled,
                                enabled = true,
                                switchTestTag = "setting_music_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(musicEnabled = it)) }
                            )
                            if (audioSettings.musicEnabled) {
                                AudioVolumeSliderRow(
                                    label = "Music Volume",
                                    value = audioSettings.musicVolume,
                                    accent = MaterialTheme.colorScheme.primary,
                                    sliderTestTag = "setting_music_volume_slider",
                                    onValueChange = { vol ->
                                        onUpdateAudio(audioSettings.copy(musicVolume = vol))
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            GroupedSwitchRow(
                                icon = Icons.Default.DirectionsCar,
                                accent = MaterialTheme.colorScheme.secondary,
                                title = "Car Motor SFX",
                                subtitle = "Speed-reactive RC motor audio",
                                checked = audioSettings.carSfxEnabled,
                                enabled = true,
                                switchTestTag = "setting_sfx_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(carSfxEnabled = it)) }
                            )
                            if (audioSettings.carSfxEnabled) {
                                AudioVolumeSliderRow(
                                    label = "Motor SFX Volume",
                                    value = audioSettings.sfxVolume,
                                    accent = MaterialTheme.colorScheme.secondary,
                                    sliderTestTag = "setting_sfx_volume_slider",
                                    onValueChange = { vol ->
                                        onUpdateAudio(audioSettings.copy(sfxVolume = vol))
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            GroupedSwitchRow(
                                icon = Icons.Default.Vibration,
                                accent = MaterialTheme.colorScheme.tertiary,
                                title = "Haptic Vibration",
                                subtitle = "Collisions, boosts & portal warps",
                                checked = audioSettings.hapticsEnabled,
                                enabled = true,
                                switchTestTag = "setting_haptics_switch",
                                onCheckedChange = { onUpdateAudio(audioSettings.copy(hapticsEnabled = it)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteeringControlsSection(
    profile: PlayerProfile,
    tiltController: TiltSteeringController?,
    onSaveControlSettings: (ControlMode, Float, Boolean, Float) -> Unit,
    onUpdateSwapControls: (Boolean) -> Unit,
    onUpdateControlButtonSize: (ControlButtonSize) -> Unit,
    onUpdateShowLiveLapDelta: (Boolean) -> Unit,
    onUpdateShowTrackMiniMap: (Boolean) -> Unit
) {
    var selectedControlMode by remember(profile.controlMode) { mutableStateOf(profile.controlMode) }
    var tiltSensitivity by remember(profile.tiltSensitivity) { mutableFloatStateOf(profile.tiltSensitivity) }
    var sliderSensitivity by remember(profile.sliderSensitivity) { mutableFloatStateOf(profile.sliderSensitivity) }
    var previewSliderSteer by remember { mutableFloatStateOf(0f) }

    val view = LocalView.current
    val configuration = LocalConfiguration.current
    LaunchedEffect(view, configuration.orientation, selectedControlMode, tiltController) {
        tiltController?.updateDisplayRotation(view.display?.rotation)
    }

    DisposableEffect(selectedControlMode, tiltController) {
        if (tiltController != null && selectedControlMode == ControlMode.TILT_SENSOR) {
            tiltController.updateDisplayRotation(view.display?.rotation)
            tiltController.setSensitivity(tiltSensitivity)
            tiltController.start()
        } else {
            tiltController?.stop()
        }
        onDispose { tiltController?.stop() }
    }

    val liveSteer = if (tiltController != null) {
        tiltController.steerValue.collectAsState().value
    } else {
        0f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_steering_controls_section")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ControlMode.entries.forEach { mode ->
                val active = selectedControlMode == mode
                Surface(
                    color = if (active) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            selectedControlMode = mode
                            if (mode != ControlMode.SPRING_SLIDER) previewSliderSteer = 0f
                            onSaveControlSettings(mode, tiltSensitivity, true, sliderSensitivity)
                        }
                        .testTag("control_mode_${mode.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ControlMode.TILT_SENSOR -> Icons.Default.PhoneAndroid
                                ControlMode.SPRING_SLIDER -> Icons.Default.SwapHoriz
                                ControlMode.TOUCH_OVERLAY -> Icons.Default.TouchApp
                            },
                            contentDescription = mode.label,
                            tint = if (active) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (active) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (selectedControlMode == ControlMode.SPRING_SLIDER) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Spring Slider Preview", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Steer: ${(previewSliderSteer * 100f).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Slide proportionally with RC expo OR tap/hold the overlaid left/right arrows for instant full lock. Lift to snap to neutral.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpringSteeringSlider(
                        onSteerChange = { previewSliderSteer = it },
                        sensitivity = sliderSensitivity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .testTag("settings_spring_slider_preview")
                    )
                    Text(
                        text = "Slider Sensitivity: ${String.format(Locale.US, "%.2fx", sliderSensitivity)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = sliderSensitivity,
                        onValueChange = {
                            sliderSensitivity = it
                            onSaveControlSettings(selectedControlMode, tiltSensitivity, true, it)
                        },
                        valueRange = 0.7f..2.6f,
                        modifier = Modifier.testTag("settings_slider_sensitivity")
                    )
                }
            }

            if (selectedControlMode == ControlMode.TILT_SENSOR) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Tilt Preview", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = { tiltController?.calibrateNeutral() },
                            enabled = tiltController != null,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_tilt_calibrate_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zero Tilt")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        val fraction = ((liveSteer + 1f) / 2f).coerceIn(0.04f, 0.96f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(18.dp, 14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                    Text(
                        text = "Tilt Sensitivity: ${String.format(Locale.US, "%.2fx", tiltSensitivity)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = tiltSensitivity,
                        onValueChange = {
                            tiltSensitivity = it
                            tiltController?.setSensitivity(it)
                            onSaveControlSettings(selectedControlMode, it, true, sliderSensitivity)
                        },
                        valueRange = 0.6f..2.2f,
                        modifier = Modifier.testTag("settings_tilt_sensitivity")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Control Button Size",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Adjust height of <, >, and BRAKE buttons while keeping the bottom edge dead-zone-free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ControlButtonSize.entries.forEachIndexed { index, option ->
                        val selected = profile.controlButtonSize == option
                        SegmentedButton(
                            selected = selected,
                            onClick = { onUpdateControlButtonSize(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ControlButtonSize.entries.size
                            ),
                            icon = {},
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("setting_control_size_${option.name}")
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            GroupedSwitchRow(
                icon = Icons.Default.SwapHoriz,
                accent = MaterialTheme.colorScheme.primary,
                title = "Left-Handed / Swap Controls",
                subtitle = "Swap steering & throttle/brake sides",
                checked = profile.swapControls,
                enabled = true,
                switchTestTag = "setting_swap_controls_switch",
                onCheckedChange = onUpdateSwapControls
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            GroupedSwitchRow(
                icon = Icons.Default.Timer,
                accent = MaterialTheme.colorScheme.secondary,
                title = "Show Live Lap Delta (+/- vs PB)",
                subtitle = "Real-time split pace against personal best",
                checked = profile.showLiveLapDelta,
                enabled = true,
                switchTestTag = "setting_live_lap_delta_switch",
                onCheckedChange = onUpdateShowLiveLapDelta
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            GroupedSwitchRow(
                icon = Icons.Default.Map,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "Show Track Mini-Map",
                subtitle = "Corner radar overlay during races",
                checked = profile.showTrackMiniMap,
                enabled = true,
                switchTestTag = "setting_show_minimap_switch",
                onCheckedChange = onUpdateShowTrackMiniMap
            )
        }
    }
}

@Composable
private fun AudioVolumeSliderRow(
    label: String,
    value: Float,
    accent: Color,
    sliderTestTag: String,
    onValueChange: (Float) -> Unit
) {
    val clamped = value.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(clamped * 100f).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Slider(
            value = clamped,
            onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(sliderTestTag)
        )
    }
}

@Composable
private fun GroupedSwitchRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    switchTestTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@Composable
private fun AboutAndVersionSection(
    appVersionName: String,
    appVersionCode: Int,
    dynamicAccentEnabled: Boolean,
    updateState: AppUpdateUiState,
    isPlayStoreInstall: Boolean,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateUrl: (String) -> Unit,
    onStartInAppUpdate: ((GitHubReleaseInfo) -> Unit)? = null
) {
    val hasUpdate = updateState is AppUpdateUiState.UpdateAvailable
    val dynamicThemeLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicAccentEnabled) {
        "Material You Dynamic"
    } else {
        "MicroRC Signature M3"
    }
    val channelLabel = if (isPlayStoreInstall) "Google Play" else "GitHub Release APK"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_about_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MicroRC v$appVersionName",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$channelLabel • Build $appVersionCode • $dynamicThemeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasUpdate) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ),
            border = BorderStroke(
                width = if (hasUpdate) 1.5.dp else 1.dp,
                color = if (hasUpdate) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                }
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_app_update_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (updateState is AppUpdateUiState.UpToDate) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.SystemUpdate
                        },
                        contentDescription = null,
                        tint = if (hasUpdate) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Updates",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (hasUpdate) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        val subtitleText = when {
                            isPlayStoreInstall -> "Updates managed via Google Play."
                            updateState is AppUpdateUiState.Checking -> "Checking latest GitHub release..."
                            updateState is AppUpdateUiState.UpdateAvailable ->
                                "New version ${updateState.release.tagName} available on GitHub."
                            updateState is AppUpdateUiState.UpToDate ->
                                "Up to date (v${updateState.latestVersion})."
                            updateState is AppUpdateUiState.Unavailable -> updateState.message
                            else -> "Check GitHub Releases for newer builds."
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasUpdate) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (!isPlayStoreInstall) {
                    when (updateState) {
                        is AppUpdateUiState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Contacting GitHub...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is AppUpdateUiState.UpdateAvailable -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (onStartInAppUpdate != null) {
                                            onStartInAppUpdate(updateState.release)
                                        } else {
                                            onOpenUpdateUrl(updateState.release.preferredUpdateUrl)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_update_download_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (onStartInAppUpdate != null) "Update Now" else "Download APK",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                if (updateState.release.apkDownloadUrl != null) {
                                    OutlinedButton(
                                        onClick = { onOpenUpdateUrl(updateState.release.releasePageUrl) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("settings_update_release_page_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "View Release Page", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = onCheckForUpdates,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_check_updates_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check for Updates", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
