package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CustomTrackData
import com.example.model.DriverProfileDoc
import com.example.model.FriendUiModel
import com.example.model.FriendshipDoc
import com.example.model.OnlineRoomDoc
import com.example.model.PlayerProfile
import com.example.model.RcCarType
import com.example.ui.components.drawRcCar
import com.example.ui.theme.adaptiveOnlineColor
import com.example.util.InviteLinkHelper
import com.example.util.SessionNotificationHelper

private val WhatsAppGreen = Color(0xFF25D366)

@Composable
fun FriendsScreen(
    profile: PlayerProfile,
    myFriendCode: String,
    activeOnlineRoom: OnlineRoomDoc?,
    friends: List<FriendUiModel>,
    friendCustomTracksByUid: Map<String, List<CustomTrackData>> = emptyMap(),
    incomingRequests: List<FriendshipDoc>,
    outgoingRequests: List<FriendshipDoc>,
    searchResults: List<DriverProfileDoc>,
    isSearching: Boolean,
    feedbackMessage: String?,
    onDismissFeedback: () -> Unit,
    onAddFriendByCode: (String) -> Unit,
    onSearchDrivers: (String) -> Unit,
    onAddDriverFromSearch: (DriverProfileDoc) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineOrRemoveFriendship: (String, String) -> Unit,
    onNotifyFriendToJoin: (FriendUiModel) -> Unit,
    onNotifyAllOnlineFriends: (List<FriendUiModel>) -> Unit,
    onJoinFriendLobby: (String) -> Unit,
    onChallengeFriendCustomTrack: (CustomTrackData) -> Unit = {},
    onOpenOnlineHub: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var queryOrCode by remember { mutableStateOf("") }
    var copiedMyCode by remember { mutableStateOf(false) }
    var friendToRemove by remember { mutableStateOf<FriendUiModel?>(null) }
    var hasNotifPerm by remember(context) {
        mutableStateOf(SessionNotificationHelper.hasNotificationPermission(context))
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (granted) {
            SessionNotificationHelper.ensureChannel(context)
        }
    }

    val onlineFriends = remember(friends) { friends.filter { it.effectivelyOnline } }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 660.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("friends_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = "FRIENDS & CREW",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${onlineFriends.size} Online • ${friends.size} Total Friends",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (onlineFriends.isNotEmpty()) adaptiveOnlineColor() else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenOnlineHub,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("friends_open_online_hub_button")
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeOnlineRoom != null) "Lobby #${activeOnlineRoom.roomCode}" else "Online Hub",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (!feedbackMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissFeedback() }
                            .testTag("friends_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "OK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }

                // Android 13+ Push Alert Permission Prompt
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Friend & Record Alerts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Get system notifications when friends post new track times or invite you to race.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("enable_invite_notifications_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                // 1. My Friend Code Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "YOUR FRIEND CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = myFriendCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("my_friend_code_text")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        InviteLinkHelper.copyToClipboard(context, "MicroRC Friend Code", myFriendCode)
                                        copiedMyCode = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("copy_my_friend_code_button")
                                ) {
                                    Icon(
                                        if (copiedMyCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy Friend Code",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedMyCode) "Copied" else "Copy")
                                }
                                Button(
                                    onClick = {
                                        val msg = InviteLinkHelper.buildFriendCodeShareMessage(profile.name, myFriendCode)
                                        InviteLinkHelper.shareViaWhatsApp(context, msg)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WhatsAppGreen,
                                        contentColor = Color(0xFF071A0E)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("share_my_friend_code_whatsapp_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("WhatsApp")
                                }
                            }
                        }
                    }
                }

                // 2. Add Friend by Code or Driver Search Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
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
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ADD FRIEND BY CODE OR CALLSIGN",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = queryOrCode,
                            onValueChange = { if (it.length <= 24) queryOrCode = it },
                            label = { Text("Friend Code (e.g. RC7K9P2M) or Driver Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("friend_search_or_code_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clean = queryOrCode.trim()
                                    if (clean.isNotEmpty()) {
                                        onAddFriendByCode(clean)
                                    }
                                },
                                enabled = queryOrCode.trim().length >= 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_friend_by_code_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add by Code")
                            }
                            FilledTonalButton(
                                onClick = { onSearchDrivers(queryOrCode) },
                                enabled = queryOrCode.trim().isNotEmpty() && !isSearching,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("search_drivers_button")
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Search Drivers")
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                searchResults.forEach { driver ->
                                    val alreadyFriend = friends.any { it.friendUid == driver.userId }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = driver.driverName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Code: ${driver.friendCode}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { onAddDriverFromSearch(driver) },
                                                enabled = !alreadyFriend,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("add_search_driver_${driver.userId}")
                                            ) {
                                                Text(if (alreadyFriend) "Friends" else "+ Add")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Pending Incoming / Outgoing Friend Requests
                if (incomingRequests.isNotEmpty() || outgoingRequests.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "FRIEND REQUESTS (${incomingRequests.size + outgoingRequests.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            incomingRequests.forEach { req ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = req.requesterName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Code: ${req.requesterFriendCode} • Wants to race!",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = { onDeclineOrRemoveFriendship(req.id, "Declined request.") },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("decline_friend_req_${req.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Decline Friend Request",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { onAcceptRequest(req.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("accept_friend_req_${req.id}")
                                            ) {
                                                Text("Accept")
                                            }
                                        }
                                    }
                                }
                            }
                            outgoingRequests.forEach { req ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Sent to ${req.receiverName} (${req.receiverFriendCode}) • Pending",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { onDeclineOrRemoveFriendship(req.id, "Cancelled request.") },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Friends List Header + Notify All Online Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FRIENDS LIST (${friends.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onlineFriends.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { onNotifyAllOnlineFriends(onlineFriends) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("notify_all_online_friends_button")
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeOnlineRoom != null) {
                                    "Notify Online (${onlineFriends.size})"
                                } else {
                                    "Host & Notify Online (${onlineFriends.size})"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (friends.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Your Friends List is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Share your Friend Code via WhatsApp above, or enter a friend's code to see their live online status and notify them to race together!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        friends.forEach { friend ->
                            FriendStatusCard(
                                friend = friend,
                                customTracks = friendCustomTracksByUid[friend.friendUid].orEmpty(),
                                currentRoomCode = activeOnlineRoom?.roomCode,
                                onNotifyToJoin = { onNotifyFriendToJoin(friend) },
                                onJoinFriendLobby = { onJoinFriendLobby(friend.activeRoomCode) },
                                onChallengeCustomTrack = onChallengeFriendCustomTrack,
                                onRequestRemove = { friendToRemove = friend }
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingRemoval = friendToRemove
    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Remove Friend?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Remove ${pendingRemoval.displayName} from your Friends List?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = pendingRemoval.friendshipId
                        val name = pendingRemoval.displayName
                        friendToRemove = null
                        onDeclineOrRemoveFriendship(id, "Removed $name from Friends List.")
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FriendStatusCard(
    friend: FriendUiModel,
    customTracks: List<CustomTrackData>,
    currentRoomCode: String?,
    onNotifyToJoin: () -> Unit,
    onJoinFriendLobby: () -> Unit,
    onChallengeCustomTrack: (CustomTrackData) -> Unit,
    onRequestRemove: () -> Unit
) {
    val canJoinTheirRoom = friend.canJoinActiveLobby &&
        friend.activeRoomCode != currentRoomCode
    val liveOnlineColor = adaptiveOnlineColor()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (friend.effectivelyOnline) {
                liveOnlineColor.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friend_card_${friend.friendUid}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRcCar(
                            x = size.width / 2f,
                            y = size.height / 2f,
                            angleRad = -0.6f,
                            carType = friend.carType,
                            primaryColor = Color(friend.primaryColorArgb),
                            accentColor = Color(friend.accentColorArgb),
                            scale = 0.85f
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(
                                if (friend.effectivelyOnline) liveOnlineColor else MaterialTheme.colorScheme.outline
                            )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = friend.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "#${friend.friendCode}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = friend.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (friend.effectivelyOnline) {
                            liveOnlineColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRequestRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_friend_button_${friend.friendUid}")
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Remove Friend",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNotifyToJoin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (friend.effectivelyOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (friend.effectivelyOnline) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("notify_friend_button_${friend.friendUid}")
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentRoomCode != null) {
                            "Notify to Join #$currentRoomCode"
                        } else {
                            "Notify to Join Session"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (canJoinTheirRoom) {
                    FilledTonalButton(
                        onClick = onJoinFriendLobby,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("join_friend_lobby_button_${friend.friendUid}")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Join #${friend.activeRoomCode}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (customTracks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "CUSTOM TRACKS BY ${friend.displayName.uppercase()} (${customTracks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (customTracks.size == 1) {
                        val soleTrack = customTracks.first()
                        FilledTonalButton(
                            onClick = { onChallengeCustomTrack(soleTrack) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("challenge_friend_custom_track_${friend.friendUid}")
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Challenge Time: ${soleTrack.name}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(customTracks.indices.toList(), key = { idx -> customTracks[idx].id }) { idx ->
                                val cTrack = customTracks[idx]
                                FilledTonalButton(
                                    onClick = { onChallengeCustomTrack(cTrack) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag(
                                            if (idx == 0) {
                                                "challenge_friend_custom_track_${friend.friendUid}"
                                            } else {
                                                "challenge_friend_custom_track_${friend.friendUid}_${cTrack.id}"
                                            }
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = cTrack.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
