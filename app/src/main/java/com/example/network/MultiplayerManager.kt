package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class MultiplayerManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

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

    private val _incomingMessages = MutableStateFlow<String?>(null)
    val incomingMessages = _incomingMessages.asStateFlow()

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

    fun clearReceivedMessage() {
        _incomingMessages.value = null
    }

    // Host: Start listening as Server Socket
    fun startHost(port: Int = 8888) {
        stopAll()
        _connectionState.value = State.HOSTING
        scope.launch {
            try {
                registerNsdService(port)
                val sSocket = ServerSocket(port)
                serverSocket = sSocket
                Log.d("DurakMultiplayer", "Server started on port $port")

                val socket = sSocket.accept() // Blocks until client connects
                clientSocket = socket
                setupSocketStreams(socket)
                _connectionState.value = State.CONNECTED
                Log.d("DurakMultiplayer", "Client connected: ${socket.inetAddress.hostAddress}")
            } catch (e: Exception) {
                if (connectionState.value == State.HOSTING) {
                    _connectionState.value = State.IDLE
                }
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
                var line: String? = null
                while (reader != null && reader?.readLine().also { line = it } != null) {
                    line?.let {
                        _incomingMessages.value = it
                    }
                }
            } catch (e: Exception) {
                // Connection broken
                handleDisconnect()
            }
        }
    }

    fun sendMessage(message: String) {
        scope.launch {
            try {
                writer?.println(message)
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
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
            serviceName = "DurakClassicGame"
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
                if (serviceInfo.serviceType.contains("_durak") && serviceInfo.serviceName.contains("DurakClassicGame")) {
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
