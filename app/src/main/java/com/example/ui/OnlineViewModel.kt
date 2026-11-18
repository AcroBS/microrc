package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OnlineRaceRepository
import com.example.data.TrackCatalog
import com.example.model.ArenaShape
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardBoardKey
import com.example.model.LeaderboardEntryDoc
import com.example.model.LobbyStatePayload
import com.example.model.OnlineRoomDoc
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RematchCountdownState
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.network.CompletedSoloRunSummary
import com.example.network.OnlineRaceSessionManager
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineViewModel(
    val repository: OnlineRaceRepository,
    val currentUserId: String
) : ViewModel() {

    val sessionManager = OnlineRaceSessionManager(
        repository = repository,
        currentUserId = currentUserId,
        scope = viewModelScope
    )

    val role: StateFlow<SessionRole> = sessionManager.role
    val activeRoom: StateFlow<OnlineRoomDoc?> = sessionManager.activeRoom
    val lobbyState: StateFlow<LobbyStatePayload?> = sessionManager.lobbyState
    val raceSnapshot: StateFlow<RaceSnapshot?> = sessionManager.raceSnapshot
    val lastResults: StateFlow<RaceSnapshot?> = sessionManager.lastResults
    val activeTrack: StateFlow<TrackDefinition?> = sessionManager.activeTrack
    val rematchState: StateFlow<RematchCountdownState?> = sessionManager.rematchState
    val errorBanner: StateFlow<String?> = sessionManager.errorBanner
    val isBusy: StateFlow<Boolean> = sessionManager.isBusy
    val navEvents: SharedFlow<RaceNavEvent> = sessionManager.navEvents

    private val _openRooms = MutableStateFlow<List<OnlineRoomDoc>>(emptyList())
    val openRooms: StateFlow<List<OnlineRoomDoc>> = _openRooms.asStateFlow()

    private val _cloudLeaderboard = MutableStateFlow<List<LeaderboardEntryDoc>>(emptyList())
    val cloudLeaderboard: StateFlow<List<LeaderboardEntryDoc>> = _cloudLeaderboard.asStateFlow()

    private val _leaderboardLoading = MutableStateFlow(false)
    val leaderboardLoading: StateFlow<Boolean> = _leaderboardLoading.asStateFlow()

    private val _ghostLoadingId = MutableStateFlow<String?>(null)
    val ghostLoadingId: StateFlow<String?> = _ghostLoadingId.asStateFlow()

    private val _cloudStatusMessage = MutableStateFlow<String?>(null)
    val cloudStatusMessage: StateFlow<String?> = _cloudStatusMessage.asStateFlow()

    private var roomsSubscriptionJob: Job? = null
    private var leaderboardSubscriptionJob: Job? = null

    init {
        startObservingOpenRooms()
    }

    private fun startObservingOpenRooms() {
        roomsSubscriptionJob?.cancel()
        roomsSubscriptionJob = viewModelScope.launch {
            try {
                repository.observeOpenRooms().collect { rooms ->
                    _openRooms.value = rooms
                }
            } catch (e: Exception) {
                _cloudStatusMessage.value = e.localizedMessage
            }
        }
    }

    fun observeLeaderboardForBoard(boardKey: String) {
        leaderboardSubscriptionJob?.cancel()
        _leaderboardLoading.value = true
        leaderboardSubscriptionJob = viewModelScope.launch {
            try {
                repository.observeLeaderboard(boardKey).collect { entries ->
                    _cloudLeaderboard.value = entries
                    _leaderboardLoading.value = false
                }
            } catch (e: Exception) {
                _leaderboardLoading.value = false
                _cloudStatusMessage.value = e.localizedMessage
            }
        }
    }

    fun syncSoloRunToCloud(profile: PlayerProfile, summary: CompletedSoloRunSummary) {
        viewModelScope.launch {
            val res = repository.submitPersonalBestAndGhost(
                profile = profile,
                gameMode = summary.gameMode,
                trackKey = summary.trackKey,
                boardKey = summary.boardKey,
                totalLaps = summary.totalLaps,
                finishTimeMs = summary.finishTimeMs,
                bestLapMs = summary.bestLapMs,
                bestScore = summary.bestScore,
                encodedGhostFrames = summary.encodedGhostFrames
            )
            res.onSuccess { improved ->
                if (improved) {
                    _cloudStatusMessage.value = "New Cloud Leaderboard Record & Ghost uploaded!"
                }
            }
        }
    }

    fun loadGhostRunForChallenge(
        ghostId: String,
        onLoaded: (GhostRunPlayback) -> Unit
    ) {
        if (ghostId.isBlank() || _ghostLoadingId.value != null) return
        _ghostLoadingId.value = ghostId
        viewModelScope.launch {
            val res = repository.fetchGhostRunPlayback(ghostId)
            _ghostLoadingId.value = null
            res.onSuccess { playback ->
                onLoaded(playback)
            }.onFailure { err ->
                _cloudStatusMessage.value = err.localizedMessage ?: "Could not load ghost run."
            }
        }
    }

    fun clearCloudStatusMessage() {
        _cloudStatusMessage.value = null
    }

    fun hostRoom(
        profile: PlayerProfile,
        roomName: String,
        maxPlayers: Int,
        discoverable: Boolean,
        password: String,
        rules: RaceRulesSettings
    ) {
        sessionManager.hostOnlineRoom(
            profile = profile,
            roomName = roomName,
            maxPlayers = maxPlayers,
            discoverable = discoverable,
            rawPassword = password,
            initialRules = rules
        )
    }

    fun joinRoom(roomId: String, profile: PlayerProfile, password: String) {
        sessionManager.joinOnlineRoom(roomId = roomId, profile = profile, rawPassword = password)
    }

    fun joinRoomByCode(
        code: String,
        profile: PlayerProfile,
        password: String,
        onPasswordRequired: (OnlineRoomDoc) -> Unit
    ) {
        sessionManager.joinOnlineRoomByCode(
            rawCode = code,
            profile = profile,
            rawPassword = password,
            onPasswordRequired = onPasswordRequired
        )
    }

    fun selectTrack(trackId: TrackId) = sessionManager.selectTrack(trackId)
    fun selectCustomTrack(customTrack: CustomTrackData) = sessionManager.selectCustomTrack(customTrack)
    fun selectArenaShape(shape: ArenaShape) = sessionManager.selectArenaShape(shape)
    fun selectStuntVariant(variant: StuntVariant) = sessionManager.selectStuntVariant(variant)
    fun generateRandomTrackForLobby() {
        val randomTrack = TrackCatalog.generateRandomTrack(seed = System.currentTimeMillis())
        sessionManager.selectCustomTrack(randomTrack)
    }
    fun selectRandomArenaForLobby() {
        val current = lobbyState.value?.arenaShape
        val choices = ArenaShape.entries.filter { it.name != current }.ifEmpty { ArenaShape.entries }
        val picked = choices[(System.currentTimeMillis() % choices.size).toInt().coerceAtLeast(0)]
        sessionManager.selectArenaShape(picked)
    }
    fun selectRandomStuntForLobby() {
        val current = lobbyState.value?.stuntVariant
        val choices = StuntVariant.entries.filter { it.name != current }.ifEmpty { StuntVariant.entries }
        val picked = choices[(System.currentTimeMillis() % choices.size).toInt().coerceAtLeast(0)]
        sessionManager.selectStuntVariant(picked)
    }

    fun updateRules(rules: RaceRulesSettings) = sessionManager.updateRules(rules)
    fun setTotalLaps(laps: Int) = sessionManager.setTotalLaps(laps)
    fun addBot() = sessionManager.addBot()
    fun removeBot() = sessionManager.removeBot()
    fun clearBots() = sessionManager.clearBots()
    fun toggleReady() = sessionManager.toggleReady()
    fun sendChat(senderName: String, text: String) = sessionManager.sendChat(senderName, text)
    fun startRace() = sessionManager.startRace()
    fun voteRematchOption(optionId: String) = sessionManager.voteRematchOption(optionId)
    fun launchRematchNow() = sessionManager.launchRematchNow()
    fun returnToLobby() = sessionManager.returnToLobby()
    fun leaveRoom() = sessionManager.leaveRoom()
    fun clearError() = sessionManager.clearError()

    suspend fun cleanupBeforeSignOut() {
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        sessionManager.leaveCurrentRoomInternal()
    }

    override fun onCleared() {
        super.onCleared()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        sessionManager.leaveRoom()
    }
}
