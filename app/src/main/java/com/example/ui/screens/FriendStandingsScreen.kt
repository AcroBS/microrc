package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FriendBoardStandingItem
import com.example.data.FriendCrewMemberRank
import com.example.data.FriendStandingsCalculator
import com.example.data.FriendStandingsSummary
import com.example.data.StandingsDistanceFilter
import com.example.model.GameMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.adaptiveGhostAccentColor
import com.example.ui.theme.adaptiveGoldColor

@Composable
fun FriendStandingsScreen(
    standings: FriendStandingsSummary,
    isSignedIn: Boolean,
    friendsCount: Int,
    ghostLoadingId: String? = null,
    onOpenManageFriends: () -> Unit,
    onLaunchGhostRace: (FriendBoardStandingItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val goldAccent = adaptiveGoldColor()
    val ghostAccent = adaptiveGhostAccentColor()

    val hasStuntBoards = remember(standings) {
        standings.allBoards.any { it.gameMode == GameMode.STUNTS }
    }
    val filterOptions = remember(hasStuntBoards) {
        if (hasStuntBoards) {
            StandingsDistanceFilter.entries
        } else {
            listOf(
                StandingsDistanceFilter.ALL,
                StandingsDistanceFilter.ONE_LAP,
                StandingsDistanceFilter.THREE_LAPS,
                StandingsDistanceFilter.FIVE_LAPS
            )
        }
    }
    var selectedFilter by rememberSaveable { mutableStateOf(StandingsDistanceFilter.ALL) }
    LaunchedEffect(hasStuntBoards) {
        if (!hasStuntBoards && selectedFilter == StandingsDistanceFilter.STUNTS) {
            selectedFilter = StandingsDistanceFilter.ALL
        }
    }

    val chasingBoards = remember(standings, selectedFilter) {
        FriendStandingsCalculator.filterBoards(
            FriendStandingsCalculator.chasingBoards(standings),
            selectedFilter
        )
    }
    val leadingBoards = remember(standings, selectedFilter) {
        FriendStandingsCalculator.filterBoards(
            FriendStandingsCalculator.leadingBoards(standings),
            selectedFilter
        )
    }
    val unchallengedBoards = remember(standings, selectedFilter) {
        FriendStandingsCalculator.filterBoards(
            FriendStandingsCalculator.unchallengedFriendBoards(standings),
            selectedFilter
        )
    }
    val soloOnlyBoards = remember(standings, selectedFilter) {
        FriendStandingsCalculator.filterBoards(
            FriendStandingsCalculator.soloOnlyBoards(standings),
            selectedFilter
        )
    }
    val unracedBuiltInBoards = remember(standings, selectedFilter) {
        FriendStandingsCalculator.unracedBuiltInRaceBoards(standings, selectedFilter)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            // Top Bar: Back + "Friend Standings" + "+ Add / Manage"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("standings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "Friend Standings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalButton(
                    onClick = onOpenManageFriends,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("standings_manage_friends_button")
                ) {
                    Text(
                        text = "+ Add / Manage",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isSignedIn || friendsCount == 0) {
                    StandingsEmptyCrewCard(
                        isSignedIn = isSignedIn,
                        onOpenManageFriends = onOpenManageFriends
                    )
                } else {
                    // 1. Overall Crew Ranking Card
                    OverallCrewRankingCard(
                        crewLeaderboard = standings.crewLeaderboard,
                        goldAccent = goldAccent
                    )

                    // 2. Compact Race Distance Filter Pill Bar (All | 1L | 3L | 5L [+ Stunts])
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("standings_filter_bar")
                    ) {
                        filterOptions.forEachIndexed { index, option ->
                            val selected = selectedFilter == option
                            SegmentedButton(
                                selected = selected,
                                onClick = { selectedFilter = option },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = filterOptions.size
                                ),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("standings_filter_${option.testSuffix}")
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 3. Interactive "Chasing (Y)" Section — single-line compact status when 0
                    if (chasingBoards.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("standings_chasing_section"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Chasing (0) • Clean sweep!",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("standings_chasing_empty_line")
                            )
                        }
                    } else {
                        RivalryBoardSection(
                            title = "Chasing (${chasingBoards.size})",
                            subtitle = "Tap any track to race rival ghost",
                            emptyText = "",
                            items = chasingBoards,
                            headerIcon = Icons.AutoMirrored.Filled.TrendingUp,
                            isLeadingSection = false,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            ghostAccent = ghostAccent,
                            goldAccent = goldAccent,
                            ghostLoadingId = ghostLoadingId,
                            sectionTag = "standings_chasing_section",
                            rowTagPrefix = "standings_chasing_row",
                            onLaunchGhostRace = onLaunchGhostRace
                        )
                    }

                    // 4. Interactive "Leading (X)" Section
                    RivalryBoardSection(
                        title = "Leading (${leadingBoards.size})",
                        subtitle = if (leadingBoards.isNotEmpty()) "Tap any track to extend your lead" else null,
                        emptyText = if (selectedFilter == StandingsDistanceFilter.ALL) {
                            "No contested track leads yet — beat a friend's time to claim #1!"
                        } else {
                            "No contested ${selectedFilter.label} leads yet — beat a friend's time to claim #1!"
                        },
                        items = leadingBoards,
                        headerIcon = Icons.Default.EmojiEvents,
                        isLeadingSection = true,
                        accentColor = goldAccent,
                        ghostAccent = ghostAccent,
                        goldAccent = goldAccent,
                        ghostLoadingId = ghostLoadingId,
                        sectionTag = "standings_leading_section",
                        rowTagPrefix = "standings_leading_row",
                        onLaunchGhostRace = onLaunchGhostRace
                    )

                    // 5. Optional Friend Benchmarks (tracks where friends raced and you haven't set a time yet)
                    if (unchallengedBoards.isNotEmpty()) {
                        RivalryBoardSection(
                            title = "Friend Benchmarks (${unchallengedBoards.size})",
                            subtitle = "Unchallenged tracks set by your crew",
                            emptyText = "",
                            items = unchallengedBoards,
                            headerIcon = Icons.Default.Flag,
                            isLeadingSection = false,
                            accentColor = MaterialTheme.colorScheme.primary,
                            ghostAccent = ghostAccent,
                            goldAccent = goldAccent,
                            ghostLoadingId = ghostLoadingId,
                            sectionTag = "standings_unchallenged_section",
                            rowTagPrefix = "standings_unchallenged_row",
                            onLaunchGhostRace = onLaunchGhostRace
                        )
                    }

                    // 6. Your Solo PBs (tracks you raced without friend entries yet, e.g. newly raced circuits)
                    if (soloOnlyBoards.isNotEmpty()) {
                        RivalryBoardSection(
                            title = "Your Solo PBs (${soloOnlyBoards.size})",
                            subtitle = "Waiting for crew challengers",
                            emptyText = "",
                            items = soloOnlyBoards,
                            headerIcon = Icons.Default.Timer,
                            isLeadingSection = false,
                            accentColor = MaterialTheme.colorScheme.primary,
                            ghostAccent = ghostAccent,
                            goldAccent = goldAccent,
                            ghostLoadingId = ghostLoadingId,
                            sectionTag = "standings_solo_section",
                            rowTagPrefix = "standings_solo_row",
                            onLaunchGhostRace = onLaunchGhostRace
                        )
                    }

                    // 7. Unraced Built-In Circuits (ensures complete track coverage, including Skyway Crossover)
                    if (unracedBuiltInBoards.isNotEmpty()) {
                        RivalryBoardSection(
                            title = "Open Circuits (${unracedBuiltInBoards.size})",
                            subtitle = "No crew times yet • Tap to race",
                            emptyText = "",
                            items = unracedBuiltInBoards,
                            headerIcon = Icons.Default.Schedule,
                            isLeadingSection = false,
                            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ghostAccent = ghostAccent,
                            goldAccent = goldAccent,
                            ghostLoadingId = ghostLoadingId,
                            sectionTag = "standings_unraced_section",
                            rowTagPrefix = "standings_unraced_row",
                            onLaunchGhostRace = onLaunchGhostRace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StandingsEmptyCrewCard(
    isSignedIn: Boolean,
    onOpenManageFriends: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("standings_empty_crew_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                text = if (!isSignedIn) "Sign in to Track Friend Rivalries" else "Add Friends to Start a Crew Ranking",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (!isSignedIn) {
                    "Sign in with Google to compare track times with friends, see who leads each circuit, and launch ghost races directly."
                } else {
                    "Share your friend code or add racers from Friends & Crew to unlock head-to-head Lead and Chase standings."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenManageFriends,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("standings_empty_cta_button")
            ) {
                Text(
                    text = if (!isSignedIn) "Open Friends & Sign In" else "Open Friends & Crew",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun OverallCrewRankingCard(
    crewLeaderboard: List<FriendCrewMemberRank>,
    goldAccent: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("standings_crew_ranking_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "OVERALL CREW RANKING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${crewLeaderboard.size} Drivers",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                crewLeaderboard.forEachIndexed { idx, member ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                    val rankColor = when (member.rank) {
                        1 -> goldAccent
                        2 -> MaterialTheme.colorScheme.secondary
                        3 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (member.isSelf) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .testTag("standings_crew_row_${member.userId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatOrdinalRank(member.rank),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = rankColor,
                            modifier = Modifier.width(38.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (member.isSelf) "${member.displayName} (You)" else member.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (member.isSelf || member.rank == 1) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

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

@Composable
private fun RivalryBoardSection(
    title: String,
    subtitle: String?,
    emptyText: String,
    items: List<FriendBoardStandingItem>,
    headerIcon: ImageVector,
    isLeadingSection: Boolean,
    accentColor: Color,
    ghostAccent: Color,
    goldAccent: Color,
    ghostLoadingId: String?,
    sectionTag: String,
    rowTagPrefix: String,
    onLaunchGhostRace: (FriendBoardStandingItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(sectionTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = PlusJakartaFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (items.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlusJakartaFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(
                    1.dp,
                    if (isLeadingSection) goldAccent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                        val isThisLoading = ghostLoadingId != null &&
                            item.topFriendGhostId != null &&
                            ghostLoadingId == item.topFriendGhostId
                        val deltaLine = FriendStandingsCalculator.formatRivalryDeltaLine(item)
                        val modeTag = buildStandingsModeBadge(item)
                        val timesLine = buildStandingsTimesSubtitle(item)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 64.dp)
                                .clickable(enabled = ghostLoadingId == null) {
                                    onLaunchGhostRace(item)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("${rowTagPrefix}_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.trackName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = modeTag,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                val deltaColor = when {
                                    item.myRank == 1 && item.topFriendName != null -> goldAccent
                                    item.myRank != null && item.myRank > 1 -> MaterialTheme.colorScheme.secondary
                                    item.myRank == 1 -> MaterialTheme.colorScheme.primary
                                    item.topFriendName != null -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = deltaLine,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (item.topFriendName != null || item.myRank != null) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        }
                                    ),
                                    color = deltaColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (timesLine.isNotBlank()) {
                                    Text(
                                        text = timesLine,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val isContestedLead = item.myRank == 1 && item.topFriendName != null
                            val isUnraced = item.myRank == null && item.topFriendName == null
                            val actionBg = when {
                                isContestedLead -> goldAccent.copy(alpha = 0.16f)
                                isUnraced -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else -> ghostAccent.copy(alpha = 0.16f)
                            }
                            val actionTint = when {
                                isContestedLead -> goldAccent
                                isUnraced -> MaterialTheme.colorScheme.primary
                                else -> ghostAccent
                            }

                            Surface(
                                color = actionBg,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isThisLoading) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = ghostAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Race ghost on ${item.trackName}",
                                            tint = actionTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildStandingsModeBadge(item: FriendBoardStandingItem): String {
    return when {
        item.isDaily && item.gameMode == GameMode.DRIFT -> "Daily Drift"
        item.isDaily -> "Daily GP"
        item.gameMode == GameMode.DRIFT -> "Drift ${item.totalLaps}L"
        item.gameMode == GameMode.STUNTS -> "Stunts"
        item.isCustom -> "Custom ${item.totalLaps}L"
        else -> "${item.totalLaps}L"
    }
}

private fun buildStandingsTimesSubtitle(item: FriendBoardStandingItem): String {
    if (item.gameMode == GameMode.STUNTS) {
        return when {
            item.myScore > 0 && item.topFriendName != null && item.topFriendScore > 0 ->
                "You ${item.myScore} pts • ${item.topFriendName} ${item.topFriendScore} pts"
            item.topFriendName != null && item.topFriendScore > 0 ->
                "${item.topFriendName} ${item.topFriendScore} pts"
            else -> ""
        }
    }
    return when {
        item.myTimeMs > 0L && item.topFriendName != null && item.topFriendTimeMs > 0L ->
            "You ${FriendStandingsCalculator.formatCompactTime(item.myTimeMs)} • ${item.topFriendName} ${FriendStandingsCalculator.formatCompactTime(item.topFriendTimeMs)}"
        item.topFriendName != null && item.topFriendTimeMs > 0L ->
            "${item.topFriendName} ${FriendStandingsCalculator.formatCompactTime(item.topFriendTimeMs)}"
        else -> ""
    }
}

private fun formatOrdinalRank(rank: Int): String {
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
