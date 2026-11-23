package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChallengeInviteResponse
import com.example.model.FriendOnlineAlert
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.LeaderboardNotificationDoc
import com.example.model.PendingChallengePhase
import com.example.model.PendingFriendChallengeState
import com.example.model.SessionInviteDoc
import com.example.ui.theme.adaptiveOnlineColor
import com.example.util.InviteLinkHelper
import com.example.util.SessionNotificationHelper

private val WhatsAppAccent = Color(0xFF25D366)

@Composable
fun LiveSessionInviteBanner(
    invite: SessionInviteDoc?,
    onAccept: (SessionInviteDoc) -> Unit,
    onDecline: (SessionInviteDoc) -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarInsets: Boolean = true
) {
    AnimatedVisibility(
        visible = invite != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = invite ?: return@AnimatedVisibility
        val modeLabel = runCatching { GameMode.valueOf(current.gameMode).displayName }
            .getOrDefault(current.gameMode)
        val avatarBg = Color(current.senderColorArgb)
        val avatarFg = if (avatarBg.luminance() > 0.45f) Color(0xFF101318) else Color.White

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("live_session_invite_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(avatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Game Session Invite",
                                tint = avatarFg,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${current.senderName} invited you to join!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${current.roomName} • #${current.roomCode} • $modeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDecline(current) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("decline_session_invite_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decline")
                        }
                        Button(
                            onClick = { onAccept(current) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(48.dp)
                                .testTag("accept_session_invite_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Join Lobby")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveLeaderboardNotificationBanner(
    notification: LeaderboardNotificationDoc?,
    onViewLeaderboard: (LeaderboardNotificationDoc) -> Unit,
    onDismiss: (LeaderboardNotificationDoc) -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarInsets: Boolean = true
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = notification ?: return@AnimatedVisibility
        val scoreLabel = if (current.gameMode == GameMode.STUNTS.name) {
            "${current.bestScore} PTS"
        } else {
            SessionNotificationHelper.formatNotifTimeMs(current.bestRaceTimeMs)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("live_leaderboard_notification_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Friend Leaderboard Record",
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${current.senderName} set a new record!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${current.trackName} • $scoreLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDismiss(current) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dismiss_leaderboard_notif_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dismiss")
                        }
                        Button(
                            onClick = { onViewLeaderboard(current) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(48.dp)
                                .testTag("view_leaderboard_notif_button")
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Board")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveFriendOnlineBanner(
    alert: FriendOnlineAlert?,
    onlineFriends: List<FriendUiModel> = emptyList(),
    alreadyInOnlineRoom: Boolean = false,
    compact: Boolean = false,
    onChallengeFriends: (List<FriendUiModel>) -> Unit,
    onDismiss: (FriendOnlineAlert) -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarInsets: Boolean = true
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = alert ?: return@AnimatedVisibility
        val onlineAccent = adaptiveOnlineColor()
        val avatarBg = Color(current.primaryColorArgb)
        val avatarFg = if (avatarBg.luminance() > 0.45f) Color(0xFF101318) else Color.White

        val fallbackAlertFriend = remember(current) {
            FriendUiModel(
                friendshipId = "alert_${current.friendUid}",
                friendUid = current.friendUid,
                displayName = current.displayName,
                friendCode = "",
                carType = current.carType,
                primaryColorArgb = current.primaryColorArgb,
                accentColorArgb = current.accentColorArgb,
                effectivelyOnline = true,
                activityStatus = current.activityStatus,
                activeRoomId = if (current.activeRoomCode.isNotBlank()) "room_${current.activeRoomCode}" else "",
                activeRoomCode = current.activeRoomCode,
                activeRoomName = current.activeRoomName,
                statusLabel = "Online"
            )
        }
        val availableFriends = remember(current, onlineFriends) {
            val merged = LinkedHashMap<String, FriendUiModel>()
            merged[current.friendUid] = onlineFriends.firstOrNull {
                it.friendUid == current.friendUid && it.effectivelyOnline
            } ?: fallbackAlertFriend
            onlineFriends
                .filter { it.effectivelyOnline && it.friendUid != current.friendUid }
                .forEach { friend -> merged[friend.friendUid] = friend }
            merged.values.toList()
        }

        var selectedUids by remember(current.friendUid) {
            mutableStateOf(setOf(current.friendUid))
        }
        val validSelectedUids = remember(selectedUids, availableFriends, current.friendUid) {
            val filtered = selectedUids.filter { uid -> availableFriends.any { it.friendUid == uid } }
            if (filtered.isEmpty()) setOf(current.friendUid) else filtered.take(3).toSet()
        }
        val selectedFriends = remember(availableFriends, validSelectedUids) {
            availableFriends.filter { it.friendUid in validSelectedUids }.take(3)
        }

        val hasMultipleOnline = availableFriends.size > 1
        val titleText = if (hasMultipleOnline) {
            "${current.displayName} + ${availableFriends.size - 1} online"
        } else {
            "${current.displayName} came online!"
        }
        val subtitleText = when {
            hasMultipleOnline -> "Pick up to 3 friends for a joint lobby challenge"
            current.hasJoinableLobby -> "In Lobby #${current.activeRoomCode} • Challenge to team up"
            alreadyInOnlineRoom -> "Ready to race • Challenge into your lobby"
            else -> "Ready to race • Notify & wait for response"
        }
        val actionLabel = if (selectedFriends.size > 1) {
            "Challenge (${selectedFriends.size})"
        } else {
            "Challenge"
        }

        val containerFg = if (compact) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = if (compact) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.2.dp, onlineAccent.copy(alpha = 0.8f)),
                modifier = Modifier
                    .widthIn(max = if (compact) 480.dp else 600.dp)
                    .fillMaxWidth()
                    .testTag("live_friend_online_banner")
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = if (compact) 12.dp else 14.dp,
                        end = if (compact) 8.dp else 14.dp,
                        top = if (compact) 8.dp else 14.dp,
                        bottom = if (compact) 8.dp else 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
                ) {
                    if (compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(avatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsCar,
                                        contentDescription = "Friend Came Online",
                                        tint = avatarFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(onlineAccent)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = containerFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onlineAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onChallengeFriends(selectedFriends) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("challenge_friend_online_button")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(actionLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { onDismiss(current) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dismiss_friend_online_button")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss online alert",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(avatarBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsCar,
                                        contentDescription = "Friend Came Online",
                                        tint = avatarFg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(onlineAccent)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = containerFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = containerFg.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (hasMultipleOnline) {
                        val allTargetUids = availableFriends.take(3).map { it.friendUid }.toSet()
                        val allSelected = validSelectedUids == allTargetUids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("friend_online_multi_select_row"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = allSelected,
                                onClick = { selectedUids = allTargetUids },
                                label = {
                                    Text(
                                        text = "All (${allTargetUids.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = if (allSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Groups,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.testTag("friend_online_select_all_chip")
                            )

                            availableFriends.forEach { friend ->
                                val isSelected = friend.friendUid in validSelectedUids
                                val canSelectMore = validSelectedUids.size < 3
                                FilterChip(
                                    selected = isSelected,
                                    enabled = isSelected || canSelectMore,
                                    onClick = {
                                        selectedUids = if (isSelected) {
                                            if (validSelectedUids.size > 1) {
                                                validSelectedUids - friend.friendUid
                                            } else {
                                                validSelectedUids
                                            }
                                        } else if (canSelectMore) {
                                            validSelectedUids + friend.friendUid
                                        } else {
                                            validSelectedUids
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = friend.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else onlineAccent)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("friend_online_select_chip_${friend.friendUid}")
                                )
                            }
                        }
                    }

                    if (!compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDismiss(current) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dismiss_friend_online_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Later")
                            }
                            Button(
                                onClick = { onChallengeFriends(selectedFriends) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .height(48.dp)
                                    .testTag("challenge_friend_online_button")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(actionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LivePendingChallengeBanner(
    state: PendingFriendChallengeState?,
    compact: Boolean = false,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarInsets: Boolean = true
) {
    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val current = state ?: return@AnimatedVisibility
        val targetNamesLabel = remember(current.targets) {
            when (current.targets.size) {
                0 -> "friend"
                1 -> current.targets.first().displayName
                2 -> "${current.targets[0].displayName} & ${current.targets[1].displayName}"
                else -> "${current.targets.first().displayName} + ${current.targets.size - 1} friends"
            }
        }

        val isErrorOutcome = current.phase == PendingChallengePhase.TIMED_OUT ||
            current.phase == PendingChallengePhase.ALL_DECLINED
        val surfaceBg = when {
            isErrorOutcome -> MaterialTheme.colorScheme.errorContainer
            compact -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }
        val surfaceFg = when {
            isErrorOutcome -> MaterialTheme.colorScheme.onErrorContainer
            compact -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }
        val borderTint = when {
            isErrorOutcome -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        }

        val headlineText = when (current.phase) {
            PendingChallengePhase.SENDING -> "Sending challenge to $targetNamesLabel..."
            PendingChallengePhase.WAITING -> "Waiting for $targetNamesLabel..."
            PendingChallengePhase.TIMED_OUT -> "No response from $targetNamesLabel"
            PendingChallengePhase.ALL_DECLINED -> "Challenge declined"
        }

        val detailText = when (current.phase) {
            PendingChallengePhase.SENDING -> "Preparing private lobby..."
            PendingChallengePhase.WAITING -> if (current.targets.size > 1) {
                current.targets.joinToString(" • ") { t ->
                    val statusTag = when (t.response) {
                        ChallengeInviteResponse.ACCEPTED -> "Joined"
                        ChallengeInviteResponse.DECLINED -> "Declined"
                        ChallengeInviteResponse.EXPIRED -> "Expired"
                        ChallengeInviteResponse.PENDING -> "Waiting"
                    }
                    "${t.displayName}: $statusTag"
                }
            } else {
                "Lobby #${current.roomCode} ready when accepted"
            }
            PendingChallengePhase.TIMED_OUT -> "Challenge timed out after ${current.totalSeconds}s"
            PendingChallengePhase.ALL_DECLINED -> "Friends are currently busy"
        }

        val progress = if (current.totalSeconds > 0 && current.phase == PendingChallengePhase.WAITING) {
            (current.remainingSeconds.toFloat() / current.totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else if (current.phase == PendingChallengePhase.SENDING) {
            1f
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = surfaceBg,
                shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.2.dp, borderTint),
                modifier = Modifier
                    .widthIn(max = if (compact) 480.dp else 600.dp)
                    .fillMaxWidth()
                    .testTag("live_pending_challenge_banner")
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = if (compact) 12.dp else 14.dp,
                        end = if (compact) 8.dp else 14.dp,
                        top = if (compact) 8.dp else 12.dp,
                        bottom = if (compact) 8.dp else 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isErrorOutcome) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (current.phase) {
                                PendingChallengePhase.SENDING -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                                PendingChallengePhase.WAITING -> Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = "Waiting for friend response",
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                PendingChallengePhase.TIMED_OUT -> Icon(
                                    Icons.Default.TimerOff,
                                    contentDescription = "Challenge timed out",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(18.dp)
                                )
                                PendingChallengePhase.ALL_DECLINED -> Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Challenge declined",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headlineText,
                                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                color = surfaceFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = detailText,
                                style = MaterialTheme.typography.labelSmall,
                                color = surfaceFg.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (current.phase == PendingChallengePhase.WAITING) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${current.remainingSeconds}s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = surfaceFg,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("pending_challenge_timer_text")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("cancel_friend_challenge_button")
                        ) {
                            Text(
                                text = if (isErrorOutcome) "Dismiss" else "Cancel",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (current.phase == PendingChallengePhase.WAITING || current.phase == PendingChallengePhase.SENDING) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShareLobbyInviteDialog(
    roomName: String,
    roomCode: String,
    gameMode: String,
    hostName: String,
    passwordProtected: Boolean,
    passwordHash: String,
    friends: List<FriendUiModel>,
    onNotifyFriend: (FriendUiModel) -> Unit,
    onNotifyAllOnline: (List<FriendUiModel>) -> Unit,
    onOpenFriendsScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var includeUnlockToken by remember(passwordProtected) { mutableStateOf(passwordProtected) }
    var copiedLink by remember { mutableStateOf(false) }
    var notifiedFriendIds by remember { mutableStateOf(setOf<String>()) }

    val effectiveToken = if (passwordProtected && includeUnlockToken) passwordHash else ""
    val inviteUrl = remember(roomCode, effectiveToken) {
        InviteLinkHelper.buildInviteUrl(roomCode = roomCode, joinToken = effectiveToken)
    }
    val shareMessage = remember(roomName, roomCode, gameMode, hostName, effectiveToken) {
        InviteLinkHelper.buildShareMessage(
            roomName = roomName,
            roomCode = roomCode,
            gameMode = gameMode,
            hostName = hostName,
            joinToken = effectiveToken
        )
    }
    val onlineFriends = remember(friends) { friends.filter { it.effectivelyOnline } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text("Invite to Lobby #$roomCode", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Shareable Link Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SESSION INVITE LINK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = inviteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (passwordProtected && passwordHash.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1-Tap Private Unlock Link",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Let link recipients join without typing the room password",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeUnlockToken,
                            onCheckedChange = { includeUnlockToken = it },
                            modifier = Modifier.testTag("toggle_invite_unlock_token")
                        )
                    }
                }

                // Primary WhatsApp Share CTA
                Button(
                    onClick = { InviteLinkHelper.shareViaWhatsApp(context, shareMessage) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppAccent,
                        contentColor = Color(0xFF071A0E)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("share_invite_whatsapp_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Link via WhatsApp", style = MaterialTheme.typography.labelLarge)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            InviteLinkHelper.copyToClipboard(context, "MicroRC Invite Link", inviteUrl)
                            copiedLink = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_invite_link_button")
                    ) {
                        Icon(
                            if (copiedLink) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedLink) "Copied!" else "Copy Link")
                    }

                    OutlinedButton(
                        onClick = { InviteLinkHelper.shareViaSystemChooser(context, shareMessage) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_invite_more_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("More Apps")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Direct Friends List Notification Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "NOTIFY FRIENDS (${onlineFriends.size} ONLINE)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (onlineFriends.size > 1) {
                        TextButton(
                            onClick = {
                                onNotifyAllOnline(onlineFriends)
                                notifiedFriendIds = notifiedFriendIds + onlineFriends.map { it.friendUid }
                            },
                            modifier = Modifier.testTag("lobby_notify_all_online_button")
                        ) {
                            Text("Notify All")
                        }
                    }
                }

                if (friends.isEmpty()) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenFriendsScreen()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("invite_dialog_open_friends_button")
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Friends List & Add Crew")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.take(8).forEach { friend ->
                            val sent = friend.friendUid in notifiedFriendIds
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val onlineDot = adaptiveOnlineColor()
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (friend.effectivelyOnline) {
                                                        onlineDot
                                                    } else {
                                                        MaterialTheme.colorScheme.outline
                                                    }
                                                )
                                        )
                                        Column {
                                            Text(
                                                text = friend.displayName,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = friend.statusLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (friend.effectivelyOnline) {
                                                    onlineDot
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            onNotifyFriend(friend)
                                            notifiedFriendIds = notifiedFriendIds + friend.friendUid
                                        },
                                        enabled = !sent,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("lobby_notify_friend_${friend.friendUid}")
                                    ) {
                                        Text(
                                            text = if (sent) "Sent!" else "Notify",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_share_invite_dialog_button")
            ) {
                Text("Done")
            }
        }
    )
}
