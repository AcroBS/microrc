package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesRepository
import com.example.data.TrackCatalog
import com.example.model.ControlMode
import com.example.model.CustomTrackData
import com.example.model.DiscoveredLobby
import com.example.model.LobbyStatePayload
import com.example.model.PlayerProfile
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RcCarType
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import com.example.network.WifiP2pRaceManager
import com.example.sensors.TiltSteeringController
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

    val customTracks: StateFlow<List<CustomTrackData>> = prefsRepo.customTracksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val discoveredLobbies: StateFlow<List<DiscoveredLobby>> = raceManager.discovery.discoveredLobbies
    val sessionRole: StateFlow<SessionRole> = raceManager.role
    val lobbyState: StateFlow<LobbyStatePayload?> = raceManager.lobbyState
    val raceSnapshot: StateFlow<RaceSnapshot?> = raceManager.raceSnapshot
    val lastResults: StateFlow<RaceSnapshot?> = raceManager.lastResults
    val activeTrack: StateFlow<TrackDefinition?> = raceManager.activeTrack
    val connectionError: StateFlow<String?> = raceManager.connectionError
    val navEvents: SharedFlow<RaceNavEvent> = raceManager.navEvents

    private val _overviewCamera = MutableStateFlow(false)
    val overviewCamera: StateFlow<Boolean> = _overviewCamera.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.ensurePlayerId()
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
        autoGasInTilt: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updateControlSettings(mode, sensitivity, autoGasInTilt)
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

    fun startHosting() {
        raceManager.hostLobby(profile.value, initialRules = rules.value)
    }

    fun startSolo() {
        raceManager.startSoloSession(profile.value, initialRules = rules.value)
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

    fun selectTrack(trackId: TrackId) = raceManager.selectTrack(trackId)
    fun selectCustomTrack(customTrack: CustomTrackData) = raceManager.selectCustomTrack(customTrack)
    fun generateRandomTrackForLobby() {
        val randomTrack = TrackCatalog.generateRandomTrack(seed = System.currentTimeMillis())
        raceManager.selectCustomTrack(randomTrack)
    }

    fun setTotalLaps(laps: Int) = raceManager.setTotalLaps(laps)
    fun addBot() = raceManager.addBot()
    fun removeBot() = raceManager.removeBot()
    fun toggleReady() = raceManager.toggleReady()
    fun sendChat(text: String) = raceManager.sendChatMessage(profile.value.name, text)
    fun startRace() = raceManager.startRace()
    fun returnToLobby() = raceManager.returnToLobby()
    fun leaveSession() = raceManager.leaveSession()
    fun clearError() = raceManager.clearError()

    override fun onCleared() {
        super.onCleared()
        tiltController.stop()
        raceManager.discovery.stopBrowsing()
        raceManager.leaveSession()
    }
}
