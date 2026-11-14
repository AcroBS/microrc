package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.CustomTrackData
import com.example.model.DiscoveredLobby
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
import com.example.model.TrackDefinition
import com.example.model.TrackId
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

sealed interface SessionRole {
    data object None : SessionRole
    data object Solo : SessionRole
    data object Host : SessionRole
    data object Client : SessionRole
}

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

    private val _navEvents = MutableSharedFlow<RaceNavEvent>(extraBufferCapacity = 8)
    val navEvents: SharedFlow<RaceNavEvent> = _navEvents.asSharedFlow()

    private var wifiLock: WifiManager.WifiLock? = null

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var physicsJob: Job? = null

    private var hostUdpSocket: DatagramSocket? = null
    private var hostUdpReadJob: Job? = null
    private var hostUdpPort: Int = 0
    private val hostStateSeq = AtomicLong(0L)

    private var clientSocket: Socket? = null
    private var clientReadJob: Job? = null
    private var clientWriteJob: Job? = null
    private var clientSendChannel: Channel<String>? = null

    private var clientUdpSocket: DatagramSocket? = null
    private var clientUdpReadJob: Job? = null
    private var clientUdpSendJob: Job? = null
    private var clientHostAddr: InetAddress? = null
    private var clientHostUdpPort: Int = 0
    private val clientInputSeq = AtomicLong(0L)
    private var lastReceivedUdpStateSeq = -1L
    private val clientFinishEmitted = AtomicBoolean(false)

    private var localPlayerId: String = ""
    private var localInput = ControlInput()
    private var pendingLocalPowerUp = false
    private var lastSentThrottle = 0f
    private var lastSentSteer = 0f
    private var lastInputSendNs = 0L

    private class ConnectedClient(
        val playerId: String,
        val socket: Socket,
        val reliableChannel: Channel<String> = Channel(capacity = 32),
        val snapshotChannel: Channel<String> = Channel(capacity = Channel.CONFLATED)
    ) {
        var writerJob: Job? = null
        @Volatile var udpAddress: SocketAddress? = null
        @Volatile var lastInputSeq: Long = -1L
    }

    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()
    private val chatLog = mutableListOf<ChatMessage>()
    private val playersList = mutableListOf<LobbyPlayer>()
    private var currentTrackId: TrackId = TrackId.LIVING_ROOM_GP
    private var currentCustomTrack: CustomTrackData? = null
    private var currentRules: RaceRulesSettings = RaceRulesSettings()
    private var currentTotalLaps: Int = 3
    private var inRace: Boolean = false
    private var tcpPort: Int = DEFAULT_TCP_PORT
    private var physicsEngine: RacePhysicsEngine? = null
    private var pointsAwardedForCurrentRace = false

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
        initialCustomTrack: CustomTrackData? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        currentRules = initialRules
        currentTotalLaps = initialRules.totalLaps.coerceIn(1, 9)
        currentCustomTrack = initialCustomTrack
        if (initialCustomTrack != null) {
            currentTrackId = runCatching { TrackId.valueOf(initialCustomTrack.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
        }
        _role.value = SessionRole.Solo
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        val botNames = listOf("Volt-AI", "Apex-AI", "Turbo-AI")
        val botCars = listOf(RcCarType.APEX_FORMULA, RcCarType.DRIFT_PHANTOM, RcCarType.TUNDRA_MONSTER)
        botNames.forEachIndexed { idx, botName ->
            val preset = RcColorPresets[(idx + 1) % RcColorPresets.size]
            playersList.add(
                LobbyPlayer(
                    id = "bot_$idx",
                    name = botName,
                    isHost = false,
                    isBot = true,
                    isReady = true,
                    carType = botCars[idx].name,
                    primaryColorArgb = preset.primaryArgb,
                    accentColorArgb = preset.accentArgb
                )
            )
        }
        addSystemChat("Solo paddock ready. Customize track or hit Start Race!")
        publishLocalLobbyState()
        _navEvents.tryEmit(RaceNavEvent.EnterLobby)
    }

    fun hostLobby(
        profile: PlayerProfile,
        initialRules: RaceRulesSettings = RaceRulesSettings(),
        initialCustomTrack: CustomTrackData? = null
    ) {
        leaveSession()
        localPlayerId = profile.playerId
        currentRules = initialRules
        currentTotalLaps = initialRules.totalLaps.coerceIn(1, 9)
        currentCustomTrack = initialCustomTrack
        if (initialCustomTrack != null) {
            currentTrackId = runCatching { TrackId.valueOf(initialCustomTrack.themeId) }.getOrDefault(TrackId.NEON_ROOFTOP)
        }
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
                }.getOrNull()?.apply {
                    runCatching { trafficClass = 0x10 }
                }
                hostUdpSocket = udpSock
                hostUdpPort = udpSock?.localPort ?: 0

                if (udpSock != null) {
                    startHostUdpReader(udpSock)
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
                            runCatching { socket.trafficClass = 0x10 }
                            handleIncomingSocket(socket)
                        } catch (_: Exception) {
                            if (ss.isClosed) break
                        }
                    }
                }
            } catch (e: Exception) {
                _connectionError.value = "Failed to open host socket: ${e.message}"
                leaveSession()
            }
        }
    }

    private fun startHostUdpReader(udpSock: DatagramSocket) {
        hostUdpReadJob?.cancel()
        hostUdpReadJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(512)
            while (isActive && !udpSock.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    udpSock.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val helloId = RaceTelemetryCodec.decodeHello(msg)
                    if (helloId != null) {
                        connectedClients[helloId]?.udpAddress = pkt.socketAddress
                        continue
                    }
                    val decoded = RaceTelemetryCodec.decodeInput(msg) ?: continue
                    val client = connectedClients[decoded.playerId] ?: continue
                    client.udpAddress = pkt.socketAddress
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
        lastReceivedUdpStateSeq = -1L
        acquireLowLatencyWifiLock()

        scope.launch(Dispatchers.IO) {
            try {
                val targetIp = ip.trim()
                val socket = Socket()
                socket.tcpNoDelay = true
                runCatching { socket.trafficClass = 0x10 }
                socket.connect(InetSocketAddress(targetIp, port), 4500)
                clientSocket = socket
                clientHostAddr = socket.inetAddress

                val sendChan = Channel<String>(capacity = 48)
                clientSendChannel = sendChan

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                clientWriteJob = scope.launch(Dispatchers.IO) {
                    try {
                        for (line in sendChan) {
                            writer.write(line)
                            writer.write("\n")
                            writer.flush()
                        }
                    } catch (_: Exception) {
                    }
                }

                // Open client UDP socket for ultra-low-latency gameplay packets
                val udpSock = DatagramSocket().apply {
                    runCatching { trafficClass = 0x10 }
                }
                clientUdpSocket = udpSock
                startClientUdpReader(udpSock)

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
                                    _activeTrack.value = payload.activeCustomTrack?.let {
                                        TrackCatalog.buildCustomTrack(it)
                                    } ?: TrackCatalog.getTrack(payload.trackId)
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
                                    lastReceivedUdpStateSeq = -1L
                                    clientFinishEmitted.set(false)
                                    msg.rules?.let { r ->
                                        currentRules = r
                                        _lobbyState.value = _lobbyState.value?.copy(rules = r)
                                    }
                                    val startSnap = msg.raceSnapshot
                                    val baseTrack = msg.customTrack?.let {
                                        TrackCatalog.buildCustomTrack(it)
                                    } ?: TrackCatalog.getTrack(startSnap?.trackId ?: TrackId.LIVING_ROOM_GP.name)
                                    val syncedTrack = if (msg.ramps != null) {
                                        baseTrack.copy(ramps = msg.ramps)
                                    } else {
                                        baseTrack
                                    }
                                    _activeTrack.value = syncedTrack
                                    _raceSnapshot.value = startSnap
                                    sendClientUdpHello()
                                    startClientUdpInputHeartbeat()
                                    _navEvents.tryEmit(RaceNavEvent.StartRace)
                                }
                                PacketType.RACE_SNAPSHOT -> {
                                    val snap = msg.raceSnapshot ?: continue
                                    // Accept TCP snapshot if finished OR if UDP hasn't delivered any frames yet
                                    if (snap.phase == RacePhase.FINISHED.name) {
                                        _raceSnapshot.value = snap
                                        _lastResults.value = snap
                                        clientUdpSendJob?.cancel()
                                        if (clientFinishEmitted.compareAndSet(false, true)) {
                                            _navEvents.tryEmit(RaceNavEvent.ShowResults)
                                        }
                                    } else if (lastReceivedUdpStateSeq < 0L) {
                                        _raceSnapshot.value = snap
                                    }
                                }
                                PacketType.RETURN_TO_LOBBY -> {
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
                _connectionError.value = "Could not connect to $ip:$port (${e.localizedMessage ?: "timeout"})"
                _role.value = SessionRole.None
                releaseLowLatencyWifiLock()
            }
        }
    }

    private fun startClientUdpReader(udpSock: DatagramSocket) {
        clientUdpReadJob?.cancel()
        clientUdpReadJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(2048)
            while (isActive && !udpSock.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    udpSock.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val roster = _lobbyState.value?.players.orEmpty()
                    val decoded = RaceTelemetryCodec.decodeSnapshot(
                        text = msg,
                        roster = roster,
                        previousSnapshot = _raceSnapshot.value,
                        activeTrack = _activeTrack.value
                    ) ?: continue

                    if (decoded.seq > lastReceivedUdpStateSeq) {
                        lastReceivedUdpStateSeq = decoded.seq
                        _raceSnapshot.value = decoded.snapshot
                        if (decoded.snapshot.phase == RacePhase.FINISHED.name) {
                            _lastResults.value = decoded.snapshot
                            clientUdpSendJob?.cancel()
                            if (clientFinishEmitted.compareAndSet(false, true)) {
                                _navEvents.tryEmit(RaceNavEvent.ShowResults)
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (udpSock.isClosed) break
                }
            }
        }
    }

    private fun startClientUdpInputHeartbeat() {
        clientUdpSendJob?.cancel()
        clientUdpSendJob = scope.launch(Dispatchers.IO) {
            while (isActive && _role.value == SessionRole.Client) {
                sendClientUdpInput(usePowerUp = false)
                delay(20L) // Steady 50Hz UDP input stream while racing
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
                    val data = RaceTelemetryCodec.encodeHello(localPlayerId)
                    sock.send(DatagramPacket(data, data.size, addr, port))
                }
                delay(40L)
            }
        }
    }

    private fun sendClientUdpInput(usePowerUp: Boolean) {
        val sock = clientUdpSocket ?: return
        val addr = clientHostAddr ?: return
        val port = clientHostUdpPort
        if (port <= 0) return
        val inp = synchronized(this) { localInput }
        val seq = clientInputSeq.incrementAndGet()
        val data = RaceTelemetryCodec.encodeInput(
            playerId = localPlayerId,
            seq = seq,
            input = inp.copy(usePowerUp = usePowerUp)
        )
        runCatching {
            sock.send(DatagramPacket(data, data.size, addr, port))
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
                    val msg = runCatching { msgAdapter.fromJson(line) }.getOrNull() ?: continue
                    handleHostReceivedMessage(joiningPlayer.id, msg)
                }
            } catch (_: Exception) {
            } finally {
                val cid = registeredClient?.playerId
                if (cid != null) {
                    connectedClients.remove(cid)
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
                            accentColorArgb = updated.accentColorArgb
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
                            accentColorArgb = profile.accentColorArgb
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
            SessionRole.None -> Unit
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
            SessionRole.None -> Unit
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

    fun updateRules(rules: RaceRulesSettings) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentRules = rules
        currentTotalLaps = rules.totalLaps.coerceIn(1, 9)
        val summary = buildList {
            add(rules.winConditionEnum.displayName)
            if (rules.autoAccelerate) add("Auto-Gas")
            if (rules.hardTrackBoundary) add("Bounce Walls")
            if (rules.randomJumpsEnabled) add("Jumps")
            if (rules.randomConditionsEnabled) add("Random Weather")
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
            SessionRole.None -> Unit
        }
    }

    fun startRace() {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        val snapshotPlayers = synchronized(playersList) { playersList.toList() }
        if (snapshotPlayers.isEmpty()) return

        inRace = true
        pointsAwardedForCurrentRace = false
        hostStateSeq.set(0L)
        val customData = currentCustomTrack
        val baseTrack = if (customData != null) {
            TrackCatalog.buildCustomTrack(customData)
        } else {
            TrackCatalog.getTrack(currentTrackId)
        }
        val track = if (customData == null) {
            TrackCatalog.withRandomJumps(baseTrack, enabled = currentRules.randomJumpsEnabled)
        } else if (!currentRules.randomJumpsEnabled) {
            baseTrack.copy(ramps = emptyList())
        } else if (baseTrack.ramps.isEmpty()) {
            TrackCatalog.withRandomJumps(baseTrack, enabled = true)
        } else {
            baseTrack
        }
        _activeTrack.value = track

        val engine = RacePhysicsEngine(track, currentTotalLaps, snapshotPlayers, currentRules)
        physicsEngine = engine

        val initialSnap = engine.step(0.016f)
        _raceSnapshot.value = initialSnap

        if (_role.value == SessionRole.Host) {
            broadcastLobbyState()
            broadcastReliablePacket(
                NetworkMessage(
                    type = PacketType.RACE_START,
                    senderId = localPlayerId,
                    raceSnapshot = initialSnap,
                    rules = currentRules,
                    customTrack = customData,
                    ramps = track.ramps
                )
            )
        }
        _navEvents.tryEmit(RaceNavEvent.StartRace)

        physicsJob?.cancel()
        physicsJob = scope.launch(Dispatchers.Default) {
            var tickCounter = 0
            var lastNs = System.nanoTime()
            while (isActive && inRace) {
                delay(16L)
                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.008f, 0.04f)
                lastNs = nowNs

                val inputToApply = synchronized(this@WifiP2pRaceManager) {
                    val inp = localInput.copy(usePowerUp = pendingLocalPowerUp)
                    pendingLocalPowerUp = false
                    inp
                }
                engine.submitInput(localPlayerId, inputToApply)

                val snap = engine.step(dt)
                _raceSnapshot.value = snap

                tickCounter++
                val isFinished = snap.phase == RacePhase.FINISHED.name
                if (isFinished) {
                    if (!pointsAwardedForCurrentRace) {
                        pointsAwardedForCurrentRace = true
                        awardChampionshipPoints(snap)
                    }
                    _lastResults.value = snap
                    if (_role.value == SessionRole.Host) {
                        broadcastReliablePacket(
                            NetworkMessage(
                                type = PacketType.RACE_SNAPSHOT,
                                senderId = localPlayerId,
                                raceSnapshot = snap
                            )
                        )
                    }
                    _navEvents.tryEmit(RaceNavEvent.ShowResults)
                    inRace = false
                    break
                } else if (_role.value == SessionRole.Host) {
                    // Send ultra-compact 60Hz UDP state to clients that registered UDP,
                    // and fallback coalesced 20Hz TCP snapshot only for clients without UDP.
                    broadcastRaceTelemetry(snap, sendTcpFallback = (tickCounter % 3 == 0))
                }
            }
        }
    }

    private fun broadcastRaceTelemetry(snap: RaceSnapshot, sendTcpFallback: Boolean) {
        val udpSock = hostUdpSocket
        val seq = hostStateSeq.incrementAndGet()
        var udpBytes: ByteArray? = null
        var tcpJson: String? = null

        for (client in connectedClients.values) {
            val udpAddr = client.udpAddress
            if (udpSock != null && !udpSock.isClosed && udpAddr != null) {
                if (udpBytes == null) {
                    udpBytes = RaceTelemetryCodec.encodeSnapshot(seq, snap)
                }
                val data = udpBytes
                runCatching {
                    udpSock.send(DatagramPacket(data, data.size, udpAddr))
                }
            } else if (sendTcpFallback) {
                if (tcpJson == null) {
                    tcpJson = msgAdapter.toJson(
                        NetworkMessage(
                            type = PacketType.RACE_SNAPSHOT,
                            senderId = localPlayerId,
                            raceSnapshot = snap
                        )
                    )
                }
                client.snapshotChannel.trySend(tcpJson)
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
            val changedSignificantly = throttleFlipped ||
                abs(throttle - lastSentThrottle) > 0.05f ||
                abs(steer - lastSentSteer) > 0.03f
            if (throttleFlipped || (changedSignificantly && (nowNs - lastInputSendNs) >= 12_000_000L)) {
                lastSentThrottle = throttle
                lastSentSteer = steer
                lastInputSendNs = nowNs
                scope.launch(Dispatchers.IO) {
                    if (clientHostUdpPort > 0 && clientUdpSocket != null) {
                        sendClientUdpInput(usePowerUp = false)
                    } else {
                        clientSendChannel?.trySend(
                            msgAdapter.toJson(
                                NetworkMessage(
                                    type = PacketType.CONTROL_INPUT,
                                    senderId = localPlayerId,
                                    controlInput = ControlInput(throttle = throttle, steer = steer, usePowerUp = false)
                                )
                            )
                        )
                    }
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

    fun returnToLobby() {
        physicsJob?.cancel()
        physicsJob = null
        physicsEngine = null
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
        runCatching { clientSocket?.close() }
        clientSocket = null

        releaseLowLatencyWifiLock()

        _role.value = SessionRole.None
        _lobbyState.value = null
        _raceSnapshot.value = null
        _activeTrack.value = null
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
        return LobbyStatePayload(
            hostId = playersCopy.firstOrNull { it.isHost }?.id ?: localPlayerId,
            lobbyName = if (_role.value == SessionRole.Solo) "Solo Paddock" else "$hostName's RC Lobby",
            hostIp = discovery.getPrimaryLocalIp(),
            tcpPort = tcpPort,
            udpPort = hostUdpPort,
            trackId = currentTrackId.name,
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
        broadcastReliablePacket(
            NetworkMessage(
                type = PacketType.LOBBY_STATE,
                senderId = localPlayerId,
                lobbyState = payload
            )
        )
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
        accentColorArgb = accentColorArgb
    )
}
