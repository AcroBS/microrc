package com.example.network

import android.content.Context
import com.example.data.TrackCatalog
import com.example.engine.RacePhysicsEngine
import com.example.model.ChatMessage
import com.example.model.ControlInput
import com.example.model.DiscoveredLobby
import com.example.model.LobbyPlayer
import com.example.model.LobbyStatePayload
import com.example.model.NetworkMessage
import com.example.model.PacketType
import com.example.model.PlayerProfile
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import com.example.model.RcCarType
import com.example.model.RcColorPresets
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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    context: Context,
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

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _navEvents = MutableSharedFlow<RaceNavEvent>(extraBufferCapacity = 8)
    val navEvents: SharedFlow<RaceNavEvent> = _navEvents.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var physicsJob: Job? = null
    private var clientSocket: Socket? = null
    private var clientReadJob: Job? = null
    private var clientWriteJob: Job? = null
    private var clientSendChannel: Channel<String>? = null

    private var localPlayerId: String = ""
    private var localInput = ControlInput()
    private var pendingLocalPowerUp = false

    private class ConnectedClient(
        val playerId: String,
        val socket: Socket,
        val writeChannel: Channel<String> = Channel(capacity = 48)
    ) {
        var readerJob: Job? = null
        var writerJob: Job? = null
    }

    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()
    private val chatLog = mutableListOf<ChatMessage>()
    private val playersList = mutableListOf<LobbyPlayer>()
    private var currentTrackId: TrackId = TrackId.LIVING_ROOM_GP
    private var currentTotalLaps: Int = 3
    private var inRace: Boolean = false
    private var tcpPort: Int = DEFAULT_TCP_PORT
    private var physicsEngine: RacePhysicsEngine? = null
    private var pointsAwardedForCurrentRace = false

    fun clearError() {
        _connectionError.value = null
    }

    fun startSoloSession(profile: PlayerProfile) {
        leaveSession()
        localPlayerId = profile.playerId
        _role.value = SessionRole.Solo
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        // Pre-populate 3 AI rivals for instant fun solo racing
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

    fun hostLobby(profile: PlayerProfile) {
        leaveSession()
        localPlayerId = profile.playerId
        _role.value = SessionRole.Host
        inRace = false
        chatLog.clear()
        playersList.clear()
        playersList.add(profile.toLobbyPlayer(isHost = true, isReady = true))
        addSystemChat("Lobby hosted on local Wi-Fi. Waiting for 2-4 players...")

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

                _navEvents.tryEmit(RaceNavEvent.EnterLobby)

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
                _connectionError.value = "Failed to open host socket: ${e.message}"
                leaveSession()
            }
        }
    }

    fun joinLobby(ip: String, port: Int, profile: PlayerProfile) {
        leaveSession()
        localPlayerId = profile.playerId
        _role.value = SessionRole.Client
        _connectionError.value = null

        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ip.trim(), port), 4500)
                clientSocket = socket

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

                // Send JOIN_REQUEST immediately
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
                                    _raceSnapshot.value = msg.raceSnapshot
                                    _navEvents.tryEmit(RaceNavEvent.StartRace)
                                }
                                PacketType.RACE_SNAPSHOT -> {
                                    val snap = msg.raceSnapshot ?: continue
                                    _raceSnapshot.value = snap
                                    if (snap.phase == RacePhase.FINISHED.name) {
                                        _lastResults.value = snap
                                        _navEvents.tryEmit(RaceNavEvent.ShowResults)
                                    }
                                }
                                PacketType.RETURN_TO_LOBBY -> {
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
            }
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
                    // Replace if same ID reconnecting, or remove a bot to make space for human!
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
                        for (line in client.writeChannel) {
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
        addSystemChat("Track set to ${trackId.displayName}")
        if (_role.value == SessionRole.Host) broadcastLobbyState() else publishLocalLobbyState()
    }

    fun setTotalLaps(laps: Int) {
        if (_role.value != SessionRole.Host && _role.value != SessionRole.Solo) return
        currentTotalLaps = laps.coerceIn(1, 7)
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
        val track = TrackCatalog.getTrack(currentTrackId)
        val engine = RacePhysicsEngine(track, currentTotalLaps, snapshotPlayers)
        physicsEngine = engine

        val initialSnap = engine.step(0.016f)
        _raceSnapshot.value = initialSnap

        if (_role.value == SessionRole.Host) {
            broadcastLobbyState()
            broadcastPacket(
                NetworkMessage(
                    type = PacketType.RACE_START,
                    senderId = localPlayerId,
                    raceSnapshot = initialSnap
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
                        broadcastPacket(
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
                } else if (_role.value == SessionRole.Host && tickCounter % 2 == 0) {
                    // ~30Hz network state sync over local Wi-Fi TCP
                    broadcastPacket(
                        NetworkMessage(
                            type = PacketType.RACE_SNAPSHOT,
                            senderId = localPlayerId,
                            raceSnapshot = snap
                        )
                    )
                }
            }
        }
    }

    fun updateLocalControlInput(throttle: Float, steer: Float) {
        synchronized(this) {
            localInput = localInput.copy(throttle = throttle, steer = steer)
        }
        if (_role.value == SessionRole.Client) {
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

    fun triggerLocalPowerUp() {
        synchronized(this) {
            pendingLocalPowerUp = true
        }
        if (_role.value == SessionRole.Client) {
            clientSendChannel?.trySend(
                msgAdapter.toJson(
                    NetworkMessage(
                        type = PacketType.CONTROL_INPUT,
                        senderId = localPlayerId,
                        controlInput = localInput.copy(usePowerUp = true)
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
            broadcastPacket(
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

        connectedClients.values.forEach {
            it.readerJob?.cancel()
            it.writerJob?.cancel()
            it.writeChannel.close()
            runCatching { it.socket.close() }
        }
        connectedClients.clear()

        clientReadJob?.cancel()
        clientReadJob = null
        clientWriteJob?.cancel()
        clientWriteJob = null
        clientSendChannel?.close()
        clientSendChannel = null
        runCatching { clientSocket?.close() }
        clientSocket = null

        _role.value = SessionRole.None
        _lobbyState.value = null
        _raceSnapshot.value = null
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
            broadcastPacket(NetworkMessage(PacketType.CHAT_BROADCAST, senderId = chat.senderId, chatMessage = chat))
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
            trackId = currentTrackId.name,
            totalLaps = currentTotalLaps,
            players = playersCopy,
            chatHistory = chatCopy,
            inRace = inRace
        )
    }

    private fun publishLocalLobbyState() {
        _lobbyState.value = buildLobbyPayload()
    }

    private fun broadcastLobbyState() {
        val payload = buildLobbyPayload()
        _lobbyState.value = payload
        broadcastPacket(
            NetworkMessage(
                type = PacketType.LOBBY_STATE,
                senderId = localPlayerId,
                lobbyState = payload
            )
        )
    }

    private fun broadcastPacket(msg: NetworkMessage) {
        val json = msgAdapter.toJson(msg)
        connectedClients.values.forEach { client ->
            client.writeChannel.trySend(json)
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
