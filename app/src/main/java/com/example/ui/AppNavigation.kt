package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HostSetupScreen
import com.example.ui.screens.JoinBrowserScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackEditorScreen

object Routes {
    const val HOME = "home"
    const val HOST_SETUP = "host_setup"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val TRACK_EDITOR = "track_editor"
    const val BROWSER = "browser"
    const val LOBBY = "lobby"
    const val RACE = "race"
    const val RESULTS = "results"
}

@Composable
fun MicroRCApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    DisposableEffect(lifecycleOwner, viewModel.audioEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.audioEngine.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.audioEngine.setAppForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.audioEngine.stop()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != Routes.RACE) {
            viewModel.audioEngine.setMenuMode()
        }
    }

    val profile by viewModel.profile.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val customTracks by viewModel.customTracks.collectAsState()
    val discoveredLobbies by viewModel.discoveredLobbies.collectAsState()
    val sessionRole by viewModel.sessionRole.collectAsState()
    val lobbyState by viewModel.lobbyState.collectAsState()
    val raceSnapshot by viewModel.raceSnapshot.collectAsState()
    val lastResults by viewModel.lastResults.collectAsState()
    val activeTrack by viewModel.activeTrack.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val overviewCamera by viewModel.overviewCamera.collectAsState()

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
                onOpenGarage = { navController.navigate(Routes.GARAGE) },
                onOpenTrackEditor = { navController.navigate(Routes.TRACK_EDITOR) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onUpdateDriverName = viewModel::updateDriverName
            )
        }

        composable(Routes.HOST_SETUP) {
            HostSetupScreen(
                initialRules = rules,
                onCreateLobby = { setupRules ->
                    viewModel.startHosting(setupRules)
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
            val canEdit = sessionRole == SessionRole.None ||
                sessionRole == SessionRole.Host ||
                sessionRole == SessionRole.Solo
            SettingsScreen(
                rules = effectiveRules,
                audioSettings = audioSettings,
                canEditRules = canEdit,
                inSession = sessionRole != SessionRole.None,
                onUpdateRules = viewModel::updateRaceRules,
                onUpdateAudio = viewModel::updateAudioSettings,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TRACK_EDITOR) {
            val canApplyToLobby = sessionRole == SessionRole.Host || sessionRole == SessionRole.Solo
            TrackEditorScreen(
                initialTrack = lobbyState?.activeCustomTrack ?: customTracks.firstOrNull(),
                savedTracks = customTracks,
                canApplyToLobby = canApplyToLobby,
                onSaveTrack = { track, apply ->
                    viewModel.saveCustomTrack(track, apply)
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
                onJoinLobby = viewModel::joinLobby,
                onJoinDirectIp = { ip, port -> viewModel.joinByDirectIp(ip, port) },
                onDismissError = viewModel::clearError,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LOBBY) {
            LobbyScreen(
                role = sessionRole,
                localPlayerId = profile.playerId,
                lobby = lobbyState,
                customTracks = customTracks,
                onSelectTrack = viewModel::selectTrack,
                onSelectArenaShape = viewModel::selectArenaShape,
                onSelectCustomTrack = viewModel::selectCustomTrack,
                onGenerateRandomTrack = viewModel::generateRandomTrackForLobby,
                onSelectRandomArena = viewModel::selectRandomArenaForLobby,
                onUpdateRules = viewModel::updateRaceRules,
                onOpenTrackEditor = { navController.navigate(Routes.TRACK_EDITOR) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSelectLaps = viewModel::setTotalLaps,
                onAddBot = viewModel::addBot,
                onRemoveBot = viewModel::removeBot,
                onToggleReady = viewModel::toggleReady,
                onSendChat = viewModel::sendChat,
                onStartRace = viewModel::startRace,
                onOpenGarage = { navController.navigate(Routes.GARAGE) },
                onLeaveLobby = {
                    viewModel.leaveSession()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.RACE) {
            RaceScreen(
                profile = profile,
                snapshot = raceSnapshot,
                activeTrack = activeTrack,
                rules = effectiveRules,
                audioSettings = audioSettings,
                audioEngine = viewModel.audioEngine,
                overviewCamera = overviewCamera,
                tiltController = viewModel.tiltController,
                onToggleOverviewCamera = viewModel::toggleOverviewCamera,
                onUpdateControlInput = { throttle, steer ->
                    viewModel.raceManager.updateLocalControlInput(throttle, steer)
                },
                onTriggerPowerUp = { viewModel.raceManager.triggerLocalPowerUp() },
                onSwitchControlMode = viewModel::updateControlSettings,
                onUpdateAudioSettings = viewModel::updateAudioSettings,
                onLeaveRace = {
                    viewModel.leaveSession()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.RESULTS) {
            ResultsScreen(
                role = sessionRole,
                localPlayerId = profile.playerId,
                results = lastResults ?: raceSnapshot,
                lobby = lobbyState,
                onReturnToLobby = viewModel::returnToLobby,
                onExitHome = {
                    viewModel.leaveSession()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
    }
}
