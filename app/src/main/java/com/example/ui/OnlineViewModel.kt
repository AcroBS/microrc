package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DailyChallengeCatalog
import com.example.data.FriendsRepository
import com.example.data.OnlineRaceRepository
import com.example.data.TrackCatalog
import com.example.data.formatUserFacingOnlineErrorOrNull
import com.example.data.rethrowIfScopeCancellation
import com.example.model.ArenaShape
import com.example.model.ChallengeInviteResponse
import com.example.model.ChallengeProgressEvaluator
import com.example.model.ChallengedFriendStatus
import com.example.model.CustomTrackData
import com.example.model.DriverFriendCode
import com.example.model.DriverProfileDoc
import com.example.model.FriendOnlineAlert
import com.example.model.FriendPresenceTransitionTracker
import com.example.model.FriendUiModel
import com.example.model.FriendshipDoc
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardEntryDoc
import com.example.model.LeaderboardNotificationDoc
import com.example.model.LobbyStatePayload
import com.example.model.OnlineRoomDoc
import com.example.model.PendingChallengePhase
import com.example.model.PendingFriendChallengeState
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
import com.example.util.SessionNotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import java.util.UUID

class OnlineViewModel(
    val repository: OnlineRaceRepository,
    val friendsRepository: FriendsRepository,
    val currentUserId: String,
    private val appContext: Context? = null
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
    val syncPingMs: StateFlow<Int?> = sessionManager.syncPingMs
    val usingRelayFallback: StateFlow<Boolean> = sessionManager.usingRelayFallback
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

    private val _incomingLeaderboardNotifications = MutableStateFlow<List<LeaderboardNotificationDoc>>(emptyList())
    val incomingLeaderboardNotifications: StateFlow<List<LeaderboardNotificationDoc>> =
        _incomingLeaderboardNotifications.asStateFlow()

    private val friendOnlineTracker = FriendPresenceTransitionTracker()
    private val _friendOnlineAlerts = MutableStateFlow<List<FriendOnlineAlert>>(emptyList())
    val friendOnlineAlerts: StateFlow<List<FriendOnlineAlert>> = _friendOnlineAlerts.asStateFlow()

    private val _pendingFriendChallenge = MutableStateFlow<PendingFriendChallengeState?>(null)
    val pendingFriendChallenge: StateFlow<PendingFriendChallengeState?> = _pendingFriendChallenge.asStateFlow()

    private val _friendCircleEntries = MutableStateFlow<List<LeaderboardEntryDoc>>(emptyList())
    val friendCircleEntries: StateFlow<List<LeaderboardEntryDoc>> = _friendCircleEntries.asStateFlow()

    private val _socialFeedback = MutableStateFlow<String?>(null)
    val socialFeedback: StateFlow<String?> = _socialFeedback.asStateFlow()

    private val _appInForeground = MutableStateFlow(true)
    private var latestKnownProfile: PlayerProfile = PlayerProfile(playerId = currentUserId)

    private var roomsSubscriptionJob: Job? = null
    private var leaderboardSubscriptionJob: Job? = null
    private var friendshipsJob: Job? = null
    private var invitesJob: Job? = null
    private var leaderboardNotifsJob: Job? = null
    private var friendCircleEntriesJob: Job? = null
    private var presenceHeartbeatJob: Job? = null
    private var pendingChallengeJob: Job? = null

    init {
        startObservingOpenRooms()
        startObservingFriendsAndPresence()
        startObservingFriendCircleEntries()
        startObservingSessionInvites()
        startObservingLeaderboardNotifications()
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
                rethrowIfScopeCancellation(e)
                formatUserFacingOnlineErrorOrNull(e, "Could not load online rooms.")?.let {
                    _cloudStatusMessage.value = it
                }
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
                    _friendOnlineAlerts.value = friendOnlineTracker.onFriendsUpdated(mapped)
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                formatUserFacingOnlineErrorOrNull(e, "Could not sync friends list.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startObservingFriendCircleEntries() {
        friendCircleEntriesJob?.cancel()
        friendCircleEntriesJob = viewModelScope.launch {
            try {
                _friendsList
                    .map { friends ->
                        (listOf(currentUserId) + friends.map { it.friendUid })
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { trackedUids ->
                        repository.observeLeaderboardEntriesForUsers(trackedUids)
                    }
                    .collect { entries ->
                        _friendCircleEntries.value = entries
                    }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
            }
        }
    }

    private fun startObservingSessionInvites() {
        invitesJob?.cancel()
        invitesJob = viewModelScope.launch {
            try {
                friendsRepository.observeIncomingSessionInvites(currentUserId).collect { invites ->
                    _incomingSessionInvites.value = invites
                    val ctx = appContext?.applicationContext
                    if (ctx != null) {
                        val currentRoomCode = activeRoom.value?.roomCode
                        invites.forEach { inv ->
                            val alreadyInThatRoom = !currentRoomCode.isNullOrBlank() && currentRoomCode == inv.roomCode
                            if (!alreadyInThatRoom) {
                                SessionNotificationHelper.showSessionInviteNotification(ctx, inv)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                formatUserFacingOnlineErrorOrNull(e, "Could not check session invites.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    private fun startObservingLeaderboardNotifications() {
        leaderboardNotifsJob?.cancel()
        leaderboardNotifsJob = viewModelScope.launch {
            try {
                friendsRepository.observeIncomingLeaderboardNotifications(currentUserId).collect { list ->
                    val boardEntries = _friendCircleEntries.value
                    val recordOnlyList = list.filter { notif ->
                        OnlineRaceRepository.isNotificationStillBoardRecord(notif, boardEntries)
                    }
                    _incomingLeaderboardNotifications.value = recordOnlyList
                    val ctx = appContext?.applicationContext
                    if (ctx != null) {
                        recordOnlyList.forEach { notif ->
                            SessionNotificationHelper.showFriendLeaderboardRecordNotification(ctx, notif)
                        }
                    }
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                formatUserFacingOnlineErrorOrNull(e, "Could not load leaderboard alerts.")?.let {
                    _socialFeedback.value = it
                }
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
                formatUserFacingOnlineErrorOrNull(err, "Driver search failed.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    fun addFriendByCode(profile: PlayerProfile, code: String) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequestByCode(profile, code)
            res.onSuccess { targetName ->
                _socialFeedback.value = "Friend request sent to $targetName!"
            }.onFailure { err ->
                formatUserFacingOnlineErrorOrNull(err, "Could not send friend request.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    fun addFriendFromDirectory(profile: PlayerProfile, target: DriverProfileDoc) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequest(profile, target)
            res.onSuccess {
                _socialFeedback.value = "Friend request sent to ${target.driverName}!"
            }.onFailure { err ->
                formatUserFacingOnlineErrorOrNull(err, "Could not send friend request.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    fun addFriendByUid(profile: PlayerProfile, targetUid: String, fallbackName: String) {
        viewModelScope.launch {
            val res = friendsRepository.sendFriendRequestByUid(profile, targetUid, fallbackName)
            res.onSuccess { targetName ->
                _socialFeedback.value = "Friend request sent to $targetName!"
            }.onFailure { err ->
                formatUserFacingOnlineErrorOrNull(err, "Could not send friend request.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    fun acceptFriendRequest(friendshipId: String, profile: PlayerProfile) {
        viewModelScope.launch {
            val res = friendsRepository.acceptFriendRequest(friendshipId, profile.name)
            res.onSuccess {
                _socialFeedback.value = "Friend added to your Crew!"
            }.onFailure { err ->
                formatUserFacingOnlineErrorOrNull(err, "Unable to accept friend request.")?.let {
                    _socialFeedback.value = it
                }
            }
        }
    }

    fun removeOrDeclineFriend(friendshipId: String, feedbackText: String = "Updated Friends List.") {
        viewModelScope.launch {
            val res = friendsRepository.removeOrDeclineFriendship(friendshipId)
            res.onSuccess {
                _socialFeedback.value = feedbackText
            }.onFailure { err ->
                formatUserFacingOnlineErrorOrNull(err, "Unable to update friendship.")?.let {
                    _socialFeedback.value = it
                }
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
                    formatUserFacingOnlineErrorOrNull(err, "Failed to notify ${friend.displayName}.")?.let {
                        _socialFeedback.value = it
                    }
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
                            formatUserFacingOnlineErrorOrNull(err, "Lobby opened, but invite failed.")?.let {
                                _socialFeedback.value = it
                            }
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

    fun dismissFriendOnlineAlert(friendUid: String) {
        _friendOnlineAlerts.value = friendOnlineTracker.dismiss(friendUid)
    }

    /**
     * Challenges 1..3 online friends from the in-app notification without interrupting gameplay.
     * Prepares a standby private lobby in the background, notifies the selected friend(s),
     * displays a 60-second waiting countdown, and transitions both challenger and accepting
     * friend(s) into the multiplayer lobby as soon as a friend accepts.
     */
    fun challengeFriendsFromNotification(
        targets: List<FriendUiModel>,
        profile: PlayerProfile,
        defaultRules: RaceRulesSettings,
        onBeforeEnterCommonLobby: () -> Unit,
        onAlreadyInLobbyNavigate: () -> Unit = {}
    ) {
        val cleanTargets = targets
            .filter { it.friendUid.isNotBlank() && it.friendUid != currentUserId }
            .distinctBy { it.friendUid }
        if (cleanTargets.isEmpty()) return

        var updatedAlerts = _friendOnlineAlerts.value
        cleanTargets.forEach { f ->
            updatedAlerts = friendOnlineTracker.dismiss(f.friendUid)
        }
        _friendOnlineAlerts.value = updatedAlerts

        val previousPending = _pendingFriendChallenge.value
        pendingChallengeJob?.cancel()
        pendingChallengeJob = null

        val onlineExistingRoom = activeRoom.value?.takeIf { role.value.isOnline }
        val inOnlineRoom = onlineExistingRoom != null
        val maxTargets = if (onlineExistingRoom != null) {
            (onlineExistingRoom.maxPlayers.toInt().coerceIn(2, 4) - 1).coerceAtLeast(1)
        } else {
            3
        }
        val cappedTargets = cleanTargets.take(maxTargets)
        val challengeId = UUID.randomUUID().toString().replace("-", "").take(12)

        _pendingFriendChallenge.value = PendingFriendChallengeState(
            challengeId = challengeId,
            roomId = onlineExistingRoom?.id.orEmpty(),
            roomCode = onlineExistingRoom?.roomCode.orEmpty(),
            ownsStandbyRoom = !inOnlineRoom,
            targets = cappedTargets.map { f ->
                ChallengedFriendStatus(
                    friendUid = f.friendUid,
                    displayName = f.displayName,
                    carType = f.carType,
                    primaryColorArgb = f.primaryColorArgb
                )
            },
            totalSeconds = 60,
            remainingSeconds = 60,
            phase = PendingChallengePhase.SENDING
        )

        pendingChallengeJob = viewModelScope.launch {
            if (previousPending != null) {
                withContext(NonCancellable) {
                    cleanupChallengeResources(previousPending)
                }
            }

            val roomResult: Result<Pair<OnlineRoomDoc, Boolean>> = if (onlineExistingRoom != null) {
                Result.success(onlineExistingRoom to false)
            } else {
                sessionManager.createStandbyChallengeRoom(
                    profile = profile,
                    roomName = "${profile.name.take(12)}'s Challenge",
                    maxPlayers = 4,
                    initialRules = defaultRules
                ).map { it to true }
            }

            val (room, ownsStandby) = roomResult.getOrElse { err ->
                rethrowIfScopeCancellation(err)
                if (_pendingFriendChallenge.value?.challengeId == challengeId) {
                    _pendingFriendChallenge.value = null
                }
                formatUserFacingOnlineErrorOrNull(err, "Could not prepare challenge lobby.")?.let {
                    _socialFeedback.value = it
                }
                return@launch
            }

            val sentStatuses = mutableListOf<ChallengedFriendStatus>()
            for (friend in cappedTargets) {
                val inviteRes = friendsRepository.sendSessionInviteReturningId(
                    senderProfile = profile,
                    recipientUid = friend.friendUid,
                    recipientName = friend.displayName,
                    room = room
                )
                inviteRes.onSuccess { invId ->
                    sentStatuses.add(
                        ChallengedFriendStatus(
                            friendUid = friend.friendUid,
                            displayName = friend.displayName,
                            carType = friend.carType,
                            primaryColorArgb = friend.primaryColorArgb,
                            inviteId = invId,
                            response = ChallengeInviteResponse.PENDING
                        )
                    )
                }
            }

            if (sentStatuses.isEmpty()) {
                if (ownsStandby) {
                    withContext(NonCancellable) {
                        sessionManager.discardStandbyChallengeRoom(room.id)
                    }
                }
                if (_pendingFriendChallenge.value?.challengeId == challengeId) {
                    _pendingFriendChallenge.value = null
                }
                _socialFeedback.value = "Could not notify friend${if (cappedTargets.size > 1) "s" else ""}. Try again."
                return@launch
            }

            _pendingFriendChallenge.value = PendingFriendChallengeState(
                challengeId = challengeId,
                roomId = room.id,
                roomCode = room.roomCode,
                ownsStandbyRoom = ownsStandby,
                targets = sentStatuses,
                totalSeconds = 60,
                remainingSeconds = 60,
                phase = PendingChallengePhase.WAITING
            )

            var settled = false
            var allDeclined = false
            val inviteIds = sentStatuses.map { it.inviteId }.toSet()

            val observerJob = launch {
                try {
                    friendsRepository.observeOutgoingSessionInvites(currentUserId, inviteIds).collect { inviteDocs ->
                        val current = _pendingFriendChallenge.value
                            ?.takeIf { it.challengeId == challengeId }
                            ?: return@collect
                        val eval = ChallengeProgressEvaluator.applyInviteUpdates(current, inviteDocs)
                        _pendingFriendChallenge.value = eval.updatedState

                        val accepted = eval.acceptedTarget
                        if (accepted != null && !settled) {
                            settled = true
                            _pendingFriendChallenge.value = null
                            _socialFeedback.value = "${accepted.displayName} accepted! Opening lobby..."
                            if (ownsStandby || sessionManager.activeRoom.value?.id != room.id) {
                                onBeforeEnterCommonLobby()
                                sessionManager.attachStandbyRoomAsHost(
                                    createdRoom = room,
                                    profile = profile,
                                    initialRules = defaultRules
                                )
                            } else {
                                onAlreadyInLobbyNavigate()
                            }
                            return@collect
                        }

                        if (eval.allDeclined && !settled) {
                            settled = true
                            allDeclined = true
                            _pendingFriendChallenge.value = eval.updatedState.copy(
                                phase = PendingChallengePhase.ALL_DECLINED
                            )
                            if (ownsStandby) {
                                withContext(NonCancellable) {
                                    sessionManager.discardStandbyChallengeRoom(room.id)
                                }
                            }
                            return@collect
                        }
                    }
                } catch (e: Exception) {
                    rethrowIfScopeCancellation(e)
                }
            }

            // 60-second countdown ticking in 200ms slices so acceptance exits promptly
            var elapsedMs = 0L
            val totalMs = 60_000L
            while (isActive && !settled && elapsedMs < totalMs) {
                delay(200L)
                if (settled) break
                elapsedMs += 200L
                val newSecondsLeft = ((totalMs - elapsedMs + 999L) / 1_000L).toInt().coerceIn(0, 60)
                val current = _pendingFriendChallenge.value
                if (current != null &&
                    current.challengeId == challengeId &&
                    current.phase == PendingChallengePhase.WAITING &&
                    current.remainingSeconds != newSecondsLeft
                ) {
                    _pendingFriendChallenge.value = current.copy(remainingSeconds = newSecondsLeft)
                }
            }

            observerJob.cancel()

            if (!settled) {
                val timedOutState = _pendingFriendChallenge.value
                if (timedOutState != null && timedOutState.challengeId == challengeId) {
                    _pendingFriendChallenge.value = timedOutState.copy(
                        remainingSeconds = 0,
                        phase = PendingChallengePhase.TIMED_OUT
                    )
                    withContext(NonCancellable) {
                        cleanupChallengeResources(timedOutState)
                    }
                    delay(3_000L)
                    if (_pendingFriendChallenge.value?.challengeId == challengeId) {
                        _pendingFriendChallenge.value = null
                    }
                }
            } else if (allDeclined) {
                delay(3_000L)
                if (_pendingFriendChallenge.value?.challengeId == challengeId) {
                    _pendingFriendChallenge.value = null
                }
            }
        }
    }

    fun cancelPendingFriendChallenge() {
        val current = _pendingFriendChallenge.value
        _pendingFriendChallenge.value = null
        pendingChallengeJob?.cancel()
        pendingChallengeJob = null
        if (current != null) {
            viewModelScope.launch(NonCancellable) {
                cleanupChallengeResources(current)
            }
        }
    }

    private suspend fun cleanupChallengeResources(state: PendingFriendChallengeState) {
        state.targets
            .filter { it.response == ChallengeInviteResponse.PENDING && it.inviteId.isNotBlank() }
            .forEach { target ->
                friendsRepository.expireSessionInvite(target.inviteId)
            }
        if (state.ownsStandbyRoom && state.roomId.isNotBlank()) {
            sessionManager.discardStandbyChallengeRoom(state.roomId)
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
                rethrowIfScopeCancellation(e)
                _leaderboardLoading.value = false
                formatUserFacingOnlineErrorOrNull(e, "Could not load cloud leaderboard.")?.let {
                    _cloudStatusMessage.value = it
                }
            }
        }
    }

    fun dismissLeaderboardNotification(notificationId: String) {
        viewModelScope.launch {
            friendsRepository.dismissLeaderboardNotification(notificationId)
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
            res.onSuccess { submission ->
                if (submission.isNewBoardRecord) {
                    val trackName = DailyChallengeCatalog.resolveTrackDisplayName(summary.trackKey)
                    val isDaily = DailyChallengeCatalog.isAnyDailyTrackId(summary.trackKey)
                    val notifiedCount = friendsRepository.sendLeaderboardNotificationsToAllFriends(
                        senderProfile = profile,
                        gameMode = summary.gameMode,
                        trackKey = summary.trackKey,
                        trackName = trackName,
                        boardKey = summary.boardKey,
                        totalLaps = summary.totalLaps,
                        finishTimeMs = summary.finishTimeMs,
                        bestScore = summary.bestScore,
                        dailyChallenge = isDaily
                    ).getOrDefault(0)
                    _cloudStatusMessage.value = if (notifiedCount > 0) {
                        "New #1 Record & Ghost uploaded — $notifiedCount friend${if (notifiedCount == 1) "" else "s"} notified!"
                    } else {
                        "New #1 Leaderboard Record & Ghost uploaded!"
                    }
                } else if (submission.improvedPersonalBest) {
                    _cloudStatusMessage.value = "Personal Best & Ghost uploaded!"
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
                formatUserFacingOnlineErrorOrNull(err, "Could not load ghost run.")?.let {
                    _cloudStatusMessage.value = it
                }
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
        pendingChallengeJob?.cancel()
        pendingChallengeJob = null
        val activeChallenge = _pendingFriendChallenge.value
        _pendingFriendChallenge.value = null
        if (activeChallenge != null) {
            cleanupChallengeResources(activeChallenge)
        }
        friendshipsJob?.cancel()
        friendCircleEntriesJob?.cancel()
        invitesJob?.cancel()
        leaderboardNotifsJob?.cancel()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        friendsRepository.setOfflinePresence(latestKnownProfile)
        friendOnlineTracker.reset()
        _friendOnlineAlerts.value = emptyList()
        sessionManager.leaveCurrentRoomInternal()
    }

    suspend fun deleteAllCloudDataBeforeAccountRemoval() {
        presenceHeartbeatJob?.cancel()
        pendingChallengeJob?.cancel()
        pendingChallengeJob = null
        val activeChallenge = _pendingFriendChallenge.value
        _pendingFriendChallenge.value = null
        if (activeChallenge != null) {
            cleanupChallengeResources(activeChallenge)
        }
        friendshipsJob?.cancel()
        friendCircleEntriesJob?.cancel()
        invitesJob?.cancel()
        leaderboardNotifsJob?.cancel()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        friendOnlineTracker.reset()
        _friendOnlineAlerts.value = emptyList()
        sessionManager.leaveCurrentRoomInternal()
        repository.deleteAllUserRaceAndLeaderboardData()
        friendsRepository.deleteAllUserSocialData()
    }

    override fun onCleared() {
        super.onCleared()
        presenceHeartbeatJob?.cancel()
        pendingChallengeJob?.cancel()
        _pendingFriendChallenge.value = null
        friendshipsJob?.cancel()
        friendCircleEntriesJob?.cancel()
        invitesJob?.cancel()
        leaderboardNotifsJob?.cancel()
        roomsSubscriptionJob?.cancel()
        leaderboardSubscriptionJob?.cancel()
        sessionManager.disconnectRelay()
        sessionManager.leaveRoom()
    }
}
