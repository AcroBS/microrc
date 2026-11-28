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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FriendBoardStandingItem
import com.example.data.FriendStandingsSummary
import com.example.data.PlayStreakState
import com.example.model.DailyWeatherTheme
import com.example.model.GameMode
import com.example.model.PlayerProfile
import com.example.ui.components.drawRcCar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveOnlineColor
import com.example.util.AppUpdateUiState
import com.example.util.GitHubReleaseInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class HomeModeScope(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline (Wi-Fi)"),
    SOLO("Solo Play")
}

internal sealed interface DailyLeadStatus {
    data object YouLead : DailyLeadStatus
    data class FriendLeads(val friendName: String) : DailyLeadStatus
    data object NotPlayedYet : DailyLeadStatus
}

internal fun resolveDailyLeadStatus(
    standing: FriendBoardStandingItem?,
    localBestTimeMs: Long?
): DailyLeadStatus {
    if (standing != null) {
        return when {
            standing.myRank == 1 -> DailyLeadStatus.YouLead
            standing.leaderName.isNotBlank() -> DailyLeadStatus.FriendLeads(standing.leaderName)
            else -> DailyLeadStatus.NotPlayedYet
        }
    }
    return if (localBestTimeMs != null && localBestTimeMs > 0L) {
        DailyLeadStatus.YouLead
    } else {
        DailyLeadStatus.NotPlayedYet
    }
}

internal fun isDailySweepAmongFriends(
    standings: FriendStandingsSummary?,
    friendsCount: Int
): Boolean {
    val gp = standings?.todayDailyGpStanding ?: return false
    val drift = standings.todayDailyDriftStanding ?: return false
    val hasFriends = friendsCount > 0 ||
        standings.totalFriendsCount > 0 ||
        !gp.topFriendName.isNullOrBlank() ||
        !drift.topFriendName.isNullOrBlank()
    return hasFriends && gp.myRank == 1 && drift.myRank == 1 && gp.myTimeMs > 0L && drift.myTimeMs > 0L
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
    dailyChallengeWeather: DailyWeatherTheme = DailyWeatherTheme.CLEAR,
    onOpenDailyDriftChallenge: () -> Unit = {},
    dailyDriftTitle: String = "Daily Drift",
    dailyDriftBestTimeMs: Long? = null,
    dailyDriftWeather: DailyWeatherTheme = DailyWeatherTheme.CLEAR,
    onOpenOnlinePlay: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenFriendStandings: () -> Unit = onOpenFriends,
    onLaunchRivalryGhostRace: (FriendBoardStandingItem) -> Unit = {},
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
    val goldAccent = adaptiveGoldColor()
    val homeInsets = if (hasBottomNav) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    val dailyGpStatus = remember(friendStandings?.todayDailyGpStanding, dailyBestTimeMs) {
        resolveDailyLeadStatus(friendStandings?.todayDailyGpStanding, dailyBestTimeMs)
    }
    val dailyDriftStatus = remember(friendStandings?.todayDailyDriftStanding, dailyDriftBestTimeMs) {
        resolveDailyLeadStatus(friendStandings?.todayDailyDriftStanding, dailyDriftBestTimeMs)
    }
    val dailyGpCompletedToday = remember(friendStandings?.todayDailyGpStanding, dailyBestTimeMs) {
        (dailyBestTimeMs != null && dailyBestTimeMs > 0L) ||
            ((friendStandings?.todayDailyGpStanding?.myTimeMs ?: 0L) > 0L)
    }
    val dailyDriftCompletedToday = remember(friendStandings?.todayDailyDriftStanding, dailyDriftBestTimeMs) {
        (dailyDriftBestTimeMs != null && dailyDriftBestTimeMs > 0L) ||
            ((friendStandings?.todayDailyDriftStanding?.myTimeMs ?: 0L) > 0L)
    }
    val hasDailySweep = remember(friendStandings, friendsCount) {
        isDailySweepAmongFriends(friendStandings, friendsCount)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Compact Hero Banner with top-right Flame Streak Pill + Driver Tag Pill
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

                        // Top-right overlay: Daily Sweep Crown (when #1 in both Dailies) + Flame Streak Pill + Driver Tag Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (hasDailySweep) {
                                DailySweepCrownBadge(goldAccent = goldAccent)
                            }
                            val streakActive = playStreak.currentStreakDays > 0
                            Surface(
                                color = Color(0xFF0E131B).copy(alpha = 0.72f),
                                border = BorderStroke(
                                    1.dp,
                                    if (streakActive) {
                                        goldAccent.copy(alpha = 0.58f)
                                    } else {
                                        Color.White.copy(alpha = 0.22f)
                                    }
                                ),
                                shape = CircleShape,
                                modifier = Modifier.testTag("home_streak_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "Play streak ${playStreak.currentStreakDays} days",
                                        tint = if (streakActive) goldAccent else Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${playStreak.currentStreakDays}d",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (streakActive) Color.White else Color.White.copy(alpha = 0.75f),
                                        maxLines = 1
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(CircleShape)
                                    .clickable { showNameDialog = true }
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
                                        modifier = Modifier.padding(
                                            start = 6.dp,
                                            end = 10.dp,
                                            top = 5.dp,
                                            bottom = 5.dp
                                        )
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

                    // 2. Streamlined Active RC Car Card (car directly on card surface + single-line telemetry)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_driver_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Canvas(
                                modifier = Modifier.size(64.dp)
                            ) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawOval(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.34f),
                                            Color.Transparent
                                        ),
                                        center = Offset(cx + 2.dp.toPx(), cy + 4.dp.toPx()),
                                        radius = size.minDimension * 0.44f
                                    ),
                                    topLeft = Offset(
                                        cx - size.minDimension * 0.40f,
                                        cy - size.minDimension * 0.34f + 3.dp.toPx()
                                    ),
                                    size = Size(size.minDimension * 0.80f, size.minDimension * 0.72f)
                                )
                                val carScale = ((size.minDimension * 0.82f) / 48f).coerceIn(1.7f, 3.2f)
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

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = profile.carType.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_car_telemetry_text"),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HomeCompactStatBar(
                                        label = "SPD",
                                        value = profile.carType.topSpeedStat,
                                        color = MaterialTheme.colorScheme.primary,
                                        testTag = "home_stat_bar_speed",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HomeCompactStatBar(
                                        label = "HDL",
                                        value = profile.carType.handlingStat,
                                        color = goldAccent,
                                        testTag = "home_stat_bar_handling",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HomeCompactStatBar(
                                        label = "GRP",
                                        value = profile.carType.gripStat,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        testTag = "home_stat_bar_grip",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenGarage,
                                shape = RoundedCornerShape(14.dp),
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

                    // 3. Mode Switcher: Online | Offline (Wi-Fi) | Solo Play
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

                    // 4. Flat Mode Content (direct cards on screen background, 12.dp vertical spacing)
                    when (modeScope) {
                        HomeModeScope.ONLINE -> {
                            LiveOnlineGameplayAction(
                                modifier = Modifier.fillMaxWidth(),
                                onOpenOnlinePlay = onOpenOnlinePlay
                            )

                            DailyChallengesAndTracksBlock(
                                modifier = Modifier.fillMaxWidth(),
                                dailyChallengeResetLabel = dailyChallengeResetLabel,
                                dailyGpStatus = dailyGpStatus,
                                dailyDriftStatus = dailyDriftStatus,
                                dailyGpCompletedToday = dailyGpCompletedToday,
                                dailyDriftCompletedToday = dailyDriftCompletedToday,
                                dailyChallengeWeather = dailyChallengeWeather,
                                dailyDriftWeather = dailyDriftWeather,
                                completedAccentColor = onlineAccent,
                                goldAccent = goldAccent,
                                onOpenDailyChallenge = onOpenDailyChallenge,
                                onOpenDailyDriftChallenge = onOpenDailyDriftChallenge,
                                onOpenLeaderboards = onOpenLeaderboards
                            )

                            HomeFiveDayMilestoneBanner(
                                playStreak = playStreak,
                                goldAccent = goldAccent
                            )

                            HomeCrewRankingCard(
                                isSignedIn = isSignedIn,
                                friendsCount = friendsCount,
                                localRecordsCount = localRecordsCount,
                                standings = friendStandings,
                                goldAccent = goldAccent,
                                onOpenFriendStandings = onOpenFriendStandings
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
                                onSoloRaceWithBots = onSoloRaceWithBots
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
private fun LiveOnlineGameplayAction(
    modifier: Modifier = Modifier,
    onOpenOnlinePlay: () -> Unit
) {
    Box(modifier = modifier.testTag("home_online_section")) {
        Button(
            onClick = onOpenOnlinePlay,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(18.dp),
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

@Composable
private fun DailySweepCrownBadge(goldAccent: Color) {
    Surface(
        color = Color(0xFF0E131B).copy(alpha = 0.78f),
        border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.75f)),
        shape = CircleShape,
        modifier = Modifier
            .testTag("home_daily_sweep_crown")
            .semantics(mergeDescendants = true) {
                contentDescription = "Daily Sweep: #1 among friends on Daily GP and Daily Drift"
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val w = size.width
                val h = size.height
                val crownPath = Path().apply {
                    moveTo(w * 0.12f, h * 0.76f)
                    lineTo(w * 0.08f, h * 0.30f)
                    lineTo(w * 0.33f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.67f, h * 0.50f)
                    lineTo(w * 0.92f, h * 0.30f)
                    lineTo(w * 0.88f, h * 0.76f)
                    close()
                }
                drawPath(path = crownPath, color = goldAccent)
                drawPath(
                    path = crownPath,
                    color = Color(0xFFFFF8E1).copy(alpha = 0.65f),
                    style = Stroke(width = w * 0.06f, join = StrokeJoin.Round)
                )
                drawLine(
                    color = goldAccent,
                    start = Offset(w * 0.14f, h * 0.86f),
                    end = Offset(w * 0.86f, h * 0.86f),
                    strokeWidth = h * 0.10f
                )
            }
        }
    }
}

@Composable
private fun DailyChallengesAndTracksBlock(
    modifier: Modifier = Modifier,
    dailyChallengeResetLabel: String,
    dailyGpStatus: DailyLeadStatus,
    dailyDriftStatus: DailyLeadStatus,
    dailyGpCompletedToday: Boolean,
    dailyDriftCompletedToday: Boolean,
    dailyChallengeWeather: DailyWeatherTheme,
    dailyDriftWeather: DailyWeatherTheme,
    completedAccentColor: Color,
    goldAccent: Color,
    onOpenDailyChallenge: () -> Unit,
    onOpenDailyDriftChallenge: () -> Unit,
    onOpenLeaderboards: () -> Unit
) {
    Column(
        modifier = modifier.testTag("home_challenges_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Single slim section label row: DAILY CHALLENGES left, countdown right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAILY CHALLENGES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dailyChallengeResetLabel.isNotBlank()) {
                    Text(
                        text = dailyChallengeResetLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("daily_reset_label")
                    )
                }
            }

            // Two compact side-by-side cards: Daily GP & Daily Drift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DailyChallengeCompactCard(
                    title = "Daily GP",
                    icon = Icons.Default.Today,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    status = dailyGpStatus,
                    completedToday = dailyGpCompletedToday,
                    completedBadgeTag = "daily_gp_completed_badge",
                    weatherTheme = dailyChallengeWeather,
                    weatherBadgeTag = "daily_gp_weather_badge",
                    completedAccentColor = completedAccentColor,
                    goldAccent = goldAccent,
                    onClick = onOpenDailyChallenge,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("daily_challenge_button")
                )

                if (com.example.drift.DriftFeature.ENABLED) {
                    DailyChallengeCompactCard(
                        title = "Daily Drift",
                        icon = Icons.Default.Speed,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        status = dailyDriftStatus,
                        completedToday = dailyDriftCompletedToday,
                        completedBadgeTag = "daily_drift_completed_badge",
                        weatherTheme = dailyDriftWeather,
                        weatherBadgeTag = "daily_drift_weather_badge",
                        completedAccentColor = completedAccentColor,
                        goldAccent = goldAccent,
                        onClick = onOpenDailyDriftChallenge,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("daily_drift_challenge_button")
                    )
                }
            }
        }

        // Full-width Tracks & Ghosts card placed directly on main screen background
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
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
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Leaderboard,
                                contentDescription = "Tracks and Ghosts",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Tracks & Ghosts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Track records, custom circuits & ghost replays",
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DailyChallengeCompactCard(
    title: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    status: DailyLeadStatus,
    completedToday: Boolean,
    completedBadgeTag: String,
    weatherTheme: DailyWeatherTheme,
    weatherBadgeTag: String,
    completedAccentColor: Color,
    goldAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLeading = status is DailyLeadStatus.YouLead
    val borderColor = when {
        isLeading -> goldAccent.copy(alpha = 0.62f)
        completedToday -> completedAccentColor.copy(alpha = 0.56f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val borderWidth = if (isLeading || completedToday) 1.25.dp else 1.dp
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 84.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = iconContainerColor,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (completedToday) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "$title completed today",
                            tint = if (isLeading) goldAccent else completedAccentColor,
                            modifier = Modifier
                                .size(15.dp)
                                .testTag(completedBadgeTag)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    when (status) {
                        DailyLeadStatus.YouLead -> {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "You lead",
                                tint = goldAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "You lead",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = goldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is DailyLeadStatus.FriendLeads -> {
                            Text(
                                text = "${status.friendName} leads",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        DailyLeadStatus.NotPlayedYet -> {
                            Text(
                                text = "Not played yet",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PlusJakartaFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(contentAlignment = Alignment.BottomEnd) {
                    DailyWeatherBadgePill(
                        weatherTheme = weatherTheme,
                        modifier = Modifier.testTag(weatherBadgeTag)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCompactStatBar(
    label: String,
    value: Int,
    color: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val targetFraction = (value / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(350),
        label = "home_stat_$label"
    )
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$label $value out of 100"
                progressBarRangeInfo = ProgressBarRangeInfo(targetFraction, 0f..1f)
            }
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlusJakartaFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
internal fun DailyWeatherBadgePill(
    weatherTheme: DailyWeatherTheme,
    modifier: Modifier = Modifier,
    containerAlpha: Float = 1f
) {
    val (weatherIcon, weatherTint) = when (weatherTheme) {
        DailyWeatherTheme.CLEAR -> Icons.Default.WbSunny to Color(0xFFFFD54F)
        DailyWeatherTheme.NIGHT -> Icons.Default.DarkMode to Color(0xFF90CAF9)
        DailyWeatherTheme.RAIN -> Icons.Default.WaterDrop to Color(0xFF4FC3F7)
        DailyWeatherTheme.SNOW -> Icons.Default.AcUnit to Color(0xFFE1F5FE)
        DailyWeatherTheme.WINDY -> Icons.Default.Air to Color(0xFF80CBC4)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = containerAlpha),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, weatherTint.copy(alpha = 0.42f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = weatherIcon,
                contentDescription = "${weatherTheme.badgeLabel} weather",
                tint = weatherTint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = weatherTheme.badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = PlusJakartaFontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeFiveDayMilestoneBanner(
    playStreak: PlayStreakState,
    goldAccent: Color
) {
    val isMilestone = playStreak.isFiveDayMilestone
    var milestoneDismissed by rememberSaveable(playStreak.currentStreakDays) { mutableStateOf(false) }

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

    AnimatedVisibility(visible = isMilestone && !milestoneDismissed) {
        val burstProgress = milestoneAnim.value
        val confettiPrimary = MaterialTheme.colorScheme.primary
        val confettiTertiary = MaterialTheme.colorScheme.tertiary
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            border = BorderStroke(1.5.dp, goldAccent.copy(alpha = 0.75f)),
            shape = RoundedCornerShape(18.dp),
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
                            .size(38.dp)
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
                                modifier = Modifier.size(20.dp)
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
}

@Composable
private fun HomeCrewRankingCard(
    isSignedIn: Boolean,
    friendsCount: Int,
    localRecordsCount: Int,
    standings: FriendStandingsSummary?,
    goldAccent: Color,
    onOpenFriendStandings: () -> Unit
) {
    val summary = standings ?: FriendStandingsSummary(totalFriendsCount = friendsCount)
    val isLeadingCrew = isSignedIn && friendsCount > 0 && summary.myCrewRank == 1 && summary.leadingBoardsCount > 0
    val topCrewMembers = remember(isSignedIn, friendsCount, summary.crewLeaderboard) {
        if (isSignedIn && friendsCount > 0) {
            summary.crewLeaderboard.take(4)
        } else {
            emptyList()
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            if (isLeadingCrew) {
                goldAccent.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenFriendStandings)
            .testTag("home_streak_standings_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_rivalry_preview_card")
        ) {
            // Compact Card Header: "CREW RANKING" on left + "Track Breakdown >" on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("home_friends_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("home_standings_rank_badge")
                ) {
                    Icon(
                        imageVector = if (isLeadingCrew) Icons.Default.EmojiEvents else Icons.Default.Leaderboard,
                        contentDescription = "Crew Ranking",
                        tint = if (isLeadingCrew) goldAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "CREW RANKING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.testTag("home_rivalry_view_all_button")
                ) {
                    Text(
                        text = if (isSignedIn && friendsCount > 0) "Track Breakdown" else "Open",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Friend Standings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            if (!isSignedIn) {
                val signedOutCopy = if (localRecordsCount > 0) {
                    "Sign in to compare ($localRecordsCount times)"
                } else {
                    "Sign in to rank vs friends"
                }
                Text(
                    text = signedOutCopy,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            } else if (friendsCount == 0) {
                Text(
                    text = "Add friends to compete on the crew leaderboard",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            } else if (topCrewMembers.isEmpty()) {
                val totalRoster = maxOf(summary.totalCrewCount, friendsCount + 1)
                val rank = summary.myCrewRank.coerceAtLeast(1)
                Text(
                    text = "#$rank of $totalRoster Friends • Lead ${summary.leadingBoardsCount} • Chase ${summary.trailingBoardsCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    topCrewMembers.forEachIndexed { idx, member ->
                        val rankColor = when (member.rank) {
                            1 -> goldAccent
                            2 -> MaterialTheme.colorScheme.secondary
                            3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (member.isSelf) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                                .testTag("home_crew_ranking_row_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatHomeOrdinalRank(member.rank),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = rankColor,
                                modifier = Modifier.width(34.dp)
                            )

                            Text(
                                text = if (member.isSelf) "${member.displayName} (You)" else member.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (member.isSelf || member.rank == 1) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "Lead ${member.contestedWins} • Chase ${member.trailingBoardsCount}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = if (member.rank == 1) goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatHomeOrdinalRank(rank: Int): String {
    val mod100 = rank % 100
    val suffix = if (mod100 in 11..13) {
        "th"
    } else {
        when (rank % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$rank$suffix"
}

@Composable
private fun OfflineEntrySection(
    modifier: Modifier = Modifier,
    onHostRace: () -> Unit,
    onJoinRace: () -> Unit
) {
    Row(
        modifier = modifier.testTag("home_offline_section"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onHostRace,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(18.dp),
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .weight(1f)
                .height(68.dp)
                .clip(RoundedCornerShape(18.dp))
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

@Composable
private fun SoloPlaySection(
    modifier: Modifier = Modifier,
    onPureSoloRace: () -> Unit,
    onSoloRaceWithBots: (Int) -> Unit
) {
    var includeBots by rememberSaveable { mutableStateOf(false) }
    var botCount by rememberSaveable { mutableIntStateOf(3) }

    Column(
        modifier = modifier.testTag("home_solo_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Include AI Bots toggle/stepper directly on screen background
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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

        // 2. Primary CTA: Solo Time Trial / Quick Race (Hybrid Tour is selectable inside Solo Race Setup)
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
            shape = RoundedCornerShape(18.dp),
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
                            text = "Practice lines, Hybrid Tour or race AI bots",
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
