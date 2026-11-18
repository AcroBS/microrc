package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FriendsRepository
import com.example.data.OnlineRaceRepository
import com.example.data.TrackCatalog
import com.example.model.ArenaShape
import com.example.model.CustomTrackData
import com.example.model.DriverFriendCode
import com.example.model.DriverProfileDoc
import com.example.model.FriendUiModel
import com.example.model.FriendshipDoc
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardEntryDoc
import com.example.model.LobbyStatePayload
import com.example.model.OnlineRoomDoc
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RematchCountdownState
import com.example.model.SessionInviteDoc
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.example.network.CompletedSoloRunSummary
import com.example.network.OnlineRaceSessionManager
import com.example.network.RaceNavEvent
import com.example.network.SessionRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OnlineViewModel(
    val repository: OnlineRaceRepository,
    val friendsRepository: FriendsRepository,
    val currentUserId: String
) : ViewModel() {

    val sessionManager = OnlineRaceSessionManager(
        repository = repository,
        currentUserId = currentUserId,
        scope = viewModelScope
    )

    val myFriendCode: String = DriverFriendCode.fromUid(currentUserId)

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

    // Social, Friends Presence & Session Invites State
    private val _friendsList = MutableStateFlow<List<FriendUiModel>>(emptyList())
    val friendsList: StateFlow<List<FriendUiModel>> = _friendsList.asStateFlow()

    private val _incomingFriendRequests = MutableStateFlow<List<FriendshipDoc>>(emptyList())
    val incomingFriendRequests: StateFlow<List<FriendshipDoc>> = _incomingFriendRequests.asStateFlow()

    private val _outgoingFriendRequests = MutableStateFlow<List<FriendshipDoc>>(emptyList())
    val outgoingFriendRequests: StateFlow<List<FriendshipDoc>> = _outgoingFriendRequests.asStateFlow()

    private val _driverSearchResults = MutableStateFlow<List<DriverProfileDoc>>(emptyList())
    val driverSearchResults: StateFlow<List<DriverProfileDoc>> = _driverSearchResults.asStateFlow()

    private val _isSearchingDrivers = MutableStateFlow(false)
    val isSearchingDrivers: StateFlow<Boolean> = _isSearchingDrivers.asStateFlow()

    private val _incomingSessionInvites = MutableStateFlow<List<SessionInviteDoc>>(emptyList())
    val incomingSessionInvites: StateFlow<List<SessionInviteDoc>> = _incomingSessionInvites.asStateFlow()

    private val _socialFeedback = MutableStateFlow<String?>(null)
    val socialFeedback: StateFlow<String?> = _socialFeedback.asStateFlow()

    private val _appInForeground = MutableStateFlow(true)
    private var latestKnownProfile: PlayerProfile = PlayerProfile(playerId = currentUserId)

    private var roomsSubscriptionJob: Job? = null
    private var leaderboardSubscriptionJob: Job? = null
    private var friendshipsJob: Job? = null
    private var invitesJob: Job? = null
    private var presenceHeartbeatJob: Job? = null

    init {
        startObservingOpenRooms()
        startObservingFriendsAndPresence()
        startObservingSessionInvites()
        startPresenceLoop()
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startObservingFriendsAndPresence() {
        friendshipsJob?.cancel()
        friendshipsJob = viewModelScope.launch {
            try {
                val rawFriendshipsFlow = MutableStateFlow<List<FriendshipDoc>>(emptyList())
                launch {
                    friendsRepository.observeFriendships(currentUserId).collect { list ->
                        rawFriendshipsFlow.value = list
                        _incomingFriendRequests.value = list.filter {
                            it.status == "PENDING" && it.receiverUid == currentUserId
                        }
                        _outgoingFriendRequests.value = list.filter {
                            it.status == "PENDING" && it.requesterUid == currentUserId
                        }
                    }
                }

                val acceptedFlow = rawFriendshipsFlow.map { list ->
                    list.filter { it.status == "ACCEPTED" }
                }

                val presenceMapFlow = acceptedFlow
                    .map { accepted ->
                        accepted.map { doc ->
                            if (doc.requesterUid == currentUserId) doc.receiverUid else doc.requesterUid
                        }.distinct().sorted()
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { friendUids ->
                        friendsRepository.observeFriendsPresence(friendUids)
                    }

                // Refresh relative freshness every 30s alongside live Firestore updates
                val tickerFlow = MutableStateFlow(System.currentTimeMillis() / 1000L)
                launch {
                    while (isActive) {
                        delay(30_000L)
                        tickerFlow.value = System.currentTimeMillis() / 1000L
                    }
                }

                combine(acceptedFlow, presenceMapFlow, tickerFlow) { accepted, presenceMap, nowSec ->
                    accepted.map { doc ->
                        val friendUid = if (doc.requesterUid == currentUserId) doc.receiverUid else doc.requesterUid
                        DriverFriendCode.toFriendUiModel(
                            doc = doc,
                            myUid = currentUserId,
                            liveProfile = presenceMap[friendUid],
                            nowEpochSec = nowSec
                        )
                    }.sortedWith(
                        compareByDescending<FriendUiModel> { it.effectivelyOnline }
                            .thenByDescending { it.canJoinActiveLobby }
                            .thenBy { it.displayName.lowercase() }
                    )
                }.collect { mapped ->
                    _friendsList.value = mapped
                }
            } catch (e: Exception) {
                _socialFeedback.value = e.localizedMessage
            }
        }
    }

    private fun startObservingSessionInvites() {
        invitesJob?.cancel()
        invitesJob = viewModelScope.launch {
            try {
                friendsRepository.observeIncomingSessionInvites(currentUserId).collect { invites ->
                    _incomingSessionInvites.value = invites
                }
            } catch (e: Exception) {
                _socialFeedback.value = e.localizedMessage
            }
        }
    }

    private fun startPresenceLoop() {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = viewModelScope.launch {
            // Immediate reactive push when online room or foreground state changes
            launch {
                combine(
                    _appInForeground,
                    activeRoom,
                    role,
                    raceSnapshot.map { it?.phase }.distinctUntilChanged()
                ) { fg, room, sessionRole, phase ->
                    val status = when {
                        !fg -> "OFFLINE"
                        sessionRole.isOnline && (room?.status == "RACING" || phase == RacePhase.RACING.name || phase == RacePhase.COUNTDOWN.name) -> "RACING"
                        sessionRole.isOnline && room != null -> "IN_LOBBY"
                        else -> "ONLINE"
                    }
                    Triple(fg, status, room)
                }.distinctUntilChanged().collect { (fg, status, room) ->
                    if (fg) {
                        friendsRepository.upsertPresence(
                            profile = latestKnownProfile,
                            online = true,
                            activityStatus = status,
                            activeRoom = if (role.value.isOnline) room else null
                        )
                    } else {
                        friendsRepository.setOfflinePresence(latestKnownProfile)
                    }
                }
            }

            // Periodic 45-second heartbeat while app is in foreground so friends see fresh online status
            while (isActive) {
                delay(45_000L)
                if (_appInForeground.value) {
                    val curRoom = if (role.value.isOnline) activeRoom.value else null
                    val status = when {
                        curRoom?.status == "RACING" -> "RACING"
                        curRoom != null -> "IN_LOBBY"
                        else -> "ONLINE"
                    }
                    friendsRepository.upsertPresence(
                        profile = latestKnownProfile,
                        online = true,
                        activityStatus = status,
                        activeRoom = curRoom
                    )
                }
            }
        }
    }

    fun syncProfileToPresence(profile: PlayerProfile) {
        latestKnownProfile = profile
        if (_appInForeground.value) {
            val curRoom = if (role.value.isOnline) activeRoom.value else null
            val status = when {
                curRoom?.status == "RACING" -> "RACING"
                curRoom != null -> "IN_LOBBY"
                else -> "ONLINE"
            }
            viewModelScope.launch {
                friendsRepository.upsertPresence(
                    profile = profile,
                    online = true,
                    activityStatus = status,
                    activeRoom = curRoom
                )
            }
        }
    }

    fun setAppForeground(inForeground: Boolean) {
        _appInForeground.value = inForeground
    }

    fun searchDrivers(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _driverSearchResults.value = emptyList()
            return
        }
        _isSearchingDrivers.value = true
        viewModelScope.launch {
            val res = friendsRepository.searchDrivers(trimmed)
            _isSearchingDrivers.value = false
            res.onSuccess { list ->
                _driverSearchResults.value = list
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Driver search failed."
            }
        }
    }

    fun addFriendByCode(profile: PlayerProfile, code: String) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequestByCode(profile, code)
            res.onSuccess { targetName ->
                _socialFeedback.value = "Friend request sent to $targetName!"
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Could not send friend request."
            }
        }
    }

    fun addFriendFromDirectory(profile: PlayerProfile, target: DriverProfileDoc) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequest(profile, target)
            res.onSuccess {
                _socialFeedback.value = "Friend request sent to ${target.driverName}!"
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Could not send friend request."
            }
        }
    }

    fun addFriendByUid(profile: PlayerProfile, targetUid: String, fallbackName: String) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequestByUid(profile, targetUid, fallbackName)
            res.onSuccess { targetName ->
                _socialFeedback.value = "Friend request sent to $targetName!"
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Could not send friend request."
            }
        }
    }

    fun acceptFriendRequest(friendshipId: String, profile: PlayerProfile) {
        viewModelScope.launch {
            val res = friendsRepository.acceptFriendRequest(friendshipId, profile.name)
            res.onSuccess {
                _socialFeedback.value = "Friend added to your Crew!"
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Unable to accept friend request."
            }
        }
    }

    fun removeOrDeclineFriend(friendshipId: String, feedbackText: String = "Updated Friends List.") {
        viewModelScope.launch {
            val res = friendsRepository.removeOrDeclineFriendship(friendshipId)
            res.onSuccess {
                _socialFeedback.value = feedbackText
            }.onFailure { err ->
                _socialFeedback.value = err.localizedMessage ?: "Unable to update friendship."
            }
        }
    }

    /**
     * Notifies a friend to join our online lobby together.
     * If we are not yet in an online lobby, automatically hosts an online lobby first
     * and dispatches the real-time invite as soon as the lobby opens!
     */
    fun notifyFriendToJoin(
        friend: FriendUiModel,
        profile: PlayerProfile,
        defaultRules: RaceRulesSettings,
        onBeforeAutoHost: (() -> Unit)? = null
    ) {
        val currentRoom = activeRoom.value
        if (role.value.isOnline && currentRoom != null) {
            viewModelScope.launch {
                val res = friendsRepository.sendSessionInvite(
                    senderProfile = profile,
                    recipientUid = friend.friendUid,
                    recipientName = friend.displayName,
                    room = currentRoom
                )
                res.onSuccess {
                    _socialFeedback.value = "Sent session invite to ${friend.displayName} (#${currentRoom.roomCode})!"
                }.onFailure { err ->
                    _socialFeedback.value = err.localizedMessage ?: "Failed to notify ${friend.displayName}."
                }
            }
        } else {
            onBeforeAutoHost?.invoke()
            sessionManager.hostOnlineRoom(
                profile = profile,
                roomName = "${profile.name.take(12)}'s Crew",
                maxPlayers = 4,
                discoverable = true,
                rawPassword = "",
                initialRules = defaultRules,
                onCreated = { createdRoom ->
                    viewModelScope.launch {
                        val res = friendsRepository.sendSessionInvite(
                            senderProfile = profile,
                            recipientUid = friend.friendUid,
                            recipientName = friend.displayName,
                            room = createdRoom
                        )
                        res.onSuccess {
                            _socialFeedback.value = "Lobby #${createdRoom.roomCode} opened & notified ${friend.displayName}!"
                        }.onFailure { err ->
                            _socialFeedback.value = err.localizedMessage ?: "Lobby opened, but invite failed."
                        }
                    }
                }
            )
        }
    }

    fun notifyMultipleFriendsToJoin(
        targets: List<FriendUiModel>,
        profile: PlayerProfile,
        defaultRules: RaceRulesSettings,
        onBeforeAutoHost: (() -> Unit)? = null
    ) {
        if (targets.isEmpty()) return
        val currentRoom = activeRoom.value
        if (role.value.isOnline && currentRoom != null) {
            viewModelScope.launch {
                var sentCount = 0
                targets.forEach { f ->
                    val r = friendsRepository.sendSessionInvite(
                        senderProfile = profile,
                        recipientUid = f.friendUid,
                        recipientName = f.displayName,
                        room = currentRoom
                    )
                    if (r.isSuccess) sentCount++
                }
                _socialFeedback.value = "Notified $sentCount friend${if (sentCount == 1) "" else "s"} to join #${currentRoom.roomCode}!"
            }
        } else {
            onBeforeAutoHost?.invoke()
            sessionManager.hostOnlineRoom(
                profile = profile,
                roomName = "${profile.name.take(12)}'s Crew",
                maxPlayers = 4,
                discoverable = true,
                rawPassword = "",
                initialRules = defaultRules,
                onCreated = { createdRoom ->
                    viewModelScope.launch {
                        var sentCount = 0
                        targets.forEach { f ->
                            val r = friendsRepository.sendSessionInvite(
                                senderProfile = profile,
                                recipientUid = f.friendUid,
                                recipientName = f.displayName,
                                room = createdRoom
                            )
                            if (r.isSuccess) sentCount++
                        }
                        _socialFeedback.value = "Lobby #${createdRoom.roomCode} ready — notified $sentCount friend${if (sentCount == 1) "" else "s"}!"
                    }
                }
            )
        }
    }

    fun acceptSessionInvite(
        invite: SessionInviteDoc,
        profile: PlayerProfile,
        onBeforeJoin: (() -> Unit)? = null,
        onPasswordRequired: (OnlineRoomDoc) -> Unit
    ) {
        viewModelScope.launch {
            friendsRepository.respondToSessionInvite(invite.id, accepted = true)
        }
        onBeforeJoin?.invoke()
        sessionManager.joinOnlineRoomByCode(
            rawCode = invite.roomCode,
            profile = profile,
            rawPassword = "",
            preAuthorizedJoinToken = invite.joinToken,
            onPasswordRequired = onPasswordRequired
        )
    }

    fun declineSessionInvite(inviteId: String) {
        viewModelScope.launch {
            friendsRepository.respondToSessionInvite(inviteId, accepted = false)
        }
    }

    fun clearSocialFeedback() {
        _socialFeedback.value = null
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

    fun joinRoom(
        roomId: String,
        profile: PlayerProfile,
        password: String,
        preAuthorizedJoinToken: String = ""
    ) {
        sessionManager.joinOnlineRoom(
            roomId = roomId,
            profile = profile,
            rawPassword = password,
            preAuthorizedJoinToken = preAuthorizedJoinToken
        )
    }

    fun joinRoomByCode(
        code: String,
        profile: PlayerProfile,
        password: String,
        preAuthorizedJoinToken: String = "",
        onPasswordRequired: (OnlineRoomDoc) -> Unit
    ) {
        sessionManager.joinOnlineRoomByCode(
            rawCode = code,
            profile = profile,
            rawPassword = password,
            preAuthorizedJoinToken = preAuthorizedJoinToken,
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
        presenceHeartbeatJob?.cancel()
        friendshipsJob?.cancel()
        invitesJob?.cancel()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        friendsRepository.setOfflinePresence(latestKnownProfile)
        sessionManager.leaveCurrentRoomInternal()
    }

    override fun onCleared() {
        super.onCleared()
        presenceHeartbeatJob?.cancel()
        friendshipsJob?.cancel()
        invitesJob?.cancel()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        sessionManager.leaveRoom()
    }
}
