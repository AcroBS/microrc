package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.RaceAudioEngine
import com.example.data.DailyChallengeCatalog
import com.example.data.PreferencesRepository
import com.example.data.TrackCatalog
import com.example.model.ActiveGhostCarState
import com.example.model.ArenaShape
import com.example.model.AudioSettings
import com.example.model.ControlMode
import com.example.model.CustomTrackData
import com.example.model.DiscoveredLobby
import com.example.model.GameMode
import com.example.model.GhostCodec
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardBoardKey
import com.example.model.LobbyStatePayload
import com.example.model.LocalLeaderboardRecord
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RcCarType
import com.example.model.RematchCountdownState
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.model.WinCondition
import com.example.network.CompletedSoloRunSummary
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import com.example.network.WifiP2pRaceManager
import com.example.sensors.RcHapticController
import com.example.sensors.TiltSteeringController
import com.example.util.AppUpdateUiState
import com.example.util.GitHubUpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val raceManager = WifiP2pRaceManager(application, viewModelScope)
    val tiltController = TiltSteeringController(application)
    val audioEngine = RaceAudioEngine(viewModelScope)
    val hapticController = RcHapticController(application)

    val profile: StateFlow<PlayerProfile> = prefsRepo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerProfile(playerId = "rc_init")
    )

    val rules: StateFlow<RaceRulesSettings> = prefsRepo.rulesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RaceRulesSettings()
    )

    val audioSettings: StateFlow<AudioSettings> = prefsRepo.audioSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioSettings()
    )

    val customTracks: StateFlow<List<CustomTrackData>> = prefsRepo.customTracksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val localLeaderboard: StateFlow<List<LocalLeaderboardRecord>> = prefsRepo.localLeaderboardFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val discoveredLobbies: StateFlow<List<DiscoveredLobby>> = raceManager.discovery.discoveredLobbies
    val sessionRole: StateFlow<SessionRole> = raceManager.role
    val lobbyState: StateFlow<LobbyStatePayload?> = raceManager.lobbyState
    val raceSnapshot: StateFlow<RaceSnapshot?> = raceManager.raceSnapshot
    val lastResults: StateFlow<RaceSnapshot?> = raceManager.lastResults
    val activeTrack: StateFlow<TrackDefinition?> = raceManager.activeTrack
    val connectionError: StateFlow<String?> = raceManager.connectionError
    val rematchState: StateFlow<RematchCountdownState?> = raceManager.rematchState
    val activeChallengeGhost: StateFlow<GhostRunPlayback?> = raceManager.activeChallengeGhost
    val activeGhostCar: StateFlow<ActiveGhostCarState?> = raceManager.activeGhostCar
    val isDailyChallengeSession: StateFlow<Boolean> = raceManager.isDailyChallenge
    val latestCompletedSoloRun: SharedFlow<CompletedSoloRunSummary> = raceManager.completedSoloRuns
    val navEvents: SharedFlow<RaceNavEvent> = raceManager.navEvents

    val dailyNotificationsEnabled: StateFlow<Boolean> = prefsRepo.dailyNotificationsEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val notificationPermissionPrompted: StateFlow<Boolean> = prefsRepo.notificationPermissionPromptedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val dismissedUpdateTag: StateFlow<String?> = prefsRepo.dismissedUpdateTagFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val isPlayStoreInstall: Boolean = GitHubUpdateChecker.isInstalledFromPlayStore(application)

    private val _appUpdateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _overviewCamera = MutableStateFlow(false)
    val overviewCamera: StateFlow<Boolean> = _overviewCamera.asStateFlow()

    private val _lastRunWasPersonalBest = MutableStateFlow(false)
    val lastRunWasPersonalBest: StateFlow<Boolean> = _lastRunWasPersonalBest.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.ensurePlayerId()
        }
        if (!isPlayStoreInstall && BuildConfig.GITHUB_REPOSITORY.isNotBlank()) {
            checkForAppUpdate(manual = false)
        }
        viewModelScope.launch {
            audioSettings.collect {
                audioEngine.updateAudioSettings(it)
                hapticController.enabled = it.hapticsEnabled
            }
        }
        viewModelScope.launch {
            raceManager.completedSoloRuns.collect { summary ->
                val improved = prefsRepo.recordSoloRun(
                    profile = profile.value,
                    boardKey = summary.boardKey,
                    gameMode = summary.gameMode,
                    trackKey = summary.trackKey,
                    totalLaps = summary.totalLaps,
                    finishTimeMs = summary.finishTimeMs,
                    bestLapMs = summary.bestLapMs,
                    bestScore = summary.bestScore,
                    encodedGhostFrames = summary.encodedGhostFrames
                )
                _lastRunWasPersonalBest.value = improved
            }
        }
    }

    fun checkForAppUpdate(manual: Boolean = true) {
        if (isPlayStoreInstall) {
            _appUpdateState.value = AppUpdateUiState.Idle
            return
        }
        if (_appUpdateState.value is AppUpdateUiState.Checking) return
        viewModelScope.launch {
            if (manual) {
                prefsRepo.clearDismissedUpdateTag()
                _appUpdateState.value = AppUpdateUiState.Checking
            }
            val result = GitHubUpdateChecker.checkLatestRelease(
                repository = BuildConfig.GITHUB_REPOSITORY,
                currentVersion = BuildConfig.VERSION_NAME
            )
            // Keep automatic startup checks silent when offline or unconfigured
            _appUpdateState.value = if (!manual && result is AppUpdateUiState.Unavailable) {
                AppUpdateUiState.Idle
            } else {
                result
            }
        }
    }

    fun dismissUpdateHint(tagName: String) {
        viewModelScope.launch {
            prefsRepo.dismissUpdateTag(tagName)
        }
    }

    fun updateDriverName(name: String) {
        viewModelScope.launch {
            prefsRepo.updateDriverName(name)
            val updated = profile.value.copy(name = name.trim().take(16).ifEmpty { "Racer" })
            raceManager.updateLocalProfile(updated)
        }
    }

    fun updateCarAndColors(carType: RcCarType, primaryArgb: Long, accentArgb: Long) {
        viewModelScope.launch {
            prefsRepo.updateCarAndColors(carType, primaryArgb, accentArgb)
            val updated = profile.value.copy(
                carType = carType,
                primaryColorArgb = primaryArgb,
                accentColorArgb = accentArgb
            )
            raceManager.updateLocalProfile(updated)
        }
    }

    fun updateControlSettings(
        mode: ControlMode,
        sensitivity: Float,
        autoGasInTilt: Boolean,
        sliderSensitivity: Float = profile.value.sliderSensitivity
    ) {
        viewModelScope.launch {
            prefsRepo.updateControlSettings(mode, sensitivity, autoGasInTilt, sliderSensitivity)
            tiltController.setSensitivity(sensitivity)
        }
    }

    fun updateRaceRules(newRules: RaceRulesSettings) {
        viewModelScope.launch {
            prefsRepo.updateRaceRules(newRules)
            if (sessionRole.value == SessionRole.Host || sessionRole.value == SessionRole.Solo) {
                raceManager.updateRules(newRules)
            }
        }
    }

    fun updateAudioSettings(newAudio: AudioSettings) {
        viewModelScope.launch {
            prefsRepo.updateAudioSettings(newAudio)
            audioEngine.updateAudioSettings(newAudio)
            hapticController.enabled = newAudio.hapticsEnabled
        }
    }

    fun saveCustomTrack(track: CustomTrackData, applyToLobby: Boolean) {
        viewModelScope.launch {
            prefsRepo.saveCustomTrack(track)
            if (applyToLobby && (sessionRole.value == SessionRole.Host || sessionRole.value == SessionRole.Solo)) {
                raceManager.selectCustomTrack(track)
            }
        }
    }

    fun deleteCustomTrack(trackId: String) {
        viewModelScope.launch {
            prefsRepo.deleteCustomTrack(trackId)
        }
    }

    fun toggleOverviewCamera() {
        _overviewCamera.value = !_overviewCamera.value
    }

    fun startHosting(setupRules: RaceRulesSettings = rules.value) {
        viewModelScope.launch {
            prefsRepo.updateRaceRules(setupRules)
        }
        raceManager.hostLobby(profile.value, initialRules = setupRules)
    }

    fun startSolo() {
        _lastRunWasPersonalBest.value = false
        val soloRules = rules.value.copy(gameMode = GameMode.RACE.name)
        raceManager.startSoloSession(profile.value, initialRules = soloRules, includeBots = true)
    }

    fun startPureSolo() {
        _lastRunWasPersonalBest.value = false
        val laps = rules.value.totalLaps.coerceIn(1, 9)
        val pureRules = rules.value.copy(
            gameMode = GameMode.RACE.name,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            totalLaps = laps
        )
        val boardKey = LeaderboardBoardKey.build(GameMode.RACE, TrackId.LIVING_ROOM_GP.name, laps)
        val savedPb = localLeaderboard.value.find { it.boardKey == boardKey }
        val pbGhost = savedPb?.takeIf { it.ghostFramesData.isNotBlank() }?.let { rec ->
            val frames = GhostCodec.decodeFrames(rec.ghostFramesData)
            if (frames.size >= 2) {
                GhostRunPlayback(
                    ghostId = "local_${rec.boardKey}",
                    driverName = "${rec.driverName} (PB)",
                    carType = rec.carType,
                    primaryColorArgb = rec.primaryColorArgb,
                    accentColorArgb = rec.accentColorArgb,
                    boardKey = rec.boardKey,
                    trackId = rec.trackKey,
                    totalLaps = rec.totalLaps,
                    finishTimeMs = rec.bestRaceTimeMs,
                    bestLapMs = rec.bestLapTimeMs,
                    frames = frames
                )
            } else {
                null
            }
        }
        raceManager.startSoloSession(
            profile = profile.value,
            initialRules = pureRules,
            includeBots = false,
            challengeGhost = pbGhost
        )
    }

    fun startPureSoloForTrack(
        mode: GameMode,
        trackId: TrackId,
        laps: Int,
        ghost: GhostRunPlayback? = null
    ) {
        _lastRunWasPersonalBest.value = false
        val cleanLaps = laps.coerceIn(1, 9)
        val setupRules = rules.value.copy(
            gameMode = mode.name,
            winCondition = WinCondition.FIRST_TO_FINISH.name,
            totalLaps = cleanLaps
        )
        raceManager.startSoloSession(
            profile = profile.value,
            initialRules = setupRules,
            includeBots = false,
            challengeGhost = if (mode == GameMode.RACE) ghost else null
        )
        if (mode == GameMode.RACE) {
            raceManager.selectTrack(trackId)
            raceManager.setTotalLaps(cleanLaps)
        }
        raceManager.startRace()
    }

    fun startGhostChallenge(playback: GhostRunPlayback) {
        val dailyDateKey = DailyChallengeCatalog.extractDateKey(playback.trackId)
        if (dailyDateKey != null) {
            startDailyChallenge(dateKey = dailyDateKey, customGhost = playback)
            return
        }
        val targetTrack = runCatching { TrackId.valueOf(playback.trackId) }
            .getOrDefault(TrackId.LIVING_ROOM_GP)
        startPureSoloForTrack(
            mode = GameMode.RACE,
            trackId = targetTrack,
            laps = playback.totalLaps,
            ghost = playback
        )
    }

    fun startDailyChallenge(
        dateKey: String = DailyChallengeCatalog.todayDateKey(),
        customGhost: GhostRunPlayback? = null
    ) {
        _lastRunWasPersonalBest.value = false
        val normalizedDate = DailyChallengeCatalog.normalizeDateKey(dateKey)
        val dailyTrack = DailyChallengeCatalog.dailyCustomTrack(normalizedDate)
        val boardKey = DailyChallengeCatalog.boardKeyForDate(normalizedDate)
        val ghostToUse = customGhost ?: localLeaderboard.value.find { it.boardKey == boardKey }
            ?.takeIf { it.ghostFramesData.isNotBlank() }
            ?.let { rec ->
                val frames = GhostCodec.decodeFrames(rec.ghostFramesData)
                if (frames.size >= 2) {
                    GhostRunPlayback(
                        ghostId = "local_${rec.boardKey}",
                        driverName = "${rec.driverName} (PB)",
                        carType = rec.carType,
                        primaryColorArgb = rec.primaryColorArgb,
                        accentColorArgb = rec.accentColorArgb,
                        boardKey = rec.boardKey,
                        trackId = rec.trackKey,
                        totalLaps = rec.totalLaps,
                        finishTimeMs = rec.bestRaceTimeMs,
                        bestLapMs = rec.bestLapTimeMs,
                        frames = frames
                    )
                } else {
                    null
                }
            }
        raceManager.startDailyChallengeSession(
            profile = profile.value,
            baseRules = rules.value,
            customTrack = dailyTrack,
            dateKey = normalizedDate,
            challengeGhost = ghostToUse
        )
    }

    fun restartActiveRace() {
        _lastRunWasPersonalBest.value = false
        raceManager.restartRace()
    }

    fun setDailyNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setDailyNotificationsEnabled(enabled)
        }
    }

    fun markNotificationPermissionPrompted() {
        viewModelScope.launch {
            prefsRepo.markNotificationPermissionPrompted()
        }
    }

    fun startSoloWithRules(setupRules: RaceRulesSettings) {
        _lastRunWasPersonalBest.value = false
        viewModelScope.launch {
            prefsRepo.updateRaceRules(setupRules)
        }
        raceManager.startSoloSession(profile.value, initialRules = setupRules)
    }

    fun startBrowsing() {
        raceManager.discovery.startBrowsing(viewModelScope, localHostIdToIgnore = profile.value.playerId)
    }

    fun stopBrowsing() {
        raceManager.discovery.stopBrowsing()
    }

    fun joinLobby(lobby: DiscoveredLobby) {
        raceManager.discovery.stopBrowsing()
        raceManager.joinLobby(lobby.hostIp, lobby.tcpPort, profile.value)
    }

    fun joinByDirectIp(ip: String, port: Int = WifiP2pRaceManager.DEFAULT_TCP_PORT) {
        raceManager.discovery.stopBrowsing()
        raceManager.joinLobby(ip, port, profile.value)
    }

    fun selectTrack(trackId: TrackId) {
        raceManager.selectTrack(trackId)
        refreshDefaultLocalGhostIfMatching()
    }
    fun selectArenaShape(shape: ArenaShape) = raceManager.selectArenaShape(shape)
    fun selectCustomTrack(customTrack: CustomTrackData) {
        raceManager.setChallengeGhost(null)
        raceManager.selectCustomTrack(customTrack)
    }
    fun generateRandomTrackForLobby() {
        raceManager.setChallengeGhost(null)
        val randomTrack = TrackCatalog.generateRandomTrack(seed = System.currentTimeMillis())
        raceManager.selectCustomTrack(randomTrack)
    }
    fun selectRandomArenaForLobby() {
        val current = lobbyState.value?.arenaShape
        val choices = ArenaShape.entries.filter { it.name != current }.ifEmpty { ArenaShape.entries }
        val picked = choices[(System.currentTimeMillis() % choices.size).toInt().coerceAtLeast(0)]
        raceManager.selectArenaShape(picked)
    }
    fun selectStuntVariant(variant: StuntVariant) = raceManager.selectStuntVariant(variant)
    fun selectRandomStuntForLobby() {
        val current = lobbyState.value?.stuntVariant
        val choices = StuntVariant.entries.filter { it.name != current }.ifEmpty { StuntVariant.entries }
        val picked = choices[(System.currentTimeMillis() % choices.size).toInt().coerceAtLeast(0)]
        raceManager.selectStuntVariant(picked)
    }

    fun setTotalLaps(laps: Int) {
        raceManager.setTotalLaps(laps)
        viewModelScope.launch {
            prefsRepo.updateRaceRules(rules.value.copy(totalLaps = laps))
        }
        refreshDefaultLocalGhostIfMatching()
    }

    private fun refreshDefaultLocalGhostIfMatching() {
        if (sessionRole.value != SessionRole.Solo) return
        val lobby = lobbyState.value ?: return
        if (lobby.activeCustomTrack != null || lobby.players.any { it.isBot }) return
        val currentGhost = activeChallengeGhost.value
        if (currentGhost != null && !currentGhost.ghostId.startsWith("local_")) return
        val boardKey = LeaderboardBoardKey.build(GameMode.RACE, lobby.trackId, lobby.totalLaps)
        val rec = localLeaderboard.value.find { it.boardKey == boardKey }
        val pbGhost = rec?.takeIf { it.ghostFramesData.isNotBlank() }?.let { r ->
            val frames = GhostCodec.decodeFrames(r.ghostFramesData)
            if (frames.size >= 2) {
                GhostRunPlayback(
                    ghostId = "local_${r.boardKey}",
                    driverName = "${r.driverName} (PB)",
                    carType = r.carType,
                    primaryColorArgb = r.primaryColorArgb,
                    accentColorArgb = r.accentColorArgb,
                    boardKey = r.boardKey,
                    trackId = r.trackKey,
                    totalLaps = r.totalLaps,
                    finishTimeMs = r.bestRaceTimeMs,
                    bestLapMs = r.bestLapTimeMs,
                    frames = frames
                )
            } else {
                null
            }
        }
        raceManager.setChallengeGhost(pbGhost)
    }

    fun addBot() = raceManager.addBot()
    fun removeBot() = raceManager.removeBot()
    fun clearBots() = raceManager.clearBots()
    fun clearChallengeGhost() = raceManager.setChallengeGhost(null)
    fun toggleReady() = raceManager.toggleReady()
    fun sendChat(text: String) = raceManager.sendChatMessage(profile.value.name, text)
    fun startRace() {
        _lastRunWasPersonalBest.value = false
        raceManager.startRace()
    }
    fun voteRematchOption(optionId: String) = raceManager.voteNextRematchTrack(optionId)
    fun launchRematchNow() {
        _lastRunWasPersonalBest.value = false
        raceManager.launchRematchNow()
    }
    fun returnToLobby() = raceManager.returnToLobby()
    fun leaveSession() = raceManager.leaveSession()
    fun clearError() = raceManager.clearError()

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
        tiltController.stop()
        raceManager.discovery.stopBrowsing()
        raceManager.leaveSession()
    }
}
