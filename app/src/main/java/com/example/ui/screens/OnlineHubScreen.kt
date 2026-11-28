package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GameMode
import com.example.model.OnlineRoomDoc
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.adaptiveGoldColor
import com.example.ui.theme.adaptiveOnlineColor
import com.example.util.InviteLinkHelper

@Composable
fun OnlineHubScreen(
    profile: PlayerProfile,
    accountLabel: String,
    initialRules: RaceRulesSettings,
    openRooms: List<OnlineRoomDoc>,
    isBusy: Boolean,
    errorBanner: String?,
    onlineFriendsCount: Int = 0,
    pendingRequestsCount: Int = 0,
    onDismissError: () -> Unit,
    onCreateRoom: (roomName: String, maxPlayers: Int, discoverable: Boolean, password: String, rules: RaceRulesSettings) -> Unit,
    onJoinRoom: (roomId: String, password: String) -> Unit,
    onJoinRoomByCode: (code: String, password: String, onPasswordRequired: (OnlineRoomDoc) -> Unit) -> Unit,
    onJoinRoomWithInviteToken: (code: String, token: String, onPasswordRequired: (OnlineRoomDoc) -> Unit) -> Unit = { code, _, onPw ->
        onJoinRoomByCode(code, "", onPw)
    },
    onOpenFriends: () -> Unit = {},
    onOpenLeaderboards: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var roomName by remember(profile.name) { mutableStateOf("${profile.name.take(12)}'s Group") }
    var maxPlayers by remember { mutableIntStateOf(4) }
    var selectedMode by remember(initialRules.gameMode) { mutableStateOf(initialRules.gameModeEnum) }
    var environmentalEffectsEnabled by remember(initialRules.hostRandomConditionsOverride) {
        mutableStateOf(initialRules.hostRandomConditionsOverride == true)
    }
    var passwordProtected by remember { mutableStateOf(false) }
    var roomPassword by remember { mutableStateOf("") }
    var unlistedCodeOnly by remember { mutableStateOf(false) }

    var joinCodeInput by remember { mutableStateOf("") }
    var pastedUnlockToken by remember { mutableStateOf("") }
    var passwordPromptRoom by remember { mutableStateOf<OnlineRoomDoc?>(null) }
    var joinPasswordDraft by remember { mutableStateOf("") }
    var localValidationHint by remember { mutableStateOf<String?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

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
                // Top Header Bar
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
                                .testTag("online_hub_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = "ONLINE WEB PADDOCK",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Signed in: $accountLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onOpenLeaderboards,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("online_hub_leaderboards_button")
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Leaderboards & Ghosts",
                                tint = adaptiveGoldColor()
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("online_sign_out_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error / Validation Banner
                val activeError = errorBanner ?: localValidationHint
                if (activeError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                localValidationHint = null
                                onDismissError()
                            }
                            .testTag("online_hub_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "DISMISS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }

                // Quick Friends & Session Invites Banner
                FilledTonalButton(
                    onClick = onOpenFriends,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("online_hub_friends_button")
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
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Friends List & Join Alerts",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (onlineFriendsCount > 0) adaptiveOnlineColor() else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    onlineFriendsCount > 0 -> "$onlineFriendsCount Online"
                                    pendingRequestsCount > 0 -> "$pendingRequestsCount Request"
                                    else -> "Open"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // 1. Join by 6-Character Room Code or Invite Link Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "JOIN BY CODE OR INVITE LINK",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            TextButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = cm?.primaryClip
                                        ?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                        .orEmpty()
                                    val parsed = InviteLinkHelper.parseInviteFromText(clipText)
                                    if (parsed != null) {
                                        joinCodeInput = parsed.roomCode
                                        pastedUnlockToken = parsed.joinToken
                                        localValidationHint = null
                                        onJoinRoomWithInviteToken(parsed.roomCode, parsed.joinToken) { lockedRoom ->
                                            joinPasswordDraft = ""
                                            passwordPromptRoom = lockedRoom
                                        }
                                    } else {
                                        localValidationHint = "No MicroRC invite link or room code found on clipboard."
                                    }
                                },
                                modifier = Modifier.testTag("paste_invite_link_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Invite")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = { raw ->
                                    val parsedLink = if (raw.length > 10) InviteLinkHelper.parseInviteFromText(raw) else null
                                    if (parsedLink != null) {
                                        joinCodeInput = parsedLink.roomCode
                                        pastedUnlockToken = parsedLink.joinToken
                                    } else {
                                        pastedUnlockToken = ""
                                        joinCodeInput = raw.uppercase()
                                            .filter { it.isLetterOrDigit() }
                                            .take(12)
                                    }
                                },
                                label = { Text("Room Code or WhatsApp Link") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("online_room_code_input")
                            )
                            Button(
                                onClick = {
                                    val trimmed = joinCodeInput.trim()
                                    if (trimmed.length < 4) {
                                        localValidationHint = "Enter the 6-character room code or paste a WhatsApp invite link."
                                    } else {
                                        localValidationHint = null
                                        if (pastedUnlockToken.isNotBlank()) {
                                            onJoinRoomWithInviteToken(trimmed, pastedUnlockToken) { lockedRoom ->
                                                joinPasswordDraft = ""
                                                passwordPromptRoom = lockedRoom
                                            }
                                        } else {
                                            onJoinRoomByCode(trimmed, "") { lockedRoom ->
                                                joinPasswordDraft = ""
                                                passwordPromptRoom = lockedRoom
                                            }
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("join_room_code_button")
                            ) {
                                Text("Join", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // 2. Create Online Room / Password-Protected Group Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "HOST ONLINE GROUP (2–4 PLAYERS)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = roomName,
                            onValueChange = { if (it.length <= 32) roomName = it },
                            label = { Text("Lobby / Group Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("online_room_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PLAYER LIMIT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2, 3, 4).forEach { limit ->
                                    FilterChip(
                                        selected = maxPlayers == limit,
                                        onClick = { maxPlayers = limit },
                                        label = { Text("$limit Players", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("online_max_players_$limit")
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "STARTING MODE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("online_starting_mode_row")
                            ) {
                                items(com.example.drift.DriftFeature.selectableGameModes(), key = { it.name }) { mode ->
                                    FilterChip(
                                        selected = selectedMode == mode,
                                        onClick = { selectedMode = mode },
                                        label = {
                                            Text(
                                                text = mode.displayName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = PlusJakartaFontFamily),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        modifier = Modifier.testTag("online_mode_${mode.name}")
                                    )
                                }
                            }
                        }

                        if (selectedMode != GameMode.STUNTS) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Thunderstorm,
                                            contentDescription = null,
                                            tint = if (environmentalEffectsEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Random Environmental Effects",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Rain puddles, meteor showers, tremors, wind & snow (multiplayer host option)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = environmentalEffectsEnabled,
                                        onCheckedChange = { environmentalEffectsEnabled = it },
                                        modifier = Modifier.testTag("online_environmental_effects_switch")
                                    )
                                }
                            }
                        }

                        // Password Protection Toggle
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        Icon(
                                            if (passwordProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = if (passwordProtected) adaptiveGoldColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Password-Protected Group",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Require a group password so only invited players can join",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = passwordProtected,
                                        onCheckedChange = { passwordProtected = it },
                                        modifier = Modifier.testTag("online_password_toggle")
                                    )
                                }

                                if (passwordProtected) {
                                    OutlinedTextField(
                                        value = roomPassword,
                                        onValueChange = { if (it.length <= 24) roomPassword = it },
                                        label = { Text("Group Password") },
                                        placeholder = { Text("Enter secret password...") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("online_room_password_input")
                                    )
                                }

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
                                        Icon(
                                            Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Hide from public room list (join via 6-char code only)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = unlistedCodeOnly,
                                        onCheckedChange = { unlistedCodeOnly = it },
                                        modifier = Modifier.testTag("online_unlisted_toggle")
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val effectivePw = if (passwordProtected) roomPassword.trim() else ""
                                if (passwordProtected && effectivePw.isEmpty()) {
                                    localValidationHint = "Please enter a group password or turn off Password Protection."
                                } else {
                                    localValidationHint = null
                                    onCreateRoom(
                                        roomName.trim().ifEmpty { "${profile.name}'s Group" },
                                        maxPlayers,
                                        !unlistedCodeOnly,
                                        effectivePw,
                                        initialRules.copy(
                                            gameMode = selectedMode.name,
                                            hostRandomConditionsOverride = environmentalEffectsEnabled,
                                            randomConditionsEnabled = environmentalEffectsEnabled
                                        )
                                    )
                                }
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("create_online_room_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting to Server...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Default.Public, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (passwordProtected) "CREATE PRIVATE GROUP" else "CREATE ONLINE LOBBY",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                // 3. Live Online Room Browser
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
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "OPEN ONLINE LOBBIES (${openRooms.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "LIVE SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (openRooms.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No public rooms waiting right now",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Create a room above or enter a room code to join.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        openRooms.forEachIndexed { idx, room ->
                            val isFull = room.playerCount >= room.maxPlayers
                            val isDriftRoom = com.example.drift.DriftFeature.isDriftTrackKey(room.trackId) ||
                                room.lobbyJson.contains("\"gameMode\":\"DRIFT\"")
                            val modeLabel = if (isDriftRoom) {
                                GameMode.DRIFT.displayName
                            } else {
                                runCatching { GameMode.valueOf(room.gameMode).displayName }
                                    .getOrDefault(room.gameMode)
                            }
                            val lockGold = adaptiveGoldColor()
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (room.passwordProtected) lockGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable(enabled = !isBusy && !isFull) {
                                        if (room.passwordProtected) {
                                            joinPasswordDraft = ""
                                            passwordPromptRoom = room
                                        } else {
                                            onJoinRoom(room.id, "")
                                        }
                                    }
                                    .testTag("online_room_card_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (room.passwordProtected) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "Password Protected",
                                                    tint = lockGold,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = room.roomName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "Host: ${room.hostName} • Mode: $modeLabel • Code #${room.roomCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    OutlinedButton(
                                        onClick = {
                                            if (room.passwordProtected) {
                                                joinPasswordDraft = ""
                                                passwordPromptRoom = room
                                            } else {
                                                onJoinRoom(room.id, "")
                                            }
                                        },
                                        enabled = !isBusy && !isFull,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(
                                            text = if (isFull) "FULL" else "${room.playerCount}/${room.maxPlayers} JOIN",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Account & Privacy Footer (Google Play User Data Policy)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("delete_online_account_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete Online Account & Data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text("Delete Online Account & Data?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "This permanently deletes your MicroRC online profile, friends list, race invites, cloud leaderboard scores, and Ghost runs, and signs out your Google account. Your offline garage and local tracks remain on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Password Entry Modal for Private / Locked Online Groups
    val targetLockedRoom = passwordPromptRoom
    if (targetLockedRoom != null && !showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { passwordPromptRoom = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = adaptiveGoldColor())
                    Text("Enter Group Password", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "\"${targetLockedRoom.roomName}\" (#${targetLockedRoom.roomCode}) is password-protected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = joinPasswordDraft,
                        onValueChange = { if (it.length <= 24) joinPasswordDraft = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_password_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pw = joinPasswordDraft.trim()
                        val roomId = targetLockedRoom.id
                        passwordPromptRoom = null
                        onJoinRoom(roomId, pw)
                    },
                    enabled = joinPasswordDraft.isNotBlank(),
                    modifier = Modifier.testTag("confirm_join_password_button")
                ) {
                    Text("Unlock & Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordPromptRoom = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
