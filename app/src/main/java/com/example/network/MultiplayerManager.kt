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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MultiplayerManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val hostPeers = ConcurrentHashMap<String, PrintWriter>()
    private val hostPeerSockets = ConcurrentHashMap<String, Socket>()
    private var hostedLobby: MultiplayerLobby? = null
    private val _lobby = MutableStateFlow<MultiplayerLobby.Snapshot?>(null)
    val lobby = _lobby.asStateFlow()
    private val _localPlayerId = MutableStateFlow<String?>(null)
    val localPlayerId = _localPlayerId.asStateFlow()

    // Connection states
    enum class State {
        IDLE,
        HOSTING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private val _connectionState = MutableStateFlow(State.IDLE)
    val connectionState = _connectionState.asStateFlow()

    data class IncomingMessage(val senderId: String?, val payload: String)

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _discoveredHosts = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()

    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress = _localIpAddress.asStateFlow()

    // NSD properties
    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    init {
        resolveLocalIp()
    }

    private fun resolveLocalIp() {
        scope.launch {
            try {
                // Try to find local IP from active networks
                val connection = Socket("8.8.8.8", 53)
                val ip = connection.localAddress.hostAddress
                connection.close()
                if (ip != null) {
                    _localIpAddress.value = ip
                }
            } catch (e: Exception) {
                // fallback if not online at all
            }
        }
    }

    // Host: Start listening as Server Socket
    fun startHost(port: Int = 8888, capacity: Int = MultiplayerLobby.MAX_PLAYERS, hostNickname: String = "Host") {
        stopAll()
        val room = MultiplayerLobby(capacity = capacity.coerceIn(MultiplayerLobby.MIN_PLAYERS, MultiplayerLobby.MAX_PLAYERS))
        room.addPlayer("host", hostNickname.ifBlank { "Host" }, isHost = true)
        hostedLobby = room
        publishLobby(room)
        _connectionState.value = State.HOSTING
        scope.launch {
            try {
                registerNsdService(port)
                val sSocket = ServerSocket(port)
                serverSocket = sSocket
                Log.d("DurakMultiplayer", "Server started on port $port")

                while (!sSocket.isClosed) {
                    val socket = sSocket.accept()
                    val peerAddress = socket.inetAddress.hostAddress ?: socket.inetAddress.hostName
                    val peerId = "$peerAddress:${socket.port}"
                    if (room.addPlayer(peerId, "Guest ${room.players.size}")) {
                        hostPeerSockets[peerId] = socket
                        hostPeers[peerId] = PrintWriter(socket.getOutputStream(), true)
                        setupHostPeerReader(peerId, socket)
                        broadcastLobbyState()
                        _connectionState.value = State.CONNECTED
                    } else socket.close()
                }
            } catch (e: Exception) {
                if (connectionState.value == State.HOSTING) {
                    _connectionState.value = State.IDLE
                }
            }
        }
    }

    private fun setupHostPeerReader(peerId: String, socket: Socket) {
        scope.launch {
            try {
                BufferedReader(InputStreamReader(socket.getInputStream())).use { input ->
                    while (true) {
                        val line = input.readLine() ?: break
                        if (!handleHostLobbyMessage(peerId, line)) {
                            _incomingMessages.emit(IncomingMessage(peerId, line))
                        }
                    }
                }
            } finally {
                _incomingMessages.emit(IncomingMessage(peerId, "MULTI_DISCONNECT"))
                hostPeers.remove(peerId)?.close()
                hostPeerSockets.remove(peerId)?.close()
                hostedLobby?.removePlayer(peerId)
                broadcastLobbyState()
            }
        }
    }

    // Client: Connect to Host IP directly
    fun connectToHost(ipAddress: String, port: Int = 8888) {
        stopAll()
        _connectionState.value = State.CONNECTING
        scope.launch {
            try {
                val socket = Socket(ipAddress, port)
                clientSocket = socket
                setupSocketStreams(socket)
                _connectionState.value = State.CONNECTED
                Log.d("DurakMultiplayer", "Connected to host at $ipAddress")
            } catch (e: Exception) {
                _connectionState.value = State.DISCONNECTED
                delay(1500)
                _connectionState.value = State.IDLE
            }
        }
    }

    private fun setupSocketStreams(socket: Socket) {
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        // Start reading loop
        scope.launch {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    val lobbyState = LobbyWireProtocol.parseState(line)
                    if (lobbyState != null) {
                        _lobby.value = lobbyState
                    } else if (line.startsWith("LOBBY_SELF:")) {
                        _localPlayerId.value = line.removePrefix("LOBBY_SELF:").ifBlank { null }
                    } else {
                        _incomingMessages.emit(IncomingMessage("host", line))
                    }
                }
                handleDisconnect()
            } catch (e: Exception) {
                // Connection broken
                handleDisconnect()
            }
        }
    }

    fun sendMessage(message: String) {
        scope.launch {
            try {
                if (hostPeers.isNotEmpty()) hostPeers.values.forEach { it.println(message) }
                else writer?.println(message)
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
    }

    fun joinLobby(nickname: String) {
        scope.launch { writer?.println(LobbyWireProtocol.join(nickname)) }
    }

    fun sendLobbyChat(text: String) {
        scope.launch {
            val room = hostedLobby
            if (room != null) {
                if (room.postMessage("host", text) != null) broadcastLobbyState()
            } else {
                writer?.println(LobbyWireProtocol.chat(text))
            }
        }
    }

    fun sendMessageToPeer(peerId: String, message: String) {
        scope.launch { hostPeers[peerId]?.println(message) }
    }

    /** Closes discovery and the listening socket once a 2–6 player match begins.
     * Existing player sockets remain open for the match state stream. */
    fun lockLobbyForMatch(): Boolean {
        val room = hostedLobby ?: return false
        if (!room.canStart || room.players.size > room.capacity) return false
        unregisterNsdService()
        runCatching { serverSocket?.close() }
        serverSocket = null
        return true
    }

    private fun handleHostLobbyMessage(peerId: String, message: String): Boolean {
        val room = hostedLobby ?: return false
        LobbyWireProtocol.decodeJoin(message)?.let { nickname ->
            room.renamePlayer(peerId, nickname)
            hostPeers[peerId]?.println("LOBBY_SELF:$peerId")
            broadcastLobbyState()
            return true
        }
        LobbyWireProtocol.decodeChat(message)?.let { text ->
            if (room.postMessage(peerId, text) != null) broadcastLobbyState()
            return true
        }
        return LobbyWireProtocol.isLobbyMessage(message)
    }

    private fun publishLobby(room: MultiplayerLobby) {
        _lobby.value = room.snapshot()
    }

    private fun broadcastLobbyState() {
        val room = hostedLobby ?: return
        publishLobby(room)
        val encoded = LobbyWireProtocol.state(room.snapshot())
        hostPeers.values.forEach { peer -> runCatching { peer.println(encoded) } }
    }

    private fun handleDisconnect() {
        _connectionState.value = State.DISCONNECTED
        // Autoclose and clean up
        stopAll()
    }

    fun stopAll() {
        unregisterNsdService()
        stopHostDiscovery()

        _discoveredHosts.value = emptyList()

        try { writer?.close() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { clientSocket?.close() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        hostPeers.values.forEach { runCatching { it.close() } }
        hostPeerSockets.values.forEach { runCatching { it.close() } }
        hostPeers.clear()
        hostPeerSockets.clear()
        hostedLobby = null
        _lobby.value = null
        _localPlayerId.value = null

        writer = null
        reader = null
        clientSocket = null
        serverSocket = null

        _connectionState.value = State.IDLE
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
