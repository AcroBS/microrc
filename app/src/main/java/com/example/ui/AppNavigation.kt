package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.DailyChallengeCatalog
import com.example.data.FriendStandingsCalculator
import com.example.data.FriendsRepository
import com.example.data.OnlineRaceRepository
import com.example.model.FriendUiModel
import com.example.model.GameMode
import com.example.model.OnlineRoomDoc
import com.example.model.PendingFriendChallengeState
import com.example.model.RacePhase
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import com.example.ui.auth.GoogleAuthManager
import com.example.ui.components.LiveFriendOnlineBanner
import com.example.ui.components.LiveLeaderboardNotificationBanner
import com.example.ui.components.LivePendingChallengeBanner
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
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.AppUpdateUiState
import com.example.util.DailyChallengeScheduler
import com.example.util.GitHubUpdateChecker
import com.example.util.PendingDailyChallengeRoute
import com.example.util.PendingLeaderboardRoute
import com.example.util.PendingLobbyInvite
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
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

private sealed interface GlobalTopBannerState {
    data class SessionInvite(val invite: com.example.model.SessionInviteDoc) : GlobalTopBannerState
    data class PendingChallenge(val state: PendingFriendChallengeState) : GlobalTopBannerState
    data class FriendOnline(val alert: com.example.model.FriendOnlineAlert) : GlobalTopBannerState
    data class LeaderboardRecord(val notification: com.example.model.LeaderboardNotificationDoc) : GlobalTopBannerState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MicroRCApp(
    viewModel: MainViewModel = viewModel(),
    pendingInvite: PendingLobbyInvite? = null,
    onConsumePendingInvite: () -> Unit = {},
    pendingDailyRoute: PendingDailyChallengeRoute? = null,
    onConsumePendingDailyRoute: () -> Unit = {},
    pendingLeaderboardRoute: PendingLeaderboardRoute? = null,
    onConsumePendingLeaderboardRoute: () -> Unit = {}
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
    var leaderboardInitialDaily by remember { mutableStateOf(false) }
    var leaderboardInitialMode by remember { mutableStateOf<GameMode?>(null) }
    var leaderboardInitialTrackKey by remember { mutableStateOf<String?>(null) }
    var currentUtcDateKey by remember { mutableStateOf(DailyChallengeCatalog.todayDateKey()) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != Routes.RACE) {
            viewModel.audioEngine.setMenuMode()
        }
    }

    val profile by viewModel.profile.collectAsState()
    val latestProfile by rememberUpdatedState(profile)
    val rules by viewModel.rules.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val themeSettings by viewModel.themeSettings.collectAsState()
    val customTracks by viewModel.customTracks.collectAsState()
    val localLeaderboard by viewModel.localLeaderboard.collectAsState()
    val playStreak by viewModel.playStreak.collectAsState()
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
    val isDailyChallengeSession by viewModel.isDailyChallengeSession.collectAsState()
    val dailyNotificationsEnabled by viewModel.dailyNotificationsEnabled.collectAsState()
    val lastRunWasPersonalBest by viewModel.lastRunWasPersonalBest.collectAsState()
    val overviewCamera by viewModel.overviewCamera.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val dismissedUpdateTag by viewModel.dismissedUpdateTag.collectAsState()

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
                            currentUserId = uid,
                            appContext = appContext
                        )
                    }
                }
            )
            activeOnlineVm = onlineVm
            signOutCleanupAction = { onlineVm.cleanupBeforeSignOut() }
            deleteAccountCleanupAction = { onlineVm.deleteAllCloudDataBeforeAccountRemoval() }

            LaunchedEffect(uid) {
                DailyChallengeScheduler.scheduleLeaderboardSync(appContext)
                DailyChallengeScheduler.triggerImmediateBackgroundSync(appContext)
            }

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
                    currentUtcDateKey = DailyChallengeCatalog.todayDateKey()
                    viewModel.audioEngine.setAppForeground(true)
                    latestOnlineVm?.setAppForeground(true)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.audioEngine.setAppForeground(false)
                    latestOnlineVm?.setAppForeground(false)
                    DailyChallengeScheduler.scheduleLeaderboardSync(appContext)
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

    // Handle deep link / notification click into today's Daily Challenge
    LaunchedEffect(pendingDailyRoute) {
        if (pendingDailyRoute != null) {
            leaderboardInitialDaily = true
            leaderboardInitialMode = GameMode.RACE
            leaderboardInitialTrackKey = null
            onConsumePendingDailyRoute()
            navController.navigate(Routes.LEADERBOARD) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        }
    }

    // Handle deep link / notification click into a Friend Leaderboard record alert
    LaunchedEffect(pendingLeaderboardRoute) {
        val route = pendingLeaderboardRoute ?: return@LaunchedEffect
        leaderboardInitialDaily = route.isDaily
        leaderboardInitialMode = runCatching { GameMode.valueOf(route.gameMode) }.getOrNull()
        leaderboardInitialTrackKey = route.trackKey.takeIf { it.isNotBlank() }
        onConsumePendingLeaderboardRoute()
        navController.navigate(Routes.LEADERBOARD) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
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
    val incomingLeaderboardNotifications = activeOnlineVm?.let {
        it.incomingLeaderboardNotifications.collectAsState().value
    }.orEmpty()
    val friendOnlineAlerts = activeOnlineVm?.let {
        it.friendOnlineAlerts.collectAsState().value
    }.orEmpty()
    val pendingFriendChallenge = activeOnlineVm?.let {
        it.pendingFriendChallenge.collectAsState().value
    }
    val socialFeedback = activeOnlineVm?.let { it.socialFeedback.collectAsState().value }

    val onlineFriendsList = remember(friendsList) { friendsList.filter { it.effectivelyOnline } }
    val onlineFriendsCount = onlineFriendsList.size
    val friendCircleEntries = activeOnlineVm?.let { it.friendCircleEntries.collectAsState().value }.orEmpty()
    val friendStandings = remember(
        currentUser?.uid,
        profile.name,
        friendsList,
        friendCircleEntries,
        localLeaderboard,
        currentUtcDateKey
    ) {
        FriendStandingsCalculator.compute(
            myUid = currentUser?.uid,
            myName = profile.name,
            friends = friendsList,
            cloudEntries = friendCircleEntries,
            localRecords = localLeaderboard,
            todayDateKey = currentUtcDateKey
        )
    }

    LaunchedEffect(isOnlineSession, onlineResults?.phase, onlineResults?.raceTimeMs) {
        if (isOnlineSession && onlineResults?.phase == RacePhase.FINISHED.name) {
            viewModel.recordCompletedRaceStreak()
        }
    }

    val knownFriendUids = remember(friendsList, incomingFriendRequests, outgoingFriendRequests) {
        buildSet {
            friendsList.forEach { add(it.friendUid) }
            incomingFriendRequests.forEach { add(it.requesterUid) }
            outgoingFriendRequests.forEach { add(it.receiverUid) }
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
    val topBannerPendingChallenge = if (topBannerInvite == null) pendingFriendChallenge else null
    val topBannerFriendOnline = if (topBannerInvite == null && topBannerPendingChallenge == null) {
        friendOnlineAlerts.firstOrNull { alert ->
            !(isOnlineSession && onlineRoom != null && alert.activeRoomCode == onlineRoom.roomCode)
        }
    } else {
        null
    }
    val topBannerLeaderboardNotif = if (
        topBannerInvite == null &&
        topBannerPendingChallenge == null &&
        topBannerFriendOnline == null
    ) {
        incomingLeaderboardNotifications.firstOrNull()
    } else {
        null
    }
    val activeTopBanner: GlobalTopBannerState? = when {
        currentRoute == Routes.RACE || globalLockedRoomPrompt != null -> null
        topBannerInvite != null -> GlobalTopBannerState.SessionInvite(topBannerInvite)
        topBannerPendingChallenge != null -> GlobalTopBannerState.PendingChallenge(topBannerPendingChallenge)
        topBannerFriendOnline != null -> GlobalTopBannerState.FriendOnline(topBannerFriendOnline)
        topBannerLeaderboardNotif != null -> GlobalTopBannerState.LeaderboardRecord(topBannerLeaderboardNotif)
        else -> null
    }
    val activeRaceOverlayBanner: GlobalTopBannerState? = when {
        currentRoute != Routes.RACE || globalLockedRoomPrompt != null -> null
        topBannerInvite != null -> GlobalTopBannerState.SessionInvite(topBannerInvite)
        topBannerPendingChallenge != null -> GlobalTopBannerState.PendingChallenge(topBannerPendingChallenge)
        topBannerFriendOnline != null -> GlobalTopBannerState.FriendOnline(topBannerFriendOnline)
        else -> null
    }

    LaunchedEffect(
        currentRoute,
        (activeRaceOverlayBanner as? GlobalTopBannerState.FriendOnline)?.alert?.friendUid
    ) {
        val alert = (activeRaceOverlayBanner as? GlobalTopBannerState.FriendOnline)?.alert
        if (currentRoute == Routes.RACE && alert != null) {
            delay(15_000L)
            activeOnlineVm?.dismissFriendOnlineAlert(alert.friendUid)
        }
    }

    val handleChallengeFriends: (List<FriendUiModel>) -> Unit = { targets ->
        activeOnlineVm?.challengeFriendsFromNotification(
            targets = targets,
            profile = profile,
            defaultRules = rules,
            onBeforeEnterCommonLobby = { viewModel.leaveSession() },
            onAlreadyInLobbyNavigate = {
                if (currentRoute != Routes.LOBBY) {
                    navController.navigate(Routes.LOBBY) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            }
        )
    }

    val showBottomNav = sessionRole == SessionRole.None &&
        (currentRoute == null || currentRoute == Routes.HOME || currentRoute == Routes.TRACK_EDITOR || currentRoute == Routes.SETTINGS)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .animateContentSize()
    ) {
        // Non-overlapping global notification slot pushed above NavHost content so headers/back buttons are never covered
        if (activeTopBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                AnimatedContent(
                    targetState = activeTopBanner,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "globalTopBanner"
                ) { bannerState ->
                    when (bannerState) {
                        is GlobalTopBannerState.SessionInvite -> LiveSessionInviteBanner(
                            invite = bannerState.invite,
                            applyStatusBarInsets = false,
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
                            }
                        )
                        is GlobalTopBannerState.PendingChallenge -> LivePendingChallengeBanner(
                            state = bannerState.state,
                            compact = false,
                            applyStatusBarInsets = false,
                            onCancel = {
                                activeOnlineVm?.cancelPendingFriendChallenge()
                            }
                        )
                        is GlobalTopBannerState.FriendOnline -> LiveFriendOnlineBanner(
                            alert = bannerState.alert,
                            onlineFriends = onlineFriendsList,
                            alreadyInOnlineRoom = isOnlineSession && onlineRoom != null,
                            compact = false,
                            applyStatusBarInsets = false,
                            onChallengeFriends = handleChallengeFriends,
                            onDismiss = { alert ->
                                activeOnlineVm?.dismissFriendOnlineAlert(alert.friendUid)
                            }
                        )
                        is GlobalTopBannerState.LeaderboardRecord -> LiveLeaderboardNotificationBanner(
                            notification = bannerState.notification,
                            applyStatusBarInsets = false,
                            onViewLeaderboard = { notif ->
                                activeOnlineVm?.dismissLeaderboardNotification(notif.id)
                                leaderboardInitialDaily = notif.dailyChallenge
                                leaderboardInitialMode = runCatching { GameMode.valueOf(notif.gameMode) }.getOrNull()
                                leaderboardInitialTrackKey = notif.trackKey.takeIf { it.isNotBlank() }
                                navController.navigate(Routes.LEADERBOARD) {
                                    popUpTo(Routes.HOME)
                                    launchSingleTop = true
                                }
                            },
                            onDismiss = { notif ->
                                activeOnlineVm?.dismissLeaderboardNotification(notif.id)
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (activeTopBanner != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(Routes.HOME) {
                val todayKey = currentUtcDateKey
                val todayBoardKey = remember(todayKey) { DailyChallengeCatalog.boardKeyForDate(todayKey) }
                val todayPbTime = remember(localLeaderboard, todayBoardKey) {
                    localLeaderboard.find { it.boardKey == todayBoardKey }?.bestRaceTimeMs
                }
                HomeScreen(
                    profile = profile,
                    onHostRace = { navController.navigate(Routes.HOST_SETUP) },
                    onJoinRace = { navController.navigate(Routes.BROWSER) },
                    onSoloRace = { viewModel.startSolo() },
                    onPureSoloRace = { viewModel.startPureSolo() },
                    onOpenDailyChallenge = {
                        leaderboardInitialDaily = true
                        leaderboardInitialMode = GameMode.RACE
                        leaderboardInitialTrackKey = null
                        navController.navigate(Routes.LEADERBOARD)
                    },
                    dailyChallengeTitle = DailyChallengeCatalog.resolveTrackDisplayName(
                        DailyChallengeCatalog.trackIdForDate(todayKey)
                    ),
                    dailyChallengeResetLabel = "Resets in ${DailyChallengeCatalog.formatCountdownUntilReset()}",
                    dailyBestTimeMs = todayPbTime,
                    onOpenOnlinePlay = { navController.navigate(Routes.ONLINE_HUB) },
                    onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    onlineFriendsCount = onlineFriendsCount,
                    pendingRequestsCount = incomingFriendRequests.size,
                    onOpenLeaderboards = {
                        leaderboardInitialDaily = false
                        leaderboardInitialMode = null
                        leaderboardInitialTrackKey = null
                        navController.navigate(Routes.LEADERBOARD)
                    },
                    onOpenStandingsBoard = { trackKey, gameMode, isDaily ->
                        leaderboardInitialDaily = isDaily
                        leaderboardInitialMode = gameMode
                        leaderboardInitialTrackKey = trackKey.takeIf { it.isNotBlank() }
                        navController.navigate(Routes.LEADERBOARD)
                    },
                    playStreak = playStreak,
                    isSignedIn = currentUser != null && activeOnlineVm != null,
                    friendsCount = friendsList.size,
                    localRecordsCount = localLeaderboard.size,
                    friendStandings = friendStandings,
                    onOpenHybridSetup = { navController.navigate(Routes.HYBRID_SETUP) },
                    onOpenGarage = { navController.navigate(Routes.GARAGE) },
                    onUpdateDriverName = viewModel::updateDriverName,
                    hasBottomNav = showBottomNav,
                    availableUpdate = (appUpdateState as? AppUpdateUiState.UpdateAvailable)?.takeIf {
                        !viewModel.isPlayStoreInstall && it.release.tagName != dismissedUpdateTag
                    },
                    onDownloadUpdate = { release ->
                        GitHubUpdateChecker.openUpdateUrl(context, release.preferredUpdateUrl)
                    },
                    onDismissUpdate = viewModel::dismissUpdateHint
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
                    initialTabDaily = leaderboardInitialDaily,
                    initialMode = leaderboardInitialMode,
                    initialTrackKey = leaderboardInitialTrackKey,
                    onSelectBoardKey = { key -> onlineVm?.observeLeaderboardForBoard(key) },
                    onStartPureSoloForTrack = { mode, trackId, laps, ghost ->
                        onlineVm?.leaveRoom()
                        viewModel.startPureSoloForTrack(mode, trackId, laps, ghost)
                    },
                    onStartDailyChallenge = { dateKey, ghost ->
                        onlineVm?.leaveRoom()
                        viewModel.startDailyChallenge(dateKey = dateKey, customGhost = ghost)
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
                    themeSettings = themeSettings,
                    dailyNotificationsEnabled = dailyNotificationsEnabled,
                    canEditRules = canEdit,
                    inSession = sessionRole != SessionRole.None,
                    hasBottomNav = showBottomNav,
                    onUpdateRules = { updatedRules ->
                        if (isOnlineSession) {
                            activeOnlineVm?.updateRules(updatedRules)
                        }
                        viewModel.updateRaceRules(updatedRules)
                    },
                    onUpdateAudio = viewModel::updateAudioSettings,
                    onUpdateThemeSettings = viewModel::updateThemeSettings,
                    onToggleDailyNotifications = viewModel::setDailyNotificationsEnabled,
                    updateState = appUpdateState,
                    isPlayStoreInstall = viewModel.isPlayStoreInstall,
                    onCheckForUpdates = { viewModel.checkForAppUpdate(manual = true) },
                    onOpenUpdateUrl = { url -> GitHubUpdateChecker.openUpdateUrl(context, url) },
                    onBack = {
                        if (showBottomNav) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.TRACK_EDITOR) {
                val canApplyToLobby = sessionRole.isHostLike
                TrackEditorScreen(
                    initialTrack = lobbyState?.activeCustomTrack ?: customTracks.firstOrNull(),
                    savedTracks = customTracks,
                    canApplyToLobby = canApplyToLobby,
                    hasBottomNav = showBottomNav,
                    onSaveTrack = { track, apply ->
                        viewModel.saveCustomTrack(track, apply && !isOnlineSession)
                        if (apply && isOnlineSession) {
                            activeOnlineVm?.selectCustomTrack(track)
                        }
                        if (!showBottomNav) {
                            navController.popBackStack()
                        }
                    },
                    onDeleteTrack = viewModel::deleteCustomTrack,
                    onBack = {
                        if (showBottomNav) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
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
                    canRestart = !isOnlineSession && (offlineRole == SessionRole.Host || offlineRole == SessionRole.Solo),
                    isDailyChallenge = !isOnlineSession && isDailyChallengeSession,
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
                    onRestartRace = { viewModel.restartActiveRace() },
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
                    isDailyChallenge = !isOnlineSession && isDailyChallengeSession,
                    challengeGhost = if (!isOnlineSession) activeChallengeGhost else null,
                    onRetryDailyChallenge = if (!isOnlineSession && isDailyChallengeSession) {
                        {
                            val dateKey = DailyChallengeCatalog.extractDateKey(lastResults?.trackId)
                                ?: DailyChallengeCatalog.todayDateKey()
                            val ghostToKeep = activeChallengeGhost
                            viewModel.startDailyChallenge(dateKey = dateKey, customGhost = ghostToKeep)
                        }
                    } else {
                        null
                    },
                    onOpenLeaderboards = if (!isOnlineSession) {
                        {
                            leaderboardInitialDaily = isDailyChallengeSession
                            leaderboardInitialMode = GameMode.RACE
                            leaderboardInitialTrackKey = null
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

            if (activeRaceOverlayBanner != null) {
                AnimatedContent(
                    targetState = activeRaceOverlayBanner,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "raceOverlayBanner",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 64.dp)
                ) { raceBanner ->
                    when (raceBanner) {
                        is GlobalTopBannerState.SessionInvite -> LiveSessionInviteBanner(
                            invite = raceBanner.invite,
                            applyStatusBarInsets = false,
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
                            }
                        )
                        is GlobalTopBannerState.PendingChallenge -> LivePendingChallengeBanner(
                            state = raceBanner.state,
                            compact = true,
                            applyStatusBarInsets = false,
                            onCancel = {
                                activeOnlineVm?.cancelPendingFriendChallenge()
                            }
                        )
                        is GlobalTopBannerState.FriendOnline -> LiveFriendOnlineBanner(
                            alert = raceBanner.alert,
                            onlineFriends = onlineFriendsList,
                            alreadyInOnlineRoom = isOnlineSession && onlineRoom != null,
                            compact = true,
                            applyStatusBarInsets = false,
                            onChallengeFriends = handleChallengeFriends,
                            onDismiss = { alert ->
                                activeOnlineVm?.dismissFriendOnlineAlert(alert.friendUid)
                            }
                        )
                        is GlobalTopBannerState.LeaderboardRecord -> Unit
                    }
                }
            }
        }

        if (showBottomNav) {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                val isPlaySelected = currentRoute == null || currentRoute == Routes.HOME
                val isStudioSelected = currentRoute == Routes.TRACK_EDITOR
                val isSettingsSelected = currentRoute == Routes.SETTINGS

                NavigationBarItem(
                    selected = isPlaySelected,
                    onClick = {
                        if (!isPlaySelected) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = "Play") },
                    label = { Text("Play", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_play")
                )

                NavigationBarItem(
                    selected = isStudioSelected,
                    onClick = {
                        if (!isStudioSelected) {
                            navController.navigate(Routes.TRACK_EDITOR) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Track Studio") },
                    label = { Text("Track Studio", maxLines = 1) },
                    modifier = Modifier.testTag("open_track_editor_button")
                )

                NavigationBarItem(
                    selected = isSettingsSelected,
                    onClick = {
                        if (!isSettingsSelected) {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                    label = { Text("Settings", maxLines = 1) },
                    modifier = Modifier.testTag("open_settings_button")
                )
            }
        }
    }

    val lockedRoom = globalLockedRoomPrompt
    if (lockedRoom != null) {
        AlertDialog(
            onDismissRequest = { globalLockedRoomPrompt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = adaptiveGoldColor())
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
