package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class MultiplayerManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionLock = Any()
    private var generation = 0L
    private var serverSocket: ServerSocket? = null
    private var clientConnection: OrderedConnection? = null
    private val hostPeers = linkedMapOf<String, OrderedConnection>()
    private var hostedLobby: MultiplayerLobby? = null
    private var matchLocked = false
    private val pendingPeers = mutableSetOf<String>()
    private val _lobby = MutableStateFlow<MultiplayerLobby.Snapshot?>(null)
    val lobby = _lobby.asStateFlow()
    private val _localPlayerId = MutableStateFlow<String?>(null)
    val localPlayerId = _localPlayerId.asStateFlow()

    enum class State { IDLE, HOSTING, CONNECTING, CONNECTED, DISCONNECTED }
    private val _connectionState = MutableStateFlow(State.IDLE)
    val connectionState = _connectionState.asStateFlow()
    data class IncomingMessage(val senderId: String?, val payload: String)
    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()
    private val _discoveredHosts = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()
    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress = _localIpAddress.asStateFlow()

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    init {
        // Local multiplayer must also work on Wi-Fi without internet access.
        runCatching {
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
            (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())
                ?.hostAddress?.let { _localIpAddress.value = it }
        }
    }

    fun startHost(port: Int = 8888, capacity: Int = MultiplayerLobby.MAX_PLAYERS, hostNickname: String = "Host") {
        stopAll()
        val token = synchronized(sessionLock) {
            hostedLobby = MultiplayerLobby(capacity = capacity.coerceIn(2, 6)).apply {
                addPlayer("host", hostNickname.ifBlank { "Host" }, isHost = true)
            }
            _localPlayerId.value = "host"
            broadcastLobbyState()
            _connectionState.value = State.HOSTING
            generation
        }
        scope.launch {
            var listener: ServerSocket? = null
            try {
                listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
                synchronized(sessionLock) {
                    if (token != generation) { listener.close(); return@launch }
                    serverSocket = listener
                    registerNsdService(listener.localPort)
                }
                while (!listener.isClosed) {
                    val socket = listener.accept()
                    synchronized(sessionLock) {
                        val room = hostedLobby
                        val peerId = "${socket.inetAddress.hostAddress}:${socket.port}"
                        if (token != generation || matchLocked || room == null || !room.addPlayer(peerId, "Guest ${room.players.size}")) {
                            socket.close()
                        } else {
                            val connection = OrderedConnection(socket, scope,
                                onMessage = { line ->
                                    val forward = synchronized(sessionLock) {
                                        token == generation && !handleHostLobbyMessage(peerId, line)
                                    }
                                    if (forward) _incomingMessages.emit(IncomingMessage(peerId, line))
                                },
                                onDisconnected = {
                                    val notify = synchronized(sessionLock) {
                                        if (token != generation) false else {
                                            hostPeers.remove(peerId)
                                            pendingPeers.remove(peerId)
                                            hostedLobby?.removePlayer(peerId)
                                            broadcastLobbyState()
                                            true
                                        }
                                    }
                                    if (notify) _incomingMessages.emit(IncomingMessage(peerId, "MULTI_DISCONNECT"))
                                })
                            pendingPeers += peerId
                            hostPeers[peerId] = connection
                            // Identity is sent immediately, even if the host starts before JOIN arrives.
                            connection.send("LOBBY_SELF:$peerId")
                            connection.start()
                            broadcastLobbyState()
                        }
                    }
                }
            } catch (_: Exception) {
                synchronized(sessionLock) {
                    if (token == generation && !matchLocked) stopAll(State.DISCONNECTED)
                }
            } finally {
                runCatching { listener?.close() }
            }
        }
    }

    fun connectToHost(ipAddress: String, port: Int = 8888, nickname: String = "Player") {
        stopAll()
        val token = synchronized(sessionLock) {
            _connectionState.value = State.CONNECTING
            generation
        }
        scope.launch {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ipAddress, port), 5000)
                synchronized(sessionLock) {
                    if (token != generation) { socket.close(); return@launch }
                    val connection = OrderedConnection(socket, scope,
                        onMessage = { line ->
                            val forward = synchronized(sessionLock) {
                                when {
                                    token != generation -> false
                                    line.startsWith("LOBBY_SELF:") -> {
                                        _localPlayerId.value = line.removePrefix("LOBBY_SELF:").ifBlank { null }
                                        false
                                    }
                                    else -> {
                                        val state = LobbyWireProtocol.parseState(line)
                                        if (state != null) _lobby.value = state
                                        state == null
                                    }
                                }
                            }
                            if (forward) _incomingMessages.emit(IncomingMessage("host", line))
                        },
                        onDisconnected = {
                            synchronized(sessionLock) {
                                if (token == generation) stopAll(State.DISCONNECTED)
                            }
                        })
                    clientConnection = connection
                    connection.send(LobbyWireProtocol.join(nickname.ifBlank { "Player" }))
                    connection.start()
                    _connectionState.value = State.CONNECTED
                }
            } catch (_: Exception) {
                runCatching { socket.close() }
                synchronized(sessionLock) {
                    if (token == generation) stopAll(State.DISCONNECTED)
                }
            }
        }
    }

    fun sendMessage(message: String) = synchronized(sessionLock) {
        if (hostedLobby != null) hostPeers.values.forEach { it.send(message) }
        else clientConnection?.send(message)
        Unit
    }

    fun sendLobbyChat(text: String) = synchronized(sessionLock) {
        val room = hostedLobby
        if (room != null) {
            if (room.postMessage("host", text) != null) broadcastLobbyState()
        } else clientConnection?.send(LobbyWireProtocol.chat(text))
        Unit
    }

    fun sendMessageToPeer(peerId: String, message: String) = synchronized(sessionLock) {
        hostPeers[peerId]?.send(message)
        Unit
    }

    fun lockLobbyForMatch(expectedPlayerIds: Set<String>? = null): Boolean = synchronized(sessionLock) {
        val room = hostedLobby ?: return false
        if (!room.canStart || matchLocked || pendingPeers.isNotEmpty()) return false
        if (expectedPlayerIds != null && room.players.map { it.id }.toSet() != expectedPlayerIds) return false
        matchLocked = true
        unregisterNsdService()
        runCatching { serverSocket?.close() }
        serverSocket = null
        true
    }

    private fun handleHostLobbyMessage(peerId: String, message: String): Boolean {
        val room = hostedLobby ?: return false
        LobbyWireProtocol.decodeJoin(message)?.let { nickname ->
            room.renamePlayer(peerId, nickname)
            pendingPeers.remove(peerId)
            broadcastLobbyState()
            return true
        }
        LobbyWireProtocol.decodeChat(message)?.let { text ->
            if (room.postMessage(peerId, text) != null) broadcastLobbyState()
            return true
        }
        return LobbyWireProtocol.isLobbyMessage(message)
    }

    private fun broadcastLobbyState() {
        val room = hostedLobby ?: return
        val snapshot = room.snapshot()
        _lobby.value = snapshot
        val encoded = LobbyWireProtocol.state(snapshot)
        hostPeers.values.forEach { it.send(encoded) }
    }

    fun stopAll() = stopAll(State.IDLE)

    private fun stopAll(nextState: State) = synchronized(sessionLock) {
        generation++
        unregisterNsdService()
        stopHostDiscovery()
        _discoveredHosts.value = emptyList()
        runCatching { serverSocket?.close() }
        clientConnection?.close()
        hostPeers.values.forEach { it.close() }
        hostPeers.clear()
        pendingPeers.clear()
        hostedLobby = null
        matchLocked = false
        _lobby.value = null
        _localPlayerId.value = null
        clientConnection = null
        serverSocket = null
        _connectionState.value = nextState
    }

    // --- NSD CONFIGURATION ---

    private fun registerNsdService(port: Int) {
        if (nsdManager == null) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Durak-${hostedLobby?.roomCode ?: "Classic"}"
            serviceType = "_durak._tcp"
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("DurakMultiplayer", "NSD Service Registered")
            }

            override fun onRegistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("DurakMultiplayer", "NSD Registration Failed: $errorCode")
            }

            override fun onServiceUnregistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("DurakMultiplayer", "NSD Service Unregistered")
            }

            override fun onUnregistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("DurakMultiplayer", "NSD Unregistration Failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            // Already registered or unavailable
        }
    }

    private fun unregisterNsdService() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {}
        }
        registrationListener = null
    }

    fun startHostDiscovery() {
        if (nsdManager == null) return
        _discoveredHosts.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("DurakMultiplayer", "NSD Discovery Start Failed: $errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("DurakMultiplayer", "NSD Discovery Stop Failed: $errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("DurakMultiplayer", "NSD Discovery Started")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("DurakMultiplayer", "NSD Discovery Stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("DurakMultiplayer", "NSD Service Found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("_durak") && serviceInfo.serviceName.startsWith("Durak-")) {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("DurakMultiplayer", "NSD Resolve Failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            Log.d("DurakMultiplayer", "NSD Resolved: ${resolvedInfo.host}:${resolvedInfo.port}")
                            val currentList = _discoveredHosts.value.toMutableList()
                            if (currentList.none { it.host?.hostAddress == resolvedInfo.host?.hostAddress }) {
                                currentList.add(resolvedInfo)
                                _discoveredHosts.value = currentList
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("DurakMultiplayer", "NSD Service Lost")
                val currentList = _discoveredHosts.value.toMutableList()
                currentList.removeAll { it.serviceName == serviceInfo.serviceName }
                _discoveredHosts.value = currentList
            }
        }

        try {
            nsdManager?.discoverServices("_durak._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            // Already discovering
        }
    }

    fun stopHostDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {}
        }
        discoveryListener = null
    }
}
