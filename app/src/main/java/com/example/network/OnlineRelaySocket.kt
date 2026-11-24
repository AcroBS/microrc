package com.example.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages a real-time WebSocket connection to the stateless room relay for active
 * in-race online multiplayer telemetry and steering inputs.
 */
class OnlineRelaySocket(
    private val scope: CoroutineScope,
    private val client: OkHttpClient = createDefaultClient(),
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onFrameReceived: (String) -> Unit = {}
) {
    companion object {
        const val RELAY_BASE_URL = "wss://microrc.hankers.aero/"

        fun createDefaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

        fun buildRelayUrl(raceId: String): String {
            val cleanRaceId = raceId.trim().ifEmpty { "default_room" }
            val encoded = URLEncoder.encode(cleanRaceId, "UTF-8")
            return "$RELAY_BASE_URL?raceId=$encoded"
        }
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val lock = Any()
    private val generation = AtomicLong(0L)
    @Volatile private var desiredRaceId: String? = null
    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var activeListener: WebSocketListener? = null
    @Volatile private var reconnectAttempt: Int = 0
    private var reconnectJob: Job? = null

    fun connect(raceId: String) {
        val cleanRaceId = raceId.trim()
        if (cleanRaceId.isEmpty()) return

        val gen: Long
        val previous: WebSocket?
        synchronized(lock) {
            if (desiredRaceId == cleanRaceId && activeListener != null) {
                return
            }
            reconnectJob?.cancel()
            reconnectJob = null
            previous = activeSocket
            activeSocket = null
            activeListener = null
            gen = generation.incrementAndGet()
            desiredRaceId = cleanRaceId
            reconnectAttempt = 0
            _isConnected.value = false
        }
        previous?.close(1000, "Switching relay room")
        openWebSocket(cleanRaceId, gen)
    }

    fun disconnect(reason: String = "Race ended") {
        val toClose: WebSocket?
        synchronized(lock) {
            generation.incrementAndGet()
            desiredRaceId = null
            reconnectAttempt = 0
            reconnectJob?.cancel()
            reconnectJob = null
            toClose = activeSocket
            activeSocket = null
            activeListener = null
            _isConnected.value = false
        }
        toClose?.close(1000, reason.take(64))
    }

    fun sendText(frame: String): Boolean {
        val ws = activeSocket ?: return false
        if (!_isConnected.value) return false
        return runCatching { ws.send(frame) }.getOrDefault(false)
    }

    private fun openWebSocket(raceId: String, expectedGen: Long) {
        val request = Request.Builder()
            .url(buildRelayUrl(raceId))
            .build()

        lateinit var listener: WebSocketListener
        listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val activeNow: Boolean
                synchronized(lock) {
                    activeNow = (generation.get() == expectedGen &&
                        desiredRaceId == raceId &&
                        activeListener === listener)
                    if (activeNow) {
                        activeSocket = webSocket
                        reconnectAttempt = 0
                        _isConnected.value = true
                    }
                }
                if (activeNow) {
                    onConnected()
                } else {
                    webSocket.close(1000, "Stale relay socket")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation.get() == expectedGen &&
                    desiredRaceId == raceId &&
                    activeListener === listener
                ) {
                    onFrameReceived(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation.get() == expectedGen &&
                    desiredRaceId == raceId &&
                    activeListener === listener
                ) {
                    onFrameReceived(bytes.utf8())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleSocketTermination(listener, raceId, expectedGen)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleSocketTermination(listener, raceId, expectedGen)
            }
        }

        val shouldConnect = synchronized(lock) {
            if (generation.get() == expectedGen && desiredRaceId == raceId) {
                activeListener = listener
                true
            } else {
                false
            }
        }
        if (!shouldConnect) return

        val ws = client.newWebSocket(request, listener)
        val closeImmediately: Boolean
        synchronized(lock) {
            if (generation.get() == expectedGen &&
                desiredRaceId == raceId &&
                activeListener === listener
            ) {
                if (activeSocket == null) {
                    activeSocket = ws
                }
                closeImmediately = false
            } else {
                closeImmediately = true
            }
        }
        if (closeImmediately) {
            ws.close(1000, "Cancelled before open")
        }
    }

    private fun handleSocketTermination(
        listener: WebSocketListener,
        raceId: String,
        expectedGen: Long
    ) {
        var shouldNotifyDisconnect = false
        var shouldScheduleReconnect = false
        var attemptNow = 0

        synchronized(lock) {
            if (generation.get() != expectedGen ||
                desiredRaceId != raceId ||
                activeListener !== listener
            ) {
                return
            }
            activeSocket = null
            activeListener = null
            shouldNotifyDisconnect = _isConnected.value
            _isConnected.value = false
            shouldScheduleReconnect = (desiredRaceId == raceId)
            if (shouldScheduleReconnect) {
                attemptNow = reconnectAttempt++
            }
        }

        if (shouldNotifyDisconnect) {
            onDisconnected()
        }

        if (shouldScheduleReconnect) {
            val backoffMs = when (attemptNow) {
                0 -> 700L
                1 -> 1500L
                2 -> 2500L
                else -> 4000L
            }
            synchronized(lock) {
                if (generation.get() != expectedGen || desiredRaceId != raceId) return
                reconnectJob?.cancel()
                reconnectJob = scope.launch(Dispatchers.IO) {
                    delay(backoffMs)
                    if (!isActive) return@launch
                    val stillDesired = synchronized(lock) {
                        generation.get() == expectedGen &&
                            desiredRaceId == raceId &&
                            activeListener == null
                    }
                    if (stillDesired) {
                        openWebSocket(raceId, expectedGen)
                    }
                }
            }
        }
    }
}
