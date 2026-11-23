package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FriendBoardStandingItem
import com.example.data.FriendCrewMemberRank
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class HomeModeScope(val label: String) {
    ONLINE("Online (Standard)"),
    OFFLINE("Offline (Wi-Fi)")
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
    onOpenIndividualTracks: () -> Unit = {},
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
    var modeScope by rememberSaveable { mutableStateOf(HomeModeScope.ONLINE) }

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

                    // 2. Play Streak Counter (with 5-day milestone welcome animation) + Overall Friends Ranking & Standings Overview
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

                    // 3. Gameplay Mode Switcher: Online (Standard Default) vs Optional Offline (Wi-Fi)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GAMEPLAY MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (modeScope == HomeModeScope.ONLINE) {
                                    "Online standard • Switch for local Wi-Fi"
                                } else {
                                    "Optional offline & local Wi-Fi mode"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HomeModeScope.entries.forEachIndexed { index, scopeOption ->
                                val icon = when (scopeOption) {
                                    HomeModeScope.ONLINE -> Icons.Default.Public
                                    HomeModeScope.OFFLINE -> Icons.Default.Wifi
                                }
                                SegmentedButton(
                                    selected = modeScope == scopeOption,
                                    onClick = { modeScope = scopeOption },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = HomeModeScope.entries.size
                                    ),
                                    icon = {
                                        if (modeScope != scopeOption) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        } else {
                                            SegmentedButtonDefaults.Icon(active = true)
                                        }
                                    },
                                    modifier = Modifier.testTag("home_scope_${scopeOption.name.lowercase()}")
                                ) {
                                    Text(
                                        text = scopeOption.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 4. Primary Content Area:
                    //    - Standard Mode (Online):
                    //        A) Challenges & Rankings (Daily Challenge, Individual Tracks, Rankings — most used)
                    //        B) Live Online Gameplay (Live Lobbies & Crew Invites — clearly distinguished)
                    //    - Optional Mode (Offline / Wi-Fi): Local Wi-Fi Host/Join, Hybrid Tour & Bot Practice
                    if (modeScope == HomeModeScope.ONLINE) {
                        if (isWideScreen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                ChallengesAndRankingsSection(
                                    modifier = Modifier.weight(1.1f),
                                    onOpenDailyChallenge = onOpenDailyChallenge,
                                    onOpenIndividualTracks = onOpenIndividualTracks,
                                    onPureSoloRace = onPureSoloRace,
                                    onOpenLeaderboards = onOpenLeaderboards,
                                    dailyChallengeTitle = dailyChallengeTitle,
                                    dailyChallengeSubtitle = dailyChallengeSubtitle,
                                    dailyChallengeResetLabel = dailyChallengeResetLabel,
                                    dailyBestTimeMs = dailyBestTimeMs
                                )
                                LiveOnlineGameplaySection(
                                    modifier = Modifier.weight(0.9f),
                                    onOpenOnlinePlay = onOpenOnlinePlay,
                                    onOpenFriends = onOpenFriends,
                                    onlineFriendsCount = onlineFriendsCount,
                                    pendingRequestsCount = pendingRequestsCount,
                                    onlineAccent = onlineAccent
                                )
                            }
                        } else {
                            ChallengesAndRankingsSection(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenDailyChallenge = onOpenDailyChallenge,
                                onOpenIndividualTracks = onOpenIndividualTracks,
                                onPureSoloRace = onPureSoloRace,
                                onOpenLeaderboards = onOpenLeaderboards,
                                dailyChallengeTitle = dailyChallengeTitle,
                                dailyChallengeSubtitle = dailyChallengeSubtitle,
                                dailyChallengeResetLabel = dailyChallengeResetLabel,
                                dailyBestTimeMs = dailyBestTimeMs
                            )
                            LiveOnlineGameplaySection(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenOnlinePlay = onOpenOnlinePlay,
                                onOpenFriends = onOpenFriends,
                                onlineFriendsCount = onlineFriendsCount,
                                pendingRequestsCount = pendingRequestsCount,
                                onlineAccent = onlineAccent
                            )
                        }
                    } else {
                        AnimatedVisibility(visible = modeScope == HomeModeScope.OFFLINE) {
                            OfflineEntrySection(
                                modifier = Modifier.fillMaxWidth(),
                                onHostRace = onHostRace,
                                onJoinRace = onJoinRace,
                                onPureSoloRace = onPureSoloRace,
                                onSoloRace = onSoloRace,
                                onOpenHybridSetup = onOpenHybridSetup
                            )
                        }
                    }

                    // 5. Driver Tag + Active RC Car Garage Card
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
private fun ChallengesAndRankingsSection(
    modifier: Modifier = Modifier,
    onOpenDailyChallenge: () -> Unit,
    onOpenIndividualTracks: () -> Unit,
    onPureSoloRace: () -> Unit,
    onOpenLeaderboards: () -> Unit,
    dailyChallengeTitle: String,
    dailyChallengeSubtitle: String,
    dailyChallengeResetLabel: String,
    dailyBestTimeMs: Long?
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.testTag("home_challenges_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header: Clearly marks Asynchronous Solo/Daily Challenges & Rankings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHALLENGES & RANKINGS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Daily circuits, individual tracks & leaderboards",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "TOP HUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 1. Daily Challenge Card (Primary Featured Challenge)
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
                                String.format(Locale.US, "%.2fs", dailyBestTimeMs / 1000f)
                            } else {
                                "RACE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 2. Individual Track Trials & Track Rankings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenIndividualTracks,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .testTag("home_individual_tracks_button")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Column {
                        Text("Individual Tracks", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Records & ghosts", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                FilledTonalButton(
                    onClick = onPureSoloRace,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .testTag("pure_solo_button")
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Column {
                        Text("Solo Time Trial", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Quick track run", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // 3. Full Leaderboards & Friend Rankings CTA
            OutlinedButton(
                onClick = onOpenLeaderboards,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("leaderboards_button")
            ) {
                Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("All Rankings, Records & Ghosts", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LiveOnlineGameplaySection(
    modifier: Modifier = Modifier,
    onOpenOnlinePlay: () -> Unit,
    onOpenFriends: () -> Unit,
    onlineFriendsCount: Int,
    pendingRequestsCount: Int,
    onlineAccent: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.testTag("home_online_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header: Clearly separates Live Multi-Player Lobbies from Solo/Daily Challenges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE ONLINE MULTIPLAYER",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time 2–4P lobbies, room codes & crew invites",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = onlineAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = onlineAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Live Online Lobby Entry (2-4P)
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
                            Text("Online Lobbies (2–4P)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Host or join live rooms & enter room codes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Crew, Friends & Live Invites
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
                            Text("Friends & Live Invites", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when {
                                    onlineFriendsCount > 0 -> "$onlineFriendsCount online • Invite to live lobby"
                                    pendingRequestsCount > 0 -> "$pendingRequestsCount friend request waiting"
                                    else -> "Friend codes, online status & live alerts"
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
        }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
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
                        text = "OFFLINE (WI-FI) GAMEPLAY",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Optional local Wi-Fi lobbies, Hybrid Tour & bot practice",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "OPTIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                text = "SOLO & HYBRID PRACTICE",
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
    val isMilestone = playStreak.isFiveDayMilestone
    var milestoneDismissed by rememberSaveable(playStreak.currentStreakDays) { mutableStateOf(false) }
    var showAllFriends by rememberSaveable { mutableStateOf(false) }

    val milestoneAnim = remember(playStreak.currentStreakDays) { Animatable(0f) }
    LaunchedEffect(playStreak.currentStreakDays, isMilestone) {
        if (isMilestone) {
            milestoneAnim.snapTo(0f)
            milestoneAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
            )
        }
    }
    val milestoneBadgeScale by animateFloatAsState(
        targetValue = if (isMilestone && milestoneAnim.value > 0.05f) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "milestoneBadgeScale"
    )

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
            // Welcome Animation Banner for every 5-day streak milestone (5, 10, 15, ...)
            AnimatedVisibility(visible = isMilestone && !milestoneDismissed) {
                val burstProgress = milestoneAnim.value
                val confettiPrimary = MaterialTheme.colorScheme.primary
                val confettiTertiary = MaterialTheme.colorScheme.tertiary
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.5.dp, goldAccent.copy(alpha = 0.75f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_streak_milestone_banner")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                        ) {
                            val cx = size.width * 0.12f
                            val cy = size.height * 0.5f
                            val maxRadius = size.minDimension * 0.85f
                            val rayCount = 8
                            for (i in 0 until rayCount) {
                                val angle = (2.0 * PI * i / rayCount) + burstProgress * 0.5
                                val r = maxRadius * (0.35f + 0.65f * burstProgress)
                                val dotColor = when (i % 3) {
                                    0 -> goldAccent
                                    1 -> confettiPrimary
                                    else -> confettiTertiary
                                }.copy(alpha = (1f - burstProgress * 0.45f).coerceIn(0.25f, 0.9f))
                                drawCircle(
                                    color = dotColor,
                                    radius = (4.5f - 1.5f * burstProgress).dp.toPx(),
                                    center = Offset(
                                        x = cx + (cos(angle) * r).toFloat(),
                                        y = cy + (sin(angle) * r).toFloat()
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = goldAccent.copy(alpha = 0.22f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        scaleX = milestoneBadgeScale
                                        scaleY = milestoneBadgeScale
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "5-Day Streak Milestone",
                                        tint = goldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Welcome! ${playStreak.currentStreakDays}-Day Streak Milestone",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "5-day tier unlocked — keep your racing momentum rolling!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { milestoneDismissed = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_streak_milestone_dismiss")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Milestone Banner",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top summary row: Play Streak Counter + Overall All-Friends Rank
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

                // Overall Ranking Across All Friends tile
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
                    val totalRoster = maxOf(summary.totalCrewCount, friendsCount + 1)
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
                            contentDescription = "Overall Friends Standings",
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
                                        "Overall Rank"
                                    }
                                    friendsCount == 0 -> "No Friends Yet"
                                    summary.myCrewRank > 0 ->
                                        "#${summary.myCrewRank} of $totalRoster Friends"
                                    else -> "Set First Time"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = rankFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !isSignedIn -> "Sign in to rank vs friends"
                                    friendsCount == 0 -> "Add friends to rank overall"
                                    contestedTotal > 0 ->
                                        "Lead ${summary.leadingBoardsCount} • Chase ${summary.trailingBoardsCount}"
                                    summary.soloOnlyBoardsCount > 0 ->
                                        "${summary.soloOnlyBoardsCount} track PB${if (summary.soloOnlyBoardsCount == 1) "" else "s"} • $friendsCount friends"
                                    summary.unchallengedFriendBoardsCount > 0 ->
                                        "${summary.unchallengedFriendBoardsCount} friend time${if (summary.unchallengedFriendBoardsCount == 1) "" else "s"} to beat"
                                    else -> "Overall across $friendsCount friends"
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

            // Overall Ranking Across All Friends (includes every friend in the roster)
            if (isSignedIn && friendsCount > 0 && summary.crewLeaderboard.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val displayedCrew = if (showAllFriends || summary.crewLeaderboard.size <= 4) {
                    summary.crewLeaderboard
                } else {
                    summary.crewLeaderboard.take(4)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OVERALL FRIENDS RANKING (${summary.crewLeaderboard.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary.crewLeaderboard.size > 4) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showAllFriends = !showAllFriends }
                                .padding(horizontal = 6.dp)
                                .testTag("home_overall_friends_toggle")
                        ) {
                            Text(
                                text = if (showAllFriends) "Show Top 4" else "Show All",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showAllFriends) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.testTag("home_overall_friends_ranking")
                ) {
                    displayedCrew.forEachIndexed { index, member ->
                        HomeOverallFriendRankRow(
                            member = member,
                            index = index,
                            goldAccent = goldAccent
                        )
                    }
                }
            }

            // Today's Daily & Track-by-Track Standings List (yesterday's Daily GP already filtered out)
            if (isSignedIn && friendsCount > 0 && summary.highlightBoards.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRACK & TODAY'S DAILY TIMES",
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
            } else if (!isSignedIn || friendsCount == 0 || summary.highlightBoards.isEmpty()) {
                // Helpful compact footer action for signed-out / no-friends / zero-records states
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            !isSignedIn -> "Sign in with Google to track head-to-head times & overall friend rankings."
                            friendsCount == 0 -> "Add friends in Crew to unlock overall & track rankings."
                            else -> "Set a track or Today's Daily GP time to start ranking against friends."
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
private fun HomeOverallFriendRankRow(
    member: FriendCrewMemberRank,
    index: Int,
    goldAccent: Color
) {
    val rowBg = if (member.isSelf) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        color = rowBg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_overall_friend_rank_$index")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = when {
                    member.rank == 1 && member.boardsEntered > 0 -> goldAccent.copy(alpha = 0.22f)
                    member.isSelf -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "#${member.rank}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        member.rank == 1 && member.boardsEntered > 0 -> goldAccent
                        member.isSelf -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Text(
                text = if (member.isSelf) "${member.displayName} (You)" else member.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (member.boardsEntered == 0) {
                    "No times yet"
                } else {
                    "${member.contestedWins}W • ${member.h2hMatchupsWon} H2H • ${member.boardsEntered} boards"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
