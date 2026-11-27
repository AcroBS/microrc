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
import com.example.model.WinCondition
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
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

    private val _syncPingMs = MutableStateFlow<Int?>(null)
    val syncPingMs: StateFlow<Int?> = _syncPingMs.asStateFlow()

    private val _usingRelayFallback = MutableStateFlow(false)
    val usingRelayFallback: StateFlow<Boolean> = _usingRelayFallback.asStateFlow()

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
    @Volatile private var inRace: Boolean = false
    private var physicsEngine: RacePhysicsEngine? = null

    private val botPlayers = mutableListOf<LobbyPlayer>()
    private val latestHumanPlayers = ConcurrentHashMap<String, OnlineRoomPlayerDoc>()
    private val lastClientRelayRxNsByUser = ConcurrentHashMap<String, Long>()
    private val sessionPointsByPlayer = ConcurrentHashMap<String, Int>()
    private val winsByPlayer = ConcurrentHashMap<String, Int>()
    private val latestChatHistory = mutableListOf<ChatMessage>()
    private val appliedInputSeqByUser = ConcurrentHashMap<String, Long>()
    private val appliedPowerSeqByUser = ConcurrentHashMap<String, Long>()
    private val appliedMineSeqByUser = ConcurrentHashMap<String, Long>()

    private val hostRoomWriteMutex = Mutex()
    private val clientSnapshotLock = Any()
    private val hostMatchSeq = AtomicLong(0L)
    private val hostTelemetrySeq = AtomicLong(0L)
    private val clientAppliedMatchSeq = AtomicLong(0L)
    private val clientAppliedTelemetrySeq = AtomicLong(-1L)
    private val clientFinishEmitted = AtomicBoolean(false)
    private val clientAwaitingLobbyReturn = AtomicBoolean(false)
    @Volatile private var raceStartedAtNs: Long = 0L

    private var localInput = ControlInput()
    private var pendingLocalPowerUp = false
    private var pendingLocalMineDrop = false
    private val localInputSeq = AtomicLong(0L)
    private val localPowerUpSeq = AtomicLong(0L)
    private val localMineDropSeq = AtomicLong(0L)
    private val inputWakeSignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var inputDirty = false
    @Volatile private var urgentInputPending = false
    private var lastPushedThrottle = Float.NaN
    private var lastPushedSteer = Float.NaN
    private var lastInputPushMs = 0L

    private var rematchOptions: List<RematchVoteOption> = emptyList()
    private val rematchVotes = ConcurrentHashMap<String, String>()
    @Volatile private var rematchSecondsLeft: Int = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS
    @Volatile private var pendingLocalRematchVote: String? = null
    @Volatile private var returnToLobbyHandledForMatchSeq: Long = -1L

    private val relaySocket = OnlineRelaySocket(
        scope = scope,
        onConnected = { handleRelayConnected() },
        onDisconnected = { handleRelayDisconnected() },
        onFrameReceived = { frame -> handleRelayFrame(frame) }
    )

    companion object {
        private const val RELAY_FALLBACK_TIMEOUT_NS = 1_500_000_000L
        internal const val RETURN_TO_LOBBY_VOTE_PREFIX = "__RETURN_TO_LOBBY__:"

        internal fun returnToLobbyVoteToken(matchSeq: Long): String =
            "$RETURN_TO_LOBBY_VOTE_PREFIX${matchSeq.coerceAtLeast(0L)}"

        internal data class HostInputDecision(
            val accepted: Boolean,
            val controlInput: ControlInput? = null
        )

        internal fun evaluateIncomingHostInput(
            doc: OnlineRoomInputDoc,
            appliedInputSeqByUser: MutableMap<String, Long>,
            appliedPowerSeqByUser: MutableMap<String, Long>,
            appliedMineSeqByUser: MutableMap<String, Long>
        ): HostInputDecision {
            val prevSeq = appliedInputSeqByUser[doc.userId] ?: -1L
            val prevPower = appliedPowerSeqByUser[doc.userId] ?: doc.powerUpSeq
            val prevMine = appliedMineSeqByUser[doc.userId] ?: doc.mineDropSeq
            val triggerPower = doc.powerUpSeq > prevPower
            val triggerMine = doc.mineDropSeq > prevMine
            if (doc.seq < prevSeq && !triggerPower && !triggerMine) {
                return HostInputDecision(accepted = false)
            }
            if (doc.seq == prevSeq && !triggerPower && !triggerMine) {
                return HostInputDecision(accepted = false)
            }
            if (doc.seq > prevSeq) {
                appliedInputSeqByUser[doc.userId] = doc.seq
            }
            if (doc.powerUpSeq > prevPower || doc.userId !in appliedPowerSeqByUser) {
                appliedPowerSeqByUser[doc.userId] = doc.powerUpSeq
            }
            if (doc.mineDropSeq > prevMine || doc.userId !in appliedMineSeqByUser) {
                appliedMineSeqByUser[doc.userId] = doc.mineDropSeq
            }
            return HostInputDecision(
                accepted = true,
                controlInput = ControlInput(
                    throttle = doc.throttle.toFloat(),
                    steer = doc.steer.toFloat(),
                    usePowerUp = triggerPower,
                    dropMine = triggerMine
                )
            )
        }

        internal fun evaluateIncomingHostRelayInput(
            decoded: RaceTelemetryCodec.DecodedInput,
            appliedInputSeqByUser: MutableMap<String, Long>,
            appliedPowerSeqByUser: MutableMap<String, Long>,
            appliedMineSeqByUser: MutableMap<String, Long>
        ): HostInputDecision {
            val pSeq = decoded.powerUpSeq
            val mSeq = decoded.mineDropSeq
            if (pSeq != null && mSeq != null) {
                return evaluateIncomingHostInput(
                    doc = OnlineRoomInputDoc(
                        userId = decoded.playerId,
                        seq = decoded.seq,
                        throttle = decoded.input.throttle.toDouble(),
                        steer = decoded.input.steer.toDouble(),
                        powerUpSeq = pSeq,
                        mineDropSeq = mSeq
                    ),
                    appliedInputSeqByUser = appliedInputSeqByUser,
                    appliedPowerSeqByUser = appliedPowerSeqByUser,
                    appliedMineSeqByUser = appliedMineSeqByUser
                )
            }
            val prevSeq = appliedInputSeqByUser[decoded.playerId] ?: -1L
            val hasAction = decoded.input.usePowerUp || decoded.input.dropMine
            if (decoded.seq <= prevSeq && !hasAction) {
                return HostInputDecision(accepted = false)
            }
            if (decoded.seq > prevSeq) {
                appliedInputSeqByUser[decoded.playerId] = decoded.seq
            }
            return HostInputDecision(accepted = true, controlInput = decoded.input)
        }

        internal fun shouldHostMirrorToFirestore(
            hostRelayConnected: Boolean,
            remoteHumanIds: Set<String>,
            lastClientRelayRxNsByUser: Map<String, Long>,
            nowNs: Long,
            raceStartedAtNs: Long
        ): Boolean {
            if (remoteHumanIds.isEmpty()) return false
            if (!hostRelayConnected) return true
            if (raceStartedAtNs > 0L && (nowNs - raceStartedAtNs) < RELAY_FALLBACK_TIMEOUT_NS) {
                return false
            }
            return remoteHumanIds.any { uid ->
                val lastRx = lastClientRelayRxNsByUser[uid] ?: 0L
                lastRx <= 0L || (nowNs - lastRx) > RELAY_FALLBACK_TIMEOUT_NS
            }
        }
    }

    private fun recordWriteLatencySample(rttMs: Long) {
        val clamped = rttMs.coerceIn(1L, 5_000L).toFloat()
        val prev = _syncPingMs.value?.toFloat()
        val smoothed = if (prev == null) clamped else (prev * 0.65f + clamped * 0.35f)
        _syncPingMs.value = smoothed.roundToInt()
    }

    private fun handleRelayConnected() {
        when (_role.value) {
            SessionRole.OnlineHost -> {
                _usingRelayFallback.value = false
                if (inRace) {
                    val hasRemoteHumans = latestHumanPlayers.keys.any { it != currentUserId }
                    if (hasRemoteHumans) {
                        relaySocket.sendText(RaceTelemetryCodec.encodeRelayPing(currentUserId, System.nanoTime()))
                    }
                    _raceSnapshot.value?.let { snap ->
                        val seq = hostTelemetrySeq.incrementAndGet()
                        val line = String(RaceTelemetryCodec.encodeSnapshot(seq, snap), Charsets.UTF_8)
                        relaySocket.sendText(line)
                    }
                }
            }
            SessionRole.OnlineClient -> {
                _usingRelayFallback.value = false
                if (inRace) {
                    val ackSeq = clientAppliedTelemetrySeq.get()
                    relaySocket.sendText(String(RaceTelemetryCodec.encodeHello(currentUserId, ackSeq), Charsets.UTF_8))
                    relaySocket.sendText(RaceTelemetryCodec.encodeRelayPing(currentUserId, System.nanoTime()))
                    inputWakeSignal.trySend(Unit)
                }
            }
            SessionRole.None, SessionRole.Host, SessionRole.Client, SessionRole.Solo -> Unit
        }
    }

    private fun handleRelayDisconnected() {
        if (inRace && _role.value.isOnline) {
            _usingRelayFallback.value = true
            if (_role.value == SessionRole.OnlineClient) {
                inputWakeSignal.trySend(Unit)
            }
        }
    }

    private fun handleRelayFrame(frame: String) {
        if (frame.length < 2) return
        when (_role.value) {
            SessionRole.OnlineHost -> {
                when {
                    frame.startsWith("I|") -> {
                        val decoded = RaceTelemetryCodec.decodeInput(frame) ?: return
                        if (decoded.playerId == currentUserId) return
                        if (!latestHumanPlayers.containsKey(decoded.playerId)) return
                        lastClientRelayRxNsByUser[decoded.playerId] = System.nanoTime()
                        val engine = physicsEngine ?: return
                        if (!inRace) return
                        val decision = evaluateIncomingHostRelayInput(
                            decoded = decoded,
                            appliedInputSeqByUser = appliedInputSeqByUser,
                            appliedPowerSeqByUser = appliedPowerSeqByUser,
                            appliedMineSeqByUser = appliedMineSeqByUser
                        )
                        val ctrl = decision.controlInput
                        if (decision.accepted && ctrl != null) {
                            engine.submitInput(decoded.playerId, ctrl)
                        }
                    }
                    frame.startsWith("H|") -> {
                        val hello = RaceTelemetryCodec.decodeHelloPacket(frame) ?: return
                        if (hello.playerId == currentUserId) return
                        if (!latestHumanPlayers.containsKey(hello.playerId)) return
                        lastClientRelayRxNsByUser[hello.playerId] = System.nanoTime()
                        if (inRace) {
                            _raceSnapshot.value?.let { snap ->
                                val seq = hostTelemetrySeq.incrementAndGet()
                                val line = String(RaceTelemetryCodec.encodeSnapshot(seq, snap), Charsets.UTF_8)
                                relaySocket.sendText(line)
                            }
                            relaySocket.sendText(RaceTelemetryCodec.encodeRelayPing(currentUserId, System.nanoTime()))
                        }
                    }
                    frame.startsWith("P|") -> {
                        val ping = RaceTelemetryCodec.decodeRelayPing(frame) ?: return
                        if (ping.senderId == currentUserId) return
                        lastClientRelayRxNsByUser[ping.senderId] = System.nanoTime()
                        relaySocket.sendText(RaceTelemetryCodec.encodeRelayPong(ping.senderId, ping.timestampNs))
                    }
                    frame.startsWith("Q|") -> {
                        val pong = RaceTelemetryCodec.decodeRelayPong(frame) ?: return
                        if (pong.targetSenderId != currentUserId) return
                        val rttMs = ((System.nanoTime() - pong.timestampNs) / 1_000_000L)
                        if (rttMs in 0L..10_000L) {
                            recordWriteLatencySample(rttMs.coerceAtLeast(1L))
                        }
                    }
                }
            }
            SessionRole.OnlineClient -> {
                when {
                    frame.startsWith("S|") -> {
                        if (!inRace) return
                        val roster = _lobbyState.value?.players.orEmpty()
                        val decoded = RaceTelemetryCodec.decodeSnapshot(
                            text = frame,
                            roster = roster,
                            previousSnapshot = _raceSnapshot.value,
                            activeTrack = _activeTrack.value
                        ) ?: return
                        applyIncomingClientSnapshot(seq = decoded.seq, snap = decoded.snapshot)
                    }
                    frame.startsWith("R|") -> {
                        if (!inRace) {
                            val parsed = runCatching {
                                rematchAdapter.fromJson(frame.removePrefix("R|"))
                            }.getOrNull() ?: return
                            applyClientRematchState(parsed)
                        }
                    }
                    frame.startsWith("P|") -> {
                        val ping = RaceTelemetryCodec.decodeRelayPing(frame) ?: return
                        if (ping.senderId == currentUserId) return
                        relaySocket.sendText(RaceTelemetryCodec.encodeRelayPong(ping.senderId, ping.timestampNs))
                    }
                    frame.startsWith("Q|") -> {
                        val pong = RaceTelemetryCodec.decodeRelayPong(frame) ?: return
                        if (pong.targetSenderId != currentUserId) return
                        val rttMs = ((System.nanoTime() - pong.timestampNs) / 1_000_000L)
                        if (rttMs in 0L..10_000L) {
                            recordWriteLatencySample(rttMs.coerceAtLeast(1L))
                        }
                    }
                }
            }
            SessionRole.None, SessionRole.Host, SessionRole.Client, SessionRole.Solo -> Unit
        }
    }

    private fun applyClientRematchState(parsed: RematchCountdownState) {
        val pendingVote = pendingLocalRematchVote
        val merged = if (pendingVote != null && parsed.options.any { it.id == pendingVote }) {
            if (parsed.votesByPlayerId[currentUserId] == pendingVote) {
                pendingLocalRematchVote = null
                parsed
            } else {
                val updatedVotes = parsed.votesByPlayerId + (currentUserId to pendingVote)
                parsed.copy(
                    votesByPlayerId = updatedVotes,
                    leadingOptionId = WifiP2pRaceManager.computeLeadingRematchOption(
                        parsed.options,
                        updatedVotes
                    )
                )
            }
        } else {
            parsed
        }
        _rematchState.value = merged
    }

    private fun applyIncomingClientSnapshot(
        seq: Long,
        snap: RaceSnapshot,
        forceFinishedFromRoomDoc: Boolean = false
    ): Boolean {
        synchronized(clientSnapshotLock) {
            if (seq <= clientAppliedTelemetrySeq.get()) {
                return false
            }
            clientAppliedTelemetrySeq.set(seq)
        }
        if (snap.gameMode == GameMode.STUNTS.name) {
            val vName = snap.stuntState?.variant
            if (vName != null && _activeTrack.value?.stuntVariant?.name != vName) {
                _activeTrack.value = TrackCatalog.getStuntTrack(vName)
            }
        }
        _raceSnapshot.value = snap
        if (snap.phase == RacePhase.FINISHED.name || forceFinishedFromRoomDoc) {
            _lastResults.value = snap
            inRace = false
            clientAwaitingLobbyReturn.set(true)
            clientInputPushJob?.cancel()
            _usingRelayFallback.value = false
            relaySocket.disconnect("Race finished")
            if (clientFinishEmitted.compareAndSet(false, true)) {
                _navEvents.tryEmit(RaceNavEvent.ShowResults)
            }
        }
        return true
    }

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
            GameMode.DRIFT -> {
                activeConcreteMode = GameMode.DRIFT
                nextPlannedHybridMode = null
                currentRules = rules.copy(
                    gameMode = GameMode.DRIFT.name,
                    winCondition = WinCondition.FIRST_TO_FINISH.name
                )
                val driftTrack = initialCustomTrack?.takeIf { com.example.drift.DriftFeature.isDriftTrackKey(it.id) }
                    ?: com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks().firstOrNull()
                if (driftTrack != null) {
                    currentCustomTrack = driftTrack
                    currentTrackId = runCatching { TrackId.valueOf(driftTrack.themeId) }
                        .getOrDefault(TrackId.WAREHOUSE_DRIFT)
                    _activeTrack.value = TrackCatalog.buildCustomTrack(driftTrack)
                } else {
                    _activeTrack.value = TrackCatalog.getTrack(currentTrackId)
                }
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
                    accentColorArgb = profile.accentColorArgb,
                    equippedUpgradesMask = profile.equippedUpgradesMask
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
                        equippedUpgradesMask = profile.equippedUpgradesMask,
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
                    com.example.data.formatUserFacingOnlineErrorOrNull(err, "Failed to create online lobby.")?.let {
                        _errorBanner.value = it
                    }
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Failed to create online lobby.")?.let {
                    _errorBanner.value = it
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Prepares a private cloud room in Firestore while the local player keeps playing their
     * current Solo/Offline game uninterrupted, waiting for a challenged friend to accept.
     */
    suspend fun createStandbyChallengeRoom(
        profile: PlayerProfile,
        roomName: String,
        maxPlayers: Int,
        initialRules: RaceRulesSettings
    ): Result<OnlineRoomDoc> {
        val cleanMax = maxPlayers.coerceIn(2, 4)
        val cleanRoomName = roomName.trim().take(40).ifEmpty { "${profile.name.take(14)}'s Challenge" }
        val cleanHostName = profile.name.trim().take(24).ifEmpty { "Host" }
        val concreteMode = when (initialRules.gameModeEnum) {
            GameMode.HYBRID -> WifiP2pRaceManager.resolveHybridStageMode(initialRules, 0, null)
            else -> initialRules.gameModeEnum
        }
        val seedDriftCustom = if (concreteMode == GameMode.DRIFT) {
            com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks().first()
        } else {
            null
        }
        val normalizedRules = if (concreteMode == GameMode.DRIFT) {
            initialRules.copy(
                winCondition = WinCondition.FIRST_TO_FINISH.name
            )
        } else {
            initialRules
        }
        val seedTrackId = when {
            seedDriftCustom != null -> seedDriftCustom.themeId
            concreteMode == GameMode.ARENA -> ArenaShape.SQUARE.themeTrackId.name
            concreteMode == GameMode.STUNTS -> StuntVariant.LONG_JUMP.themeTrackId.name
            else -> TrackId.LIVING_ROOM_GP.name
        }
        val initialHostPlayer = LobbyPlayer(
            id = currentUserId,
            name = cleanHostName,
            isHost = true,
            isBot = false,
            isReady = true,
            carType = profile.carType.name,
            primaryColorArgb = profile.primaryColorArgb,
            accentColorArgb = profile.accentColorArgb,
            equippedUpgradesMask = profile.equippedUpgradesMask
        )
        val seedPayload = LobbyStatePayload(
            hostId = currentUserId,
            lobbyName = cleanRoomName,
            hostIp = "CLOUD ROOM",
            tcpPort = cleanMax,
            udpPort = 0,
            trackId = seedTrackId,
            arenaShape = ArenaShape.SQUARE.name,
            stuntVariant = StuntVariant.LONG_JUMP.name,
            totalLaps = normalizedRules.totalLaps.coerceIn(1, 9),
            players = listOf(initialHostPlayer),
            chatHistory = emptyList(),
            inRace = false,
            rules = normalizedRules,
            activeCustomTrack = seedDriftCustom
        )
        val lobbyJson = lobbyAdapter.toJson(seedPayload)
        return repository.createOnlineRoom(
            profile = profile,
            roomName = cleanRoomName,
            maxPlayers = cleanMax,
            discoverable = false,
            rawPassword = "",
            gameMode = initialRules.gameModeEnum,
            trackId = seedTrackId,
            initialLobbyJson = lobbyJson
        )
    }

    /**
     * Activates a prepared standby room as Host once at least one challenged friend accepts,
     * bringing the local player immediately into the shared multiplayer lobby.
     */
    fun attachStandbyRoomAsHost(
        createdRoom: OnlineRoomDoc,
        profile: PlayerProfile,
        initialRules: RaceRulesSettings
    ) {
        scope.launch {
            if (activeRoomId != null && activeRoomId != createdRoom.id) {
                leaveCurrentRoomInternal()
            }
            configureInitialMode(initialRules, null)
            maxRoomPlayers = createdRoom.maxPlayers.toInt().coerceIn(2, 4)
            roomDisplayName = createdRoom.roomName.take(40).ifEmpty { "${profile.name.take(14)}'s Challenge" }
            roomCodeDisplay = createdRoom.roomCode
            activeRoomId = createdRoom.id
            _activeRoom.value = createdRoom
            _role.value = SessionRole.OnlineHost
            inRace = false
            botPlayers.clear()
            sessionPointsByPlayer.clear()
            winsByPlayer.clear()
            latestHumanPlayers.clear()

            val cleanHostName = profile.name.trim().take(24).ifEmpty { "Host" }
            val initialHostPlayer = LobbyPlayer(
                id = currentUserId,
                name = cleanHostName,
                isHost = true,
                isBot = false,
                isReady = true,
                carType = profile.carType.name,
                primaryColorArgb = profile.primaryColorArgb,
                accentColorArgb = profile.accentColorArgb,
                equippedUpgradesMask = profile.equippedUpgradesMask
            )
            latestHumanPlayers[currentUserId] = OnlineRoomPlayerDoc(
                id = currentUserId,
                roomId = createdRoom.id,
                userId = currentUserId,
                name = cleanHostName,
                carType = profile.carType.name,
                primaryColorArgb = profile.primaryColorArgb,
                accentColorArgb = profile.accentColorArgb,
                equippedUpgradesMask = profile.equippedUpgradesMask,
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
            _navEvents.tryEmit(RaceNavEvent.EnterLobby)

            repository.sendRoomChat(
                roomId = createdRoom.id,
                senderName = "Race Control",
                text = "Challenge accepted! Room ${createdRoom.roomCode} ready to configure your joint challenge.",
                systemMessage = true
            )
        }
    }

    suspend fun discardStandbyChallengeRoom(roomId: String) {
        if (roomId.isBlank()) return
        if (activeRoomId == roomId) return
        repository.leaveOnlineRoom(roomId = roomId, isHost = true)
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
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Unable to join online lobby.")?.let {
                    _errorBanner.value = it
                }
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
                    com.example.data.formatUserFacingOnlineErrorOrNull(err, "Room code not found.")?.let {
                        _errorBanner.value = it
                    }
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
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Room code not found.")?.let {
                    _errorBanner.value = it
                }
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
            clientAwaitingLobbyReturn.set(false)
            pendingLocalRematchVote = null

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
            com.example.data.formatUserFacingOnlineErrorOrNull(err, "Unable to join online lobby.")?.let {
                _errorBanner.value = it
            }
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
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Room sync interrupted.")?.let {
                    _errorBanner.value = it
                }
            }
        }

        playersObserverJob?.cancel()
        playersObserverJob = scope.launch {
            try {
                repository.observeRoomPlayers(roomId).collect { playerDocs ->
                    val currentSeq = hostMatchSeq.get()
                    val lobbyReturnToken = returnToLobbyVoteToken(currentSeq)
                    var lobbyRequesterName: String? = null
                    latestHumanPlayers.clear()
                    playerDocs.forEach { p ->
                        latestHumanPlayers[p.userId] = p
                        if (currentSeq > 0L &&
                            p.rematchVote == lobbyReturnToken &&
                            returnToLobbyHandledForMatchSeq != currentSeq &&
                            _activeRoom.value?.status != "OPEN"
                        ) {
                            lobbyRequesterName = p.name
                        } else if (p.rematchVote.isNotBlank() &&
                            _rematchState.value != null &&
                            rematchOptions.any { it.id == p.rematchVote }
                        ) {
                            rematchVotes[p.userId] = p.rematchVote
                        }
                    }
                    synchronized(botPlayers) {
                        while (latestHumanPlayers.size + botPlayers.size > maxRoomPlayers && botPlayers.isNotEmpty()) {
                            botPlayers.removeAt(botPlayers.lastIndex)
                        }
                    }
                    if (lobbyRequesterName != null && returnToLobbyHandledForMatchSeq != currentSeq) {
                        returnToLobbyHandledForMatchSeq = currentSeq
                        val requester = lobbyRequesterName ?: "A player"
                        returnToLobby()
                        scope.launch {
                            repository.sendRoomChat(
                                roomId = roomId,
                                senderName = "Race Control",
                                text = "$requester returned the group to the lobby.",
                                systemMessage = true
                            )
                        }
                        return@collect
                    }
                    syncHostLobbyToCloud()
                    if (_rematchState.value != null) {
                        publishHostRematchState()
                    }
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Player list sync interrupted.")?.let {
                    _errorBanner.value = it
                }
            }
        }

        inputsObserverJob?.cancel()
        inputsObserverJob = scope.launch {
            try {
                repository.observeRoomInputs(roomId).collect { inputDocs ->
                    val engine = physicsEngine ?: return@collect
                    for (doc in inputDocs) {
                        if (doc.userId == currentUserId) continue
                        val decision = evaluateIncomingHostInput(
                            doc = doc,
                            appliedInputSeqByUser = appliedInputSeqByUser,
                            appliedPowerSeqByUser = appliedPowerSeqByUser,
                            appliedMineSeqByUser = appliedMineSeqByUser
                        )
                        val ctrl = decision.controlInput
                        if (decision.accepted && ctrl != null) {
                            engine.submitInput(doc.userId, ctrl)
                        }
                    }
                }
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Remote player input sync lost.")?.let {
                    _errorBanner.value = it
                }
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
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
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
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
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
            } catch (e: Exception) {
                rethrowIfScopeCancellation(e)
                com.example.data.formatUserFacingOnlineErrorOrNull(e, "Connection to online race host interrupted.")?.let {
                    _errorBanner.value = it
                }
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
                    GameMode.DRIFT -> (parsedLobby.activeCustomTrack
                        ?: com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks().first()
                    ).let { TrackCatalog.buildCustomTrack(it) }
                    else -> parsedLobby.activeCustomTrack?.let {
                        TrackCatalog.buildCustomTrack(it)
                    } ?: TrackCatalog.getTrack(parsedLobby.trackId)
                }
            }
        }

        if (doc.status == "OPEN") {
            _rematchState.value = null
            pendingLocalRematchVote = null
            if (inRace || clientAwaitingLobbyReturn.compareAndSet(true, false)) {
                inRace = false
                clientFinishEmitted.set(false)
                clientInputPushJob?.cancel()
                _usingRelayFallback.value = false
                relaySocket.disconnect("Returned to lobby")
                scope.launch {
                    repository.updatePlayerFields(doc.id, mapOf("rematchVote" to ""))
                }
                _navEvents.tryEmit(RaceNavEvent.BackToLobby)
                return
            }
        } else if (doc.rematchJson.isNotBlank() && (doc.status == "FINISHED" || !inRace)) {
            val parsedRematch = runCatching { rematchAdapter.fromJson(doc.rematchJson) }.getOrNull()
            if (parsedRematch != null) {
                applyClientRematchState(parsedRematch)
            }
        } else if (doc.rematchJson.isBlank() && _rematchState.value != null) {
            _rematchState.value = null
        }

        if (doc.status == "RACING" && doc.raceStartJson.isNotBlank()) {
            val startPayload = runCatching { raceStartAdapter.fromJson(doc.raceStartJson) }.getOrNull()
            if (startPayload != null && startPayload.matchSeq > clientAppliedMatchSeq.get()) {
                clientAppliedMatchSeq.set(startPayload.matchSeq)
                clientAppliedTelemetrySeq.set(0L)
                clientFinishEmitted.set(false)
                clientAwaitingLobbyReturn.set(true)
                pendingLocalRematchVote = null
                _rematchState.value = null
                currentRules = startPayload.rules
                scope.launch {
                    repository.updatePlayerFields(doc.id, mapOf("rematchVote" to ""))
                }

                val snapMode = startPayload.initialSnapshot?.gameMode ?: startPayload.rules.gameMode
                val isStunt = snapMode == GameMode.STUNTS.name
                val isArena = !isStunt && snapMode == GameMode.ARENA.name
                val isDrift = !isStunt && !isArena && snapMode == GameMode.DRIFT.name
                val baseTrack = when {
                    isStunt -> TrackCatalog.getStuntTrack(
                        startPayload.stuntVariant ?: startPayload.initialSnapshot?.stuntState?.variant
                    )
                    isArena -> TrackCatalog.getArenaTrack(
                        startPayload.arenaShape ?: startPayload.initialSnapshot?.arenaShape
                    )
                    isDrift -> (startPayload.customTrack
                        ?: com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks().first()
                    ).let { TrackCatalog.buildCustomTrack(it) }
                    else -> startPayload.customTrack?.let { TrackCatalog.buildCustomTrack(it) }
                        ?: TrackCatalog.getTrack(startPayload.trackId)
                }
                _activeTrack.value = if (!isStunt && !isDrift && startPayload.ramps.isNotEmpty()) {
                    baseTrack.copy(ramps = startPayload.ramps)
                } else {
                    baseTrack
                }
                startPayload.initialSnapshot?.let { _raceSnapshot.value = it }
                synchronized(this) {
                    lastPushedThrottle = Float.NaN
                    lastPushedSteer = Float.NaN
                    lastInputPushMs = 0L
                    inputDirty = true
                    urgentInputPending = false
                }
                _syncPingMs.value = null
                _usingRelayFallback.value = false
                inRace = true
                relaySocket.connect(doc.id)
                startClientInputLoop(doc.id)
                _navEvents.tryEmit(RaceNavEvent.StartRace)
            }
        }

        var decodedFinishedNow = false
        if (doc.telemetryLine.startsWith("S|") && doc.telemetrySeq > clientAppliedTelemetrySeq.get()) {
            val roster = _lobbyState.value?.players.orEmpty()
            val decoded = RaceTelemetryCodec.decodeSnapshot(
                text = doc.telemetryLine,
                roster = roster,
                previousSnapshot = _raceSnapshot.value,
                activeTrack = _activeTrack.value
            )
            if (decoded != null) {
                val applied = applyIncomingClientSnapshot(
                    seq = doc.telemetrySeq,
                    snap = decoded.snapshot,
                    forceFinishedFromRoomDoc = (doc.status == "FINISHED")
                )
                if (applied && (decoded.snapshot.phase == RacePhase.FINISHED.name || doc.status == "FINISHED")) {
                    decodedFinishedNow = true
                }
            }
        }

        if (!decodedFinishedNow && doc.status == "FINISHED" && (inRace || !clientFinishEmitted.get())) {
            val fallbackSnap = if (doc.telemetryLine.startsWith("S|")) {
                RaceTelemetryCodec.decodeSnapshot(
                    text = doc.telemetryLine,
                    roster = _lobbyState.value?.players.orEmpty(),
                    previousSnapshot = _raceSnapshot.value,
                    activeTrack = _activeTrack.value
                )?.snapshot
            } else {
                null
            } ?: _raceSnapshot.value
            fallbackSnap?.let {
                _raceSnapshot.value = it
                _lastResults.value = it
            }
            inRace = false
            clientAwaitingLobbyReturn.set(true)
            clientInputPushJob?.cancel()
            _usingRelayFallback.value = false
            relaySocket.disconnect("Race finished")
            if (clientFinishEmitted.compareAndSet(false, true)) {
                _navEvents.tryEmit(RaceNavEvent.ShowResults)
            }
        }
    }

    private fun startClientInputLoop(roomId: String) {
        clientInputPushJob?.cancel()
        clientInputPushJob = scope.launch(Dispatchers.IO) {
            var lastSentPowerSeq = localPowerUpSeq.get()
            var lastSentMineSeq = localMineDropSeq.get()
            var lastWsPingSentMs = 0L
            val loopStartMs = System.currentTimeMillis()
            while (isActive && _role.value == SessionRole.OnlineClient && inRace) {
                val nowMs = System.currentTimeMillis()
                val wsConnected = relaySocket.isConnected.value
                val (inp, pSeq, mSeq, isUrgent, isDirty) = synchronized(this@OnlineRaceSessionManager) {
                    SnapshotInputTuple(
                        input = localInput,
                        powerSeq = localPowerUpSeq.get(),
                        mineSeq = localMineDropSeq.get(),
                        urgent = urgentInputPending,
                        dirty = inputDirty
                    )
                }
                val actionChanged = pSeq != lastSentPowerSeq || mSeq != lastSentMineSeq
                val deltaChanged = lastPushedThrottle.isNaN() ||
                    lastPushedSteer.isNaN() ||
                    abs(inp.throttle - lastPushedThrottle) > 0.04f ||
                    abs(inp.steer - lastPushedSteer) > 0.03f

                if (wsConnected) {
                    _usingRelayFallback.value = false
                    if ((nowMs - lastWsPingSentMs) >= 1000L) {
                        lastWsPingSentMs = nowMs
                        relaySocket.sendText(RaceTelemetryCodec.encodeRelayPing(currentUserId, System.nanoTime()))
                    }

                    val wsKeepaliveDue = (nowMs - lastInputPushMs) >= 120L
                    val wsIntervalMet = (nowMs - lastInputPushMs) >= 16L
                    val shouldSendWs = isUrgent || actionChanged || ((isDirty || deltaChanged) && wsIntervalMet) || wsKeepaliveDue

                    if (shouldSendWs) {
                        val outgoingInput = inp.copy(
                            usePowerUp = pSeq > lastSentPowerSeq,
                            dropMine = mSeq > lastSentMineSeq
                        )
                        val seq = localInputSeq.incrementAndGet()
                        val line = RaceTelemetryCodec.encodeInputLine(
                            playerId = currentUserId,
                            seq = seq,
                            input = outgoingInput,
                            ackedHostUdpSeq = clientAppliedTelemetrySeq.get(),
                            powerUpSeq = pSeq,
                            mineDropSeq = mSeq
                        )
                        if (relaySocket.sendText(line)) {
                            synchronized(this@OnlineRaceSessionManager) {
                                inputDirty = false
                                urgentInputPending = false
                            }
                            lastPushedThrottle = inp.throttle
                            lastPushedSteer = inp.steer
                            lastSentPowerSeq = pSeq
                            lastSentMineSeq = mSeq
                            lastInputPushMs = nowMs
                        } else {
                            // WebSocket dropped mid-tick; fall back immediately on next loop iteration
                            delay(10L)
                        }
                    } else {
                        val waitMs = if (isDirty || deltaChanged) {
                            (16L - (nowMs - lastInputPushMs)).coerceIn(4L, 16L)
                        } else {
                            (120L - (nowMs - lastInputPushMs)).coerceIn(10L, 120L)
                        }
                        withTimeoutOrNull(waitMs) {
                            inputWakeSignal.receive()
                        }
                    }
                } else {
                    if ((nowMs - loopStartMs) > 900L) {
                        _usingRelayFallback.value = true
                    }
                    val keepaliveDue = (nowMs - lastInputPushMs) >= 220L
                    val shouldSendNow = isUrgent || actionChanged || ((isDirty || deltaChanged) && (nowMs - lastInputPushMs) >= 60L) || keepaliveDue

                    if (shouldSendNow) {
                        val seq = localInputSeq.incrementAndGet()
                        val startNs = System.nanoTime()
                        val res = repository.pushDriverInput(
                            roomId = roomId,
                            seq = seq,
                            throttle = inp.throttle,
                            steer = inp.steer,
                            powerUpSeq = pSeq,
                            mineDropSeq = mSeq
                        )
                        if (res.isSuccess) {
                            synchronized(this@OnlineRaceSessionManager) {
                                inputDirty = false
                                urgentInputPending = false
                            }
                            lastPushedThrottle = inp.throttle
                            lastPushedSteer = inp.steer
                            lastSentPowerSeq = pSeq
                            lastSentMineSeq = mSeq
                            lastInputPushMs = nowMs
                            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                            recordWriteLatencySample(rttMs)
                        } else {
                            delay(60L)
                        }
                    } else {
                        val waitMs = if (isDirty || deltaChanged) {
                            (60L - (nowMs - lastInputPushMs)).coerceIn(10L, 60L)
                        } else {
                            (220L - (nowMs - lastInputPushMs)).coerceIn(20L, 220L)
                        }
                        withTimeoutOrNull(waitMs) {
                            inputWakeSignal.receive()
                        }
                    }
                }
            }
        }
    }

    private data class SnapshotInputTuple(
        val input: ControlInput,
        val powerSeq: Long,
        val mineSeq: Long,
        val urgent: Boolean,
        val dirty: Boolean
    )

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
                    equippedUpgradesMask = p.equippedUpgradesMask,
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
            "gameMode" to com.example.drift.DriftFeature.toFirestoreWireGameMode(currentRules.gameModeEnum),
            "trackId" to payload.trackId,
            "playerCount" to totalCount,
            "maxPlayers" to maxRoomPlayers.toLong(),
            "memberUids" to humanUids,
            "lobbyJson" to lobbyJson
        )
        fields.putAll(extraFields)
        scope.launch {
            hostRoomWriteMutex.withLock {
                repository.updateHostRoomFields(rid, fields)
            }
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
                    "accentColorArgb" to profile.accentColorArgb,
                    "equippedUpgradesMask" to profile.equippedUpgradesMask
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
        val keepCustom = if (rules.gameModeEnum == GameMode.RACE || rules.gameModeEnum == GameMode.DRIFT) {
            currentCustomTrack
        } else {
            null
        }
        configureInitialMode(rules, keepCustom)
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
        appliedInputSeqByUser.clear()
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
        val isDriftMode = concreteMode == GameMode.DRIFT
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
        val track = baseTrack
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
            nextHybridModeName = upcomingHybridMode?.name,
            isMultiplayerSession = true
        )
        physicsEngine = engine

        val initialSnap = engine.step(0.016f)
        _raceSnapshot.value = initialSnap
        _syncPingMs.value = null
        _usingRelayFallback.value = false
        raceStartedAtNs = System.nanoTime()
        lastClientRelayRxNsByUser.clear()
        relaySocket.connect(rid)

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
            var lastNs = System.nanoTime()
            var lastPublishNs = 0L
            var lastHostPingNs = 0L
            var tickCounter = 0L
            var minPublishIntervalNs = 65_000_000L
            val publishBusy = AtomicBoolean(false)

            while (isActive && inRace) {
                delay(16L)
                tickCounter++
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

                val remoteHumanIds = latestHumanPlayers.keys.filter { it != currentUserId }.toSet()
                val wsConnected = relaySocket.isConnected.value

                if (snap.phase == RacePhase.FINISHED.name) {
                    awardOnlineSessionPoints(snap)
                    _lastResults.value = snap
                    inRace = false
                    _usingRelayFallback.value = false
                    val initialRematch = prepareHostRematchState()
                    val initialRematchJson = rematchAdapter.toJson(initialRematch).take(7800)
                    val finalSeq = hostTelemetrySeq.incrementAndGet()
                    val fullFinalLine = String(RaceTelemetryCodec.encodeSnapshot(finalSeq, snap), Charsets.UTF_8)
                    if (wsConnected) {
                        relaySocket.sendText(fullFinalLine)
                        relaySocket.sendText("R|$initialRematchJson")
                    }
                    val finalLineForFirestore = fullFinalLine.take(15_800)
                    val updatedLobbyJson = lobbyAdapter.toJson(buildHostLobbyPayload()).take(29_500)
                    scope.launch(Dispatchers.IO) {
                        try {
                            hostRoomWriteMutex.withLock {
                                val payload = mapOf(
                                    "status" to "FINISHED",
                                    "lobbyJson" to updatedLobbyJson,
                                    "telemetrySeq" to finalSeq,
                                    "telemetryLine" to finalLineForFirestore,
                                    "rematchJson" to initialRematchJson
                                )
                                var attempt = 0
                                while (attempt < 3) {
                                    val res = repository.updateHostRoomFields(rid, payload)
                                    if (res.isSuccess) break
                                    attempt++
                                    delay(250L)
                                }
                            }
                        } finally {
                            // Allow final WebSocket frames to flush over TCP before cleanly closing the room socket
                            delay(250L)
                            if (!inRace) {
                                relaySocket.disconnect("Race finished")
                            }
                        }
                    }
                    _navEvents.tryEmit(RaceNavEvent.ShowResults)
                    startHostRematchCountdown(alreadyInitialized = true)
                    break
                }

                if (wsConnected && remoteHumanIds.isNotEmpty() && (nowNs - lastHostPingNs) >= 1_100_000_000L) {
                    lastHostPingNs = nowNs
                    relaySocket.sendText(RaceTelemetryCodec.encodeRelayPing(currentUserId, nowNs))
                }

                val mirrorToFirestore = shouldHostMirrorToFirestore(
                    hostRelayConnected = wsConnected,
                    remoteHumanIds = remoteHumanIds,
                    lastClientRelayRxNsByUser = lastClientRelayRxNsByUser,
                    nowNs = nowNs,
                    raceStartedAtNs = raceStartedAtNs
                )
                _usingRelayFallback.value = remoteHumanIds.isNotEmpty() && !wsConnected &&
                    (nowNs - raceStartedAtNs) > 900_000_000L

                val sendWsTick = wsConnected && (tickCounter % 2L == 0L)
                val sendFirestoreTick = mirrorToFirestore &&
                    (nowNs - lastPublishNs) >= minPublishIntervalNs &&
                    publishBusy.compareAndSet(false, true)

                if (sendWsTick || sendFirestoreTick) {
                    val seq = hostTelemetrySeq.incrementAndGet()
                    val fullLine = String(RaceTelemetryCodec.encodeSnapshot(seq, snap), Charsets.UTF_8)
                    if (sendWsTick) {
                        relaySocket.sendText(fullLine)
                    }
                    if (sendFirestoreTick) {
                        lastPublishNs = nowNs
                        val firestoreLine = fullLine.take(15_800)
                        scope.launch(Dispatchers.IO) {
                            try {
                                hostRoomWriteMutex.withLock {
                                    val startNs = System.nanoTime()
                                    val res = repository.updateHostRoomFields(
                                        rid,
                                        mapOf(
                                            "telemetrySeq" to seq,
                                            "telemetryLine" to firestoreLine
                                        )
                                    )
                                    if (res.isSuccess && !wsConnected) {
                                        val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                                        recordWriteLatencySample(rttMs)
                                        minPublishIntervalNs = when {
                                            rttMs > 260L -> 110_000_000L
                                            rttMs > 160L -> 85_000_000L
                                            else -> 65_000_000L
                                        }
                                    }
                                }
                            } finally {
                                publishBusy.set(false)
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateLocalControlInput(throttle: Float, steer: Float) {
        val isOnlineClientInRace: Boolean
        synchronized(this) {
            val prev = localInput
            localInput = prev.copy(throttle = throttle, steer = steer)
            isOnlineClientInRace = (_role.value == SessionRole.OnlineClient && inRace)
            if (isOnlineClientInRace) {
                val baseT = if (lastPushedThrottle.isNaN()) prev.throttle else lastPushedThrottle
                val baseS = if (lastPushedSteer.isNaN()) prev.steer else lastPushedSteer
                val throttleFlipped = (throttle * baseT <= 0f) && abs(throttle - baseT) > 0.12f
                val steerFlipped = (steer == 0f && abs(baseS) > 0.02f) || (steer * baseS < 0f)
                val movedEnough = abs(throttle - baseT) > 0.03f || abs(steer - baseS) > 0.02f
                if (throttleFlipped || steerFlipped) {
                    urgentInputPending = true
                    inputDirty = true
                } else if (movedEnough || abs(throttle - prev.throttle) > 0.02f || abs(steer - prev.steer) > 0.02f) {
                    inputDirty = true
                }
            }
        }
        if (isOnlineClientInRace) {
            inputWakeSignal.trySend(Unit)
        }
    }

    fun triggerLocalPowerUp() {
        val isOnlineClientInRace: Boolean
        synchronized(this) {
            pendingLocalPowerUp = true
            isOnlineClientInRace = (_role.value == SessionRole.OnlineClient && inRace)
            if (isOnlineClientInRace) {
                localPowerUpSeq.incrementAndGet()
                urgentInputPending = true
                inputDirty = true
            }
        }
        if (isOnlineClientInRace) {
            inputWakeSignal.trySend(Unit)
        }
    }

    fun triggerLocalMineDrop() {
        val isOnlineClientInRace: Boolean
        synchronized(this) {
            pendingLocalMineDrop = true
            isOnlineClientInRace = (_role.value == SessionRole.OnlineClient && inRace)
            if (isOnlineClientInRace) {
                localMineDropSeq.incrementAndGet()
                urgentInputPending = true
                inputDirty = true
            }
        }
        if (isOnlineClientInRace) {
            inputWakeSignal.trySend(Unit)
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
        pendingLocalRematchVote = null
        _rematchState.value = null
    }

    private fun prepareHostRematchState(): RematchCountdownState {
        rematchVotes.clear()
        val voteTargetMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            (nextPlannedHybridMode ?: WifiP2pRaceManager.resolveHybridStageMode(currentRules, hybridStageNumber, activeConcreteMode)).also {
                nextPlannedHybridMode = it
            }
        } else {
            currentRules.gameModeEnum
        }
        val opts = WifiP2pRaceManager.buildRematchVoteOptions(
            gameMode = voteTargetMode,
            currentTrackId = currentTrackId,
            currentArenaShape = currentArenaShape,
            currentStuntVariant = currentStuntVariant,
            hasActiveCustomTrack = currentCustomTrack != null,
            activeCustomTrack = currentCustomTrack
        )
        rematchOptions = opts
        rematchSecondsLeft = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS
        val votesMap = rematchVotes.toMap()
        val state = RematchCountdownState(
            secondsLeft = rematchSecondsLeft,
            totalSeconds = WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS,
            options = opts,
            votesByPlayerId = votesMap,
            leadingOptionId = WifiP2pRaceManager.computeLeadingRematchOption(opts, votesMap)
        )
        _rematchState.value = state
        return state
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
            hostRoomWriteMutex.withLock {
                repository.updateHostRoomFields(rid, mapOf("rematchJson" to json))
            }
        }
    }

    private fun startHostRematchCountdown(alreadyInitialized: Boolean = false) {
        if (_role.value != SessionRole.OnlineHost) return
        rematchJob?.cancel()
        if (!alreadyInitialized) {
            prepareHostRematchState()
            publishHostRematchState()
        }

        rematchJob = scope.launch(Dispatchers.Default) {
            for (sec in (WifiP2pRaceManager.REMATCH_COUNTDOWN_SECONDS - 1) downTo 1) {
                delay(1000L)
                if (!isActive || inRace || _role.value != SessionRole.OnlineHost) return@launch
                rematchSecondsLeft = sec
                publishHostRematchState()
            }
            delay(1000L)
            if (!isActive || inRace || _role.value != SessionRole.OnlineHost) return@launch
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
                pendingLocalRematchVote = optionId
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
            GameMode.DRIFT -> {
                val chosenDrift = if (winnerId == WifiP2pRaceManager.REMATCH_OPTION_RANDOM) {
                    if (Random.nextFloat() < 0.5f) {
                        com.example.drift.DriftTrackCatalog.generateRandomDriftTrack(seed = System.currentTimeMillis())
                    } else {
                        val defaults = com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks()
                        defaults.filter { it.id != currentCustomTrack?.id }.randomOrNull() ?: defaults.first()
                    }
                } else {
                    com.example.drift.DriftTrackCatalog.resolveCustomTrackByKey(winnerId)
                        ?: com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks().first()
                }
                currentCustomTrack = chosenDrift
                currentTrackId = runCatching { TrackId.valueOf(chosenDrift.themeId) }
                    .getOrDefault(TrackId.WAREHOUSE_DRIFT)
                _activeTrack.value = TrackCatalog.buildCustomTrack(chosenDrift)
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
                } else if (currentCustomTrack != null && winnerId == currentCustomTrack?.id) {
                    val replayCustom = currentCustomTrack!!
                    currentTrackId = runCatching { TrackId.valueOf(replayCustom.themeId) }
                        .getOrDefault(TrackId.NEON_ROOFTOP)
                    _activeTrack.value = TrackCatalog.buildCustomTrack(replayCustom)
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
        val rid = activeRoomId ?: return
        when (_role.value) {
            SessionRole.OnlineHost -> {
                returnToLobbyHandledForMatchSeq = hostMatchSeq.get()
                cancelRematchCountdown()
                physicsJob?.cancel()
                physicsJob = null
                physicsEngine = null
                inRace = false
                _usingRelayFallback.value = false
                relaySocket.disconnect("Returned to lobby")
                syncHostLobbyToCloud(
                    extraFields = mapOf(
                        "status" to "OPEN",
                        "rematchJson" to ""
                    )
                )
                _navEvents.tryEmit(RaceNavEvent.BackToLobby)
            }
            SessionRole.OnlineClient -> {
                val seq = clientAppliedMatchSeq.get()
                pendingLocalRematchVote = null
                _rematchState.value = null
                scope.launch {
                    repository.updatePlayerFields(
                        roomId = rid,
                        fields = mapOf("rematchVote" to returnToLobbyVoteToken(seq).take(64))
                    )
                }
            }
            else -> Unit
        }
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
        clientAwaitingLobbyReturn.set(false)
        pendingLocalRematchVote = null
        returnToLobbyHandledForMatchSeq = -1L
        _usingRelayFallback.value = false
        relaySocket.disconnect("Left room")
        lastClientRelayRxNsByUser.clear()
        raceStartedAtNs = 0L
        activeRoomId = null
        _role.value = SessionRole.None
        _syncPingMs.value = null
        _activeRoom.value = null
        _lobbyState.value = null
        _raceSnapshot.value = null
        _activeTrack.value = null
        appliedInputSeqByUser.clear()
        appliedPowerSeqByUser.clear()
        appliedMineSeqByUser.clear()
        localInputSeq.set(0L)
        localPowerUpSeq.set(0L)
        localMineDropSeq.set(0L)
        synchronized(this) {
            localInput = ControlInput()
            pendingLocalPowerUp = false
            pendingLocalMineDrop = false
            inputDirty = false
            urgentInputPending = false
            lastPushedThrottle = Float.NaN
            lastPushedSteer = Float.NaN
            lastInputPushMs = 0L
        }
        latestHumanPlayers.clear()
        synchronized(botPlayers) { botPlayers.clear() }
        if (rid != null) {
            repository.leaveOnlineRoom(roomId = rid, isHost = wasHost)
        }
    }

    fun disconnectRelay() {
        relaySocket.disconnect("Session closed")
    }

    fun leaveRoom() {
        relaySocket.disconnect("Left room")
        scope.launch {
            leaveCurrentRoomInternal()
        }
    }
}
