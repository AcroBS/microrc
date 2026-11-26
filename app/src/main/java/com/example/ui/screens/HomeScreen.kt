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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FriendBoardStandingItem
import com.example.data.FriendCrewMemberRank
import com.example.data.FriendStandingsSummary
import com.example.data.PlayStreakState
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.ui.components.drawRcCar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
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
    ONLINE("Online"),
    OFFLINE("Offline (Wi-Fi)"),
    SOLO("Solo Play")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: PlayerProfile,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit,
    onSoloRace: () -> Unit,
    onSoloRaceWithBots: (Int) -> Unit = { onSoloRace() },
    onPureSoloRace: () -> Unit = {},
    onOpenDailyChallenge: () -> Unit = {},
    dailyChallengeTitle: String = "Daily GP",
    dailyChallengeSubtitle: String = "Fixed Portals • 1 Nitro/Lap • No Mines",
    dailyChallengeResetLabel: String = "",
    dailyBestTimeMs: Long? = null,
    onOpenDailyDriftChallenge: () -> Unit = {},
    dailyDriftTitle: String = "Daily Drift",
    dailyDriftBestTimeMs: Long? = null,
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
                    // 1. Compact Material You Hero Banner + Overlaid Driver Tag Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .clip(RoundedCornerShape(24.dp))
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
                                            Color.Black.copy(alpha = 0.22f),
                                            Color(0xFF0B0E14).copy(alpha = 0.90f)
                                        )
                                    )
                                )
                        )

                        // Compact semi-transparent Driver Tag pill overlay in top-right corner
                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(CircleShape)
                                .clickable { showNameDialog = true }
                                .padding(10.dp)
                                .testTag("driver_tag_row")
                        ) {
                            Surface(
                                color = Color(0xFF0E131B).copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                shape = CircleShape
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(driverBadgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = driverBadgeFg
                                        )
                                    }
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Driver Name",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "MicroRC",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Top-down RC racing & arenas",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = Color.White.copy(alpha = 0.84f)
                            )
                        }
                    }

                    // 1b. Inline GitHub Update Hint Card (only shown when newer GitHub release is available)
                    if (availableUpdate != null) {
                        HomeUpdateHintCard(
                            update = availableUpdate,
                            onDownload = { onDownloadUpdate(availableUpdate.release) },
                            onDismiss = { onDismissUpdate(availableUpdate.release.tagName) }
                        )
                    }

                    // 2. Active RC Car Card (compact above the fold with scaled car sprite + drop shadow + inline stat bars)
                    HomeSectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_driver_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    // Subtle drop shadow under the spinning RC car
                                    drawOval(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.34f),
                                                Color.Transparent
                                            ),
                                            center = Offset(cx + 2.dp.toPx(), cy + 5.dp.toPx()),
                                            radius = size.minDimension * 0.44f
                                        ),
                                        topLeft = Offset(cx - size.minDimension * 0.40f, cy - size.minDimension * 0.34f + 4.dp.toPx()),
                                        size = Size(size.minDimension * 0.80f, size.minDimension * 0.72f)
                                    )
                                    val carScale = ((size.minDimension * 0.78f) / 48f).coerceIn(1.75f, 3.3f)
                                    drawRcCar(
                                        x = cx,
                                        y = cy,
                                        angleRad = spinAngle,
                                        carType = profile.carType,
                                        primaryColor = Color(profile.primaryColorArgb),
                                        accentColor = Color(profile.accentColorArgb),
                                        scale = carScale,
                                        visualUpgrades = profile.equippedVisualUpgrades
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = profile.carType.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactStatBar(
                                        label = "SPD",
                                        value = profile.carType.topSpeedStat,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactStatBar(
                                        label = "HDL",
                                        value = profile.carType.handlingStat,
                                        accentColor = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactStatBar(
                                        label = "GRP",
                                        value = profile.carType.gripStat,
                                        accentColor = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = onOpenGarage,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("open_garage_button")
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Garage",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    // 3. Mode Switcher (directly below Active Car Card): Online | Offline (Wi-Fi) | Solo Play
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_mode_tabs_row")
                    ) {
                        HomeModeScope.entries.forEachIndexed { index, scopeOption ->
                            val icon = when (scopeOption) {
                                HomeModeScope.ONLINE -> Icons.Default.Public
                                HomeModeScope.OFFLINE -> Icons.Default.Wifi
                                HomeModeScope.SOLO -> Icons.Default.DirectionsCar
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
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                    } else {
                                        SegmentedButtonDefaults.Icon(active = true)
                                    }
                                },
                                modifier = Modifier.testTag("home_scope_${scopeOption.name.lowercase()}")
                            ) {
                                Text(
                                    text = scopeOption.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 4. Mode Content (Action First, Stats Second):
                    //    - Online: Online Lobbies CTA -> 2-col Daily grid + Tracks & Ghosts -> Rivalry & Leaderboards
                    //    - Offline (Wi-Fi): Host Race + Join Race side-by-side
                    //    - Solo Play: AI Bots toggle -> Solo Time Trial / Quick Race CTA -> Hybrid Tour
                    when (modeScope) {
                        HomeModeScope.ONLINE -> {
                            LiveOnlineGameplaySection(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenOnlinePlay = onOpenOnlinePlay
                            )

                            ChallengesAndRankingsSection(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenDailyChallenge = onOpenDailyChallenge,
                                onOpenLeaderboards = onOpenLeaderboards,
                                dailyChallengeTitle = dailyChallengeTitle,
                                dailyChallengeResetLabel = dailyChallengeResetLabel,
                                dailyBestTimeMs = dailyBestTimeMs,
                                onOpenDailyDriftChallenge = onOpenDailyDriftChallenge,
                                dailyDriftTitle = dailyDriftTitle,
                                dailyDriftBestTimeMs = dailyDriftBestTimeMs
                            )

                            HomeMomentumAndStandingsCard(
                                playStreak = playStreak,
                                isSignedIn = isSignedIn,
                                friendsCount = friendsCount,
                                onlineFriendsCount = onlineFriendsCount,
                                pendingRequestsCount = pendingRequestsCount,
                                localRecordsCount = localRecordsCount,
                                standings = friendStandings,
                                onlineAccent = onlineAccent,
                                onOpenFriends = onOpenFriends,
                                onOpenStandingsBoard = onOpenStandingsBoard
                            )
                        }

                        HomeModeScope.OFFLINE -> {
                            OfflineEntrySection(
                                modifier = Modifier.fillMaxWidth(),
                                onHostRace = onHostRace,
                                onJoinRace = onJoinRace
                            )
                        }

                        HomeModeScope.SOLO -> {
                            SoloPlaySection(
                                modifier = Modifier.fillMaxWidth(),
                                onPureSoloRace = onPureSoloRace,
                                onSoloRaceWithBots = onSoloRaceWithBots,
                                onOpenHybridSetup = onOpenHybridSetup
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
private fun HomeSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
        content = content
    )
}

@Composable
private fun ChallengesAndRankingsSection(
    modifier: Modifier = Modifier,
    onOpenDailyChallenge: () -> Unit,
    onOpenLeaderboards: () -> Unit,
    dailyChallengeTitle: String,
    dailyChallengeResetLabel: String,
    dailyBestTimeMs: Long?,
    onOpenDailyDriftChallenge: () -> Unit = {},
    dailyDriftTitle: String = "Daily Drift",
    dailyDriftBestTimeMs: Long? = null
) {
    HomeSectionCard(
        modifier = modifier.testTag("home_challenges_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "DAILY CHALLENGES & GHOSTS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 2-column grid for Daily Challenge & Daily Drift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Daily Challenge tile
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenDailyChallenge)
                        .testTag("daily_challenge_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Today,
                                        contentDescription = "Daily Challenge",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (dailyChallengeResetLabel.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = dailyChallengeResetLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Daily Challenge",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dailyChallengeTitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (dailyBestTimeMs != null && dailyBestTimeMs > 0L) {
                                    "PB ${String.format(Locale.US, "%.2fs", dailyBestTimeMs / 1000f)}"
                                } else {
                                    "No PB yet"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = if (dailyBestTimeMs != null && dailyBestTimeMs > 0L) {
                                        JetBrainsMonoFontFamily
                                    } else {
                                        PlusJakartaFontFamily
                                    }
                                ),
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Daily Drift tile
                if (com.example.drift.DriftFeature.ENABLED) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenDailyDriftChallenge)
                            .testTag("daily_drift_challenge_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Speed,
                                            contentDescription = "Daily Drift",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "DRIFT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Daily Drift",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dailyDriftTitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (dailyDriftBestTimeMs != null && dailyDriftBestTimeMs > 0L) {
                                        "PB ${String.format(Locale.US, "%.2fs", dailyDriftBestTimeMs / 1000f)}"
                                    } else {
                                        "No PB yet"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = if (dailyDriftBestTimeMs != null && dailyDriftBestTimeMs > 0L) {
                                            JetBrainsMonoFontFamily
                                        } else {
                                            PlusJakartaFontFamily
                                        }
                                    ),
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Full-width Tracks & Ghosts accent card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenLeaderboards)
                    .testTag("leaderboards_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Leaderboard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Tracks & Ghosts",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Leaderboards, custom tracks & rival ghosts",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveOnlineGameplaySection(
    modifier: Modifier = Modifier,
    onOpenOnlinePlay: () -> Unit
) {
    HomeSectionCard(
        modifier = modifier.testTag("home_online_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "LIVE ONLINE MULTIPLAYER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                onClick = onOpenOnlinePlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
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
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Online Lobbies (2–4P)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Host or join live multiplayer rooms",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
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
        }
    }
}

@Composable
private fun OfflineEntrySection(
    modifier: Modifier = Modifier,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit
) {
    HomeSectionCard(
        modifier = modifier.testTag("home_offline_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "OFFLINE (WI-FI) GAMEPLAY",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Only two side-by-side cards: Host Race (primary CTA) & Join Race (secondary surfaceContainerHigh card)
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
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .testTag("host_race_button")
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Host Race", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            text = "Wi-Fi Lobby",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onJoinRace)
                        .testTag("join_race_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Join Race",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "Scan Wi-Fi",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoloPlaySection(
    modifier: Modifier = Modifier,
    onPureSoloRace: () -> Unit,
    onSoloRaceWithBots: (Int) -> Unit,
    onOpenHybridSetup: () -> Unit
) {
    var includeBots by rememberSaveable { mutableStateOf(false) }
    var botCount by rememberSaveable { mutableIntStateOf(3) }

    HomeSectionCard(
        modifier = modifier.testTag("home_solo_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "SOLO PLAY",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 1. Include AI Bots toggle/stepper at the top
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = if (includeBots) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include AI Bots",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (includeBots) {
                                    "Race with $botCount AI opponent${if (botCount == 1) "" else "s"}"
                                } else {
                                    "0 Bots • Pure solo driving"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeBots,
                            onCheckedChange = { includeBots = it },
                            modifier = Modifier.testTag("solo_bots_switch")
                        )
                    }

                    AnimatedVisibility(visible = includeBots) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3).forEach { count ->
                                FilterChip(
                                    selected = botCount == count,
                                    onClick = { botCount = count },
                                    label = {
                                        Text(
                                            text = "$count Bot${if (count == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("solo_bot_count_$count")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Primary CTA: Solo Time Trial / Quick Race
            Button(
                onClick = {
                    if (includeBots) {
                        onSoloRaceWithBots(botCount)
                    } else {
                        onPureSoloRace()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .testTag(if (includeBots) "solo_race_button" else "pure_solo_button")
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
                        Icon(
                            imageVector = if (includeBots) Icons.Default.Speed else Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Solo Time Trial / Quick Race",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Practice lines or race against AI bots",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (includeBots) "$botCount BOT${if (botCount == 1) "" else "S"}" else "0 BOTS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 3. Hybrid Tour secondary card below (Tracks & Ghosts removed from Solo Play)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenHybridSetup)
                    .testTag("solo_hybrid_tour_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hybrid Tour",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Circuit • Arena • Stunts",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
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
    onlineFriendsCount: Int = 0,
    pendingRequestsCount: Int = 0,
    localRecordsCount: Int,
    standings: FriendStandingsSummary?,
    onlineAccent: Color,
    onOpenFriends: () -> Unit,
    onOpenStandingsBoard: (trackKey: String, gameMode: GameMode, isDaily: Boolean) -> Unit
) {
    val goldAccent = adaptiveGoldColor()
    val summary = standings ?: FriendStandingsSummary(totalFriendsCount = friendsCount)
    val streakActive = playStreak.currentStreakDays > 0
    val isMilestone = playStreak.isFiveDayMilestone
    var milestoneDismissed by rememberSaveable(playStreak.currentStreakDays) { mutableStateOf(false) }
    var showAllFriends by rememberSaveable { mutableStateOf(false) }
    var showAllStandings by rememberSaveable { mutableStateOf(false) }

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

    HomeSectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_streak_standings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_streak_milestone_banner")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Canvas(
                            modifier = Modifier.matchParentSize()
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = PlusJakartaFontFamily),
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

            // 2-column summary tiles: Streak + Friends Rank (styled consistently with surfaceContainerHigh & 16.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (playStreak.activeToday) {
                            goldAccent.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_streak_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = if (streakActive) {
                                goldAccent.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Daily Play Streak",
                                    tint = if (streakActive) goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = when {
                                    playStreak.currentStreakDays == 1 -> "1 Day Streak"
                                    playStreak.currentStreakDays > 1 -> "${playStreak.currentStreakDays} Day Streak"
                                    else -> "0 Day Streak"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    playStreak.activeToday -> "Active today • Best ${playStreak.bestStreakDays}d"
                                    streakActive -> "Race today! • Best ${playStreak.bestStreakDays}d"
                                    playStreak.bestStreakDays > 0 -> "Best ${playStreak.bestStreakDays}d"
                                    else -> "Finish any race today"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val needsFriendSetup = !isSignedIn || friendsCount == 0
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (summary.myCrewRank == 1 && summary.leadingBoardsCount > 0) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (needsFriendSetup) Modifier.clickable(onClick = onOpenFriends) else Modifier
                        )
                        .testTag("home_standings_rank_badge")
                ) {
                    val totalRoster = maxOf(summary.totalCrewCount, friendsCount + 1)
                    val contestedTotal = summary.leadingBoardsCount + summary.trailingBoardsCount
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = if (summary.myCrewRank == 1 && summary.leadingBoardsCount > 0) {
                                goldAccent.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = PlusJakartaFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Overall Friends Ranking Header + Invite / Manage Friends IconButton (replaces bottom Friends button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isSignedIn && friendsCount > 0 && summary.crewLeaderboard.isNotEmpty()) {
                            "OVERALL FRIENDS RANKING (${summary.crewLeaderboard.size})"
                        } else {
                            "OVERALL FRIENDS RANKING"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onlineFriendsCount > 0 || pendingRequestsCount > 0) {
                        Surface(
                            color = if (onlineFriendsCount > 0) {
                                onlineAccent.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (onlineFriendsCount > 0) {
                                    "$onlineFriendsCount online"
                                } else {
                                    "$pendingRequestsCount req"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (onlineFriendsCount > 0) {
                                    onlineAccent
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isSignedIn && friendsCount > 0 && summary.crewLeaderboard.size > 4) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(CircleShape)
                                .clickable { showAllFriends = !showAllFriends }
                                .padding(horizontal = 8.dp)
                                .testTag("home_overall_friends_toggle")
                        ) {
                            Text(
                                text = if (showAllFriends) "Top 4" else "All",
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

                    IconButton(
                        onClick = onOpenFriends,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_friends_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Invite or Manage Friends",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isSignedIn && friendsCount > 0 && summary.crewLeaderboard.isNotEmpty()) {
                val displayedCrew = if (showAllFriends || summary.crewLeaderboard.size <= 4) {
                    summary.crewLeaderboard
                } else {
                    summary.crewLeaderboard.take(4)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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

            // Today's Daily & Track-by-Track Standings List (top 3 + View All toggle)
            if (isSignedIn && summary.highlightBoards.isNotEmpty()) {
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
                    if (summary.highlightBoards.size > 3) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(CircleShape)
                                .clickable { showAllStandings = !showAllStandings }
                                .testTag("home_standings_view_all_toggle")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (showAllStandings) {
                                        "Top 3"
                                    } else {
                                        "View All (${summary.highlightBoards.size})"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (showAllStandings) {
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
                }

                val displayedBoards = if (showAllStandings || summary.highlightBoards.size <= 3) {
                    summary.highlightBoards
                } else {
                    summary.highlightBoards.take(3)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("home_standings_list")
                ) {
                    displayedBoards.forEachIndexed { index, item ->
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
            }

            if (!isSignedIn || friendsCount == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!isSignedIn) {
                            "Sign in to rank your times against friends."
                        } else {
                            "Add friends to unlock overall standings."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onOpenFriends,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("home_standings_cta_button")
                    ) {
                        Text(
                            text = if (!isSignedIn) "Sign In" else "Add Friends",
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
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        color = rowBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_overall_friend_rank_$index")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = when {
                    member.rank == 1 && member.boardsEntered > 0 -> goldAccent.copy(alpha = 0.22f)
                    member.isSelf -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = CircleShape
            ) {
                Text(
                    text = "#${member.rank}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = when {
                        member.rank == 1 && member.boardsEntered > 0 -> goldAccent
                        member.isSelf -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                    "${member.contestedWins}W • ${member.h2hMatchupsWon} H2H • ${member.boardsEntered}B"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = if (member.boardsEntered == 0) PlusJakartaFontFamily else JetBrainsMonoFontFamily
                ),
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
    val isUncontestedSolo = hasMyEntry && item.totalDriversOnBoard <= 1
    val leading = myRank == 1 && item.totalDriversOnBoard > 1 && item.topFriendName != null

    val myMetric = if (hasMyEntry) {
        if (isStunts) "${item.myScore} PTS" else SessionNotificationHelper.formatNotifTimeMs(item.myTimeMs)
    } else {
        "Unset"
    }
    val friendMetric = if (item.topFriendName != null) {
        val raw = if (isStunts) "${item.topFriendScore} PTS" else SessionNotificationHelper.formatNotifTimeMs(item.topFriendTimeMs)
        "${item.topFriendName}: $raw"
    } else {
        "Solo record"
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("home_standings_board_row_$index")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Muted slate badge for uncontested #1/1 solo records so multi-player podium badges stand out
            Surface(
                color = when {
                    isUncontestedSolo -> MaterialTheme.colorScheme.surfaceContainerHighest
                    leading -> goldAccent.copy(alpha = 0.22f)
                    hasMyEntry -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                },
                shape = CircleShape
            ) {
                Text(
                    text = if (hasMyEntry) "#$myRank/${item.totalDriversOnBoard}" else "BEAT",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = when {
                        isUncontestedSolo -> MaterialTheme.colorScheme.onSurfaceVariant
                        leading -> goldAccent
                        hasMyEntry -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        item.isDaily || isStunts -> item.trackName
                        item.isCustom -> "${item.trackName} • Custom • ${item.totalLaps}L"
                        else -> "${item.trackName} • ${item.totalLaps}L"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "You: $myMetric  •  $friendMetric",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
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
                    shape = CircleShape
                ) {
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
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
private fun CompactStatBar(
    label: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
        )
    }
}

