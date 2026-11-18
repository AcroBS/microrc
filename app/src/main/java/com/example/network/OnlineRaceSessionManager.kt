package com.example.network

import com.example.data.OnlineRaceRepository
import com.example.data.TrackCatalog
import com.example.data.formatUserFacingOnlineError
import com.example.data.rethrowIfScopeCancellation
import com.example.engine.RacePhysicsEngine
import com.example.model.ArenaShape
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.CustomTrackData
import com.example.model.GameMode
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.OnlineRaceStartPayload
import com.example.model.OnlineRoomChatDoc
import com.example.model.OnlineRoomDoc
import com.example.model.OnlineRoomInputDoc
import com.example.model.OnlineRoomPlayerDoc
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceRulesSettings
import com.example.model.RaceSnapshot
import com.example.model.RcCarType
import com.example.model.RcColorPresets
import com.example.model.RematchCountdownState
import com.example.model.RematchVoteOption
import com.example.model.StuntVariant
import com.example.model.TrackDefinition
import com.example.model.TrackId
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

class OnlineRaceSessionManager(
    private val repository: OnlineRaceRepository,
    val currentUserId: String,
    private val scope: CoroutineScope
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val lobbyAdapter = moshi.adapter(LobbyStatePayload::class.java)
    private val raceStartAdapter = moshi.adapter(OnlineRaceStartPayload::class.java)
    private val rematchAdapter = moshi.adapter(RematchCountdownState::class.java)

    private val _role = MutableStateFlow<SessionRole>(SessionRole.None)
    val role: StateFlow<SessionRole> = _role.asStateFlow()

    private val _activeRoom = MutableStateFlow<OnlineRoomDoc?>(null)
    val activeRoom: StateFlow<OnlineRoomDoc?> = _activeRoom.asStateFlow()

    private val _lobbyState = MutableStateFlow<LobbyStatePayload?>(null)
    val lobbyState: StateFlow<LobbyStatePayload?> = _lobbyState.asStateFlow()

    private val _raceSnapshot = MutableStateFlow<RaceSnapshot?>(null)
    val raceSnapshot: StateFlow<RaceSnapshot?> = _raceSnapshot.asStateFlow()

    private val _lastResults = MutableStateFlow<RaceSnapshot?>(null)
    val lastResults: StateFlow<RaceSnapshot?> = _lastResults.asStateFlow()

    private val _activeTrack = MutableStateFlow<TrackDefinition?>(null)
    val activeTrack: StateFlow<TrackDefinition?> = _activeTrack.asStateFlow()

    private val _rematchState = MutableStateFlow<RematchCountdownState?>(null)
    val rematchState: StateFlow<RematchCountdownState?> = _rematchState.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _navEvents = MutableSharedFlow<RaceNavEvent>(extraBufferCapacity = 8)
    val navEvents: SharedFlow<RaceNavEvent> = _navEvents.asSharedFlow()

    // Session runtime jobs & host state
    private var roomObserverJob: Job? = null
    private var playersObserverJob: Job? = null
    private var inputsObserverJob: Job? = null
    private var chatObserverJob: Job? = null
    private var physicsJob: Job? = null
    private var rematchJob: Job? = null
    private var clientInputPushJob: Job? = null

    private var activeRoomId: String? = null
    private var currentRules: RaceRulesSettings = RaceRulesSettings()
    private var currentTrackId: TrackId = TrackId.LIVING_ROOM_GP
    private var currentArenaShape: ArenaShape = ArenaShape.SQUARE
    private var currentStuntVariant: StuntVariant = StuntVariant.LONG_JUMP
    private var currentCustomTrack: CustomTrackData? = null
    private var currentTotalLaps: Int = 3
    private var maxRoomPlayers: Int = 4
    private var roomDisplayName: String = "Online Paddock"
    private var roomCodeDisplay: String = ""
    private var hybridStageNumber: Int = 1
    private var activeConcreteMode: GameMode = GameMode.RACE
    private var nextPlannedHybridMode: GameMode? = null
    private var inRace: Boolean = false
    private var physicsEngine: RacePhysicsEngine? = null

    private val botPlayers = mutableListOf<LobbyPlayer>()
    private val latestHumanPlayers = ConcurrentHashMap<String, OnlineRoomPlayerDoc>()
    private val sessionPointsByPlayer = ConcurrentHashMap<String, Int>()
    private val winsByPlayer = ConcurrentHashMap<String, Int>()
    private val latestChatHistory = mutableListOf<ChatMessage>()
    private val appliedPowerSeqByUser = ConcurrentHashMap<String, Long>()
    private val appliedMineSeqByUser = ConcurrentHashMap<String, Long>()

    private val hostMatchSeq = AtomicLong(0L)
    private val hostTelemetrySeq = AtomicLong(0L)
    private val clientAppliedMatchSeq = AtomicLong(0L)
    private val clientAppliedTelemetrySeq = AtomicLong(-1L)
    private val clientFinishEmitted = AtomicBoolean(false)

    private var localInput = ControlInput()
    private var pendingLocalPowerUp = false
    private var pendingLocalMineDrop = false
    private val localInputSeq = AtomicLong(0L)
    private val localPowerUpSeq = AtomicLong(0L)
    private val localMineDropSeq = AtomicLong(0L)
    private var lastPushedThrottle = 0f
    private var lastPushedSteer = 0f
    private var lastInputPushMs = 0L

    private var rematchOptions: List<RematchVoteOption> = emptyList()
    private val rematchVotes = ConcurrentHashMap<String, String>()
    @Volatile private var rematchSecondsLeft: Int = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS

    fun clearError() {
        _errorBanner.value = null
    }

    private fun configureInitialMode(rules: RaceRulesSettings, initialCustomTrack: CustomTrackData?) {
        hybridStageNumber = 1
        currentRules = rules
        currentTotalLaps = rules.totalLaps.coerceIn(1, 9)
        currentCustomTrack = initialCustomTrack

        when (rules.gameModeEnum) {
            GameMode.HYBRID -> {
                activeConcreteMode = WifiP2pRaceManager.resolveHybridStageMode(rules, 0, null)
                nextPlannedHybridMode = WifiP2pRaceManager.resolveHybridStageMode(rules, 1, activeConcreteMode)
                when (activeConcreteMode) {
                    GameMode.ARENA -> {
                        currentCustomTrack = null
                        currentArenaShape = ArenaShape.entries.random()
                        currentTrackId = currentArenaShape.themeTrackId
                        _activeTrack.value = TrackCatalog.getArenaTrack(currentArenaShape)
                    }
                    GameMode.STUNTS -> {
                        currentCustomTrack = null
                        currentStuntVariant = StuntVariant.entries.random()
                        currentTrackId = currentStuntVariant.themeTrackId
                        _activeTrack.value = TrackCatalog.getStuntTrack(currentStuntVariant)
                    }
                    else -> {
                        currentCustomTrack = null
                        currentTrackId = TrackId.entries.random()
                        _activeTrack.value = TrackCatalog.getTrack(currentTrackId)
                    }
                }
            }
            GameMode.STUNTS -> {
                activeConcreteMode = GameMode.STUNTS
                nextPlannedHybridMode = null
                currentCustomTrack = null
                currentStuntVariant = StuntVariant.entries.random()
                currentTrackId = currentStuntVariant.themeTrackId
                _activeTrack.value = TrackCatalog.getStuntTrack(currentStuntVariant)
            }
            GameMode.ARENA -> {
                activeConcreteMode = GameMode.ARENA
                nextPlannedHybridMode = null
                currentCustomTrack = null
                currentTrackId = currentArenaShape.themeTrackId
                _activeTrack.value = TrackCatalog.getArenaTrack(currentArenaShape)
            }
            GameMode.RACE -> {
                activeConcreteMode = GameMode.RACE
                nextPlannedHybridMode = null
                if (initialCustomTrack != null) {
                    currentTrackId = runCatching { TrackId.valueOf(initialCustomTrack.themeId) }
                        .getOrDefault(TrackId.NEON_ROOFTOP)
                    _activeTrack.value = TrackCatalog.buildCustomTrack(initialCustomTrack)
                } else {
                    _activeTrack.value = TrackCatalog.getTrack(currentTrackId)
                }
            }
        }
    }

    fun hostOnlineRoom(
        profile: PlayerProfile,
        roomName: String,
        maxPlayers: Int,
        discoverable: Boolean,
        rawPassword: String,
        initialRules: RaceRulesSettings,
        onCreated: ((OnlineRoomDoc) -> Unit)? = null
    ) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorBanner.value = null

        scope.launch {
            try {
                leaveCurrentRoomInternal()
                configureInitialMode(initialRules, null)
                maxRoomPlayers = maxPlayers.coerceIn(2, 4)
                roomDisplayName = roomName.trim().take(40).ifEmpty { "${profile.name.take(14)}'s Paddock" }

                val cleanHostName = profile.name.trim().take(24).ifEmpty { "Host" }
                val initialHostPlayer = LobbyPlayer(
                    id = currentUserId,
                    name = cleanHostName,
                    isHost = true,
                    isBot = false,
                    isReady = true,
                    carType = profile.carType.name,
                    primaryColorArgb = profile.primaryColorArgb,
                    accentColorArgb = profile.accentColorArgb
                )
                val seedPayload = buildHostLobbyPayload(
                    roomCode = "ONLINE",
                    playersOverride = listOf(initialHostPlayer)
                )
                val lobbyJson = lobbyAdapter.toJson(seedPayload)

                val res = repository.createOnlineRoom(
                    profile = profile,
                    roomName = roomDisplayName,
                    maxPlayers = maxRoomPlayers,
                    discoverable = discoverable,
                    rawPassword = rawPassword,
                    gameMode = initialRules.gameModeEnum,
                    trackId = seedPayload.trackId,
                    initialLobbyJson = lobbyJson
                )
                _isBusy.value = false

                res.onSuccess { createdRoom ->
                    activeRoomId = createdRoom.id
                    roomCodeDisplay = createdRoom.roomCode
                    _activeRoom.value = createdRoom
                    _role.value = SessionRole.OnlineHost
                    inRace = false
                    botPlayers.clear()
                    sessionPointsByPlayer.clear()
                    winsByPlayer.clear()
                    latestHumanPlayers.clear()
                    latestHumanPlayers[currentUserId] = OnlineRoomPlayerDoc(
                        id = currentUserId,
                        roomId = createdRoom.id,
                        userId = currentUserId,
                        name = cleanHostName,
                        carType = profile.carType.name,
                        primaryColorArgb = profile.primaryColorArgb,
                        accentColorArgb = profile.accentColorArgb,
                        ready = true,
                        host = true,
                        rematchVote = "",
                        joinToken = createdRoom.passwordHash
                    )
                    synchronized(latestChatHistory) {
                        latestChatHistory.clear()
                    }
                    _lobbyState.value = buildHostLobbyPayload(
                        roomCode = createdRoom.roomCode,
                        playersOverride = listOf(initialHostPlayer)
                    )
                    startHostObservers(createdRoom.id)
                    onCreated?.invoke(createdRoom)
                    _navEvents.tryEmit(RaceNavEvent.EnterLobby)

                    val pwNote = if (createdRoom.passwordProtected) "Password-protected" else "Open"
                    scope.launch {
                        repository.sendRoomChat(
                            roomId = createdRoom.id,
                            senderName = "Race Control",
                            text = "$pwNote room ${createdRoom.roomCode} live! Share code ${createdRoom.roomCode} with up to ${createdRoom.maxPlayers} players.",
                            systemMessage = true
                        )
                    }
                }.onFailure { err ->
                    _errorBanner.value = formatUserFacingOnlineError(err, "Failed to create online lobby.")
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                _errorBanner.value = formatUserFacingOnlineError(e, "Failed to create online lobby.")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun joinOnlineRoom(
        roomId: String,
        profile: PlayerProfile,
        rawPassword: String,
        preAuthorizedJoinToken: String = ""
    ) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorBanner.value = null

        scope.launch {
            try {
                performJoinOnlineRoom(
                    roomId = roomId,
                    profile = profile,
                    rawPassword = rawPassword,
                    preAuthorizedJoinToken = preAuthorizedJoinToken
                )
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                _errorBanner.value = formatUserFacingOnlineError(e, "Unable to join online lobby.")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun joinOnlineRoomByCode(
        rawCode: String,
        profile: PlayerProfile,
        rawPassword: String,
        preAuthorizedJoinToken: String = "",
        onPasswordRequired: (OnlineRoomDoc) -> Unit
    ) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorBanner.value = null

        scope.launch {
            try {
                val foundRes = repository.findRoomByCode(rawCode)
                val room = foundRes.getOrElse { err ->
                    _errorBanner.value = formatUserFacingOnlineError(err, "Room code not found.")
                    return@launch
                }
                val cleanToken = preAuthorizedJoinToken.trim()
                val tokenValid = cleanToken.isNotEmpty() && cleanToken == room.passwordHash
                if (room.passwordProtected &&
                    rawPassword.trim().isEmpty() &&
                    !tokenValid &&
                    room.hostUid != currentUserId
                ) {
                    _isBusy.value = false
                    onPasswordRequired(room)
                } else {
                    performJoinOnlineRoom(
                        roomId = room.id,
                        profile = profile,
                        rawPassword = rawPassword,
                        preAuthorizedJoinToken = cleanToken
                    )
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                _errorBanner.value = formatUserFacingOnlineError(e, "Room code not found.")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private suspend fun performJoinOnlineRoom(
        roomId: String,
        profile: PlayerProfile,
        rawPassword: String,
        preAuthorizedJoinToken: String
    ) {
        leaveCurrentRoomInternal()
        val res = repository.joinOnlineRoom(
            roomId = roomId,
            profile = profile,
            rawPassword = rawPassword,
            preAuthorizedJoinToken = preAuthorizedJoinToken
        )
        _isBusy.value = false

        res.onSuccess { joinedRoom ->
            activeRoomId = joinedRoom.id
            roomDisplayName = joinedRoom.roomName
            roomCodeDisplay = joinedRoom.roomCode
            _activeRoom.value = joinedRoom
            val amHost = joinedRoom.hostUid == currentUserId
            _role.value = if (amHost) SessionRole.OnlineHost else SessionRole.OnlineClient
            clientAppliedMatchSeq.set(0L)
            clientAppliedTelemetrySeq.set(-1L)
            clientFinishEmitted.set(false)

            if (amHost) {
                configureInitialMode(currentRules, null)
                _lobbyState.value = buildHostLobbyPayload(roomCode = joinedRoom.roomCode)
                startHostObservers(joinedRoom.id)
            } else {
                applyClientRoomSnapshot(joinedRoom)
                startClientObservers(joinedRoom.id)
            }
            _navEvents.tryEmit(RaceNavEvent.EnterLobby)

            scope.launch {
                repository.sendRoomChat(
                    roomId = joinedRoom.id,
                    senderName = "Race Control",
                    text = "${profile.name.take(18)} joined the online paddock!",
                    systemMessage = true
                )
            }
        }.onFailure { err ->
            _errorBanner.value = formatUserFacingOnlineError(err, "Unable to join online lobby.")
        }
    }

    private fun startHostObservers(roomId: String) {
        roomObserverJob?.cancel()
        roomObserverJob = scope.launch {
            try {
                repository.observeRoom(roomId).collect { doc ->
                    if (doc != null) {
                        _activeRoom.value = doc
                    }
                }
            } catch (_: Exception) {
            }
        }

        playersObserverJob?.cancel()
        playersObserverJob = scope.launch {
            try {
                repository.observeRoomPlayers(roomId).collect { playerDocs ->
                    latestHumanPlayers.clear()
                    playerDocs.forEach { p ->
                        latestHumanPlayers[p.userId] = p
                        if (p.rematchVote.isNotBlank() && _rematchState.value != null) {
                            rematchVotes[p.userId] = p.rematchVote
                        }
                    }
                    synchronized(botPlayers) {
                        while (latestHumanPlayers.size + botPlayers.size > maxRoomPlayers && botPlayers.isNotEmpty()) {
                            botPlayers.removeAt(botPlayers.lastIndex)
                        }
                    }
                    syncHostLobbyToCloud()
                    if (_rematchState.value != null) {
                        publishHostRematchState()
                    }
                }
            } catch (_: Exception) {
            }
        }

        inputsObserverJob?.cancel()
        inputsObserverJob = scope.launch {
            try {
                repository.observeRoomInputs(roomId).collect { inputDocs ->
                    val engine = physicsEngine ?: return@collect
                    for (doc in inputDocs) {
                        if (doc.userId == currentUserId) continue
                        val prevPower = appliedPowerSeqByUser[doc.userId] ?: doc.powerUpSeq
                        val prevMine = appliedMineSeqByUser[doc.userId] ?: doc.mineDropSeq
                        val triggerPower = doc.powerUpSeq > prevPower
                        val triggerMine = doc.mineDropSeq > prevMine
                        appliedPowerSeqByUser[doc.userId] = doc.powerUpSeq
                        appliedMineSeqByUser[doc.userId] = doc.mineDropSeq
                        engine.submitInput(
                            doc.userId,
                            ControlInput(
                                throttle = doc.throttle.toFloat(),
                                steer = doc.steer.toFloat(),
                                usePowerUp = triggerPower,
                                dropMine = triggerMine
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        chatObserverJob?.cancel()
        chatObserverJob = scope.launch {
            try {
                repository.observeRoomChat(roomId).collect { docs ->
                    updateChatCacheFromDocs(docs)
                    val cur = _lobbyState.value
                    if (cur != null) {
                        _lobbyState.value = cur.copy(
                            chatHistory = synchronized(latestChatHistory) { latestChatHistory.toList() }
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startClientObservers(roomId: String) {
        chatObserverJob?.cancel()
        chatObserverJob = scope.launch {
            try {
                repository.observeRoomChat(roomId).collect { docs ->
                    updateChatCacheFromDocs(docs)
                    val cur = _lobbyState.value
                    if (cur != null) {
                        _lobbyState.value = cur.copy(
                            chatHistory = synchronized(latestChatHistory) { latestChatHistory.toList() }
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        roomObserverJob?.cancel()
        roomObserverJob = scope.launch {
            try {
                repository.observeRoom(roomId).collect { doc ->
                    if (doc == null) {
                        _navEvents.tryEmit(RaceNavEvent.Disconnected("Host closed the online room."))
                        return@collect
                    }
                    _activeRoom.value = doc
                    applyClientRoomSnapshot(doc)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun applyClientRoomSnapshot(doc: OnlineRoomDoc) {
        val parsedLobby = runCatching { lobbyAdapter.fromJson(doc.lobbyJson) }.getOrNull()
        if (parsedLobby != null) {
            val mergedChat = synchronized(latestChatHistory) {
                if (latestChatHistory.isNotEmpty()) latestChatHistory.toList() else parsedLobby.chatHistory
            }
            _lobbyState.value = parsedLobby.copy(chatHistory = mergedChat)
            currentRules = parsedLobby.rules
            if (doc.status == "OPEN") {
                _activeTrack.value = when (parsedLobby.rules.gameModeEnum) {
                    GameMode.ARENA -> TrackCatalog.getArenaTrack(parsedLobby.arenaShape)
                    GameMode.STUNTS -> TrackCatalog.getStuntTrack(parsedLobby.stuntVariant)
                    else -> parsedLobby.activeCustomTrack?.let {
                        TrackCatalog.buildCustomTrack(it)
                    } ?: TrackCatalog.getTrack(parsedLobby.trackId)
                }
            }
        }

        if (doc.rematchJson.isNotBlank()) {
            _rematchState.value = runCatching { rematchAdapter.fromJson(doc.rematchJson) }.getOrNull()
        } else {
            _rematchState.value = null
        }

        if (doc.status == "RACING" && doc.raceStartJson.isNotBlank()) {
            val startPayload = runCatching { raceStartAdapter.fromJson(doc.raceStartJson) }.getOrNull()
            if (startPayload != null && startPayload.matchSeq > clientAppliedMatchSeq.get()) {
                clientAppliedMatchSeq.set(startPayload.matchSeq)
                clientAppliedTelemetrySeq.set(0L)
                clientFinishEmitted.set(false)
                _rematchState.value = null
                currentRules = startPayload.rules

                val snapMode = startPayload.initialSnapshot?.gameMode ?: startPayload.rules.gameMode
                val isStunt = snapMode == GameMode.STUNTS.name
                val isArena = !isStunt && snapMode == GameMode.ARENA.name
                val baseTrack = when {
                    isStunt -> TrackCatalog.getStuntTrack(
                        startPayload.stuntVariant ?: startPayload.initialSnapshot?.stuntState?.variant
                    )
                    isArena -> TrackCatalog.getArenaTrack(
                        startPayload.arenaShape ?: startPayload.initialSnapshot?.arenaShape
                    )
                    else -> startPayload.customTrack?.let { TrackCatalog.buildCustomTrack(it) }
                        ?: TrackCatalog.getTrack(startPayload.trackId)
                }
                _activeTrack.value = if (!isStunt && startPayload.ramps.isNotEmpty()) {
                    baseTrack.copy(ramps = startPayload.ramps)
                } else {
                    baseTrack
                }
                startPayload.initialSnapshot?.let { _raceSnapshot.value = it }
                inRace = true
                startClientInputLoop(doc.id)
                _navEvents.tryEmit(RaceNavEvent.StartRace)
            }
        }

        if (doc.telemetryLine.startsWith("S|") && doc.telemetrySeq > clientAppliedTelemetrySeq.get()) {
            val roster = _lobbyState.value?.players.orEmpty()
            val decoded = RaceTelemetryCodec.decodeSnapshot(
                text = doc.telemetryLine,
                roster = roster,
                previousSnapshot = _raceSnapshot.value,
                activeTrack = _activeTrack.value
            )
            if (decoded != null) {
                clientAppliedTelemetrySeq.set(doc.telemetrySeq)
                val snap = decoded.snapshot
                if (snap.gameMode == GameMode.STUNTS.name) {
                    val vName = snap.stuntState?.variant
                    if (vName != null && _activeTrack.value?.stuntVariant?.name != vName) {
                        _activeTrack.value = TrackCatalog.getStuntTrack(vName)
                    }
                }
                _raceSnapshot.value = snap
                if (snap.phase == RacePhase.FINISHED.name || doc.status == "FINISHED") {
                    _lastResults.value = snap
                    inRace = false
                    clientInputPushJob?.cancel()
                    if (clientFinishEmitted.compareAndSet(false, true)) {
                        _navEvents.tryEmit(RaceNavEvent.ShowResults)
                    }
                }
            }
        } else if (doc.status == "OPEN" && inRace) {
            inRace = false
            clientInputPushJob?.cancel()
            _rematchState.value = null
            _navEvents.tryEmit(RaceNavEvent.BackToLobby)
        }
    }

    private fun startClientInputLoop(roomId: String) {
        clientInputPushJob?.cancel()
        clientInputPushJob = scope.launch(Dispatchers.IO) {
            while (isActive && _role.value == SessionRole.OnlineClient && inRace) {
                val nowMs = System.currentTimeMillis()
                val inp = synchronized(this@OnlineRaceSessionManager) { localInput }
                val changed = abs(inp.throttle - lastPushedThrottle) > 0.05f ||
                    abs(inp.steer - lastPushedSteer) > 0.03f ||
                    (nowMs - lastInputPushMs) >= 180L
                if (changed) {
                    lastPushedThrottle = inp.throttle
                    lastPushedSteer = inp.steer
                    lastInputPushMs = nowMs
                    repository.pushDriverInput(
                        roomId = roomId,
                        seq = localInputSeq.incrementAndGet(),
                        throttle = inp.throttle,
                        steer = inp.steer,
                        powerUpSeq = localPowerUpSeq.get(),
                        mineDropSeq = localMineDropSeq.get()
                    )
                }
                delay(70L)
            }
        }
    }

    private fun updateChatCacheFromDocs(docs: List<OnlineRoomChatDoc>) {
        val mapped = docs.map { d ->
            ChatMessage(
                id = d.id.ifEmpty { UUID.randomUUID().toString().take(8) },
                senderId = d.senderUid,
                senderName = d.senderName,
                text = d.text,
                timestampMs = d.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                isSystem = d.systemMessage
            )
        }
        synchronized(latestChatHistory) {
            latestChatHistory.clear()
            latestChatHistory.addAll(mapped.takeLast(50))
        }
    }

    private fun buildCombinedRoster(): List<LobbyPlayer> {
        val humans = latestHumanPlayers.values
            .sortedWith(compareByDescending<OnlineRoomPlayerDoc> { it.host }.thenBy { it.createdAt?.seconds ?: 0L })
            .map { p ->
                LobbyPlayer(
                    id = p.userId,
                    name = p.name,
                    isHost = p.host,
                    isBot = false,
                    isReady = p.host || p.ready,
                    carType = p.carType,
                    primaryColorArgb = p.primaryColorArgb,
                    accentColorArgb = p.accentColorArgb,
                    sessionPoints = sessionPointsByPlayer[p.userId] ?: 0,
                    wins = winsByPlayer[p.userId] ?: 0
                )
            }
        val bots = synchronized(botPlayers) {
            botPlayers.map { b ->
                b.copy(
                    sessionPoints = sessionPointsByPlayer[b.id] ?: b.sessionPoints,
                    wins = winsByPlayer[b.id] ?: b.wins
                )
            }
        }
        return (humans + bots).take(maxRoomPlayers)
    }

    private fun buildHostLobbyPayload(
        roomCode: String = roomCodeDisplay.ifEmpty { "ONLINE" },
        playersOverride: List<LobbyPlayer>? = null
    ): LobbyStatePayload {
        val roster = playersOverride ?: buildCombinedRoster()
        val chatCopy = synchronized(latestChatHistory) { latestChatHistory.toList() }
        val effectiveMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            activeConcreteMode
        } else {
            currentRules.gameModeEnum
        }
        val trackName = when (effectiveMode) {
            GameMode.ARENA -> currentArenaShape.themeTrackId.name
            GameMode.STUNTS -> currentStuntVariant.themeTrackId.name
            else -> currentTrackId.name
        }
        return LobbyStatePayload(
            hostId = currentUserId,
            lobbyName = "$roomDisplayName • #$roomCode",
            hostIp = "CLOUD ROOM $roomCode",
            tcpPort = maxRoomPlayers,
            udpPort = 0,
            trackId = trackName,
            arenaShape = currentArenaShape.name,
            stuntVariant = currentStuntVariant.name,
            totalLaps = currentTotalLaps,
            players = roster,
            chatHistory = chatCopy,
            inRace = inRace,
            rules = currentRules,
            activeCustomTrack = currentCustomTrack
        )
    }

    private fun syncHostLobbyToCloud(extraFields: Map<String, Any> = emptyMap()) {
        if (_role.value != SessionRole.OnlineHost) return
        val rid = activeRoomId ?: return
        val payload = buildHostLobbyPayload()
        _lobbyState.value = payload
        val humanUids = latestHumanPlayers.keys.toList().let { list ->
            if (currentUserId in list) {
                listOf(currentUserId) + list.filter { it != currentUserId }
            } else {
                listOf(currentUserId) + list
            }
        }.take(4)
        val totalCount = payload.players.size.coerceIn(1, maxRoomPlayers).toLong()
        val lobbyJson = lobbyAdapter.toJson(payload).take(29_500)
        val fields = mutableMapOf<String, Any>(
            "roomName" to roomDisplayName,
            "gameMode" to currentRules.gameModeEnum.name,
            "trackId" to payload.trackId,
            "playerCount" to totalCount,
            "maxPlayers" to maxRoomPlayers.toLong(),
            "memberUids" to humanUids,
            "lobbyJson" to lobbyJson
        )
        fields.putAll(extraFields)
        scope.launch {
            repository.updateHostRoomFields(rid, fields)
        }
    }

    fun updateLocalProfile(profile: PlayerProfile) {
        val rid = activeRoomId ?: return
        scope.launch {
            repository.updatePlayerFields(
                roomId = rid,
                fields = mapOf(
                    "name" to profile.name.trim().take(24).ifEmpty { "Driver" },
                    "carType" to profile.carType.name,
                    "primaryColorArgb" to profile.primaryColorArgb,
                    "accentColorArgb" to profile.accentColorArgb
                )
            )
            if (_role.value == SessionRole.OnlineHost) {
                repository.updateHostRoomFields(
                    rid,
                    mapOf("hostName" to profile.name.trim().take(24).ifEmpty { "Host" })
                )
            }
        }
    }

    fun toggleReady() {
        if (_role.value != SessionRole.OnlineClient) return
        val rid = activeRoomId ?: return
        val me = _lobbyState.value?.players?.find { it.id == currentUserId } ?: return
        scope.launch {
            repository.updatePlayerFields(rid, mapOf("ready" to !me.isReady))
        }
    }

    fun selectTrack(trackId: TrackId) {
        if (_role.value != SessionRole.OnlineHost) return
        currentTrackId = trackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getTrack(trackId)
        syncHostLobbyToCloud()
    }

    fun selectCustomTrack(customTrack: CustomTrackData) {
        if (_role.value != SessionRole.OnlineHost) return
        currentCustomTrack = customTrack
        currentTrackId = runCatching { TrackId.valueOf(customTrack.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
        _activeTrack.value = TrackCatalog.buildCustomTrack(customTrack)
        syncHostLobbyToCloud()
    }

    fun selectArenaShape(shape: ArenaShape) {
        if (_role.value != SessionRole.OnlineHost) return
        currentArenaShape = shape
        currentTrackId = shape.themeTrackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getArenaTrack(shape)
        syncHostLobbyToCloud()
    }

    fun selectStuntVariant(variant: StuntVariant) {
        if (_role.value != SessionRole.OnlineHost) return
        currentStuntVariant = variant
        currentTrackId = variant.themeTrackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getStuntTrack(variant)
        syncHostLobbyToCloud()
    }

    fun updateRules(rules: RaceRulesSettings) {
        if (_role.value != SessionRole.OnlineHost) return
        configureInitialMode(rules, if (rules.gameModeEnum == GameMode.RACE) currentCustomTrack else null)
        syncHostLobbyToCloud()
    }

    fun setTotalLaps(laps: Int) {
        if (_role.value != SessionRole.OnlineHost) return
        currentTotalLaps = laps.coerceIn(1, 9)
        currentRules = currentRules.copy(totalLaps = currentTotalLaps)
        syncHostLobbyToCloud()
    }

    fun addBot() {
        if (_role.value != SessionRole.OnlineHost) return
        synchronized(botPlayers) {
            if (latestHumanPlayers.size + botPlayers.size >= maxRoomPlayers) return
            val botNum = botPlayers.size + 1
            val car = RcCarType.entries[botNum % RcCarType.entries.size]
            val preset = RcColorPresets[(botNum + 2) % RcColorPresets.size]
            botPlayers.add(
                LobbyPlayer(
                    id = "bot_${UUID.randomUUID().toString().take(6)}",
                    name = "Bot #$botNum",
                    isHost = false,
                    isBot = true,
                    isReady = true,
                    carType = car.name,
                    primaryColorArgb = preset.primaryArgb,
                    accentColorArgb = preset.accentArgb
                )
            )
        }
        syncHostLobbyToCloud()
    }

    fun removeBot() {
        if (_role.value != SessionRole.OnlineHost) return
        synchronized(botPlayers) {
            if (botPlayers.isNotEmpty()) botPlayers.removeAt(botPlayers.lastIndex)
        }
        syncHostLobbyToCloud()
    }

    fun clearBots() {
        if (_role.value != SessionRole.OnlineHost) return
        synchronized(botPlayers) { botPlayers.clear() }
        syncHostLobbyToCloud()
    }

    fun sendChat(senderName: String, text: String) {
        val rid = activeRoomId ?: return
        scope.launch {
            repository.sendRoomChat(roomId = rid, senderName = senderName, text = text, systemMessage = false)
        }
    }

    fun startRace() {
        if (_role.value != SessionRole.OnlineHost) return
        val rid = activeRoomId ?: return
        val roster = buildCombinedRoster()
        if (roster.isEmpty()) return

        cancelRematchCountdown()
        inRace = true
        appliedPowerSeqByUser.clear()
        appliedMineSeqByUser.clear()

        val isHybrid = currentRules.gameModeEnum == GameMode.HYBRID
        val concreteMode = if (isHybrid) activeConcreteMode else currentRules.gameModeEnum
        val upcomingHybridMode = if (isHybrid) {
            (nextPlannedHybridMode ?: WifiP2pRaceManager.resolveHybridStageMode(currentRules, hybridStageNumber, concreteMode)).also {
                nextPlannedHybridMode = it
            }
        } else {
            null
        }
        val isStuntMode = concreteMode == GameMode.STUNTS
        val isArenaMode = concreteMode == GameMode.ARENA
        val customData = if (isArenaMode || isStuntMode) null else currentCustomTrack
        val stuntSequence: List<StuntVariant>? = if (isStuntMode) {
            if (isHybrid) {
                val others = (StuntVariant.entries - currentStuntVariant).shuffled()
                (listOf(currentStuntVariant) + others).take(currentRules.hybridStuntRounds.coerceIn(1, 4))
            } else {
                StuntVariant.entries.shuffled()
            }
        } else {
            null
        }
        if (stuntSequence != null) {
            currentStuntVariant = stuntSequence.first()
        }

        val baseTrack = when {
            isStuntMode -> TrackCatalog.getStuntTrack(currentStuntVariant)
            isArenaMode -> TrackCatalog.getArenaTrack(currentArenaShape)
            customData != null -> TrackCatalog.buildCustomTrack(customData)
            else -> TrackCatalog.getTrack(currentTrackId)
        }
        val track = when {
            isStuntMode -> baseTrack
            customData == null -> TrackCatalog.withRandomJumps(baseTrack, enabled = currentRules.randomJumpsEnabled)
            !currentRules.randomJumpsEnabled -> baseTrack.copy(ramps = emptyList())
            baseTrack.ramps.isEmpty() -> TrackCatalog.withRandomJumps(baseTrack, enabled = true)
            else -> baseTrack
        }
        _activeTrack.value = track

        val effectiveStageRules = currentRules.copy(gameMode = concreteMode.name)
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = currentTotalLaps,
            players = roster,
            rules = effectiveStageRules,
            stuntSequenceOverride = stuntSequence,
            isHybridTour = isHybrid,
            hybridStageNumber = hybridStageNumber,
            nextHybridModeName = upcomingHybridMode?.name
        )
        physicsEngine = engine

        val initialSnap = engine.step(0.016f)
        _raceSnapshot.value = initialSnap
        val matchSeq = hostMatchSeq.incrementAndGet()
        val initTelemetrySeq = hostTelemetrySeq.incrementAndGet()
        val initTelemetryLine = String(RaceTelemetryCodec.encodeSnapshot(initTelemetrySeq, initialSnap), Charsets.UTF_8)

        val startPayload = OnlineRaceStartPayload(
            matchSeq = matchSeq,
            rules = currentRules,
            trackId = track.id.name,
            arenaShape = if (isArenaMode) currentArenaShape.name else null,
            stuntVariant = if (isStuntMode) currentStuntVariant.name else null,
            stuntSequence = stuntSequence?.map { it.name },
            customTrack = customData,
            ramps = track.ramps,
            initialSnapshot = initialSnap
        )
        val startJson = raceStartAdapter.toJson(startPayload).take(39_500)

        syncHostLobbyToCloud(
            extraFields = mapOf(
                "status" to "RACING",
                "raceStartJson" to startJson,
                "telemetrySeq" to initTelemetrySeq,
                "telemetryLine" to initTelemetryLine,
                "rematchJson" to ""
            )
        )
        _navEvents.tryEmit(RaceNavEvent.StartRace)

        physicsJob?.cancel()
        physicsJob = scope.launch(Dispatchers.Default) {
            var tickCounter = 0
            var lastNs = System.nanoTime()
            val publishBusy = AtomicBoolean(false)

            while (isActive && inRace) {
                delay(16L)
                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.008f, 0.04f)
                lastNs = nowNs

                val localToApply = synchronized(this@OnlineRaceSessionManager) {
                    val inp = localInput.copy(
                        usePowerUp = pendingLocalPowerUp,
                        dropMine = pendingLocalMineDrop
                    )
                    pendingLocalPowerUp = false
                    pendingLocalMineDrop = false
                    inp
                }
                engine.submitInput(currentUserId, localToApply)

                val snap = engine.step(dt)
                if (isStuntMode) {
                    val vName = snap.stuntState?.variant
                    if (vName != null && _activeTrack.value?.stuntVariant?.name != vName) {
                        _activeTrack.value = TrackCatalog.getStuntTrack(vName)
                    }
                }
                _raceSnapshot.value = snap
                tickCounter++

                if (snap.phase == RacePhase.FINISHED.name) {
                    awardOnlineSessionPoints(snap)
                    _lastResults.value = snap
                    inRace = false
                    val finalSeq = hostTelemetrySeq.incrementAndGet()
                    val finalLine = String(RaceTelemetryCodec.encodeSnapshot(finalSeq, snap), Charsets.UTF_8)
                    val updatedLobbyJson = lobbyAdapter.toJson(buildHostLobbyPayload()).take(29_500)
                    scope.launch(Dispatchers.IO) {
                        repository.updateHostRoomFields(
                            rid,
                            mapOf(
                                "status" to "FINISHED",
                                "lobbyJson" to updatedLobbyJson,
                                "telemetrySeq" to finalSeq,
                                "telemetryLine" to finalLine
                            )
                        )
                    }
                    _navEvents.tryEmit(RaceNavEvent.ShowResults)
                    startHostRematchCountdown()
                    break
                } else if (tickCounter % 5 == 0 && publishBusy.compareAndSet(false, true)) {
                    val seq = hostTelemetrySeq.incrementAndGet()
                    val line = String(RaceTelemetryCodec.encodeSnapshot(seq, snap), Charsets.UTF_8)
                        .take(15_800)
                    scope.launch(Dispatchers.IO) {
                        try {
                            repository.updateHostRoomFields(
                                rid,
                                mapOf(
                                    "telemetrySeq" to seq,
                                    "telemetryLine" to line
                                )
                            )
                        } finally {
                            publishBusy.set(false)
                        }
                    }
                }
            }
        }
    }

    fun updateLocalControlInput(throttle: Float, steer: Float) {
        synchronized(this) {
            localInput = localInput.copy(throttle = throttle, steer = steer)
        }
        if (_role.value == SessionRole.OnlineClient && inRace) {
            val rid = activeRoomId ?: return
            val throttleFlipped = (throttle * lastPushedThrottle <= 0f) && abs(throttle - lastPushedThrottle) > 0.12f
            val steerFlipped = (steer == 0f && abs(lastPushedSteer) > 0.02f) || (steer * lastPushedSteer < 0f)
            if (throttleFlipped || steerFlipped) {
                lastPushedThrottle = throttle
                lastPushedSteer = steer
                lastInputPushMs = System.currentTimeMillis()
                scope.launch(Dispatchers.IO) {
                    repository.pushDriverInput(
                        roomId = rid,
                        seq = localInputSeq.incrementAndGet(),
                        throttle = throttle,
                        steer = steer,
                        powerUpSeq = localPowerUpSeq.get(),
                        mineDropSeq = localMineDropSeq.get()
                    )
                }
            }
        }
    }

    fun triggerLocalPowerUp() {
        synchronized(this) {
            pendingLocalPowerUp = true
        }
        if (_role.value == SessionRole.OnlineClient && inRace) {
            val rid = activeRoomId ?: return
            val inp = synchronized(this) { localInput }
            val nextPowerSeq = localPowerUpSeq.incrementAndGet()
            scope.launch(Dispatchers.IO) {
                repository.pushDriverInput(
                    roomId = rid,
                    seq = localInputSeq.incrementAndGet(),
                    throttle = inp.throttle,
                    steer = inp.steer,
                    powerUpSeq = nextPowerSeq,
                    mineDropSeq = localMineDropSeq.get()
                )
            }
        }
    }

    fun triggerLocalMineDrop() {
        synchronized(this) {
            pendingLocalMineDrop = true
        }
        if (_role.value == SessionRole.OnlineClient && inRace) {
            val rid = activeRoomId ?: return
            val inp = synchronized(this) { localInput }
            val nextMineSeq = localMineDropSeq.incrementAndGet()
            scope.launch(Dispatchers.IO) {
                repository.pushDriverInput(
                    roomId = rid,
                    seq = localInputSeq.incrementAndGet(),
                    throttle = inp.throttle,
                    steer = inp.steer,
                    powerUpSeq = localPowerUpSeq.get(),
                    mineDropSeq = nextMineSeq
                )
            }
        }
    }

    private fun awardOnlineSessionPoints(snapshot: RaceSnapshot) {
        val pointsTable = listOf(10, 6, 4, 2)
        snapshot.racers.forEach { r ->
            val pts = pointsTable.getOrElse(r.rank - 1) { 1 }
            sessionPointsByPlayer[r.playerId] = (sessionPointsByPlayer[r.playerId] ?: 0) + pts
            if (r.rank == 1) {
                winsByPlayer[r.playerId] = (winsByPlayer[r.playerId] ?: 0) + 1
            }
        }
    }

    private fun cancelRematchCountdown() {
        rematchJob?.cancel()
        rematchJob = null
        rematchVotes.clear()
        rematchOptions = emptyList()
        _rematchState.value = null
    }

    private fun publishHostRematchState() {
        val rid = activeRoomId ?: return
        val opts = rematchOptions
        if (opts.isEmpty()) return
        val votesMap = rematchVotes.toMap()
        val state = RematchCountdownState(
            secondsLeft = rematchSecondsLeft,
            totalSeconds = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS,
            options = opts,
            votesByPlayerId = votesMap,
            leadingOptionId = WifiP2pRaceManager.computeLeadingRematchOption(opts, votesMap)
        )
        _rematchState.value = state
        val json = rematchAdapter.toJson(state).take(7800)
        scope.launch(Dispatchers.IO) {
            repository.updateHostRoomFields(rid, mapOf("rematchJson" to json))
        }
    }

    private fun startHostRematchCountdown() {
        if (_role.value != SessionRole.OnlineHost) return
        rematchJob?.cancel()
        rematchVotes.clear()
        val voteTargetMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            (nextPlannedHybridMode ?: WifiP2pRaceManager.resolveHybridStageMode(currentRules, hybridStageNumber, activeConcreteMode)).also {
                nextPlannedHybridMode = it
            }
        } else {
            currentRules.gameModeEnum
        }
        rematchOptions = WifiP2pRaceManager.buildRematchVoteOptions(
            gameMode = voteTargetMode,
            currentTrackId = currentTrackId,
            currentArenaShape = currentArenaShape,
            currentStuntVariant = currentStuntVariant,
            hasActiveCustomTrack = currentCustomTrack != null
        )
        rematchSecondsLeft = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS
        publishHostRematchState()

        rematchJob = scope.launch(Dispatchers.Default) {
            for (sec in (WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS - 1) downTo 1) {
                delay(1000L)
                if (!isActive || inRace) return@launch
                rematchSecondsLeft = sec
                publishHostRematchState()
            }
            delay(1000L)
            if (!isActive || inRace) return@launch
            launchRematchNow()
        }
    }

    fun voteRematchOption(optionId: String) {
        val rid = activeRoomId ?: return
        when (_role.value) {
            SessionRole.OnlineHost -> {
                if (_rematchState.value == null || rematchOptions.none { it.id == optionId }) return
                rematchVotes[currentUserId] = optionId
                publishHostRematchState()
            }
            SessionRole.OnlineClient -> {
                val cur = _rematchState.value ?: return
                if (cur.options.none { it.id == optionId }) return
                val updatedVotes = cur.votesByPlayerId + (currentUserId to optionId)
                _rematchState.value = cur.copy(
                    votesByPlayerId = updatedVotes,
                    leadingOptionId = WifiP2pRaceManager.computeLeadingRematchOption(cur.options, updatedVotes)
                )
                scope.launch {
                    repository.updatePlayerFields(rid, mapOf("rematchVote" to optionId.take(64)))
                }
            }
            else -> Unit
        }
    }

    fun launchRematchNow() {
        if (_role.value != SessionRole.OnlineHost || inRace) return
        val opts = rematchOptions
        val votes = rematchVotes.toMap()
        cancelRematchCountdown()

        val winnerId = if (opts.isNotEmpty()) {
            WifiP2pRaceManager.resolveRematchWinner(opts, votes)
        } else {
            WifiP2pRaceManager.REMATCH_OPTION_RANDOM
        }

        val targetMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            val nextMode = nextPlannedHybridMode
                ?: WifiP2pRaceManager.resolveHybridStageMode(currentRules, hybridStageNumber, activeConcreteMode)
            hybridStageNumber += 1
            activeConcreteMode = nextMode
            nextPlannedHybridMode = WifiP2pRaceManager.resolveHybridStageMode(currentRules, hybridStageNumber, nextMode)
            nextMode
        } else {
            currentRules.gameModeEnum
        }

        when (targetMode) {
            GameMode.ARENA -> {
                val nextShape = if (winnerId == WifiP2pRaceManager.REMATCH_OPTION_RANDOM) {
                    ArenaShape.entries.filter { it != currentArenaShape }.randomOrNull() ?: currentArenaShape
                } else {
                    runCatching { ArenaShape.valueOf(winnerId) }.getOrDefault(currentArenaShape)
                }
                currentArenaShape = nextShape
                currentTrackId = nextShape.themeTrackId
                currentCustomTrack = null
                _activeTrack.value = TrackCatalog.getArenaTrack(nextShape)
            }
            GameMode.STUNTS -> {
                val nextStunt = if (winnerId == WifiP2pRaceManager.REMATCH_OPTION_RANDOM) {
                    StuntVariant.entries.filter { it != currentStuntVariant }.randomOrNull() ?: currentStuntVariant
                } else {
                    runCatching { StuntVariant.valueOf(winnerId) }.getOrDefault(currentStuntVariant)
                }
                currentStuntVariant = nextStunt
                currentTrackId = nextStunt.themeTrackId
                currentCustomTrack = null
                _activeTrack.value = TrackCatalog.getStuntTrack(nextStunt)
            }
            else -> {
                if (winnerId == WifiP2pRaceManager.REMATCH_OPTION_RANDOM) {
                    if (Random.nextFloat() < 0.35f) {
                        val generated = TrackCatalog.generateRandomTrack(seed = System.currentTimeMillis())
                        currentCustomTrack = generated
                        currentTrackId = runCatching { TrackId.valueOf(generated.themeId) }
                            .getOrDefault(TrackId.NEON_ROOFTOP)
                        _activeTrack.value = TrackCatalog.buildCustomTrack(generated)
                    } else {
                        val choices = TrackId.entries.filter { it != currentTrackId }.ifEmpty { TrackId.entries }
                        val nextTrack = choices.random()
                        currentTrackId = nextTrack
                        currentCustomTrack = null
                        _activeTrack.value = TrackCatalog.getTrack(nextTrack)
                    }
                } else {
                    val nextTrack = runCatching { TrackId.valueOf(winnerId) }.getOrDefault(currentTrackId)
                    currentTrackId = nextTrack
                    currentCustomTrack = null
                    _activeTrack.value = TrackCatalog.getTrack(nextTrack)
                }
            }
        }
        startRace()
    }

    fun returnToLobby() {
        if (_role.value != SessionRole.OnlineHost) return
        cancelRematchCountdown()
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
        inRace = false
        syncHostLobbyToCloud(
            extraFields = mapOf(
                "status" to "OPEN",
                "rematchJson" to ""
            )
        )
        _navEvents.tryEmit(RaceNavEvent.BackToLobby)
    }

    suspend fun leaveCurrentRoomInternal() {
        val rid = activeRoomId
        val wasHost = _role.value == SessionRole.OnlineHost
        cancelRematchCountdown()
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
        roomObserverJob?.cancel()
        roomObserverJob = null
        playersObserverJob?.cancel()
        playersObserverJob = null
        inputsObserverJob?.cancel()
        inputsObserverJob = null
        chatObserverJob?.cancel()
        chatObserverJob = null
        clientInputPushJob?.cancel()
        clientInputPushJob = null
        inRace = false
        activeRoomId = null
        _role.value = SessionRole.None
        _activeRoom.value = null
        _lobbyState.value = null
        _raceSnapshot.value = null
        _activeTrack.value = null
        latestHumanPlayers.clear()
        synchronized(botPlayers) { botPlayers.clear() }
        if (rid != null) {
            repository.leaveOnlineRoom(roomId = rid, isHost = wasHost)
        }
    }

    fun leaveRoom() {
        scope.launch {
            leaveCurrentRoomInternal()
        }
    }
}
