package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.network.RaceNavEvent
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JoinBrowserScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.RaceScreen
import com.example.ui.screens.ResultsScreen

object Routes {
    const val HOME = "home"
    const val GARAGE = "garage"
    const val BROWSER = "browser"
    const val LOBBY = "lobby"
    const val RACE = "race"
    const val RESULTS = "results"
}

@Composable
fun MicroRCApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    val profile by viewModel.profile.collectAsState()
    val discoveredLobbies by viewModel.discoveredLobbies.collectAsState()
    val sessionRole by viewModel.sessionRole.collectAsState()
    val lobbyState by viewModel.lobbyState.collectAsState()
    val raceSnapshot by viewModel.raceSnapshot.collectAsState()
    val lastResults by viewModel.lastResults.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val overviewCamera by viewModel.overviewCamera.collectAsState()

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
                onHostRace = { viewModel.startHosting() },
                onJoinRace = { navController.navigate(Routes.BROWSER) },
                onSoloRace = { viewModel.startSolo() },
                onOpenGarage = { navController.navigate(Routes.GARAGE) },
                onUpdateDriverName = viewModel::updateDriverName
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
                onSelectTrack = viewModel::selectTrack,
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
                overviewCamera = overviewCamera,
                tiltController = viewModel.tiltController,
                onToggleOverviewCamera = viewModel::toggleOverviewCamera,
                onUpdateControlInput = { throttle, steer ->
                    viewModel.raceManager.updateLocalControlInput(throttle, steer)
                },
                onTriggerPowerUp = { viewModel.raceManager.triggerLocalPowerUp() },
                onSwitchControlMode = viewModel::updateControlSettings,
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
