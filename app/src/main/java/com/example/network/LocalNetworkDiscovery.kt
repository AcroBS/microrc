package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.model.DiscoveredLobby
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

class LocalNetworkDiscovery(private val context: Context) {

    companion object {
        const val DISCOVERY_UDP_PORT = 45455
        const val MULTICAST_GROUP = "239.255.45.45"
        const val NSD_SERVICE_TYPE = "_microrc._tcp."
        const val BEACON_PREFIX = "MICRORC_BEACON|"
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val lobbyAdapter = moshi.adapter(DiscoveredLobby::class.java)

    private val _discoveredLobbies = MutableStateFlow<List<DiscoveredLobby>>(emptyList())
    val discoveredLobbies: StateFlow<List<DiscoveredLobby>> = _discoveredLobbies.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var advertiseJob: Job? = null
    private var browseJob: Job? = null
    private var pruneJob: Job? = null

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private val lobbyMap = linkedMapOf<String, DiscoveredLobby>()

    fun getPrimaryLocalIp(): String {
        return getActiveIpv4Interfaces().firstOrNull()?.first ?: "192.168.43.1"
    }

    fun getAllLocalIps(): List<String> = getActiveIpv4Interfaces().map { it.first }

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock == null) {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("MicroRCMulticastLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        }
    }

    fun startAdvertising(scope: CoroutineScope, lobbyProvider: () -> DiscoveredLobby?) {
        stopAdvertising()
        acquireMulticastLock()

        val initial = lobbyProvider()
        if (initial != null) {
            registerNsdService(initial)
        }

        advertiseJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                while (isActive) {
                    val lobby = lobbyProvider()
                    if (lobby != null) {
                        val ip = getPrimaryLocalIp()
                        val payload = lobbyAdapter.toJson(lobby.copy(hostIp = ip))
                        val bytes = (BEACON_PREFIX + payload).toByteArray(Charsets.UTF_8)

                        // 1. Send to every active interface subnet broadcast (covers Wi-Fi & Phone Hotspot!)
                        for ((_, broadcastAddr) in getActiveIpv4Interfaces()) {
                            if (broadcastAddr != null) {
                                runCatching {
                                    socket.send(DatagramPacket(bytes, bytes.size, broadcastAddr, DISCOVERY_UDP_PORT))
                                }
                            }
                        }

                        // 2. Send to global broadcast
                        runCatching {
                            val globalBcast = InetAddress.getByName("255.255.255.255")
                            socket.send(DatagramPacket(bytes, bytes.size, globalBcast, DISCOVERY_UDP_PORT))
                        }

                        // 3. Send to multicast group
                        runCatching {
                            val mcast = InetAddress.getByName(MULTICAST_GROUP)
                            socket.send(DatagramPacket(bytes, bytes.size, mcast, DISCOVERY_UDP_PORT))
                        }
                    }
                    delay(750L)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun stopAdvertising() {
        advertiseJob?.cancel()
        advertiseJob = null
        unregisterNsdService()
        releaseMulticastLock()
    }

    fun startBrowsing(scope: CoroutineScope, localHostIdToIgnore: String? = null) {
        stopBrowsing()
        acquireMulticastLock()
        synchronized(lobbyMap) { lobbyMap.clear() }
        _discoveredLobbies.value = emptyList()

        startNsdDiscovery()

        browseJob = scope.launch(Dispatchers.IO) {
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                    soTimeout = 1200
                    runCatching { joinGroup(InetAddress.getByName(MULTICAST_GROUP)) }
                }
                val buf = ByteArray(4096)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (msg.startsWith(BEACON_PREFIX)) {
                            val json = msg.removePrefix(BEACON_PREFIX)
                            val parsed = runCatching { lobbyAdapter.fromJson(json) }.getOrNull()
                            if (parsed != null && parsed.hostId != localHostIdToIgnore) {
                                val senderIp = packet.address?.hostAddress ?: parsed.hostIp
                                upsertLobby(
                                    parsed.copy(
                                        hostIp = senderIp,
                                        lastSeenMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Expected periodic timeout to check coroutine cancellation
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { socket?.close() }
            }
        }

        pruneJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1500L)
                val now = System.currentTimeMillis()
                synchronized(lobbyMap) {
                    val it = lobbyMap.entries.iterator()
                    var changed = false
                    while (it.hasNext()) {
                        if (now - it.next().value.lastSeenMs > 4500L) {
                            it.remove()
                            changed = true
                        }
                    }
                    if (changed) {
                        _discoveredLobbies.value = lobbyMap.values.toList()
                    }
                }
            }
        }
    }

    fun stopBrowsing() {
        browseJob?.cancel()
        browseJob = null
        pruneJob?.cancel()
        pruneJob = null
        stopNsdDiscovery()
        releaseMulticastLock()
    }

    private fun upsertLobby(lobby: DiscoveredLobby) {
        synchronized(lobbyMap) {
            lobbyMap[lobby.hostId] = lobby
            _discoveredLobbies.value = lobbyMap.values.toList()
        }
    }

    private fun registerNsdService(lobby: DiscoveredLobby) {
        runCatching {
            val mgr = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return
            nsdManager = mgr
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "MicroRC-${lobby.hostName.take(10)}"
                serviceType = NSD_SERVICE_TYPE
                port = lobby.tcpPort
                setAttribute("hostId", lobby.hostId)
                setAttribute("hostName", lobby.hostName)
                setAttribute("trackId", lobby.trackId)
                setAttribute("players", lobby.playerCount.toString())
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        runCatching {
            val mgr = nsdManager
            val listener = nsdRegistrationListener
            if (mgr != null && listener != null) {
                mgr.unregisterService(listener)
            }
        }
        nsdRegistrationListener = null
    }

    @Suppress("DEPRECATION")
    private fun startNsdDiscovery() {
        runCatching {
            val mgr = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return
            nsdManager = mgr
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("microrc")) {
                        runCatching {
                            mgr.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val ip = info.host?.hostAddress ?: return
                                    val hostId = info.attributes["hostId"]?.decodeToString() ?: ip
                                    val hostName = info.attributes["hostName"]?.decodeToString() ?: info.serviceName
                                    val trackId = info.attributes["trackId"]?.decodeToString() ?: "LIVING_ROOM_GP"
                                    val count = info.attributes["players"]?.decodeToString()?.toIntOrNull() ?: 1
                                    upsertLobby(
                                        DiscoveredLobby(
                                            hostId = hostId,
                                            hostName = hostName,
                                            lobbyName = "$hostName's Race",
                                            hostIp = ip,
                                            tcpPort = info.port,
                                            trackId = trackId,
                                            playerCount = count,
                                            maxPlayers = 4,
                                            inRace = false,
                                            lastSeenMs = System.currentTimeMillis()
                                        )
                                    )
                                }
                            })
                        }
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            nsdDiscoveryListener = listener
            mgr.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun stopNsdDiscovery() {
        runCatching {
            val mgr = nsdManager
            val listener = nsdDiscoveryListener
            if (mgr != null && listener != null) {
                mgr.stopServiceDiscovery(listener)
            }
        }
        nsdDiscoveryListener = null
    }

    private fun getActiveIpv4Interfaces(): List<Pair<String, InetAddress?>> {
        val result = mutableListOf<Pair<String, InetAddress?>>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                val isWifiOrHotspot = name.contains("wlan") || name.contains("ap") ||
                    name.contains("swlan") || name.contains("eth")
                for (addr in iface.interfaceAddresses) {
                    val inet = addr.address
                    if (inet is Inet4Address && !inet.isLoopbackAddress) {
                        val ip = inet.hostAddress ?: continue
                        val pair = ip to addr.broadcast
                        if (isWifiOrHotspot) {
                            result.add(0, pair)
                        } else {
                            result.add(pair)
                        }
                    }
                }
            }
        }
        return result
    }
}
