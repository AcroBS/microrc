package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.FriendsRepository
import com.example.data.OnlineRaceRepository
import com.example.model.OnlineRoomDoc
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.components.LiveSessionInviteBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HostSetupScreen
import com.example.ui.screens.HybridSetupScreen
import com.example.ui.screens.JoinBrowserScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.OnlineHubScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackEditorScreen
import com.example.ui.theme.GoldTrophy
import com.example.util.PendingLobbyInvite
import com.example.util.SessionNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val ONLINE_HUB = "online_hub"
    const val FRIENDS = "friends"
    const val LEADERBOARD = "leaderboard"
    const val HOST_SETUP = "host_setup"
    const val HYBRID_SETUP = "hybrid_setup"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val TRACK_EDITOR = "track_editor"
    const val BROWSER = "browser"
    const val LOBBY = "lobby"
    const val RACE = "race"
    const val RESULTS = "results"
}

@Composable
fun MicroRCApp(
    viewModel: MainViewModel = viewModel(),
    pendingInvite: PendingLobbyInvite? = null,
    onConsumePendingInvite: () -> Unit = {}
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val uiScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var globalLockedRoomPrompt by remember { mutableStateOf<OnlineRoomDoc?>(null) }
    var globalLockedPasswordDraft by remember { mutableStateOf("") }
    val notifiedInviteIds = remember { mutableSetOf<String>() }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != Routes.RACE) {
            viewModel.audioEngine.setMenuMode()
        }
    }

    val profile by viewModel.profile.collectAsState()
    val latestProfile by rememberUpdatedState(profile)
    val rules by viewModel.rules.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val customTracks by viewModel.customTracks.collectAsState()
    val localLeaderboard by viewModel.localLeaderboard.collectAsState()
    val discoveredLobbies by viewModel.discoveredLobbies.collectAsState()
    val offlineRole by viewModel.sessionRole.collectAsState()
    val offlineLobby by viewModel.lobbyState.collectAsState()
    val offlineSnapshot by viewModel.raceSnapshot.collectAsState()
    val offlineResults by viewModel.lastResults.collectAsState()
    val offlineActiveTrack by viewModel.activeTrack.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val offlineRematchState by viewModel.rematchState.collectAsState()
    val activeChallengeGhost by viewModel.activeChallengeGhost.collectAsState()
    val activeGhostCar by viewModel.activeGhostCar.collectAsState()
    val lastRunWasPersonalBest by viewModel.lastRunWasPersonalBest.collectAsState()
    val overviewCamera by viewModel.overviewCamera.collectAsState()

    // Reactive Firebase Auth observation — automatically picks up late/runtime Firebase init
    val authFlow = remember(appContext) {
        GoogleAuthManager.observeAuthState(appContext)
    }
    val currentUser by authFlow.collectAsState(
        initial = runCatching {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) Firebase.auth.currentUser else null
        }.getOrNull()
    )

    var activeOnlineVm: OnlineViewModel? = null
    var signOutCleanupAction: (suspend () -> Unit)? = null
    var deleteAccountCleanupAction: (suspend () -> Unit)? = null

    if (currentUser != null) {
        val uid = currentUser!!.uid
        key(uid) {
            val onlineVm: OnlineViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        OnlineViewModel(
                            repository = OnlineRaceRepository(appContext),
                            friendsRepository = FriendsRepository(appContext),
                            currentUserId = uid
                        )
                    }
                }
            )
            activeOnlineVm = onlineVm
            signOutCleanupAction = { onlineVm.cleanupBeforeSignOut() }
            deleteAccountCleanupAction = { onlineVm.deleteAllCloudDataBeforeAccountRemoval() }

            LaunchedEffect(onlineVm) {
                viewModel.latestCompletedSoloRun.collect { summary ->
                    onlineVm.syncSoloRunToCloud(latestProfile, summary)
                }
            }

            LaunchedEffect(
                onlineVm,
                profile.name,
                profile.carType,
                profile.primaryColorArgb,
                profile.accentColorArgb
            ) {
                onlineVm.syncProfileToPresence(profile)
                if (onlineVm.role.value.isOnline) {
                    onlineVm.sessionManager.updateLocalProfile(profile)
                }
            }

            LaunchedEffect(onlineVm) {
                onlineVm.navEvents.collect { event ->
                    when (event) {
                        is RaceNavEvent.EnterLobby -> {
                            navController.navigate(Routes.LOBBY) {
                                popUpTo(Routes.HOME)
                                launchSingleTop = true
                            }
                        }
                        is RaceNavEvent.StartRace -> {
                            navController.navigate(Routes.RACE) {
                                popUpTo(Routes.LOBBY) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        is RaceNavEvent.ShowResults -> {
                            navController.navigate(Routes.RESULTS) {
                                popUpTo(Routes.LOBBY)
                                launchSingleTop = true
                            }
                        }
                        is RaceNavEvent.BackToLobby -> {
                            navController.navigate(Routes.LOBBY) {
                                popUpTo(Routes.HOME)
                                launchSingleTop = true
                            }
                        }
                        is RaceNavEvent.Disconnected -> {
                            onlineVm.leaveRoom()
                            val poppedToHub = navController.popBackStack(Routes.ONLINE_HUB, inclusive = false)
                            if (!poppedToHub) {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                        }
                    }
                }
            }
        }
    }

    val latestOnlineVm by rememberUpdatedState(activeOnlineVm)
    DisposableEffect(lifecycleOwner, viewModel.audioEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.audioEngine.setAppForeground(true)
                    latestOnlineVm?.setAppForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.audioEngine.setAppForeground(false)
                    latestOnlineVm?.setAppForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.audioEngine.stop()
        }
    }

    // Handle incoming WhatsApp / Deep-Link / Notification Session Invite
    LaunchedEffect(pendingInvite, currentUser?.uid, activeOnlineVm) {
        val invite = pendingInvite ?: return@LaunchedEffect
        val onlineVm = activeOnlineVm
        if (currentUser == null || onlineVm == null) {
            if (currentRoute != Routes.ONLINE_HUB) {
                navController.navigate(Routes.ONLINE_HUB) {
                    launchSingleTop = true
                }
            }
        } else {
            onConsumePendingInvite()
            if (invite.inviteId.isNotBlank()) {
                onlineVm.friendsRepository.respondToSessionInvite(invite.inviteId, accepted = true)
            }
            viewModel.leaveSession()
            onlineVm.joinRoomByCode(
                code = invite.roomCode,
                profile = latestProfile,
                password = "",
                preAuthorizedJoinToken = invite.joinToken,
                onPasswordRequired = { lockedRoom ->
                    globalLockedPasswordDraft = ""
                    globalLockedRoomPrompt = lockedRoom
                }
            )
        }
    }

    val onlineRole = activeOnlineVm?.let { it.role.collectAsState().value } ?: SessionRole.None
    val isOnlineSession = onlineRole.isOnline && activeOnlineVm != null
    val sessionRole = if (isOnlineSession) onlineRole else offlineRole

    val onlineRoom = activeOnlineVm?.let { it.activeRoom.collectAsState().value }
    val onlineLobby = activeOnlineVm?.let { it.lobbyState.collectAsState().value }
    val onlineSnapshot = activeOnlineVm?.let { it.raceSnapshot.collectAsState().value }
    val onlineResults = activeOnlineVm?.let { it.lastResults.collectAsState().value }
    val onlineTrack = activeOnlineVm?.let { it.activeTrack.collectAsState().value }
    val onlineRematch = activeOnlineVm?.let { it.rematchState.collectAsState().value }

    val friendsList = activeOnlineVm?.let { it.friendsList.collectAsState().value }.orEmpty()
    val incomingFriendRequests = activeOnlineVm?.let { it.incomingFriendRequests.collectAsState().value }.orEmpty()
    val outgoingFriendRequests = activeOnlineVm?.let { it.outgoingFriendRequests.collectAsState().value }.orEmpty()
    val incomingSessionInvites = activeOnlineVm?.let { it.incomingSessionInvites.collectAsState().value }.orEmpty()
    val socialFeedback = activeOnlineVm?.let { it.socialFeedback.collectAsState().value }

    val onlineFriendsCount = remember(friendsList) { friendsList.count { it.effectivelyOnline } }
    val knownFriendUids = remember(friendsList, incomingFriendRequests, outgoingFriendRequests) {
        buildSet {
            friendsList.forEach { add(it.friendUid) }
            incomingFriendRequests.forEach { add(it.requesterUid) }
            outgoingFriendRequests.forEach { add(it.receiverUid) }
        }
    }

    // Trigger Android system notification for newly arrived friend session invites
    LaunchedEffect(incomingSessionInvites) {
        incomingSessionInvites.forEach { inv ->
            val alreadyInThatRoom = onlineRoom?.roomCode == inv.roomCode
            if (inv.id.isNotBlank() && notifiedInviteIds.add(inv.id) && !alreadyInThatRoom) {
                SessionNotificationHelper.showSessionInviteNotification(context, inv)
            }
        }
    }

    val lobbyState = if (isOnlineSession) onlineLobby else offlineLobby
    val raceSnapshot = if (isOnlineSession) onlineSnapshot else offlineSnapshot
    val lastResults = if (isOnlineSession) onlineResults else offlineResults
    val activeTrack = if (isOnlineSession) onlineTrack else offlineActiveTrack
    val rematchState = if (isOnlineSession) onlineRematch else offlineRematchState
    val effectiveRules = lobbyState?.rules ?: rules

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is RaceNavEvent.EnterLobby -> {
                    navController.navigate(Routes.LOBBY) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
                is RaceNavEvent.StartRace -> {
                    navController.navigate(Routes.RACE) {
                        popUpTo(Routes.LOBBY) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                is RaceNavEvent.ShowResults -> {
                    navController.navigate(Routes.RESULTS) {
                        popUpTo(Routes.LOBBY)
                        launchSingleTop = true
                    }
                }
                is RaceNavEvent.BackToLobby -> {
                    navController.navigate(Routes.LOBBY) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
                is RaceNavEvent.Disconnected -> {
                    viewModel.leaveSession()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            }
        }
    }

    val topBannerInvite = incomingSessionInvites.firstOrNull { inv ->
        onlineRoom?.roomCode != inv.roomCode
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    profile = profile,
                    onHostRace = { navController.navigate(Routes.HOST_SETUP) },
                    onJoinRace = { navController.navigate(Routes.BROWSER) },
                    onSoloRace = { viewModel.startSolo() },
                    onPureSoloRace = { viewModel.startPureSolo() },
                    onOpenOnlinePlay = { navController.navigate(Routes.ONLINE_HUB) },
                    onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    onlineFriendsCount = onlineFriendsCount,
                    pendingRequestsCount = incomingFriendRequests.size,
                    onOpenLeaderboards = { navController.navigate(Routes.LEADERBOARD) },
                    onOpenHybridSetup = { navController.navigate(Routes.HYBRID_SETUP) },
                    onOpenGarage = { navController.navigate(Routes.GARAGE) },
                    onOpenTrackEditor = { navController.navigate(Routes.TRACK_EDITOR) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onUpdateDriverName = viewModel::updateDriverName
                )
            }

            composable(Routes.ONLINE_HUB) {
                val signedInUser = currentUser
                val onlineVm = activeOnlineVm
                if (signedInUser == null || onlineVm == null) {
                    AuthScreen(
                        pendingInviteCode = pendingInvite?.roomCode,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    val openRooms by onlineVm.openRooms.collectAsState()
                    val isBusy by onlineVm.isBusy.collectAsState()
                    val errorBanner by onlineVm.errorBanner.collectAsState()
                    val accountLabel = signedInUser.email
                        ?.takeIf { it.isNotBlank() }
                        ?: signedInUser.displayName?.takeIf { it.isNotBlank() }
                        ?: profile.name

                    OnlineHubScreen(
                        profile = profile,
                        accountLabel = accountLabel,
                        initialRules = rules,
                        openRooms = openRooms,
                        isBusy = isBusy,
                        errorBanner = errorBanner,
                        onlineFriendsCount = onlineFriendsCount,
                        pendingRequestsCount = incomingFriendRequests.size,
                        onDismissError = onlineVm::clearError,
                        onCreateRoom = { roomName, maxPlayers, discoverable, password, hostRules ->
                            viewModel.leaveSession()
                            onlineVm.hostRoom(
                                profile = profile,
                                roomName = roomName,
                                maxPlayers = maxPlayers,
                                discoverable = discoverable,
                                password = password,
                                rules = hostRules
                            )
                        },
                        onJoinRoom = { roomId, password ->
                            viewModel.leaveSession()
                            onlineVm.joinRoom(roomId = roomId, profile = profile, password = password)
                        },
                        onJoinRoomByCode = { code, password, onPwNeeded ->
                            viewModel.leaveSession()
                            onlineVm.joinRoomByCode(
                                code = code,
                                profile = profile,
                                password = password,
                                onPasswordRequired = onPwNeeded
                            )
                        },
                        onJoinRoomWithInviteToken = { code, token, onPwNeeded ->
                            viewModel.leaveSession()
                            onlineVm.joinRoomByCode(
                                code = code,
                                profile = profile,
                                password = "",
                                preAuthorizedJoinToken = token,
                                onPasswordRequired = onPwNeeded
                            )
                        },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                        onOpenLeaderboards = { navController.navigate(Routes.LEADERBOARD) },
                        onSignOut = {
                            uiScope.launch {
                                GoogleAuthManager.signOut(
                                    context = context,
                                    beforeSignOutCleanup = signOutCleanupAction
                                )
                            }
                        },
                        onDeleteAccount = {
                            uiScope.launch {
                                GoogleAuthManager.deleteAccountAndSignOut(
                                    context = context,
                                    beforeDeleteCleanup = deleteAccountCleanupAction
                                )
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.FRIENDS) {
                val signedInUser = currentUser
                val onlineVm = activeOnlineVm
                if (signedInUser == null || onlineVm == null) {
                    AuthScreen(
                        headline = "Friends List & Live Join Alerts",
                        subtitle = "Sign in with Google to see which friends are online, add friends by Friend Code, and notify friends to join a race lobby together.",
                        pendingInviteCode = pendingInvite?.roomCode,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    val searchResults by onlineVm.driverSearchResults.collectAsState()
                    val isSearching by onlineVm.isSearchingDrivers.collectAsState()

                    FriendsScreen(
                        profile = profile,
                        myFriendCode = onlineVm.myFriendCode,
                        activeOnlineRoom = if (isOnlineSession) onlineRoom else null,
                        friends = friendsList,
                        incomingRequests = incomingFriendRequests,
                        outgoingRequests = outgoingFriendRequests,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        feedbackMessage = socialFeedback,
                        onDismissFeedback = onlineVm::clearSocialFeedback,
                        onAddFriendByCode = { code -> onlineVm.addFriendByCode(profile, code) },
                        onSearchDrivers = onlineVm::searchDrivers,
                        onAddDriverFromSearch = { target -> onlineVm.addFriendFromDirectory(profile, target) },
                        onAcceptRequest = { reqId -> onlineVm.acceptFriendRequest(reqId, profile) },
                        onDeclineOrRemoveFriendship = { id, msg -> onlineVm.removeOrDeclineFriend(id, msg) },
                        onNotifyFriendToJoin = { friend ->
                            onlineVm.notifyFriendToJoin(
                                friend = friend,
                                profile = profile,
                                defaultRules = rules,
                                onBeforeAutoHost = { viewModel.leaveSession() }
                            )
                        },
                        onNotifyAllOnlineFriends = { targets ->
                            onlineVm.notifyMultipleFriendsToJoin(
                                targets = targets,
                                profile = profile,
                                defaultRules = rules,
                                onBeforeAutoHost = { viewModel.leaveSession() }
                            )
                        },
                        onJoinFriendLobby = { friendRoomCode ->
                            viewModel.leaveSession()
                            onlineVm.joinRoomByCode(
                                code = friendRoomCode,
                                profile = profile,
                                password = "",
                                onPasswordRequired = { lockedRoom ->
                                    globalLockedPasswordDraft = ""
                                    globalLockedRoomPrompt = lockedRoom
                                }
                            )
                        },
                        onOpenOnlineHub = {
                            if (isOnlineSession && onlineRoom != null) {
                                navController.navigate(Routes.LOBBY) { launchSingleTop = true }
                            } else {
                                navController.navigate(Routes.ONLINE_HUB) { launchSingleTop = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.LEADERBOARD) {
                val onlineVm = activeOnlineVm
                val cloudEntries = onlineVm?.let { it.cloudLeaderboard.collectAsState().value }.orEmpty()
                val isLoadingCloud = onlineVm?.let { it.leaderboardLoading.collectAsState().value } ?: false
                val ghostLoadingId = onlineVm?.let { it.ghostLoadingId.collectAsState().value }
                val cloudStatus = onlineVm?.let { it.cloudStatusMessage.collectAsState().value }

                LeaderboardScreen(
                    localRecords = localLeaderboard,
                    isSignedIn = currentUser != null && onlineVm != null,
                    currentUserId = onlineVm?.currentUserId,
                    cloudEntries = cloudEntries,
                    isLoadingCloud = isLoadingCloud,
                    ghostLoadingId = ghostLoadingId,
                    cloudStatusMessage = cloudStatus,
                    onSelectBoardKey = { key -> onlineVm?.observeLeaderboardForBoard(key) },
                    onStartPureSoloForTrack = { mode, trackId, laps, ghost ->
                        onlineVm?.leaveRoom()
                        viewModel.startPureSoloForTrack(mode, trackId, laps, ghost)
                    },
                    onChallengeCloudGhost = { entry ->
                        onlineVm?.loadGhostRunForChallenge(entry.ghostId) { playback ->
                            onlineVm.leaveRoom()
                            viewModel.startGhostChallenge(playback)
                        }
                    },
                    onRequestSignIn = { navController.navigate(Routes.ONLINE_HUB) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.HOST_SETUP) {
                HostSetupScreen(
                    initialRules = rules,
                    onCreateLobby = { setupRules ->
                        activeOnlineVm?.leaveRoom()
                        viewModel.startHosting(setupRules)
                    },
                    onOpenHybridSetup = { draftRules ->
                        viewModel.updateRaceRules(draftRules)
                        navController.navigate(Routes.HYBRID_SETUP)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.HYBRID_SETUP) {
                val inActiveLobby = sessionRole.isHostLike
                HybridSetupScreen(
                    initialRules = effectiveRules,
                    inActiveLobby = inActiveLobby,
                    onHostHybridWifi = { hybridRules ->
                        activeOnlineVm?.leaveRoom()
                        viewModel.startHosting(hybridRules)
                    },
                    onStartHybridSolo = { hybridRules ->
                        activeOnlineVm?.leaveRoom()
                        viewModel.startSoloWithRules(hybridRules)
                    },
                    onApplyToLobby = { hybridRules ->
                        if (isOnlineSession) {
                            activeOnlineVm?.updateRules(hybridRules)
                        } else {
                            viewModel.updateRaceRules(hybridRules)
                        }
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.GARAGE) {
                GarageScreen(
                    profile = profile,
                    tiltController = viewModel.tiltController,
                    onSaveCarAndColors = viewModel::updateCarAndColors,
                    onSaveControlSettings = viewModel::updateControlSettings,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                val canEdit = sessionRole == SessionRole.None || sessionRole.isHostLike
                SettingsScreen(
                    rules = effectiveRules,
                    audioSettings = audioSettings,
                    canEditRules = canEdit,
                    inSession = sessionRole != SessionRole.None,
                    onUpdateRules = { updatedRules ->
                        if (isOnlineSession) {
                            activeOnlineVm?.updateRules(updatedRules)
                        }
                        viewModel.updateRaceRules(updatedRules)
                    },
                    onUpdateAudio = viewModel::updateAudioSettings,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.TRACK_EDITOR) {
                val canApplyToLobby = sessionRole.isHostLike
                TrackEditorScreen(
                    initialTrack = lobbyState?.activeCustomTrack ?: customTracks.firstOrNull(),
                    savedTracks = customTracks,
                    canApplyToLobby = canApplyToLobby,
                    onSaveTrack = { track, apply ->
                        viewModel.saveCustomTrack(track, apply && !isOnlineSession)
                        if (apply && isOnlineSession) {
                            activeOnlineVm?.selectCustomTrack(track)
                        }
                        navController.popBackStack()
                    },
                    onDeleteTrack = viewModel::deleteCustomTrack,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BROWSER) {
                JoinBrowserScreen(
                    lobbies = discoveredLobbies,
                    connectionError = connectionError,
                    onStartScan = viewModel::startBrowsing,
                    onStopScan = viewModel::stopBrowsing,
                    onJoinLobby = { discovered ->
                        activeOnlineVm?.leaveRoom()
                        viewModel.joinLobby(discovered)
                    },
                    onJoinDirectIp = { ip, port ->
                        activeOnlineVm?.leaveRoom()
                        viewModel.joinByDirectIp(ip, port)
                    },
                    onDismissError = viewModel::clearError,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.LOBBY) {
                val onlineVm = activeOnlineVm
                val effectiveLocalId = if (isOnlineSession) {
                    onlineVm?.currentUserId ?: profile.playerId
                } else {
                    profile.playerId
                }
                val roomCap = if (isOnlineSession) {
                    (onlineRoom?.maxPlayers ?: 4L).toInt().coerceIn(2, 4)
                } else {
                    4
                }

                LobbyScreen(
                    role = sessionRole,
                    localPlayerId = effectiveLocalId,
                    lobby = lobbyState,
                    customTracks = customTracks,
                    onlineRoom = if (isOnlineSession) onlineRoom else null,
                    onlineRoomCode = if (isOnlineSession) onlineRoom?.roomCode else null,
                    onlinePasswordProtected = if (isOnlineSession) onlineRoom?.passwordProtected == true else false,
                    maxPlayers = roomCap,
                    activeChallengeGhost = if (!isOnlineSession) activeChallengeGhost else null,
                    friends = friendsList,
                    knownFriendUids = knownFriendUids,
                    socialFeedback = if (isOnlineSession) socialFeedback else null,
                    onDismissSocialFeedback = { onlineVm?.clearSocialFeedback() },
                    onNotifyFriendToJoin = { friend ->
                        onlineVm?.notifyFriendToJoin(
                            friend = friend,
                            profile = profile,
                            defaultRules = effectiveRules
                        )
                    },
                    onNotifyAllOnlineFriends = { targets ->
                        onlineVm?.notifyMultipleFriendsToJoin(
                            targets = targets,
                            profile = profile,
                            defaultRules = effectiveRules
                        )
                    },
                    onAddPlayerAsFriend = { targetUid, targetName ->
                        onlineVm?.addFriendByUid(profile, targetUid, targetName)
                    },
                    onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    onSelectTrack = { trackId ->
                        if (isOnlineSession) onlineVm?.selectTrack(trackId) else viewModel.selectTrack(trackId)
                    },
                    onSelectArenaShape = { shape ->
                        if (isOnlineSession) onlineVm?.selectArenaShape(shape) else viewModel.selectArenaShape(shape)
                    },
                    onSelectStuntVariant = { variant ->
                        if (isOnlineSession) onlineVm?.selectStuntVariant(variant) else viewModel.selectStuntVariant(variant)
                    },
                    onSelectCustomTrack = { cTrack ->
                        if (isOnlineSession) onlineVm?.selectCustomTrack(cTrack) else viewModel.selectCustomTrack(cTrack)
                    },
                    onGenerateRandomTrack = {
                        if (isOnlineSession) onlineVm?.generateRandomTrackForLobby() else viewModel.generateRandomTrackForLobby()
                    },
                    onSelectRandomArena = {
                        if (isOnlineSession) onlineVm?.selectRandomArenaForLobby() else viewModel.selectRandomArenaForLobby()
                    },
                    onSelectRandomStunt = {
                        if (isOnlineSession) onlineVm?.selectRandomStuntForLobby() else viewModel.selectRandomStuntForLobby()
                    },
                    onUpdateRules = { updatedRules ->
                        if (isOnlineSession) onlineVm?.updateRules(updatedRules) else viewModel.updateRaceRules(updatedRules)
                    },
                    onOpenHybridSetup = { navController.navigate(Routes.HYBRID_SETUP) },
                    onOpenTrackEditor = { navController.navigate(Routes.TRACK_EDITOR) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onSelectLaps = { laps ->
                        if (isOnlineSession) onlineVm?.setTotalLaps(laps) else viewModel.setTotalLaps(laps)
                    },
                    onAddBot = {
                        if (isOnlineSession) onlineVm?.addBot() else viewModel.addBot()
                    },
                    onRemoveBot = {
                        if (isOnlineSession) onlineVm?.removeBot() else viewModel.removeBot()
                    },
                    onClearBots = {
                        if (isOnlineSession) onlineVm?.clearBots() else viewModel.clearBots()
                    },
                    onClearGhost = viewModel::clearChallengeGhost,
                    onOpenLeaderboards = { navController.navigate(Routes.LEADERBOARD) },
                    onToggleReady = {
                        if (isOnlineSession) onlineVm?.toggleReady() else viewModel.toggleReady()
                    },
                    onSendChat = { msg ->
                        if (isOnlineSession) onlineVm?.sendChat(profile.name, msg) else viewModel.sendChat(msg)
                    },
                    onStartRace = {
                        if (isOnlineSession) onlineVm?.startRace() else viewModel.startRace()
                    },
                    onOpenGarage = { navController.navigate(Routes.GARAGE) },
                    onLeaveLobby = {
                        if (isOnlineSession) {
                            onlineVm?.leaveRoom()
                            val popped = navController.popBackStack(Routes.ONLINE_HUB, inclusive = false)
                            if (!popped) navController.popBackStack(Routes.HOME, inclusive = false)
                        } else {
                            viewModel.leaveSession()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    }
                )
            }

            composable(Routes.RACE) {
                val onlineVm = activeOnlineVm
                val raceProfile = if (isOnlineSession) {
                    profile.copy(playerId = onlineVm?.currentUserId ?: profile.playerId)
                } else {
                    profile
                }

                RaceScreen(
                    profile = raceProfile,
                    snapshot = raceSnapshot,
                    activeTrack = activeTrack,
                    ghostCar = if (!isOnlineSession) activeGhostCar else null,
                    rules = effectiveRules,
                    audioSettings = audioSettings,
                    audioEngine = viewModel.audioEngine,
                    hapticController = viewModel.hapticController,
                    overviewCamera = overviewCamera,
                    tiltController = viewModel.tiltController,
                    onToggleOverviewCamera = viewModel::toggleOverviewCamera,
                    onUpdateControlInput = { throttle, steer ->
                        if (isOnlineSession) {
                            onlineVm?.sessionManager?.updateLocalControlInput(throttle, steer)
                        } else {
                            viewModel.raceManager.updateLocalControlInput(throttle, steer)
                        }
                    },
                    onTriggerPowerUp = {
                        if (isOnlineSession) {
                            onlineVm?.sessionManager?.triggerLocalPowerUp()
                        } else {
                            viewModel.raceManager.triggerLocalPowerUp()
                        }
                    },
                    onDropMine = {
                        if (isOnlineSession) {
                            onlineVm?.sessionManager?.triggerLocalMineDrop()
                        } else {
                            viewModel.raceManager.triggerLocalMineDrop()
                        }
                    },
                    onSwitchControlMode = viewModel::updateControlSettings,
                    onUpdateAudioSettings = viewModel::updateAudioSettings,
                    onLeaveRace = {
                        if (isOnlineSession) {
                            onlineVm?.leaveRoom()
                            val popped = navController.popBackStack(Routes.ONLINE_HUB, inclusive = false)
                            if (!popped) navController.popBackStack(Routes.HOME, inclusive = false)
                        } else {
                            viewModel.leaveSession()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    }
                )
            }

            composable(Routes.RESULTS) {
                val onlineVm = activeOnlineVm
                val effectiveLocalId = if (isOnlineSession) {
                    onlineVm?.currentUserId ?: profile.playerId
                } else {
                    profile.playerId
                }

                ResultsScreen(
                    role = sessionRole,
                    localPlayerId = effectiveLocalId,
                    results = lastResults ?: raceSnapshot,
                    lobby = lobbyState,
                    rematchState = rematchState,
                    isNewPersonalBest = !isOnlineSession && lastRunWasPersonalBest,
                    challengeGhost = if (!isOnlineSession) activeChallengeGhost else null,
                    onOpenLeaderboards = if (!isOnlineSession) {
                        {
                            viewModel.leaveSession()
                            navController.navigate(Routes.LEADERBOARD) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    } else {
                        null
                    },
                    onVoteRematch = { optId ->
                        if (isOnlineSession) onlineVm?.voteRematchOption(optId) else viewModel.voteRematchOption(optId)
                    },
                    onLaunchRematchNow = {
                        if (isOnlineSession) onlineVm?.launchRematchNow() else viewModel.launchRematchNow()
                    },
                    onReturnToLobby = {
                        if (isOnlineSession) onlineVm?.returnToLobby() else viewModel.returnToLobby()
                    },
                    onExitHome = {
                        if (isOnlineSession) {
                            onlineVm?.leaveRoom()
                            val popped = navController.popBackStack(Routes.ONLINE_HUB, inclusive = false)
                            if (!popped) navController.popBackStack(Routes.HOME, inclusive = false)
                        } else {
                            viewModel.leaveSession()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    }
                )
            }
        }

        // Global Live Session Invite Banner (hidden during active race to keep HUD clear)
        if (currentRoute != Routes.RACE) {
            LiveSessionInviteBanner(
                invite = topBannerInvite,
                onAccept = { invite ->
                    activeOnlineVm?.acceptSessionInvite(
                        invite = invite,
                        profile = profile,
                        onBeforeJoin = { viewModel.leaveSession() },
                        onPasswordRequired = { lockedRoom ->
                            globalLockedPasswordDraft = ""
                            globalLockedRoomPrompt = lockedRoom
                        }
                    )
                },
                onDecline = { invite ->
                    activeOnlineVm?.declineSessionInvite(invite.id)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    val lockedRoom = globalLockedRoomPrompt
    if (lockedRoom != null) {
        AlertDialog(
            onDismissRequest = { globalLockedRoomPrompt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GoldTrophy)
                    Text(
                        text = "  Enter Group Password",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "\"${lockedRoom.roomName}\" (#${lockedRoom.roomCode}) requires a password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = globalLockedPasswordDraft,
                        onValueChange = { if (it.length <= 24) globalLockedPasswordDraft = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_join_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pw = globalLockedPasswordDraft.trim()
                        val rid = lockedRoom.id
                        globalLockedRoomPrompt = null
                        viewModel.leaveSession()
                        activeOnlineVm?.joinRoom(roomId = rid, profile = profile, password = pw)
                    },
                    enabled = globalLockedPasswordDraft.isNotBlank()
                ) {
                    Text("Unlock & Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { globalLockedRoomPrompt = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
