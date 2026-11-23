package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FriendBoardStandingItem
import com.example.data.FriendStandingsSummary
import com.example.data.PlayStreakState
import com.example.model.ControlMode
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.ui.components.drawRcCar
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveOnlineColor
import com.example.util.AppUpdateUiState
import com.example.util.GitHubReleaseInfo
import com.example.util.SessionNotificationHelper
import java.util.Locale
import kotlin.math.abs

private enum class HomeModeScope(val label: String) {
    ALL("All Modes"),
    OFFLINE("Offline"),
    ONLINE("Online")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: PlayerProfile,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onSoloRace: () -> Unit,
    onPureSoloRace: () -> Unit = {},
    onOpenDailyChallenge: () -> Unit = {},
    dailyChallengeTitle: String = "Daily GP",
    dailyChallengeSubtitle: String = "Fixed Portals • 1 Nitro/Lap • No Mines",
    dailyChallengeResetLabel: String = "",
    dailyBestTimeMs: Long? = null,
    onOpenOnlinePlay: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onlineFriendsCount: Int = 0,
    pendingRequestsCount: Int = 0,
    onOpenLeaderboards: () -> Unit = {},
    onOpenStandingsBoard: (trackKey: String, gameMode: GameMode, isDaily: Boolean) -> Unit = { _, _, _ -> },
    playStreak: PlayStreakState = PlayStreakState(),
    isSignedIn: Boolean = false,
    friendsCount: Int = 0,
    localRecordsCount: Int = 0,
    friendStandings: FriendStandingsSummary? = null,
    onOpenHybridSetup: () -> Unit = {},
    onOpenGarage: () -> Unit,
    onUpdateDriverName: (String) -> Unit,
    hasBottomNav: Boolean = false,
    availableUpdate: AppUpdateUiState.UpdateAvailable? = null,
    onDownloadUpdate: (GitHubReleaseInfo) -> Unit = {},
    onDismissUpdate: (String) -> Unit = {}
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var modeScope by remember { mutableStateOf(HomeModeScope.ALL) }

    val infiniteTransition = rememberInfiniteTransition(label = "carSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val driverBadgeBg = Color(profile.primaryColorArgb)
    val driverBadgeFg = if (driverBadgeBg.luminance() > 0.45f) Color(0xFF101318) else Color.White
    val onlineAccent = adaptiveOnlineColor()
    val homeInsets = if (hasBottomNav) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(homeInsets)
        ) {
            val isWideScreen = maxWidth >= 680.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = if (isWideScreen) 860.dp else 640.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Material You Hero Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .clip(MaterialTheme.shapes.large)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_paddock),
                            contentDescription = "MicroRC Paddock Hero Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.14f),
                                            Color(0xFF0B0E14).copy(alpha = 0.88f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "MicroRC",
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Top-down RC racing & arenas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.84f)
                            )
                        }
                    }

                    // 1b. Non-overlapping Inline GitHub Update Hint Card (only shown when newer GitHub release is available)
                    if (availableUpdate != null) {
                        HomeUpdateHintCard(
                            update = availableUpdate,
                            onDownload = { onDownloadUpdate(availableUpdate.release) },
                            onDismiss = { onDismissUpdate(availableUpdate.release.tagName) }
                        )
                    }

                    // 2. Driver Tag + Active RC Car Garage Card (Material You Tonal Surface)
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Driver Callsign & Steering Control Chip Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { showNameDialog = true }
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(driverBadgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = driverBadgeFg
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "DRIVER TAG",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Driver Name",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable(onClick = onOpenGarage)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            when (profile.controlMode) {
                                                ControlMode.TILT_SENSOR -> Icons.Default.PhoneAndroid
                                                ControlMode.SPRING_SLIDER -> Icons.Default.SwapHoriz
                                                ControlMode.TOUCH_OVERLAY -> Icons.Default.TouchApp
                                            },
                                            contentDescription = "Control Mode",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = profile.controlMode.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                            // Active Car Turntable + Stats + Garage Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRcCar(
                                            x = size.width / 2f,
                                            y = size.height / 2f,
                                            angleRad = spinAngle,
                                            carType = profile.carType,
                                            primaryColor = Color(profile.primaryColorArgb),
                                            accentColor = Color(profile.accentColorArgb),
                                            scale = 1.35f
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.carType.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = profile.carType.tagline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MiniStatBadge(
                                            label = "SPD ${profile.carType.topSpeedStat}",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        MiniStatBadge(
                                            label = "HDL ${profile.carType.handlingStat}",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        MiniStatBadge(
                                            label = "GRP ${profile.carType.gripStat}",
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenGarage,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_garage_button")
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Garage, Colors & Controls")
                            }
                        }
                    }

                    // 2b. Play Streak Counter + Personal vs. Friends Standings Overview
                    HomeMomentumAndStandingsCard(
                        playStreak = playStreak,
                        isSignedIn = isSignedIn,
                        friendsCount = friendsCount,
                        localRecordsCount = localRecordsCount,
                        standings = friendStandings,
                        onlineAccent = onlineAccent,
                        onOpenFriends = onOpenFriends,
                        onOpenLeaderboards = onOpenLeaderboards,
                        onOpenStandingsBoard = onOpenStandingsBoard
                    )

                    // 3. Intuitive Entry Filter (Material 3 SingleChoiceSegmentedButtonRow)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HomeModeScope.entries.forEachIndexed { index, scopeOption ->
                            val icon = when (scopeOption) {
                                HomeModeScope.ALL -> null
                                HomeModeScope.OFFLINE -> Icons.Default.Wifi
                                HomeModeScope.ONLINE -> Icons.Default.Public
                            }
                            SegmentedButton(
                                selected = modeScope == scopeOption,
                                onClick = { modeScope = scopeOption },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = HomeModeScope.entries.size
                                ),
                                icon = {
                                    if (icon != null && modeScope != scopeOption) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    } else {
                                        SegmentedButtonDefaults.Icon(active = modeScope == scopeOption)
                                    }
                                },
                                modifier = Modifier.testTag("home_scope_${scopeOption.name.lowercase()}")
                            ) {
                                Text(
                                    text = scopeOption.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 4. Primary Game Entry Pillars: OFFLINE (Wi-Fi & Solo) vs ONLINE (Lobbies, Challenges & Leaderboards)
                    val showOffline = modeScope == HomeModeScope.ALL || modeScope == HomeModeScope.OFFLINE
                    val showOnline = modeScope == HomeModeScope.ALL || modeScope == HomeModeScope.ONLINE

                    if (isWideScreen && showOffline && showOnline) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OfflineEntrySection(
                                modifier = Modifier.weight(1f),
                                onHostRace = onHostRace,
                                onJoinRace = onJoinRace,
                                onPureSoloRace = onPureSoloRace,
                                onSoloRace = onSoloRace,
                                onOpenHybridSetup = onOpenHybridSetup
                            )
                            OnlineEntrySection(
                                modifier = Modifier.weight(1f),
                                onOpenOnlinePlay = onOpenOnlinePlay,
                                onOpenFriends = onOpenFriends,
                                onlineFriendsCount = onlineFriendsCount,
                                pendingRequestsCount = pendingRequestsCount,
                                onlineAccent = onlineAccent,
                                onOpenDailyChallenge = onOpenDailyChallenge,
                                dailyChallengeTitle = dailyChallengeTitle,
                                dailyChallengeSubtitle = dailyChallengeSubtitle,
                                dailyChallengeResetLabel = dailyChallengeResetLabel,
                                dailyBestTimeMs = dailyBestTimeMs,
                                onOpenLeaderboards = onOpenLeaderboards
                            )
                        }
                    } else {
                        AnimatedVisibility(visible = showOffline) {
                            OfflineEntrySection(
                                modifier = Modifier.fillMaxWidth(),
                                onHostRace = onHostRace,
                                onJoinRace = onJoinRace,
                                onPureSoloRace = onPureSoloRace,
                                onSoloRace = onSoloRace,
                                onOpenHybridSetup = onOpenHybridSetup
                            )
                        }

                        AnimatedVisibility(visible = showOnline) {
                            OnlineEntrySection(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenOnlinePlay = onOpenOnlinePlay,
                                onOpenFriends = onOpenFriends,
                                onlineFriendsCount = onlineFriendsCount,
                                pendingRequestsCount = pendingRequestsCount,
                                onlineAccent = onlineAccent,
                                onOpenDailyChallenge = onOpenDailyChallenge,
                                dailyChallengeTitle = dailyChallengeTitle,
                                dailyChallengeSubtitle = dailyChallengeSubtitle,
                                dailyChallengeResetLabel = dailyChallengeResetLabel,
                                dailyBestTimeMs = dailyBestTimeMs,
                                onOpenLeaderboards = onOpenLeaderboards
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }

    if (showNameDialog) {
        var draft by remember(profile.name) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Driver Callsign", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 16) draft = it },
                    label = { Text("Driver Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDriverName(draft)
                        showNameDialog = false
                    },
                    modifier = Modifier.testTag("save_driver_name_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OfflineEntrySection(
    modifier: Modifier = Modifier,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onPureSoloRace: () -> Unit,
    onSoloRace: () -> Unit,
    onOpenHybridSetup: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.testTag("home_offline_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OFFLINE PLAY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Local Wi-Fi Lobbies & Solo Modes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Wi-Fi Lobby Entry Points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onHostRace,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("host_race_button")
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Host Race", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("Wi-Fi Lobby", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                FilledTonalButton(
                    onClick = onJoinRace,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .testTag("join_race_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Join Race", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("Scan Wi-Fi", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Text(
                text = "SOLO & OFFLINE GAMEPLAY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onPureSoloRace,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pure_solo_button")
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Solo Time Trial", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                FilledTonalButton(
                    onClick = onSoloRace,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("solo_race_button")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Solo vs Bots", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            FilledTonalButton(
                onClick = onOpenHybridSetup,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("home_hybrid_tour_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Hybrid Tour", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        text = "Circuit • Arena • Stunts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineEntrySection(
    modifier: Modifier = Modifier,
    onOpenOnlinePlay: () -> Unit,
    onOpenFriends: () -> Unit,
    onlineFriendsCount: Int,
    pendingRequestsCount: Int,
    onlineAccent: Color,
    onOpenDailyChallenge: () -> Unit,
    dailyChallengeTitle: String,
    dailyChallengeSubtitle: String,
    dailyChallengeResetLabel: String,
    dailyBestTimeMs: Long?,
    onOpenLeaderboards: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.testTag("home_online_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ONLINE PLAY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Online Lobbies, Daily Challenges & Leaderboards",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Online Lobby (2-4P)
            Button(
                onClick = onOpenOnlinePlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .testTag("online_play_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Online Lobby (2–4P)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Public & private rooms, codes & invites",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Friends & Crew
            FilledTonalButton(
                onClick = onOpenFriends,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("home_friends_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Friends & Session Invites", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when {
                                    onlineFriendsCount > 0 -> "$onlineFriendsCount online • Invite to lobby"
                                    pendingRequestsCount > 0 -> "$pendingRequestsCount request waiting"
                                    else -> "Friend codes, online status & alerts"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onlineFriendsCount > 0 || pendingRequestsCount > 0) {
                        Surface(
                            color = if (onlineFriendsCount > 0) {
                                onlineAccent.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (onlineFriendsCount > 0) "$onlineFriendsCount ONLINE" else "$pendingRequestsCount NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (onlineFriendsCount > 0) onlineAccent else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            Text(
                text = "CHALLENGES & LEADERBOARDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Daily Challenge Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onOpenDailyChallenge)
                    .testTag("daily_challenge_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = "Daily Challenge",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DAILY CHALLENGE",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (dailyChallengeResetLabel.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = dailyChallengeResetLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$dailyChallengeTitle • $dailyChallengeSubtitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (dailyBestTimeMs != null && dailyBestTimeMs > 0L) {
                                String.format(java.util.Locale.US, "%.2fs", dailyBestTimeMs / 1000f)
                            } else {
                                "RACE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Records & Ghosts Leaderboard CTA
            FilledTonalButton(
                onClick = onOpenLeaderboards,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("leaderboards_button")
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Records & Ghosts", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeUpdateHintCard(
    update: AppUpdateUiState.UpdateAvailable,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_update_banner")
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Update Available • ${update.release.tagName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Installed v${update.currentVersion} • Download the latest MicroRC APK from GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_update_download_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download APK", style = MaterialTheme.typography.labelLarge)
                }

                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_update_dismiss_button")
                ) {
                    Text("Later", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HomeMomentumAndStandingsCard(
    playStreak: PlayStreakState,
    isSignedIn: Boolean,
    friendsCount: Int,
    localRecordsCount: Int,
    standings: FriendStandingsSummary?,
    onlineAccent: Color,
    onOpenFriends: () -> Unit,
    onOpenLeaderboards: () -> Unit,
    onOpenStandingsBoard: (trackKey: String, gameMode: GameMode, isDaily: Boolean) -> Unit
) {
    val goldAccent = adaptiveGoldColor()
    val summary = standings ?: FriendStandingsSummary(totalFriendsCount = friendsCount)
    val streakActive = playStreak.currentStreakDays > 0

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_streak_standings_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top summary row: Play Streak Counter + Overall Friend-Crew Rank
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak tile
                Surface(
                    color = if (playStreak.activeToday) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else if (streakActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_streak_badge")
                ) {
                    val streakFg = when {
                        playStreak.activeToday -> MaterialTheme.colorScheme.onTertiaryContainer
                        streakActive -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Daily Play Streak",
                            tint = if (streakActive) goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = when {
                                    playStreak.currentStreakDays == 1 -> "1 Day Streak"
                                    playStreak.currentStreakDays > 1 -> "${playStreak.currentStreakDays} Day Streak"
                                    else -> "0 Day Streak"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = streakFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    playStreak.activeToday -> "Active today • Best ${playStreak.bestStreakDays}d"
                                    streakActive -> "Race today! • Best ${playStreak.bestStreakDays}d"
                                    playStreak.bestStreakDays > 0 -> "Best ${playStreak.bestStreakDays}d • Race to start"
                                    else -> "Finish any race today"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = streakFg.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Personal vs Friends Standing Summary tile
                Surface(
                    color = if (summary.myCrewRank == 1 && summary.leadingBoardsCount > 0) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            if (isSignedIn && friendsCount > 0) {
                                onOpenLeaderboards()
                            } else {
                                onOpenFriends()
                            }
                        }
                        .testTag("home_standings_rank_badge")
                ) {
                    val rankFg = if (summary.myCrewRank == 1 && summary.leadingBoardsCount > 0) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val contestedTotal = summary.leadingBoardsCount + summary.trailingBoardsCount
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSignedIn && friendsCount > 0) {
                                Icons.Default.Leaderboard
                            } else {
                                Icons.Default.EmojiEvents
                            },
                            contentDescription = "Crew Standings",
                            tint = if (summary.myCrewRank == 1 && summary.leadingBoardsCount > 0) {
                                goldAccent
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = when {
                                    !isSignedIn -> if (localRecordsCount > 0) {
                                        "$localRecordsCount Local PB${if (localRecordsCount == 1) "" else "s"}"
                                    } else {
                                        "Crew Rank"
                                    }
                                    friendsCount == 0 -> "No Friends Yet"
                                    summary.myCrewRank > 0 && summary.crewSizeWithEntries > 1 ->
                                        "#${summary.myCrewRank} of ${summary.crewSizeWithEntries} Crew"
                                    summary.myCrewRank == 1 -> "1st in Crew"
                                    else -> "Set First Time"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = rankFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !isSignedIn -> "Sign in to compare friends"
                                    friendsCount == 0 -> "Add friends to compare"
                                    contestedTotal > 0 ->
                                        "Lead ${summary.leadingBoardsCount} • Chase ${summary.trailingBoardsCount}"
                                    summary.soloOnlyBoardsCount > 0 ->
                                        "${summary.soloOnlyBoardsCount} track record${if (summary.soloOnlyBoardsCount == 1) "" else "s"} set"
                                    summary.unchallengedFriendBoardsCount > 0 ->
                                        "${summary.unchallengedFriendBoardsCount} friend time${if (summary.unchallengedFriendBoardsCount == 1) "" else "s"} to beat"
                                    else -> "Race a track to rank"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = rankFg.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Compact Friend-vs-Personal Standings List
            if (isSignedIn && friendsCount > 0 && summary.highlightBoards.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "YOUR TIMES VS. FRIENDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap track to challenge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("home_standings_list")
                ) {
                    summary.highlightBoards.forEachIndexed { index, item ->
                        HomeStandingBoardRow(
                            item = item,
                            index = index,
                            onlineAccent = onlineAccent,
                            goldAccent = goldAccent,
                            onClick = {
                                onOpenStandingsBoard(item.trackKey, item.gameMode, item.isDaily)
                            }
                        )
                    }
                }
            } else {
                // Helpful compact footer action for signed-out / no-friends / zero-records states
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            !isSignedIn -> "Sign in with Google to track head-to-head times & friend rankings."
                            friendsCount == 0 -> "Add friends in Crew to compare track & Daily GP rankings."
                            else -> "Set a track or Daily GP time to start ranking against friends."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = if (!isSignedIn || friendsCount == 0) onOpenFriends else onOpenLeaderboards,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("home_standings_cta_button")
                    ) {
                        Text(
                            text = when {
                                !isSignedIn -> "Sign In"
                                friendsCount == 0 -> "Add Friends"
                                else -> "Records"
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
private fun HomeStandingBoardRow(
    item: FriendBoardStandingItem,
    index: Int,
    onlineAccent: Color,
    goldAccent: Color,
    onClick: () -> Unit
) {
    val isStunts = item.gameMode == GameMode.STUNTS
    val myRank = item.myRank
    val hasMyEntry = myRank != null
    val leading = myRank == 1 && item.topFriendName != null

    val myMetric = if (hasMyEntry) {
        if (isStunts) "${item.myScore} PTS" else SessionNotificationHelper.formatNotifTimeMs(item.myTimeMs)
    } else {
        "Unset"
    }
    val friendMetric = if (item.topFriendName != null) {
        val raw = if (isStunts) "${item.topFriendScore} PTS" else SessionNotificationHelper.formatNotifTimeMs(item.topFriendTimeMs)
        "${item.topFriendName}: $raw"
    } else {
        "No friend time yet"
    }

    val deltaText: String? = when {
        item.deltaVsTopFriendMs != null -> {
            val absSec = abs(item.deltaVsTopFriendMs) / 1000.0
            if (item.deltaVsTopFriendMs <= 0L) {
                String.format(Locale.US, "-%.2fs", absSec)
            } else {
                String.format(Locale.US, "+%.2fs", absSec)
            }
        }
        item.deltaVsTopFriendScore != null -> {
            if (item.deltaVsTopFriendScore >= 0) "+${item.deltaVsTopFriendScore}" else "${item.deltaVsTopFriendScore}"
        }
        else -> null
    }
    val isAhead = when {
        item.deltaVsTopFriendMs != null -> item.deltaVsTopFriendMs <= 0L
        item.deltaVsTopFriendScore != null -> item.deltaVsTopFriendScore >= 0
        else -> false
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("home_standings_board_row_$index")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = when {
                    leading -> goldAccent.copy(alpha = 0.2f)
                    hasMyEntry -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (hasMyEntry) "#$myRank/${item.totalDriversOnBoard}" else "BEAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        leading -> goldAccent
                        hasMyEntry -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.isDaily || isStunts) {
                        item.trackName
                    } else {
                        "${item.trackName} • ${item.totalLaps}L"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "You: $myMetric  •  $friendMetric",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (deltaText != null) {
                Surface(
                    color = if (isAhead) {
                        onlineAccent.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAhead) onlineAccent else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open leaderboard for ${item.trackName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MiniStatBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
