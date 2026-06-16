package com.example.desktop.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Desktop variant of the multiplayer manager. Wire-compatible with the Android
 * build:
 *  - TCP game traffic on port 8888 (identical JSON-line protocol).
 *  - mDNS / Bonjour discovery via jmDNS, advertising and looking up the same
 *    `_durak._tcp.local.` service the Android NSD layer registers, so a
 *    Windows host shows up in the Android lobby list and vice versa.
 */
class MultiplayerManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private var jmdns: JmDNS? = null
    private var registeredService: ServiceInfo? = null
    private var discoveryListener: ServiceListener? = null

    enum class State { IDLE, HOSTING, CONNECTING, CONNECTED, DISCONNECTED }

    /** Mirrors the shape Android's `NsdServiceInfo` exposes to the UI. */
    data class DiscoveredHost(
        val serviceName: String,
        val host: InetAddress,
        val port: Int
    ) {
        val address: String get() = host.hostAddress ?: "0.0.0.0"
    }

    private val _connectionState = MutableStateFlow(State.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _incomingMessages = MutableStateFlow<String?>(null)
    val incomingMessages = _incomingMessages.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()

    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress = _localIpAddress.asStateFlow()

    init { resolveLocalIp() }

    fun clearReceivedMessage() { _incomingMessages.value = null }

    // ---- Public lifecycle --------------------------------------------------
    fun startHost(port: Int = 8888) {
        stopAll()
        _connectionState.value = State.HOSTING
        scope.launch {
            try {
                registerMdnsService(port)
                val sSocket = ServerSocket(port)
                serverSocket = sSocket
                val socket = sSocket.accept()
                clientSocket = socket
                setupSocketStreams(socket)
                unregisterMdnsService()
                _connectionState.value = State.CONNECTED
            } catch (_: Exception) {
                if (connectionState.value == State.HOSTING) _connectionState.value = State.IDLE
            }
        }
    }

    fun connectToHost(ipAddress: String, port: Int = 8888) {
        stopAll()
        _connectionState.value = State.CONNECTING
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 5000)
                clientSocket = socket
                setupSocketStreams(socket)
                _connectionState.value = State.CONNECTED
            } catch (_: Exception) {
                _connectionState.value = State.DISCONNECTED
                delay(1500)
                _connectionState.value = State.IDLE
            }
        }
    }

    fun sendMessage(message: String) {
        scope.launch {
            try { writer?.println(message) } catch (_: Exception) { handleDisconnect() }
        }
    }

    fun stopAll() {
        stopHostDiscovery()
        unregisterMdnsService()
        _discoveredHosts.value = emptyList()

        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}

        writer = null
        reader = null
        clientSocket = null
        serverSocket = null

        _connectionState.value = State.IDLE
    }

    // ---- Internal: TCP streams --------------------------------------------
    private fun setupSocketStreams(socket: Socket) {
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        scope.launch {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    _incomingMessages.value = line
                }
            } catch (_: Exception) { }
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        if (_connectionState.value != State.IDLE) _connectionState.value = State.DISCONNECTED
        stopAll()
    }

    // ---- Internal: mDNS / Bonjour -----------------------------------------
    private fun jmdnsInstance(): JmDNS? {
        jmdns?.let { return it }
        return try {
            val bind = bestLocalAddress() ?: InetAddress.getLocalHost()
            JmDNS.create(bind, "DyrachokWindowsHost").also { jmdns = it }
        } catch (_: Exception) { null }
    }

    private fun registerMdnsService(port: Int) {
        unregisterMdnsService()
        scope.launch {
            try {
                val md = jmdnsInstance() ?: return@launch
                val info = ServiceInfo.create(
                    "_durak._tcp.local.",
                    "DurakClassicGame",
                    port,
                    "Dyrachok host"
                )
                md.registerService(info)
                registeredService = info
            } catch (_: Exception) { }
        }
    }

    private fun unregisterMdnsService() {
        try { registeredService?.let { jmdns?.unregisterService(it) } } catch (_: Exception) {}
        registeredService = null
    }

    fun startHostDiscovery() {
        stopHostDiscovery()
        _discoveredHosts.value = emptyList()
        scope.launch {
            try {
                val md = jmdnsInstance() ?: return@launch
                val listener = object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        // Force resolution; the actual host/port arrive in serviceResolved.
                        md.requestServiceInfo(event.type, event.name, true)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        val list = _discoveredHosts.value.toMutableList()
                        list.removeAll { it.serviceName == event.name }
                        _discoveredHosts.value = list
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info ?: return
                        if (!info.name.contains("DurakClassicGame")) return
                        val addr = info.inet4Addresses.firstOrNull()
                            ?: info.inetAddresses.firstOrNull()
                            ?: return
                        val host = DiscoveredHost(info.name, addr, info.port)
                        val list = _discoveredHosts.value.toMutableList()
                        if (list.none { it.host.hostAddress == host.host.hostAddress && it.port == host.port }) {
                            list.add(host)
                            _discoveredHosts.value = list
                        }
                    }
                }
                discoveryListener = listener
                md.addServiceListener("_durak._tcp.local.", listener)
            } catch (_: Exception) { }
        }
    }

    fun stopHostDiscovery() {
        val md = jmdns
        val l = discoveryListener
        if (md != null && l != null) {
            try { md.removeServiceListener("_durak._tcp.local.", l) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    // ---- Internal: local IP resolution ------------------------------------
    private fun resolveLocalIp() {
        scope.launch {
            try {
                bestLocalAddress()?.hostAddress?.let { _localIpAddress.value = it }
            } catch (_: Exception) { }
        }
    }

    private fun bestLocalAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nic in interfaces) {
                if (!nic.isUp || nic.isLoopback || nic.isVirtual) continue
                for (addr in nic.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.address.size == 4) return addr
                }
            }
        } catch (_: Exception) { }
        return null
    }
}
