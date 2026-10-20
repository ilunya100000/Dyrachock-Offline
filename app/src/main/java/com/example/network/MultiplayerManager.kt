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
import java.util.UUID

class MultiplayerManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionLock = Any()
    private var generation = 0L
    private var serverSocket: ServerSocket? = null
    private var clientConnection: OrderedConnection? = null
    private val hostPeers = linkedMapOf<String, OrderedConnection>()
    private val credentials = mutableMapOf<String, String>()
    private val revokedTokens = mutableSetOf<String>()
    private val cachedStates = mutableMapOf<String, String>()
    private var hostedLobby: MultiplayerLobby? = null
    private var matchLocked = false
    private var resumeToken = UUID.randomUUID().toString()
    private var endpoint: Pair<String, Int>? = null
    private var nickname = "Player"
    private val prefs = context.getSharedPreferences("multiplayer_resume", Context.MODE_PRIVATE)
    private val _lobby = MutableStateFlow<MultiplayerLobby.Snapshot?>(null)
    val lobby = _lobby.asStateFlow()
    private val _localPlayerId = MutableStateFlow<String?>(null)
    val localPlayerId = _localPlayerId.asStateFlow()
    enum class State { IDLE, HOSTING, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED }
    private val _connectionState = MutableStateFlow(State.IDLE)
    val connectionState = _connectionState.asStateFlow()
    data class IncomingMessage(val senderId: String?, val payload: String)
    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()
    private val _discoveredHosts = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()
    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress = _localIpAddress.asStateFlow()
    private val nsdManager: NsdManager? by lazy { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    val canRestoreSession get() = prefs.contains("token") && prefs.contains("ip")

    init {
        runCatching {
            val addresses = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())?.hostAddress?.let { _localIpAddress.value = it }
        }
        scope.launch {
            while (isActive) {
                delay(3000)
                synchronized(sessionLock) {
                    clientConnection?.send("SESSION_PING")
                    hostPeers.values.forEach { it.send("SESSION_PING") }
                }
            }
        }
    }
    fun startHost(port: Int = 8888, capacity: Int = 6, hostNickname: String = "Host") {
        stopAll()
        val token = synchronized(sessionLock) {
            hostedLobby = MultiplayerLobby(capacity = capacity.coerceIn(2, 6)).apply { addPlayer("host", hostNickname.ifBlank { "Host" }, true) }
            _localPlayerId.value = "host"; _connectionState.value = State.HOSTING
            broadcastLobbyState(); generation
        }
        scope.launch {
            val listener = ServerSocket()
            try {
                listener.reuseAddress = true; listener.bind(InetSocketAddress(port))
                synchronized(sessionLock) {
                    if (token != generation) { listener.close(); return@launch }
                    serverSocket = listener; registerNsdService(listener.localPort)
                }
                while (!listener.isClosed) {
                    val socket = listener.accept()
                    scope.launch { acceptPeer(socket, token) }
                }
            } catch (_: Exception) {
                synchronized(sessionLock) { if (token == generation && !listener.isClosed) stopAll(State.DISCONNECTED) }
            } finally { runCatching { listener.close() } }
        }
    }
    private suspend fun acceptPeer(socket: Socket, token: Long) {
        try {
            socket.soTimeout = 5000
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 1024) {
                val byte = socket.getInputStream().read()
                if (byte < 0) throw java.io.EOFException()
                if (byte == 10) break
                bytes.write(byte)
            }
            val hello = LobbyWireProtocol.parseSessionJoin(bytes.toString("UTF-8")) ?: throw java.io.IOException("Invalid handshake")
            var resumed = false
            var acceptedId: String? = null
            synchronized(sessionLock) {
                val room = hostedLobby
                if (generation != token || room == null) { socket.close(); return }
                if (hello.second in revokedTokens) {
                    socket.getOutputStream().write("SESSION_REJECT:Removed from room\n".toByteArray()); socket.close(); return
                }
                val saved = credentials[hello.second]
                val peerId = saved ?: UUID.randomUUID().toString()
                if ((saved == null && (matchLocked || !room.addPlayer(peerId, hello.first.ifBlank { "Player" }))) ||
                    (saved != null && room.players.none { it.id == saved })) {
                    socket.getOutputStream().write("SESSION_REJECT:Room unavailable\n".toByteArray()); socket.close(); return
                }
                val secret = if (saved != null) hello.second else UUID.randomUUID().toString()
                credentials[secret] = peerId
                resumed = saved != null; acceptedId = peerId
                hostPeers.remove(peerId)?.close()
                room.setConnected(peerId, true)
                socket.soTimeout = 12000
                lateinit var connection: OrderedConnection
                connection = OrderedConnection(socket, scope,
                    onMessage = { line ->
                        val forward = synchronized(sessionLock) {
                            token == generation && hostPeers[peerId] === connection && when (line) {
                                "SESSION_PING" -> { connection.send("SESSION_PONG"); false }
                                "SESSION_PONG" -> false
                                else -> !handleHostLobbyMessage(peerId, line)
                            }
                        }
                        if (forward) _incomingMessages.emit(IncomingMessage(peerId, line))
                    },
                    onDisconnected = {
                        val notify = synchronized(sessionLock) {
                            if (token != generation || hostPeers[peerId] !== connection) false else {
                                hostPeers.remove(peerId)
                                room.setConnected(peerId, false)
                                broadcastLobbyState(); true
                            }
                        }
                        if (notify) _incomingMessages.emit(IncomingMessage(peerId, "MULTI_DISCONNECT"))
                    })
                hostPeers[peerId] = connection
                connection.send("SESSION_SELF:$peerId|$secret")
                connection.start()
                broadcastLobbyState()
                cachedStates[peerId]?.let { connection.send(it) }
            }
            if (resumed) _incomingMessages.emit(IncomingMessage(acceptedId, "MULTI_RESUMED"))
        } catch (_: Exception) { runCatching { socket.close() } }
    }
    fun connectToHost(ipAddress: String, port: Int = 8888, nickname: String = "Player") {
        if (endpoint != (ipAddress to port)) resumeToken = UUID.randomUUID().toString()
        endpoint = ipAddress to port; this.nickname = nickname.ifBlank { "Player" }
        connectLoop()
    }
    fun restoreSavedSession(): Boolean {
        if (!canRestoreSession) return false
        endpoint = prefs.getString("ip", null)!! to prefs.getInt("port", 8888)
        resumeToken = prefs.getString("token", null)!!; nickname = prefs.getString("nickname", "Player")!!
        connectLoop(); return true
    }
    fun retryConnection() { if (endpoint != null) connectLoop() }
    private fun connectLoop() {
        val target = endpoint ?: return
        stopAll()
        val token = synchronized(sessionLock) { _connectionState.value = State.CONNECTING; generation }
        scope.launch {
            var deadline = System.nanoTime() + 90_000_000_000L
            while (synchronized(sessionLock) { generation == token }) {
                val socket = Socket(); val closed = CompletableDeferred<Unit>()
                try {
                    socket.connect(InetSocketAddress(target.first, target.second), 4000); socket.soTimeout = 12000
                    synchronized(sessionLock) {
                        if (generation != token) { socket.close(); return@launch }
                        lateinit var connection: OrderedConnection
                        connection = OrderedConnection(socket, scope,
                            onMessage = { line ->
                                val forward = synchronized(sessionLock) {
                                    if (generation != token) false else when {
                                        line == "SESSION_PING" -> { connection.send("SESSION_PONG"); false }
                                        line == "SESSION_PONG" -> false
                                        line.startsWith("SESSION_SELF:") -> {
                                            val f = line.removePrefix("SESSION_SELF:").split("|")
                                            if (f.size == 2) {
                                                _localPlayerId.value = f[0]; resumeToken = f[1]
                                                prefs.edit().putString("ip", target.first).putInt("port", target.second).putString("token", resumeToken).putString("nickname", nickname).apply()
                                                _connectionState.value = State.CONNECTED
                                            }; false
                                        }
                                        line.startsWith("SESSION_REJECT:") -> { forgetSession(); stopAll(State.DISCONNECTED); closed.complete(Unit); true }
                                        else -> {
                                            val room = LobbyWireProtocol.parseState(line)
                                            if (room != null) _lobby.value = room
                                            room == null
                                        }
                                    }
                                }
                                if (forward) _incomingMessages.emit(IncomingMessage("host", line))
                            }, onDisconnected = { closed.complete(Unit) })
                        clientConnection = connection
                        connection.send(LobbyWireProtocol.sessionJoin(nickname, resumeToken)); connection.start()
                    }
                    closed.await()
                    if (_connectionState.value == State.CONNECTED) deadline = System.nanoTime() + 90_000_000_000L
                } catch (_: Exception) { runCatching { socket.close() } }
                synchronized(sessionLock) {
                    if (generation != token) return@launch
                    clientConnection?.close(); clientConnection = null
                    _connectionState.value = State.RECONNECTING
                }
                if (System.nanoTime() > deadline) {
                    synchronized(sessionLock) { if (generation == token) _connectionState.value = State.DISCONNECTED }
                    return@launch
                }
                delay(1000)
            }
        }
    }
    fun forgetSession() { prefs.edit().clear().apply() }
    fun leaveRoom() {
        sendMessage(if (hostedLobby != null) "MULTI_ABORT" else "MULTI_LEAVE")
        forgetSession()
        val token = synchronized(sessionLock) { generation }
        scope.launch { delay(200); synchronized(sessionLock) { if (token == generation) stopAll() } }
    }
    fun sendMessage(message: String) = synchronized(sessionLock) {
        if (hostedLobby != null) hostPeers.values.forEach { it.send(message) } else clientConnection?.send(message)
        Unit
    }
    fun sendLobbyChat(text: String) = synchronized(sessionLock) {
        val room = hostedLobby
        if (room != null) { if (room.postMessage("host", text) != null) broadcastLobbyState() }
        else clientConnection?.send(LobbyWireProtocol.chat(text))
        Unit
    }
    fun renameLocal(name: String) = synchronized(sessionLock) {
        if (hostedLobby != null) { hostedLobby?.renamePlayer("host", name); broadcastLobbyState() }
        else clientConnection?.send(LobbyWireProtocol.join(name))
        Unit
    }
    fun setReady(value: Boolean) = synchronized(sessionLock) {
        if (hostedLobby != null) { hostedLobby?.setReady("host", value); broadcastLobbyState() }
        else clientConnection?.send(LobbyWireProtocol.ready(value))
        Unit
    }
    fun configureLobby(capacity: Int, settings: MultiplayerLobby.Settings): Boolean = synchronized(sessionLock) {
        val changed = hostedLobby?.configure(capacity, settings) == true
        if (changed) broadcastLobbyState()
        changed
    }
    fun sendMessageToPeer(peerId: String, message: String) = synchronized(sessionLock) {
        if (message.startsWith("MULTI_STATE:")) cachedStates[peerId] = message
        hostPeers[peerId]?.send(message); Unit
    }
    fun lockLobbyForMatch(expectedPlayerIds: Set<String>? = null): Boolean = synchronized(sessionLock) {
        val room = hostedLobby ?: return false
        if (!room.canStart || matchLocked || (expectedPlayerIds != null && room.players.map { it.id }.toSet() != expectedPlayerIds)) return false
        matchLocked = true; room.inMatch = true; broadcastLobbyState(); true
    }
    fun returnToLobby() = synchronized(sessionLock) {
        matchLocked = false; cachedStates.clear(); hostedLobby?.returnToLobby()
        hostPeers.values.forEach { it.send("MULTI_RETURN_LOBBY") }; broadcastLobbyState()
    }
    fun removePeer(id: String) = synchronized(sessionLock) {
        hostPeers.remove(id)?.close()
        revokedTokens.addAll(credentials.filterValues { it == id }.keys)
        credentials.entries.removeAll { it.value == id }; cachedStates.remove(id)
        hostedLobby?.removePlayer(id); broadcastLobbyState()
    }
    private fun handleHostLobbyMessage(peerId: String, message: String): Boolean {
        val room = hostedLobby ?: return false
        LobbyWireProtocol.decodeJoin(message)?.let { room.renamePlayer(peerId, it); broadcastLobbyState(); return true }
        if (message.startsWith("LOBBY_READY:")) {
            message.removePrefix("LOBBY_READY:").toBooleanStrictOrNull()?.let { room.setReady(peerId, it) }
            broadcastLobbyState(); return true
        }
        LobbyWireProtocol.decodeChat(message)?.let { if (room.postMessage(peerId, it) != null) broadcastLobbyState(); return true }
        return LobbyWireProtocol.isLobbyMessage(message)
    }
    private fun broadcastLobbyState() {
        val snapshot = hostedLobby?.snapshot() ?: return
        _lobby.value = snapshot
        val encoded = LobbyWireProtocol.state(snapshot); hostPeers.values.forEach { it.send(encoded) }
    }
    fun shutdown() { stopAll(); scope.cancel() }
    fun stopAll() = stopAll(State.IDLE)
    private fun stopAll(next: State) = synchronized(sessionLock) {
        generation++; unregisterNsdService(); stopHostDiscovery(); _discoveredHosts.value = emptyList()
        runCatching { serverSocket?.close() }; clientConnection?.close(); hostPeers.values.forEach { it.close() }
        hostPeers.clear(); credentials.clear(); revokedTokens.clear(); cachedStates.clear(); hostedLobby = null; matchLocked = false
        _lobby.value = null; _localPlayerId.value = null; clientConnection = null; serverSocket = null
        _connectionState.value = next
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
