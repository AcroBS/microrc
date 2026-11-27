package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.DailyChallengeCatalog
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ActiveGhostCarState
import com.example.model.ArenaShape
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.CustomTrackData
import com.example.model.DiscoveredLobby
import com.example.model.GameMode
import com.example.model.GhostCodec
import com.example.model.GhostFrame
import com.example.model.GhostRunPlayback
import com.example.model.LeaderboardBoardKey
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
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
import kotlinx.coroutines.selects.select
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random

sealed interface SessionRole {
    data object None : SessionRole
    data object Solo : SessionRole
    data object Host : SessionRole
    data object Client : SessionRole
    data object OnlineHost : SessionRole
    data object OnlineClient : SessionRole

    val isHostLike: Boolean
        get() = this == Host || this == Solo || this == OnlineHost

    val isOnline: Boolean
        get() = this == OnlineHost || this == OnlineClient
}

data class CompletedSoloRunSummary(
    val boardKey: String,
    val gameMode: GameMode,
    val trackKey: String,
    val totalLaps: Int,
    val finishTimeMs: Long,
    val bestLapMs: Long,
    val bestScore: Int,
    val pureSoloNoBots: Boolean,
    val encodedGhostFrames: String
)

sealed interface RaceNavEvent {
    data object EnterLobby : RaceNavEvent
    data object StartRace : RaceNavEvent
    data object ShowResults : RaceNavEvent
    data object BackToLobby : RaceNavEvent
    data class Disconnected(val reason: String) : RaceNavEvent
}

class WifiP2pRaceManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val DEFAULT_TCP_PORT = 45456
        const val REMATCH_COUNTDOWN_SECONDS = 10
        const val REMATCH_OPTION_RANDOM = "RANDOM"

        fun resolveHybridStageMode(
            rules: RaceRulesSettings,
            stageIndexZeroBased: Int,
            previousMode: GameMode? = null,
            random: Random = Random.Default
        ): GameMode {
            val enabled = rules.enabledHybridModes()
            if (enabled.size == 1) return enabled.first()
            return if (rules.hybridRandomOrder) {
                val filtered = if (previousMode != null) enabled.filter { it != previousMode } else enabled
                val pool = filtered.ifEmpty { enabled }
                pool[random.nextInt(pool.size)]
            } else {
                enabled[stageIndexZeroBased.mod(enabled.size)]
            }
        }

        fun buildRematchVoteOptions(
            gameMode: GameMode,
            currentTrackId: TrackId,
            currentArenaShape: ArenaShape,
            currentStuntVariant: StuntVariant = StuntVariant.LONG_JUMP,
            hasActiveCustomTrack: Boolean = false,
            activeCustomTrack: CustomTrackData? = null,
            random: Random = Random.Default
        ): List<RematchVoteOption> {
            return when (gameMode) {
                GameMode.ARENA -> {
                    val candidates = ArenaShape.entries
                        .filter { it != currentArenaShape }
                        .shuffled(random)
                        .take(2)
                        .map { shape ->
                            RematchVoteOption(
                                id = shape.name,
                                displayName = shape.displayName,
                                subtitle = "${shape.shapeLabel} Arena",
                                isRandom = false
                            )
                        }
                    candidates + RematchVoteOption(
                        id = REMATCH_OPTION_RANDOM,
                        displayName = "Random Arena",
                        subtitle = "Surprise battleground",
                        isRandom = true
                    )
                }
                GameMode.STUNTS -> {
                    val candidates = StuntVariant.entries
                        .filter { it != currentStuntVariant }
                        .shuffled(random)
                        .take(2)
                        .map { st ->
                            RematchVoteOption(
                                id = st.name,
                                displayName = st.displayName,
                                subtitle = "Stunt • ${st.shortName}",
                                isRandom = false
                            )
                        }
                    candidates + RematchVoteOption(
                        id = REMATCH_OPTION_RANDOM,
                        displayName = "Random Stunt",
                        subtitle = "Surprise stunt gauntlet",
                        isRandom = true
                    )
                }
                GameMode.DRIFT -> {
                    val driftTracks = com.example.drift.DriftTrackCatalog.defaultDriftCustomTracks()
                    val candidates = driftTracks
                        .shuffled(random)
                        .take(2)
                        .map { dt ->
                            RematchVoteOption(
                                id = dt.id,
                                displayName = dt.name,
                                subtitle = "Drift • Time Trial (1 Nitro/Lap)",
                                isRandom = false
                            )
                        }
                    candidates + RematchVoteOption(
                        id = REMATCH_OPTION_RANDOM,
                        displayName = "Random Drift Track",
                        subtitle = "Procedural obstacle drift circuit",
                        isRandom = true
                    )
                }
                else -> {
                    if (activeCustomTrack != null) {
                        val replayOption = RematchVoteOption(
                            id = activeCustomTrack.id.take(64),
                            displayName = activeCustomTrack.name.take(24),
                            subtitle = "Custom Circuit • Race Again",
                            isRandom = false
                        )
                        val builtInPick = TrackId.entries
                            .shuffled(random)
                            .first()
                            .let { track ->
                                RematchVoteOption(
                                    id = track.name,
                                    displayName = track.displayName,
                                    subtitle = "${track.surfaceLabel} • ${track.difficulty}",
                                    isRandom = false
                                )
                            }
                        listOf(
                            replayOption,
                            builtInPick,
                            RematchVoteOption(
                                id = REMATCH_OPTION_RANDOM,
                                displayName = "Random Circuit",
                                subtitle = "Mystery circuit or generated layout",
                                isRandom = true
                            )
                        )
                    } else {
                        val pool = if (hasActiveCustomTrack) {
                            TrackId.entries.toList()
                        } else {
                            TrackId.entries.filter { it != currentTrackId }
                        }
                        val candidates = pool
                            .shuffled(random)
                            .take(2)
                            .map { track ->
                                RematchVoteOption(
                                    id = track.name,
                                    displayName = track.displayName,
                                    subtitle = "${track.surfaceLabel} • ${track.difficulty}",
                                    isRandom = false
                                )
                            }
                        candidates + RematchVoteOption(
                            id = REMATCH_OPTION_RANDOM,
                            displayName = "Random Circuit",
                            subtitle = "Mystery circuit or generated layout",
                            isRandom = true
                        )
                    }
                }
            }
        }

        fun computeLeadingRematchOption(
            options: List<RematchVoteOption>,
            votesByPlayerId: Map<String, String>
        ): String {
            val validIds = options.map { it.id }.toSet()
            val counts = votesByPlayerId.values
                .filter { it in validIds }
                .groupingBy { it }
                .eachCount()
            if (counts.isEmpty()) return REMATCH_OPTION_RANDOM
            val maxCount = counts.values.maxOrNull() ?: return REMATCH_OPTION_RANDOM
            val tied = options.map { it.id }.filter { counts[it] == maxCount }
            return if (REMATCH_OPTION_RANDOM in tied) {
                REMATCH_OPTION_RANDOM
            } else {
                tied.firstOrNull() ?: REMATCH_OPTION_RANDOM
            }
        }

        fun resolveRematchWinner(
            options: List<RematchVoteOption>,
            votesByPlayerId: Map<String, String>,
            random: Random = Random.Default
        ): String {
            val validIds = options.map { it.id }.toSet()
            val counts = votesByPlayerId.values
                .filter { it in validIds }
                .groupingBy { it }
                .eachCount()
            if (counts.isEmpty()) return REMATCH_OPTION_RANDOM
            val maxCount = counts.values.maxOrNull() ?: return REMATCH_OPTION_RANDOM
            val tied = options.map { it.id }.filter { counts[it] == maxCount }
            if (tied.isEmpty()) return REMATCH_OPTION_RANDOM
            return tied[random.nextInt(tied.size)]
        }
    }

    val discovery = LocalNetworkDiscovery(context)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val msgAdapter = moshi.adapter(NetworkMessage::class.java)

    private val _role = MutableStateFlow<SessionRole>(SessionRole.None)
    val role: StateFlow<SessionRole> = _role.asStateFlow()

    private val _lobbyState = MutableStateFlow<LobbyStatePayload?>(null)
    val lobbyState: StateFlow<LobbyStatePayload?> = _lobbyState.asStateFlow()

    private val _raceSnapshot = MutableStateFlow<RaceSnapshot?>(null)
    val raceSnapshot: StateFlow<RaceSnapshot?> = _raceSnapshot.asStateFlow()

    private val _lastResults = MutableStateFlow<RaceSnapshot?>(null)
    val lastResults: StateFlow<RaceSnapshot?> = _lastResults.asStateFlow()

    private val _activeTrack = MutableStateFlow<TrackDefinition?>(null)
    val activeTrack: StateFlow<TrackDefinition?> = _activeTrack.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _rematchState = MutableStateFlow<RematchCountdownState?>(null)
    val rematchState: StateFlow<RematchCountdownState?> = _rematchState.asStateFlow()

    private val _activeChallengeGhost = MutableStateFlow<GhostRunPlayback?>(null)
    val activeChallengeGhost: StateFlow<GhostRunPlayback?> = _activeChallengeGhost.asStateFlow()

    private val _activeGhostCar = MutableStateFlow<ActiveGhostCarState?>(null)
    val activeGhostCar: StateFlow<ActiveGhostCarState?> = _activeGhostCar.asStateFlow()

    private val _isDailyChallenge = MutableStateFlow(false)
    val isDailyChallenge: StateFlow<Boolean> = _isDailyChallenge.asStateFlow()

    private var dailySeedForSession: Long? = null

    private val _completedSoloRuns = MutableSharedFlow<CompletedSoloRunSummary>(extraBufferCapacity = 4)
    val completedSoloRuns: SharedFlow<CompletedSoloRunSummary> = _completedSoloRuns.asSharedFlow()

    private val _navEvents = MutableSharedFlow<RaceNavEvent>(extraBufferCapacity = 8)
    val navEvents: SharedFlow<RaceNavEvent> = _navEvents.asSharedFlow()

    private var wifiLock: WifiManager.WifiLock? = null

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var physicsJob: Job? = null

    private var hostUdpSocket: DatagramSocket? = null
    private var hostUdpReadJob: Job? = null
    private var hostUdpPort: Int = 0
    private val interfaceUdpSockets = ConcurrentHashMap<String, DatagramSocket>()
    private val interfaceUdpReaderJobs = ConcurrentHashMap<String, Job>()
    private val hostStateSeq = AtomicLong(0L)

    /** Test-only hook to simulate a Hotspot one-way UDP blackhole (Client->Host UDP works, Host->Client UDP dropped). */
    @Volatile var simulateHostToClientUdpBlackholeForTest: Boolean = false

    private var clientSocket: Socket? = null
    private var clientReadJob: Job? = null
    private var clientWriteJob: Job? = null
    private var clientSendChannel: Channel<String>? = null
    private var clientFastInputChannel: Channel<String>? = null

    private var clientUdpSocket: DatagramSocket? = null
    private var clientUdpReadJob: Job? = null
    private var clientUdpSendJob: Job? = null
    private var clientHostAddr: InetAddress? = null
    private var clientHostUdpPort: Int = 0
    private val clientInputSeq = AtomicLong(0L)
    private val clientSnapshotLock = Any()
    @Volatile private var lastAppliedStateSeq = -1L
    @Volatile private var lastReceivedUdpStateSeq = -1L
    private val lastHostUdpRxNs = AtomicLong(0L)
    private val clientFinishEmitted = AtomicBoolean(false)

    private var localPlayerId: String = ""
    private var localInput = ControlInput()
    private var pendingLocalPowerUp = false
    private var pendingLocalMineDrop = false
    private var lastSentThrottle = 0f
    private var lastSentSteer = 0f
    private var lastInputSendNs = 0L

    private class ConnectedClient(
        val playerId: String,
        val socket: Socket,
        val reliableChannel: Channel<String> = Channel(capacity = Channel.UNLIMITED),
        val snapshotChannel: Channel<String> = Channel(capacity = Channel.CONFLATED)
    ) {
        var writerJob: Job? = null
        @Volatile var udpAddress: SocketAddress? = null
        @Volatile var udpSendSocket: DatagramSocket? = null
        @Volatile var preferredLocalUdpPort: Int = 0
        @Volatile var lastInputSeq: Long = -1L
        @Volatile var lastClientUdpRxNs: Long = 0L
        @Volatile var lastBidirectionalUdpVerifiedNs: Long = 0L

        fun hasVerifiedBidirectionalUdp(nowNs: Long): Boolean {
            return udpAddress != null &&
                lastClientUdpRxNs > 0L &&
                lastBidirectionalUdpVerifiedNs > 0L &&
                (nowNs - lastClientUdpRxNs) <= 600_000_000L &&
                (nowNs - lastBidirectionalUdpVerifiedNs) <= 600_000_000L
        }
    }

    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()
    private val chatLog = mutableListOf<ChatMessage>()
    private val playersList = mutableListOf<LobbyPlayer>()
    private var currentTrackId: TrackId = TrackId.LIVING_ROOM_GP
    private var currentArenaShape: ArenaShape = ArenaShape.SQUARE
    private var currentStuntVariant: StuntVariant = StuntVariant.LONG_JUMP
    private var currentCustomTrack: CustomTrackData? = null
    private var currentRules: RaceRulesSettings = RaceRulesSettings()
    private var hybridStageNumber: Int = 1
    private var activeConcreteMode: GameMode = GameMode.RACE
    private var nextPlannedHybridMode: GameMode? = null
    private var currentTotalLaps: Int = 3
    private var inRace: Boolean = false
    private var tcpPort: Int = DEFAULT_TCP_PORT
    private var physicsEngine: RacePhysicsEngine? = null
    private var pointsAwardedForCurrentRace = false
    private var rematchJob: Job? = null
    private var rematchOptions: List<RematchVoteOption> = emptyList()
    private val rematchVotes = ConcurrentHashMap<String, String>()
    @Volatile private var rematchSecondsLeft: Int = REMATCH_COUNTDOWN_SECONDS

    private fun configureInitialSessionMode(rules: RaceRulesSettings, initialCustomTrack: CustomTrackData?) {
        hybridStageNumber = 1
        currentRules = rules
        currentTotalLaps = rules.totalLaps.coerceIn(1, 9)
        currentCustomTrack = initialCustomTrack

        when (rules.gameModeEnum) {
            GameMode.HYBRID -> {
                activeConcreteMode = resolveHybridStageMode(rules, 0, null)
                nextPlannedHybridMode = resolveHybridStageMode(rules, 1, activeConcreteMode)
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
                    currentTrackId = runCatching { TrackId.valueOf(initialCustomTrack.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
                    _activeTrack.value = TrackCatalog.buildCustomTrack(initialCustomTrack)
                } else {
                    _activeTrack.value = TrackCatalog.getTrack(currentTrackId)
                }
            }
        }
    }

    fun clearError() {
        _connectionError.value = null
    }

    @Suppress("DEPRECATION")
    private fun acquireLowLatencyWifiLock() {
        runCatching {
            if (wifiLock == null) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifi?.createWifiLock(mode, "MicroRCLowLatency")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseLowLatencyWifiLock() {
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        }
    }

    fun startSoloSession(
        profile: PlayerProfile,
        initialRules: RaceRulesSettings = RaceRulesSettings(),
        initialCustomTrack: CustomTrackData? = null,
        includeBots: Boolean = true,
        botCount: Int = if (includeBots) 3 else 0,
        challengeGhost: GhostRunPlayback? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        val sanitizedSoloRules = initialRules.copy(
            randomConditionsEnabled = false,
            hostRandomConditionsOverride = null
        )
        configureInitialSessionMode(sanitizedSoloRules, initialCustomTrack)
        _role.value = SessionRole.Solo
        _activeChallengeGhost.value = challengeGhost
        _activeGhostCar.value = null
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        val effectiveBotCount = if (includeBots) botCount.coerceIn(0, 3) else 0
        if (effectiveBotCount > 0) {
            val botNames = listOf("Volt-AI", "Apex-AI", "Turbo-AI")
            val botCars = listOf(RcCarType.APEX_FORMULA, RcCarType.DRIFT_PHANTOM, RcCarType.TUNDRA_MONSTER)
            for (idx in 0 until effectiveBotCount) {
                val preset = RcColorPresets[(idx + 1) % RcColorPresets.size]
                playersList.add(
                    LobbyPlayer(
                        id = "bot_$idx",
                        name = botNames[idx],
                        isHost = false,
                        isBot = true,
                        isReady = true,
                        carType = botCars[idx].name,
                        primaryColorArgb = preset.primaryArgb,
                        accentColorArgb = preset.accentArgb
                    )
                )
            }
            addSystemChat("Solo paddock ready ($effectiveBotCount AI rival${if (effectiveBotCount == 1) "" else "s"}). Customize track or toggle bots off for Time Trials!")
        } else {
            val ghostMsg = challengeGhost?.let {
                "Ghost Challenge loaded vs ${it.driverName} (${String.format("%.2fs", it.finishTimeMs / 1000f)})!"
            } ?: "Pure Solo Driving (0 AI Bots). Set Personal Best times & record your Ghost car!"
            addSystemChat(ghostMsg)
        }
        publishLocalLobbyState()
        _navEvents.tryEmit(RaceNavEvent.EnterLobby)
    }

    fun setChallengeGhost(ghost: GhostRunPlayback?) {
        _activeChallengeGhost.value = ghost
        if (ghost == null) {
            _activeGhostCar.value = null
        } else if (_role.value == SessionRole.Solo && !inRace) {
            addSystemChat("Ghost target set: ${ghost.driverName} (${String.format("%.2fs", ghost.finishTimeMs / 1000f)})")
            publishLocalLobbyState()
        }
    }

    fun startDailyChallengeSession(
        profile: PlayerProfile,
        baseRules: RaceRulesSettings = RaceRulesSettings(),
        customTrack: CustomTrackData,
        dateKey: String,
        challengeGhost: GhostRunPlayback? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        val dailyRules = DailyChallengeCatalog.lockedDailyRules(baseRules)
        _isDailyChallenge.value = true
        dailySeedForSession = DailyChallengeCatalog.seedForDate(dateKey)
        configureInitialSessionMode(dailyRules, customTrack)
        _role.value = SessionRole.Solo
        _activeChallengeGhost.value = challengeGhost
        _activeGhostCar.value = null
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        val ghostMsg = challengeGhost?.let {
            "Daily GP ($dateKey) vs ${it.driverName} (${String.format("%.2fs", it.finishTimeMs / 1000f)})!"
        } ?: "Daily GP ($dateKey) • Fair Fixed Track Layout (0 AI Bots)!"
        addSystemChat(ghostMsg)
        publishLocalLobbyState()
        startRace()
    }

    fun startDailyDriftChallengeSession(
        profile: PlayerProfile,
        baseRules: RaceRulesSettings = RaceRulesSettings(),
        customTrack: CustomTrackData,
        dateKey: String,
        challengeGhost: GhostRunPlayback? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        val dailyRules = com.example.drift.DriftDailyChallenge.lockedDailyDriftRules(baseRules)
        _isDailyChallenge.value = true
        dailySeedForSession = com.example.drift.DriftDailyChallenge.seedForDate(dateKey)
        configureInitialSessionMode(dailyRules, customTrack)
        _role.value = SessionRole.Solo
        _activeChallengeGhost.value = challengeGhost
        _activeGhostCar.value = null
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        val ghostMsg = challengeGhost?.let {
            "Daily Drift ($dateKey) vs ${it.driverName} (${String.format("%.2fs", it.finishTimeMs / 1000f)})!"
        } ?: "Daily Drift ($dateKey) • Obstacle Drift Layout (0 Powerups, 1 Nitro/Lap)!"
        addSystemChat(ghostMsg)
        publishLocalLobbyState()
        startRace()
    }

    fun restartRace() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        cancelRematchCountdown()
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
        _activeGhostCar.value = null
        inRace = false
        startRace()
    }

    fun hostLobby(
        profile: PlayerProfile,
        initialRules: RaceRulesSettings = RaceRulesSettings(),
        initialCustomTrack: CustomTrackData? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        configureInitialSessionMode(initialRules, initialCustomTrack)
        _role.value = SessionRole.Host
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        addSystemChat("Lobby hosted on local Wi-Fi. Waiting for 2-4 players...")
        publishLocalLobbyState()
        _navEvents.tryEmit(RaceNavEvent.EnterLobby)
        acquireLowLatencyWifiLock()

        scope.launch(Dispatchers.IO) {
            try {
                val ss = runCatching {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(DEFAULT_TCP_PORT))
                    }
                }.getOrElse {
                    ServerSocket(0)
                }
                serverSocket = ss
                tcpPort = ss.localPort

                val udpSock = runCatching {
                    DatagramSocket(tcpPort)
                }.recoverCatching {
                    DatagramSocket(0)
                }.getOrNull()
                hostUdpSocket = udpSock
                hostUdpPort = udpSock?.localPort ?: 0

                if (udpSock != null) {
                    hostUdpReadJob = launchHostUdpReader(udpSock)
                }
                publishLocalLobbyState()

                discovery.startAdvertising(scope) {
                    val state = _lobbyState.value ?: return@startAdvertising null
                    DiscoveredLobby(
                        hostId = profile.playerId,
                        hostName = profile.name,
                        lobbyName = "${profile.name}'s RC Lobby",
                        hostIp = discovery.getPrimaryLocalIp(),
                        tcpPort = tcpPort,
                        trackId = state.trackId,
                        playerCount = state.players.size,
                        maxPlayers = 4,
                        inRace = state.inRace
                    )
                }

                acceptJob = scope.launch(Dispatchers.IO) {
                    while (isActive && !ss.isClosed) {
                        try {
                            val socket = ss.accept()
                            socket.tcpNoDelay = true
                            handleIncomingSocket(socket)
                        } catch (_: Exception) {
                            if (ss.isClosed) break
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _connectionError.value = "Failed to open host socket: ${e.message}"
                leaveSession()
            }
        }
    }

    private fun ensureInterfaceUdpSocket(localAddr: InetAddress?): DatagramSocket? {
        if (localAddr == null || localAddr.isAnyLocalAddress) return hostUdpSocket
        val ipKey = localAddr.hostAddress ?: return hostUdpSocket
        interfaceUdpSockets[ipKey]?.takeIf { !it.isClosed }?.let { return it }
        val created = runCatching {
            DatagramSocket(InetSocketAddress(localAddr, 0))
        }.getOrNull() ?: return hostUdpSocket
        val prev = interfaceUdpSockets.putIfAbsent(ipKey, created)
        return if (prev != null && !prev.isClosed) {
            runCatching { created.close() }
            prev
        } else {
            interfaceUdpSockets[ipKey] = created
            interfaceUdpReaderJobs[ipKey]?.cancel()
            interfaceUdpReaderJobs[ipKey] = launchHostUdpReader(created)
            created
        }
    }

    private fun launchHostUdpReader(udpSock: DatagramSocket): Job {
        return scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            while (isActive && !udpSock.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    udpSock.receive(pkt)
                    val nowNs = System.nanoTime()
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val hello = RaceTelemetryCodec.decodeHelloPacket(msg)
                    if (hello != null) {
                        val client = connectedClients[hello.playerId]
                        if (client != null) {
                            client.udpAddress = pkt.socketAddress
                            client.udpSendSocket = udpSock
                            client.lastClientUdpRxNs = nowNs
                            if (hello.ackedHostUdpSeq >= 0L) {
                                client.lastBidirectionalUdpVerifiedNs = nowNs
                            }
                            if (!simulateHostToClientUdpBlackholeForTest) {
                                runCatching {
                                    val ack = RaceTelemetryCodec.encodeUdpAck(hostStateSeq.get().coerceAtLeast(0L))
                                    udpSock.send(DatagramPacket(ack, ack.size, pkt.socketAddress))
                                }
                            }
                        }
                        continue
                    }
                    val decoded = RaceTelemetryCodec.decodeInput(msg) ?: continue
                    val client = connectedClients[decoded.playerId] ?: continue
                    client.udpAddress = pkt.socketAddress
                    client.udpSendSocket = udpSock
                    client.lastClientUdpRxNs = nowNs
                    if (decoded.ackedHostUdpSeq >= 0L) {
                        client.lastBidirectionalUdpVerifiedNs = nowNs
                    }
                    if (decoded.seq > client.lastInputSeq) {
                        client.lastInputSeq = decoded.seq
                        physicsEngine?.submitInput(decoded.playerId, decoded.input)
                    }
                } catch (_: Exception) {
                    if (udpSock.isClosed) break
                }
            }
        }
    }

    fun joinLobby(ip: String, port: Int, profile: PlayerProfile) {
        leaveSession()
        localPlayerId = profile.playerId
        _role.value = SessionRole.Client
        _connectionError.value = null
        synchronized(clientSnapshotLock) {
            lastAppliedStateSeq = -1L
            lastReceivedUdpStateSeq = -1L
        }
        lastHostUdpRxNs.set(0L)
        acquireLowLatencyWifiLock()

        scope.launch(Dispatchers.IO) {
            try {
                val targetIp = ip.trim()
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(targetIp, port), 4500)
                clientSocket = socket
                clientHostAddr = socket.inetAddress

                val sendChan = Channel<String>(capacity = Channel.UNLIMITED)
                val fastInputChan = Channel<String>(capacity = Channel.CONFLATED)
                clientSendChannel = sendChan
                clientFastInputChannel = fastInputChan

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                clientWriteJob = scope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val line: String? = select<String?> {
                                sendChan.onReceiveCatching { it.getOrNull() }
                                fastInputChan.onReceiveCatching { it.getOrNull() }
                            } ?: break
                            writer.write(line)
                            writer.write("\n")
                            writer.flush()
                        }
                    } catch (_: Exception) {
                    }
                }

                // Open client UDP socket for ultra-low-latency gameplay packets
                val udpSock = runCatching { DatagramSocket() }.getOrNull()
                clientUdpSocket = udpSock
                if (udpSock != null) {
                    startClientUdpReader(udpSock)
                }

                val joinMsg = NetworkMessage(
                    type = PacketType.JOIN_REQUEST,
                    senderId = profile.playerId,
                    playerProfile = profile.toLobbyPlayer(isHost = false, isReady = false)
                )
                sendChan.trySend(msgAdapter.toJson(joinMsg))

                var enteredLobby = false
                clientReadJob = scope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("S|")) {
                                val roster = _lobbyState.value?.players.orEmpty()
                                val decoded = RaceTelemetryCodec.decodeSnapshot(
                                    text = line,
                                    roster = roster,
                                    previousSnapshot = _raceSnapshot.value,
                                    activeTrack = _activeTrack.value
                                )
                                if (decoded != null) {
                                    applyClientIncomingSnapshot(decoded.seq, decoded.snapshot, fromUdp = false)
                                }
                                continue
                            }

                            val msg = runCatching { msgAdapter.fromJson(line) }.getOrNull() ?: continue
                            when (msg.type) {
                                PacketType.JOIN_REJECTED -> {
                                    _connectionError.value = msg.errorReason ?: "Lobby rejected join"
                                    _navEvents.tryEmit(RaceNavEvent.Disconnected(msg.errorReason ?: "Rejected"))
                                    break
                                }
                                PacketType.LOBBY_STATE -> {
                                    val payload = msg.lobbyState ?: continue
                                    _lobbyState.value = payload
                                    currentRules = payload.rules
                                    if (!payload.inRace) {
                                        _activeTrack.value = when (payload.rules.gameModeEnum) {
                                            GameMode.ARENA -> TrackCatalog.getArenaTrack(payload.arenaShape)
                                            GameMode.STUNTS -> TrackCatalog.getStuntTrack(payload.stuntVariant)
                                            else -> payload.activeCustomTrack?.let {
                                                TrackCatalog.buildCustomTrack(it)
                                            } ?: TrackCatalog.getTrack(payload.trackId)
                                        }
                                    }
                                    if (payload.udpPort > 0) {
                                        clientHostUdpPort = payload.udpPort
                                        sendClientUdpHello()
                                    }
                                    if (!enteredLobby) {
                                        enteredLobby = true
                                        _navEvents.tryEmit(RaceNavEvent.EnterLobby)
                                    }
                                }
                                PacketType.CHAT_BROADCAST -> {
                                    val chat = msg.chatMessage ?: continue
                                    val cur = _lobbyState.value ?: continue
                                    _lobbyState.value = cur.copy(
                                        chatHistory = (cur.chatHistory + chat).takeLast(50)
                                    )
                                }
                                PacketType.RACE_START -> {
                                    _rematchState.value = null
                                    clientFinishEmitted.set(false)
                                    msg.rules?.let { r ->
                                        currentRules = r
                                        _lobbyState.value = _lobbyState.value?.copy(rules = r)
                                    }
                                    val startSnap = msg.raceSnapshot
                                    val snapMode = startSnap?.gameMode
                                    val isStuntMatch = snapMode == GameMode.STUNTS.name ||
                                        currentRules.gameModeEnum == GameMode.STUNTS
                                    val isArenaMatch = !isStuntMatch && (
                                        snapMode == GameMode.ARENA.name ||
                                            currentRules.gameModeEnum == GameMode.ARENA
                                        )
                                    val baseTrack = when {
                                        isStuntMatch -> TrackCatalog.getStuntTrack(
                                            msg.stuntVariant ?: startSnap?.stuntState?.variant
                                        )
                                        isArenaMatch -> TrackCatalog.getArenaTrack(msg.arenaShape ?: startSnap?.arenaShape)
                                        else -> msg.customTrack?.let {
                                            TrackCatalog.buildCustomTrack(it)
                                        } ?: TrackCatalog.getTrack(startSnap?.trackId ?: TrackId.LIVING_ROOM_GP.name)
                                    }
                                    val syncedTrack = if (!isStuntMatch && msg.ramps != null) {
                                        baseTrack.copy(ramps = msg.ramps)
                                    } else {
                                        baseTrack
                                    }
                                    _activeTrack.value = syncedTrack
                                    synchronized(clientSnapshotLock) {
                                        lastAppliedStateSeq = 0L
                                        lastReceivedUdpStateSeq = -1L
                                        _raceSnapshot.value = startSnap
                                    }
                                    sendClientUdpHello()
                                    startClientUdpInputHeartbeat()
                                    _navEvents.tryEmit(RaceNavEvent.StartRace)
                                }
                                PacketType.RACE_SNAPSHOT -> {
                                    val snap = msg.raceSnapshot ?: continue
                                    if (snap.phase == RacePhase.FINISHED.name) {
                                        applyClientIncomingSnapshot(Long.MAX_VALUE - 1L, snap, fromUdp = false)
                                    } else {
                                        val udpFresh = (System.nanoTime() - lastHostUdpRxNs.get()) <= 450_000_000L
                                        if (!udpFresh) {
                                            _raceSnapshot.value = snap
                                        }
                                    }
                                }
                                PacketType.REMATCH_STATE -> {
                                    _rematchState.value = msg.rematchState
                                }
                                PacketType.RETURN_TO_LOBBY -> {
                                    _rematchState.value = null
                                    clientUdpSendJob?.cancel()
                                    msg.lobbyState?.let { _lobbyState.value = it }
                                    _navEvents.tryEmit(RaceNavEvent.BackToLobby)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        if (_role.value == SessionRole.Client) {
                            _navEvents.tryEmit(RaceNavEvent.Disconnected("Host connection closed"))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _connectionError.value = "Could not connect to $ip:$port (${e.localizedMessage ?: "timeout"})"
                _role.value = SessionRole.None
                releaseLowLatencyWifiLock()
            }
        }
    }

    private fun applyClientIncomingSnapshot(seq: Long, snapshot: RaceSnapshot, fromUdp: Boolean) {
        var shouldEmitResults = false
        synchronized(clientSnapshotLock) {
            if (fromUdp) {
                lastHostUdpRxNs.set(System.nanoTime())
                if (seq > lastReceivedUdpStateSeq) {
                    lastReceivedUdpStateSeq = seq
                }
            }
            val isFinished = snapshot.phase == RacePhase.FINISHED.name
            if (seq > lastAppliedStateSeq || isFinished) {
                if (seq > lastAppliedStateSeq && seq < Long.MAX_VALUE - 1L) {
                    lastAppliedStateSeq = seq
                }
                if (snapshot.gameMode == GameMode.STUNTS.name) {
                    val vName = snapshot.stuntState?.variant
                    if (vName != null && _activeTrack.value?.stuntVariant?.name != vName) {
                        _activeTrack.value = TrackCatalog.getStuntTrack(vName)
                    }
                }
                _raceSnapshot.value = snapshot
                if (isFinished) {
                    _lastResults.value = snapshot
                    clientUdpSendJob?.cancel()
                    if (clientFinishEmitted.compareAndSet(false, true)) {
                        shouldEmitResults = true
                    }
                }
            }
        }
        if (shouldEmitResults) {
            _navEvents.tryEmit(RaceNavEvent.ShowResults)
        }
    }

    private fun startClientUdpReader(udpSock: DatagramSocket) {
        clientUdpReadJob?.cancel()
        clientUdpReadJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(8192)
            while (isActive && !udpSock.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    udpSock.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    if (RaceTelemetryCodec.isUdpAck(msg)) {
                        lastHostUdpRxNs.set(System.nanoTime())
                        // Confirm bidirectional UDP path back to Host
                        val addr = clientHostAddr
                        val port = clientHostUdpPort
                        if (addr != null && port > 0) {
                            runCatching {
                                val ackBack = RaceTelemetryCodec.encodeHello(localPlayerId, ackedHostUdpSeq = 0L)
                                udpSock.send(DatagramPacket(ackBack, ackBack.size, addr, port))
                            }
                        }
                        continue
                    }
                    val roster = _lobbyState.value?.players.orEmpty()
                    val decoded = RaceTelemetryCodec.decodeSnapshot(
                        text = msg,
                        roster = roster,
                        previousSnapshot = _raceSnapshot.value,
                        activeTrack = _activeTrack.value
                    ) ?: continue
                    applyClientIncomingSnapshot(decoded.seq, decoded.snapshot, fromUdp = true)
                } catch (_: Exception) {
                    if (udpSock.isClosed) break
                }
            }
        }
    }

    private fun startClientUdpInputHeartbeat() {
        clientUdpSendJob?.cancel()
        clientUdpSendJob = scope.launch(Dispatchers.IO) {
            var hbTick = 0
            while (isActive && _role.value == SessionRole.Client) {
                val udpFresh = (System.nanoTime() - lastHostUdpRxNs.get()) <= 450_000_000L
                val alsoTcp = !udpFresh && (hbTick % 2 == 0) || (hbTick % 12 == 0)
                sendClientGameplayInput(usePowerUp = false, dropMine = false, sendOverTcpToo = alsoTcp)
                hbTick++
                delay(20L)
            }
        }
    }

    private fun sendClientUdpHello() {
        val sock = clientUdpSocket ?: return
        val addr = clientHostAddr ?: return
        val port = clientHostUdpPort
        if (port <= 0) return
        scope.launch(Dispatchers.IO) {
            repeat(3) {
                runCatching {
                    val ackSeq = if ((System.nanoTime() - lastHostUdpRxNs.get()) <= 1_500_000_000L) {
                        lastReceivedUdpStateSeq.coerceAtLeast(0L)
                    } else {
                        -1L
                    }
                    val data = RaceTelemetryCodec.encodeHello(localPlayerId, ackedHostUdpSeq = ackSeq)
                    sock.send(DatagramPacket(data, data.size, addr, port))
                }
                delay(45L)
            }
        }
    }

    private fun sendClientGameplayInput(
        usePowerUp: Boolean,
        dropMine: Boolean = false,
        sendOverTcpToo: Boolean = false
    ) {
        val inp = synchronized(this) { localInput }.copy(usePowerUp = usePowerUp, dropMine = dropMine)
        val seq = clientInputSeq.incrementAndGet()
        val ackSeq = if ((System.nanoTime() - lastHostUdpRxNs.get()) <= 600_000_000L) {
            lastReceivedUdpStateSeq.coerceAtLeast(0L)
        } else {
            -1L
        }
        val line = RaceTelemetryCodec.encodeInputLine(
            playerId = localPlayerId,
            seq = seq,
            input = inp,
            ackedHostUdpSeq = ackSeq
        )

        val sock = clientUdpSocket
        val addr = clientHostAddr
        val port = clientHostUdpPort
        if (sock != null && !sock.isClosed && addr != null && port > 0) {
            val bytes = line.toByteArray(Charsets.UTF_8)
            runCatching {
                sock.send(DatagramPacket(bytes, bytes.size, addr, port))
            }
        }

        if (sendOverTcpToo) {
            clientFastInputChannel?.trySend(line)
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            var registeredClient: ConnectedClient? = null

            try {
                val firstLine = reader.readLine() ?: run {
                    socket.close(); return@launch
                }
                val joinMsg = runCatching { msgAdapter.fromJson(firstLine) }.getOrNull()
                val joiningPlayer = joinMsg?.playerProfile
                if (joinMsg?.type != PacketType.JOIN_REQUEST || joiningPlayer == null) {
                    socket.close(); return@launch
                }

                synchronized(playersList) {
                    if (inRace) {
                        val reject = msgAdapter.toJson(
                            NetworkMessage(PacketType.JOIN_REJECTED, errorReason = "Race already in progress")
                        )
                        writer.write(reject + "\n"); writer.flush(); socket.close(); return@launch
                    }
                    playersList.removeAll { it.id == joiningPlayer.id }
                    if (playersList.size >= 4) {
                        val botIndex = playersList.indexOfLast { it.isBot }
                        if (botIndex >= 0) {
                            playersList.removeAt(botIndex)
                        } else {
                            val reject = msgAdapter.toJson(
                                NetworkMessage(PacketType.JOIN_REJECTED, errorReason = "Lobby is full (4/4)")
                            )
                            writer.write(reject + "\n"); writer.flush(); socket.close(); return@launch
                        }
                    }
                    playersList.add(joiningPlayer.copy(isHost = false, isBot = false))
                }

                val client = ConnectedClient(joiningPlayer.id, socket)
                val ifaceUdpSock = ensureInterfaceUdpSocket(socket.localAddress)
                if (ifaceUdpSock != null) {
                    client.udpSendSocket = ifaceUdpSock
                    client.preferredLocalUdpPort = ifaceUdpSock.localPort
                }
                registeredClient = client
                connectedClients[joiningPlayer.id] = client

                client.writerJob = scope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val line: String? = select<String?> {
                                client.reliableChannel.onReceiveCatching { it.getOrNull() }
                                client.snapshotChannel.onReceiveCatching { it.getOrNull() }
                            } ?: break
                            writer.write(line)
                            writer.write("\n")
                            writer.flush()
                        }
                    } catch (_: Exception) {
                    }
                }

                addSystemChat("${joiningPlayer.name} joined the paddock!")
                broadcastLobbyState()

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("I|")) {
                        val decoded = RaceTelemetryCodec.decodeInput(line) ?: continue
                        if (decoded.seq > client.lastInputSeq) {
                            client.lastInputSeq = decoded.seq
                            physicsEngine?.submitInput(joiningPlayer.id, decoded.input)
                        }
                        continue
                    }
                    val msg = runCatching { msgAdapter.fromJson(line) }.getOrNull() ?: continue
                    handleHostReceivedMessage(joiningPlayer.id, msg)
                }
            } catch (_: Exception) {
            } finally {
                val cid = registeredClient?.playerId
                if (cid != null) {
                    connectedClients.remove(cid)
                    val hadVote = rematchVotes.remove(cid) != null
                    var removedName: String? = null
                    synchronized(playersList) {
                        val existing = playersList.find { it.id == cid }
                        removedName = existing?.name
                        playersList.removeAll { it.id == cid }
                    }
                    if (removedName != null) {
                        addSystemChat("$removedName disconnected.")
                        broadcastLobbyState()
                    }
                    if (hadVote && _rematchState.value != null) {
                        publishAndBroadcastRematchState()
                    }
                }
                registeredClient?.writerJob?.cancel()
                registeredClient?.reliableChannel?.close()
                registeredClient?.snapshotChannel?.close()
                runCatching { socket.close() }
            }
        }
    }

    private fun handleHostReceivedMessage(clientId: String, msg: NetworkMessage) {
        when (msg.type) {
            PacketType.PLAYER_UPDATE -> {
                val updated = msg.playerProfile ?: return
                synchronized(playersList) {
                    val idx = playersList.indexOfFirst { it.id == clientId }
                    if (idx >= 0) {
                        val old = playersList[idx]
                        playersList[idx] = old.copy(
                            name = updated.name,
                            carType = updated.carType,
                            primaryColorArgb = updated.primaryColorArgb,
                            accentColorArgb = updated.accentColorArgb,
                            equippedUpgradesMask = updated.equippedUpgradesMask
                        )
                    }
                }
                broadcastLobbyState()
            }
            PacketType.READY_TOGGLE -> {
                val ready = msg.isReady ?: return
                synchronized(playersList) {
                    val idx = playersList.indexOfFirst { it.id == clientId }
                    if (idx >= 0) {
                        playersList[idx] = playersList[idx].copy(isReady = ready)
                    }
                }
                broadcastLobbyState()
            }
            PacketType.CHAT_SEND -> {
                val chat = msg.chatMessage ?: return
                appendAndBroadcastChat(chat)
            }
            PacketType.CONTROL_INPUT -> {
                val input = msg.controlInput ?: return
                physicsEngine?.submitInput(clientId, input)
            }
            PacketType.REMATCH_VOTE -> {
                val optId = msg.voteOptionId ?: return
                if (_rematchState.value != null && rematchOptions.any { it.id == optId }) {
                    rematchVotes[clientId] = optId
                    publishAndBroadcastRematchState()
                }
            }
        }
    }

    fun updateLocalProfile(profile: PlayerProfile) {
        localPlayerId = profile.playerId
        when (_role.value) {
            SessionRole.Host, SessionRole.Solo -> {
                synchronized(playersList) {
                    val idx = playersList.indexOfFirst { it.id == profile.playerId }
                    if (idx >= 0) {
                        val old = playersList[idx]
                        playersList[idx] = old.copy(
                            name = profile.name,
                            carType = profile.carType.name,
                            primaryColorArgb = profile.primaryColorArgb,
                            accentColorArgb = profile.accentColorArgb,
                            equippedUpgradesMask = profile.equippedUpgradesMask
                        )
                    }
                }
                if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
            }
            SessionRole.Client -> {
                val msg = NetworkMessage(
                    type = PacketType.PLAYER_UPDATE,
                    senderId = profile.playerId,
                    playerProfile = profile.toLobbyPlayer(isHost = false, isReady = false)
                )
                clientSendChannel?.trySend(msgAdapter.toJson(msg))
            }
            SessionRole.None, SessionRole.OnlineHost, SessionRole.OnlineClient -> Unit
        }
    }

    fun toggleReady() {
        val current = _lobbyState.value ?: return
        val me = current.players.find { it.id == localPlayerId } ?: return
        val nextReady = !me.isReady
        when (_role.value) {
            SessionRole.Client -> {
                clientSendChannel?.trySend(
                    msgAdapter.toJson(
                        NetworkMessage(
                            type = PacketType.READY_TOGGLE,
                            senderId = localPlayerId,
                            isReady = nextReady
                        )
                    )
                )
            }
            SessionRole.Host, SessionRole.Solo -> {
                synchronized(playersList) {
                    val idx = playersList.indexOfFirst { it.id == localPlayerId }
                    if (idx >= 0) playersList[idx] = playersList[idx].copy(isReady = nextReady)
                }
                if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
            }
            SessionRole.None, SessionRole.OnlineHost, SessionRole.OnlineClient -> Unit
        }
    }

    fun selectTrack(trackId: TrackId) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentTrackId = trackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getTrack(trackId)
        addSystemChat("Track set to ${trackId.displayName}")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun selectCustomTrack(customTrack: CustomTrackData) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentCustomTrack = customTrack
        currentTrackId = runCatching { TrackId.valueOf(customTrack.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
        _activeTrack.value = TrackCatalog.buildCustomTrack(customTrack)
        addSystemChat("Custom track set: ${customTrack.name}")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun selectArenaShape(shape: ArenaShape) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentArenaShape = shape
        currentTrackId = shape.themeTrackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getArenaTrack(shape)
        addSystemChat("Arena set to ${shape.displayName} (${shape.shapeLabel})")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun selectStuntVariant(variant: StuntVariant) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentStuntVariant = variant
        currentTrackId = variant.themeTrackId
        currentCustomTrack = null
        _activeTrack.value = TrackCatalog.getStuntTrack(variant)
        addSystemChat("Starting stunt set to ${variant.displayName}")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun updateRules(rules: RaceRulesSettings) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        val keepCustom = if (rules.gameModeEnum == GameMode.RACE || rules.gameModeEnum == GameMode.DRIFT) {
            currentCustomTrack
        } else {
            null
        }
        configureInitialSessionMode(rules, keepCustom)
        val summary = buildList {
            when (rules.gameModeEnum) {
                GameMode.ARENA -> add("Arena (${rules.arenaTargetMinutes} min flag)")
                GameMode.STUNTS -> add("Stunts (4 turn-based events)")
                GameMode.HYBRID -> {
                    val enabledNames = rules.enabledHybridModes().joinToString("+") { it.displayName }
                    add("Hybrid Tour ($enabledNames)")
                }
                GameMode.DRIFT -> add("Drifting (${rules.totalLaps} Laps • Time-Based • 1 Nitro/Lap)")
                GameMode.RACE -> {
                    if (rules.winConditionEnum == com.example.model.WinCondition.TIME_TRIAL_DISTANCE) {
                        add("Max Time: ${rules.timeLimitSec}s")
                    } else {
                        add("${rules.totalLaps} Laps (${rules.winConditionEnum.displayName})")
                    }
                }
            }
            add("Speed: ${rules.carSpeedEnum.displayName}")
            if (rules.gameModeEnum != GameMode.STUNTS && _role.value != SessionRole.Solo) {
                if (rules.hostRandomConditionsOverride == true) {
                    add("Environmental Effects: On")
                } else {
                    add("Environmental Effects: Off")
                }
            }
            if (rules.showPlayerNames) add("Name Tags")
        }.joinToString(" • ").ifEmpty { "Standard Rules" }
        addSystemChat("Match settings updated: $summary")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun setTotalLaps(laps: Int) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentTotalLaps = laps.coerceIn(1, 9)
        currentRules = currentRules.copy(totalLaps = currentTotalLaps)
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun addBot() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        synchronized(playersList) {
            if (playersList.size >= 4) return
            val botNum = playersList.count { it.isBot } + 1
            val cars = RcCarType.entries
            val car = cars[(botNum) % cars.size]
            val preset = RcColorPresets[(botNum + 2) % RcColorPresets.size]
            playersList.add(
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
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun removeBot() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        synchronized(playersList) {
            val idx = playersList.indexOfLast { it.isBot }
            if (idx >= 0) playersList.removeAt(idx)
        }
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun clearBots() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        var removedCount = 0
        synchronized(playersList) {
            val before = playersList.size
            playersList.removeAll { it.isBot }
            removedCount = before - playersList.size
        }
        if (removedCount > 0) {
            addSystemChat("Switched to Pure Solo Driving (0 AI Bots).")
        }
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun sendChatMessage(senderName: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val msgObj = ChatMessage(
            id = UUID.randomUUID().toString().take(8),
            senderId = localPlayerId,
            senderName = senderName,
            text = clean.take(140),
            timestampMs = System.currentTimeMillis(),
            isSystem = false
        )
        when (_role.value) {
            SessionRole.Host, SessionRole.Solo -> appendAndBroadcastChat(msgObj)
            SessionRole.Client -> {
                clientSendChannel?.trySend(
                    msgAdapter.toJson(
                        NetworkMessage(
                            type = PacketType.CHAT_SEND,
                            senderId = localPlayerId,
                            chatMessage = msgObj
                        )
                    )
                )
            }
            SessionRole.None, SessionRole.OnlineHost, SessionRole.OnlineClient -> Unit
        }
    }

    fun startRace() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        val snapshotPlayers = synchronized(playersList) { playersList.toList() }
        if (snapshotPlayers.isEmpty()) return

        cancelRematchCountdown()
        inRace = true
        pointsAwardedForCurrentRace = false
        hostStateSeq.set(0L)

        val isHybrid = currentRules.gameModeEnum == GameMode.HYBRID
        val concreteMode = if (isHybrid) activeConcreteMode else currentRules.gameModeEnum
        val upcomingHybridMode = if (isHybrid) {
            (nextPlannedHybridMode ?: resolveHybridStageMode(currentRules, hybridStageNumber, concreteMode)).also {
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
                // Play all 4 different stunts in random order
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
        val dailyChallengeRun = _isDailyChallenge.value
        val track = baseTrack
        _activeTrack.value = track

        val effectiveStageRules = currentRules.copy(gameMode = concreteMode.name)
        val engineRng = if (dailyChallengeRun) {
            Random(
                dailySeedForSession
                    ?: if (isDriftMode) com.example.drift.DriftDailyChallenge.seedForDate() else DailyChallengeCatalog.seedForDate()
            )
        } else {
            Random.Default
        }
        val engine = RacePhysicsEngine(
            track = track,
            totalLaps = currentTotalLaps,
            players = snapshotPlayers,
            rules = effectiveStageRules,
            random = engineRng,
            stuntSequenceOverride = stuntSequence,
            isHybridTour = isHybrid,
            hybridStageNumber = hybridStageNumber,
            nextHybridModeName = upcomingHybridMode?.name,
            isDailyChallenge = dailyChallengeRun,
            isMultiplayerSession = (_role.value == SessionRole.Host || _role.value == SessionRole.Client)
        )
        physicsEngine = engine

        val initialSnap = engine.step(0.016f)
        _raceSnapshot.value = initialSnap

        if (_role.value == SessionRole.Host) {
            connectedClients.values.forEach { client ->
                client.lastBidirectionalUdpVerifiedNs = 0L
            }
            broadcastLobbyState()
            broadcastReliablePacket(
                NetworkMessage(
                    type = PacketType.RACE_START,
                    senderId = localPlayerId,
                    raceSnapshot = initialSnap,
                    arenaShape = if (isArenaMode) currentArenaShape.name else null,
                    stuntVariant = if (isStuntMode) currentStuntVariant.name else null,
                    stuntSequence = stuntSequence?.map { it.name },
                    rules = currentRules,
                    customTrack = customData,
                    ramps = track.ramps
                )
            )
        }
        _navEvents.tryEmit(RaceNavEvent.StartRace)

        val isSoloSession = _role.value == SessionRole.Solo
        val pureSoloNoBots = isSoloSession && snapshotPlayers.none { it.isBot }
        val isGhostTimeMode = concreteMode == GameMode.RACE || concreteMode == GameMode.DRIFT
        val activeGhostForRun = if (isSoloSession && isGhostTimeMode && !isHybrid) {
            _activeChallengeGhost.value?.takeIf { ghost ->
                ghost.trackId == (customData?.id ?: currentTrackId.name)
            }
        } else {
            null
        }
        val recordedGhostFrames = mutableListOf<GhostFrame>()
        if (isSoloSession && isGhostTimeMode && !isHybrid) {
            initialSnap.racers.find { it.playerId == localPlayerId }?.let { me ->
                recordedGhostFrames.add(
                    GhostFrame(
                        tMs = 0L,
                        x = me.x,
                        y = me.y,
                        angleRad = me.angleRad,
                        jumpZ = me.jumpZ,
                        progress = me.totalProgress
                    )
                )
            }
        }
        if (activeGhostForRun != null) {
            val g0 = GhostCodec.interpolateAt(activeGhostForRun.frames, 0L)
            _activeGhostCar.value = g0?.let {
                ActiveGhostCarState(
                    driverName = activeGhostForRun.driverName,
                    carType = activeGhostForRun.carType,
                    primaryColorArgb = activeGhostForRun.primaryColorArgb,
                    accentColorArgb = activeGhostForRun.accentColorArgb,
                    equippedUpgradesMask = activeGhostForRun.equippedUpgradesMask,
                    finishTimeMs = activeGhostForRun.finishTimeMs,
                    bestLapMs = activeGhostForRun.bestLapMs,
                    x = it.x,
                    y = it.y,
                    angleRad = it.angleRad,
                    jumpZ = it.jumpZ,
                    finished = false,
                    splitDeltaMs = null
                )
            }
        } else {
            _activeGhostCar.value = null
        }

        physicsJob?.cancel()
        physicsJob = scope.launch(Dispatchers.Default) {
            var tickCounter = 0
            var lastGhostRecordMs = -100L
            var lastNs = System.nanoTime()
            while (isActive && inRace) {
                delay(16L)
                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.008f, 0.04f)
                lastNs = nowNs

                val inputToApply = synchronized(this@WifiP2pRaceManager) {
                    val inp = localInput.copy(
                        usePowerUp = pendingLocalPowerUp,
                        dropMine = pendingLocalMineDrop
                    )
                    pendingLocalPowerUp = false
                    pendingLocalMineDrop = false
                    inp
                }
                engine.submitInput(localPlayerId, inputToApply)

                val snap = engine.step(dt)
                if (isStuntMode) {
                    val vName = snap.stuntState?.variant
                    if (vName != null && _activeTrack.value?.stuntVariant?.name != vName) {
                        _activeTrack.value = TrackCatalog.getStuntTrack(vName)
                    }
                }
                _raceSnapshot.value = snap

                // Ghost recording & live Ghost Challenge interpolation in Solo mode
                if (isSoloSession && isGhostTimeMode && !isHybrid) {
                    val me = snap.racers.find { it.playerId == localPlayerId }
                    if (me != null && snap.phase == RacePhase.RACING.name && (snap.raceTimeMs - lastGhostRecordMs) >= 95L) {
                        lastGhostRecordMs = snap.raceTimeMs
                        recordedGhostFrames.add(
                            GhostFrame(
                                tMs = snap.raceTimeMs,
                                x = me.x,
                                y = me.y,
                                angleRad = me.angleRad,
                                jumpZ = me.jumpZ,
                                progress = me.totalProgress
                            )
                        )
                    }
                    if (activeGhostForRun != null) {
                        val ghostTime = if (snap.phase == RacePhase.COUNTDOWN.name) 0L else snap.raceTimeMs
                        val pose = GhostCodec.interpolateAt(activeGhostForRun.frames, ghostTime)
                        if (pose != null) {
                            val deltaMs = if (snap.phase == RacePhase.RACING.name && me != null) {
                                GhostCodec.computeSplitDeltaMs(
                                    frames = activeGhostForRun.frames,
                                    playerProgress = me.totalProgress,
                                    playerRaceTimeMs = snap.raceTimeMs
                                )
                            } else {
                                null
                            }
                            _activeGhostCar.value = ActiveGhostCarState(
                                driverName = activeGhostForRun.driverName,
                                carType = activeGhostForRun.carType,
                                primaryColorArgb = activeGhostForRun.primaryColorArgb,
                                accentColorArgb = activeGhostForRun.accentColorArgb,
                                equippedUpgradesMask = activeGhostForRun.equippedUpgradesMask,
                                finishTimeMs = activeGhostForRun.finishTimeMs,
                                bestLapMs = activeGhostForRun.bestLapMs,
                                x = pose.x,
                                y = pose.y,
                                angleRad = pose.angleRad,
                                jumpZ = pose.jumpZ,
                                finished = ghostTime >= activeGhostForRun.finishTimeMs,
                                splitDeltaMs = deltaMs
                            )
                        }
                    }
                }

                tickCounter++
                val isFinished = snap.phase == RacePhase.FINISHED.name
                if (isFinished) {
                    if (!pointsAwardedForCurrentRace) {
                        pointsAwardedForCurrentRace = true
                        awardChampionshipPoints(snap)
                    }
                    _lastResults.value = snap
                    _activeGhostCar.value = null
                    if (isSoloSession && !isHybrid) {
                        val me = snap.racers.find { it.playerId == localPlayerId }
                        if (me != null) {
                            val finishMs = me.finishTimeMs ?: snap.raceTimeMs
                            if (isGhostTimeMode &&
                                (isDriftMode || currentRules.winConditionEnum == WinCondition.FIRST_TO_FINISH) &&
                                finishMs > 0L
                            ) {
                                recordedGhostFrames.add(
                                    GhostFrame(
                                        tMs = finishMs,
                                        x = me.x,
                                        y = me.y,
                                        angleRad = me.angleRad,
                                        jumpZ = me.jumpZ,
                                        progress = me.totalProgress
                                    )
                                )
                                val trackKey = customData?.id ?: currentTrackId.name
                                val bKey = LeaderboardBoardKey.build(
                                    gameMode = concreteMode,
                                    trackKey = trackKey,
                                    totalLaps = currentTotalLaps
                                )
                                _completedSoloRuns.tryEmit(
                                    CompletedSoloRunSummary(
                                        boardKey = bKey,
                                        gameMode = concreteMode,
                                        trackKey = trackKey,
                                        totalLaps = currentTotalLaps,
                                        finishTimeMs = finishMs,
                                        bestLapMs = me.bestLapMs ?: 0L,
                                        bestScore = me.racePoints,
                                        pureSoloNoBots = pureSoloNoBots,
                                        encodedGhostFrames = GhostCodec.encodeFrames(recordedGhostFrames)
                                    )
                                )
                            } else if (concreteMode == GameMode.STUNTS && me.racePoints > 0) {
                                val bKey = LeaderboardBoardKey.build(
                                    gameMode = GameMode.STUNTS,
                                    trackKey = "STUNT_GAUNTLET"
                                )
                                _completedSoloRuns.tryEmit(
                                    CompletedSoloRunSummary(
                                        boardKey = bKey,
                                        gameMode = GameMode.STUNTS,
                                        trackKey = "STUNT_GAUNTLET",
                                        totalLaps = 4,
                                        finishTimeMs = snap.raceTimeMs.coerceAtLeast(1L),
                                        bestLapMs = 0L,
                                        bestScore = me.racePoints,
                                        pureSoloNoBots = pureSoloNoBots,
                                        encodedGhostFrames = ""
                                    )
                                )
                            }
                        }
                    }
                    if (_role.value == SessionRole.Host) {
                        val finalSeq = hostStateSeq.incrementAndGet()
                        val finalLine = String(RaceTelemetryCodec.encodeSnapshot(finalSeq, snap), Charsets.UTF_8)
                        connectedClients.values.forEach { it.snapshotChannel.trySend(finalLine) }
                        broadcastReliablePacket(
                            NetworkMessage(
                                type = PacketType.RACE_SNAPSHOT,
                                senderId = localPlayerId,
                                raceSnapshot = snap
                            )
                        )
                    }
                    inRace = false
                    _navEvents.tryEmit(RaceNavEvent.ShowResults)
                    if (!dailyChallengeRun) {
                        startRematchCountdown()
                    }
                    break
                } else if (_role.value == SessionRole.Host) {
                    broadcastRaceTelemetry(snap, tickCounter)
                }
            }
        }
    }

    private fun broadcastRaceTelemetry(snap: RaceSnapshot, tickCounter: Int) {
        if (connectedClients.isEmpty()) return
        val seq = hostStateSeq.incrementAndGet()
        val nowNs = System.nanoTime()
        val udpBytes = RaceTelemetryCodec.encodeSnapshot(seq, snap)
        var compactLine: String? = null
        val isCountdown = snap.phase == RacePhase.COUNTDOWN.name

        for (client in connectedClients.values) {
            val udpAddr = client.udpAddress
            val sendSock = client.udpSendSocket?.takeIf { !it.isClosed }
                ?: hostUdpSocket?.takeIf { !it.isClosed }
            var udpSentCleanly = false
            if (sendSock != null && udpAddr != null && !simulateHostToClientUdpBlackholeForTest) {
                udpSentCleanly = runCatching {
                    sendSock.send(DatagramPacket(udpBytes, udpBytes.size, udpAddr))
                    true
                }.getOrDefault(false)
                if (!udpSentCleanly) {
                    client.lastBidirectionalUdpVerifiedNs = 0L
                }
            }

            val udpHealthy = udpSentCleanly && client.hasVerifiedBidirectionalUdp(nowNs)
            val shouldSendTcpCompact = when {
                // Always stream 30Hz compact TCP frames during 3-2-1 countdown so no client can EVER get stuck at 3
                isCountdown -> (tickCounter % 2 == 0)
                // Automatic 30Hz compact TCP telemetry whenever UDP is blocked, one-way (Hotspot), or unverified
                !udpHealthy -> (tickCounter % 2 == 0)
                // Lightweight 4Hz TCP safety heartbeat even when 60Hz UDP is active
                else -> (tickCounter % 15 == 0)
            }

            if (shouldSendTcpCompact) {
                val line = compactLine ?: String(udpBytes, Charsets.UTF_8).also { compactLine = it }
                client.snapshotChannel.trySend(line)
            }
        }
    }

    fun updateLocalControlInput(throttle: Float, steer: Float) {
        synchronized(this) {
            localInput = localInput.copy(throttle = throttle, steer = steer)
        }
        if (_role.value == SessionRole.Client) {
            val nowNs = System.nanoTime()
            val throttleFlipped = (throttle * lastSentThrottle <= 0f) && abs(throttle - lastSentThrottle) > 0.1f
            val steerResetOrFlipped = (steer == 0f && abs(lastSentSteer) > 0.01f) || (steer * lastSentSteer < 0f)
            val changedSignificantly = throttleFlipped ||
                steerResetOrFlipped ||
                abs(throttle - lastSentThrottle) > 0.04f ||
                abs(steer - lastSentSteer) > 0.025f
            if (throttleFlipped || steerResetOrFlipped || (changedSignificantly && (nowNs - lastInputSendNs) >= 12_000_000L)) {
                lastSentThrottle = throttle
                lastSentSteer = steer
                lastInputSendNs = nowNs
                val udpFresh = (nowNs - lastHostUdpRxNs.get()) <= 450_000_000L
                scope.launch(Dispatchers.IO) {
                    sendClientGameplayInput(
                        usePowerUp = false,
                        dropMine = false,
                        sendOverTcpToo = !udpFresh || throttleFlipped || steerResetOrFlipped
                    )
                }
            }
        }
    }

    fun triggerLocalPowerUp() {
        synchronized(this) {
            pendingLocalPowerUp = true
        }
        if (_role.value == SessionRole.Client) {
            // Send power-up reliably over TCP so activation is never dropped
            val inp = synchronized(this) { localInput }
            clientSendChannel?.trySend(
                msgAdapter.toJson(
                    NetworkMessage(
                        type = PacketType.CONTROL_INPUT,
                        senderId = localPlayerId,
                        controlInput = inp.copy(usePowerUp = true)
                    )
                )
            )
        }
    }

    fun triggerLocalMineDrop() {
        synchronized(this) {
            pendingLocalMineDrop = true
        }
        if (_role.value == SessionRole.Client) {
            val inp = synchronized(this) { localInput }
            clientSendChannel?.trySend(
                msgAdapter.toJson(
                    NetworkMessage(
                        type = PacketType.CONTROL_INPUT,
                        senderId = localPlayerId,
                        controlInput = inp.copy(dropMine = true)
                    )
                )
            )
        }
    }

    private fun cancelRematchCountdown() {
        rematchJob?.cancel()
        rematchJob = null
        rematchVotes.clear()
        rematchOptions = emptyList()
        _rematchState.value = null
    }

    private fun publishAndBroadcastRematchState() {
        val opts = rematchOptions
        if (opts.isEmpty()) return
        val votesMap = rematchVotes.toMap()
        val state = RematchCountdownState(
            secondsLeft = rematchSecondsLeft,
            totalSeconds = REMATCH_COUNTDOWN_SECONDS,
            options = opts,
            votesByPlayerId = votesMap,
            leadingOptionId = computeLeadingRematchOption(opts, votesMap)
        )
        _rematchState.value = state
        if (_role.value == SessionRole.Host) {
            broadcastReliablePacket(
                NetworkMessage(
                    type = PacketType.REMATCH_STATE,
                    senderId = localPlayerId,
                    rematchState = state
                )
            )
        }
    }

    private fun startRematchCountdown() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        rematchJob?.cancel()
        rematchVotes.clear()
        val voteTargetMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            (nextPlannedHybridMode ?: resolveHybridStageMode(currentRules, hybridStageNumber, activeConcreteMode)).also {
                nextPlannedHybridMode = it
            }
        } else {
            currentRules.gameModeEnum
        }
        rematchOptions = buildRematchVoteOptions(
            gameMode = voteTargetMode,
            currentTrackId = currentTrackId,
            currentArenaShape = currentArenaShape,
            currentStuntVariant = currentStuntVariant,
            hasActiveCustomTrack = currentCustomTrack != null,
            activeCustomTrack = currentCustomTrack
        )
        rematchSecondsLeft = REMATCH_COUNTDOWN_SECONDS
        publishAndBroadcastRematchState()

        rematchJob = scope.launch(Dispatchers.Default) {
            for (sec in (REMATCH_COUNTDOWN_SECONDS - 1) downTo 1) {
                delay(1000L)
                if (!isActive || inRace) return@launch
                rematchSecondsLeft = sec
                publishAndBroadcastRematchState()
            }
            delay(1000L)
            if (!isActive || inRace) return@launch
            launchRematchNow()
        }
    }

    fun voteNextRematchTrack(optionId: String) {
        when (_role.value) {
            SessionRole.Host, SessionRole.Solo -> {
                if (_rematchState.value == null || rematchOptions.none { it.id == optionId }) return
                rematchVotes[localPlayerId] = optionId
                publishAndBroadcastRematchState()
            }
            SessionRole.Client -> {
                val cur = _rematchState.value ?: return
                if (cur.options.none { it.id == optionId }) return
                val updatedVotes = cur.votesByPlayerId + (localPlayerId to optionId)
                _rematchState.value = cur.copy(
                    votesByPlayerId = updatedVotes,
                    leadingOptionId = computeLeadingRematchOption(cur.options, updatedVotes)
                )
                clientSendChannel?.trySend(
                    msgAdapter.toJson(
                        NetworkMessage(
                            type = PacketType.REMATCH_VOTE,
                            senderId = localPlayerId,
                            voteOptionId = optionId
                        )
                    )
                )
            }
            SessionRole.None, SessionRole.OnlineHost, SessionRole.OnlineClient -> Unit
        }
    }

    fun launchRematchNow() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        if (inRace) return
        val opts = rematchOptions
        val votes = rematchVotes.toMap()
        cancelRematchCountdown()

        val winnerId = if (opts.isNotEmpty()) {
            resolveRematchWinner(opts, votes)
        } else {
            REMATCH_OPTION_RANDOM
        }

        val targetMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            val nextMode = nextPlannedHybridMode
                ?: resolveHybridStageMode(currentRules, hybridStageNumber, activeConcreteMode)
            hybridStageNumber += 1
            activeConcreteMode = nextMode
            nextPlannedHybridMode = resolveHybridStageMode(currentRules, hybridStageNumber, nextMode)
            nextMode
        } else {
            currentRules.gameModeEnum
        }

        when (targetMode) {
            GameMode.ARENA -> {
                val nextShape = if (winnerId == REMATCH_OPTION_RANDOM) {
                    ArenaShape.entries.filter { it != currentArenaShape }.randomOrNull() ?: currentArenaShape
                } else {
                    runCatching { ArenaShape.valueOf(winnerId) }.getOrDefault(currentArenaShape)
                }
                currentArenaShape = nextShape
                currentTrackId = nextShape.themeTrackId
                currentCustomTrack = null
                _activeTrack.value = TrackCatalog.getArenaTrack(nextShape)
                addSystemChat("Next up: ${nextShape.displayName}!")
            }
            GameMode.STUNTS -> {
                val nextStunt = if (winnerId == REMATCH_OPTION_RANDOM) {
                    StuntVariant.entries.filter { it != currentStuntVariant }.randomOrNull() ?: currentStuntVariant
                } else {
                    runCatching { StuntVariant.valueOf(winnerId) }.getOrDefault(currentStuntVariant)
                }
                currentStuntVariant = nextStunt
                currentTrackId = nextStunt.themeTrackId
                currentCustomTrack = null
                _activeTrack.value = TrackCatalog.getStuntTrack(nextStunt)
                addSystemChat("Next stunt event: ${nextStunt.displayName}!")
            }
            GameMode.DRIFT -> {
                val chosenDrift = if (winnerId == REMATCH_OPTION_RANDOM) {
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
                addSystemChat("Next Drift track: ${chosenDrift.name}!")
            }
            else -> {
                if (winnerId == REMATCH_OPTION_RANDOM) {
                    if (Random.nextFloat() < 0.35f) {
                        val generated = TrackCatalog.generateRandomTrack(seed = System.currentTimeMillis())
                        currentCustomTrack = generated
                        currentTrackId = runCatching { TrackId.valueOf(generated.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
                        _activeTrack.value = TrackCatalog.buildCustomTrack(generated)
                        addSystemChat("Next circuit: ${generated.name}!")
                    } else {
                        val choices = if (currentCustomTrack != null) {
                            TrackId.entries.toList()
                        } else {
                            TrackId.entries.filter { it != currentTrackId }
                        }
                        val nextTrack = choices.randomOrNull() ?: currentTrackId
                        currentTrackId = nextTrack
                        currentCustomTrack = null
                        _activeTrack.value = TrackCatalog.getTrack(nextTrack)
                        addSystemChat("Next circuit: ${nextTrack.displayName}!")
                    }
                } else if (currentCustomTrack != null && winnerId == currentCustomTrack?.id) {
                    val replayCustom = currentCustomTrack!!
                    currentTrackId = runCatching { TrackId.valueOf(replayCustom.themeId) }
                        .getOrDefault(TrackId.NEON_ROOFTOP)
                    _activeTrack.value = TrackCatalog.buildCustomTrack(replayCustom)
                    addSystemChat("Vote winner: ${replayCustom.name} (Race Again)!")
                } else {
                    val nextTrack = runCatching { TrackId.valueOf(winnerId) }.getOrDefault(currentTrackId)
                    currentTrackId = nextTrack
                    currentCustomTrack = null
                    _activeTrack.value = TrackCatalog.getTrack(nextTrack)
                    addSystemChat("Vote winner: ${nextTrack.displayName}!")
                }
            }
        }

        startRace()
    }

    fun returnToLobby() {
        cancelRematchCountdown()
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
        _activeGhostCar.value = null
        inRace = false
        if (_role.value == SessionRole.Host) {
            broadcastLobbyState()
            broadcastReliablePacket(
                NetworkMessage(
                    type = PacketType.RETURN_TO_LOBBY,
                    senderId = localPlayerId,
                    lobbyState = _lobbyState.value
                )
            )
            _navEvents.tryEmit(RaceNavEvent.BackToLobby)
        } else if (_role.value == SessionRole.Solo) {
            publishLocalLobbyState()
            _navEvents.tryEmit(RaceNavEvent.BackToLobby)
        }
    }

    private fun awardChampionshipPoints(snapshot: RaceSnapshot) {
        val pointsTable = listOf(10, 6, 4, 2)
        synchronized(playersList) {
            snapshot.racers.forEach { racer ->
                val pts = pointsTable.getOrElse(racer.rank - 1) { 1 }
                val idx = playersList.indexOfFirst { it.id == racer.playerId }
                if (idx >= 0) {
                    val old = playersList[idx]
                    playersList[idx] = old.copy(
                        sessionPoints = old.sessionPoints + pts,
                        wins = old.wins + if (racer.rank == 1) 1 else 0
                    )
                }
            }
        }
        if (_role.value == SessionRole.Host) {
            broadcastLobbyState()
        } else {
            publishLocalLobbyState()
        }
    }

    fun leaveSession() {
        cancelRematchCountdown()
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
        inRace = false

        discovery.stopAdvertising()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null

        hostUdpReadJob?.cancel()
        hostUdpReadJob = null
        runCatching { hostUdpSocket?.close() }
        hostUdpSocket = null
        hostUdpPort = 0

        interfaceUdpReaderJobs.values.forEach { it.cancel() }
        interfaceUdpReaderJobs.clear()
        interfaceUdpSockets.values.forEach { runCatching { it.close() } }
        interfaceUdpSockets.clear()

        connectedClients.values.forEach {
            it.writerJob?.cancel()
            it.reliableChannel.close()
            it.snapshotChannel.close()
            runCatching { it.socket.close() }
        }
        connectedClients.clear()

        clientUdpSendJob?.cancel()
        clientUdpSendJob = null
        clientUdpReadJob?.cancel()
        clientUdpReadJob = null
        runCatching { clientUdpSocket?.close() }
        clientUdpSocket = null
        clientHostUdpPort = 0
        clientHostAddr = null

        clientReadJob?.cancel()
        clientReadJob = null
        clientWriteJob?.cancel()
        clientWriteJob = null
        clientSendChannel?.close()
        clientSendChannel = null
        clientFastInputChannel?.close()
        clientFastInputChannel = null
        runCatching { clientSocket?.close() }
        clientSocket = null

        releaseLowLatencyWifiLock()

        _role.value = SessionRole.None
        _lobbyState.value = null
        _raceSnapshot.value = null
        _activeTrack.value = null
        _activeChallengeGhost.value = null
        _activeGhostCar.value = null
        _isDailyChallenge.value = false
        dailySeedForSession = null
        currentCustomTrack = null
    }

    private fun addSystemChat(text: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString().take(8),
            senderId = "system",
            senderName = "Race Control",
            text = text,
            timestampMs = System.currentTimeMillis(),
            isSystem = true
        )
        synchronized(chatLog) {
            chatLog.add(msg)
            if (chatLog.size > 50) chatLog.removeAt(0)
        }
    }

    private fun appendAndBroadcastChat(chat: ChatMessage) {
        synchronized(chatLog) {
            chatLog.add(chat)
            if (chatLog.size > 50) chatLog.removeAt(0)
        }
        if (_role.value == SessionRole.Host) {
            broadcastReliablePacket(
                NetworkMessage(PacketType.CHAT_BROADCAST, senderId = chat.senderId, chatMessage = chat)
            )
            broadcastLobbyState()
        } else {
            publishLocalLobbyState()
        }
    }

    private fun buildLobbyPayload(): LobbyStatePayload {
        val playersCopy = synchronized(playersList) { playersList.toList() }
        val chatCopy = synchronized(chatLog) { chatLog.toList() }
        val hostName = playersCopy.firstOrNull { it.isHost }?.name ?: "Host"
        val effectiveLobbyMode = if (currentRules.gameModeEnum == GameMode.HYBRID) {
            activeConcreteMode
        } else {
            currentRules.gameModeEnum
        }
        return LobbyStatePayload(
            hostId = playersCopy.firstOrNull { it.isHost }?.id ?: localPlayerId,
            lobbyName = if (_role.value == SessionRole.Solo) "Solo Paddock" else "$hostName's RC Lobby",
            hostIp = discovery.getPrimaryLocalIp(),
            tcpPort = tcpPort,
            udpPort = hostUdpPort,
            trackId = when (effectiveLobbyMode) {
                GameMode.ARENA -> currentArenaShape.themeTrackId.name
                GameMode.STUNTS -> currentStuntVariant.themeTrackId.name
                else -> currentTrackId.name
            },
            arenaShape = currentArenaShape.name,
            stuntVariant = currentStuntVariant.name,
            totalLaps = currentTotalLaps,
            players = playersCopy,
            chatHistory = chatCopy,
            inRace = inRace,
            rules = currentRules,
            activeCustomTrack = currentCustomTrack
        )
    }

    private fun publishLocalLobbyState() {
        _lobbyState.value = buildLobbyPayload()
    }

    private fun broadcastLobbyState() {
        val payload = buildLobbyPayload()
        _lobbyState.value = payload
        connectedClients.values.forEach { client ->
            val preferredPort = client.preferredLocalUdpPort
            val clientPayload = if (preferredPort > 0 && preferredPort != payload.udpPort) {
                payload.copy(udpPort = preferredPort)
            } else {
                payload
            }
            val json = msgAdapter.toJson(
                NetworkMessage(
                    type = PacketType.LOBBY_STATE,
                    senderId = localPlayerId,
                    lobbyState = clientPayload
                )
            )
            client.reliableChannel.trySend(json)
        }
    }

    private fun broadcastReliablePacket(msg: NetworkMessage) {
        val json = msgAdapter.toJson(msg)
        connectedClients.values.forEach { client ->
            client.reliableChannel.trySend(json)
        }
    }

    private fun PlayerProfile.toLobbyPlayer(isHost: Boolean, isReady: Boolean) = LobbyPlayer(
        id = playerId,
        name = name,
        isHost = isHost,
        isBot = false,
        isReady = isReady,
        carType = carType.name,
        primaryColorArgb = primaryColorArgb,
        accentColorArgb = accentColorArgb,
        equippedUpgradesMask = equippedUpgradesMask
    )
}
