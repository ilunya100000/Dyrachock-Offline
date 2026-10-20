package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.MultiplayerDurakEngine
import com.example.network.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class MultiplayerRecoveryTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSystemService(name: String): Any? = if (name == Context.NSD_SERVICE) null else super.getSystemService(name)
    }
    private suspend fun await(test: () -> Boolean) = withTimeout(15000) { while (!test()) delay(20) }
    private suspend fun join(host: MultiplayerManager, client: MultiplayerManager, port: Int, name: String) {
        client.connectToHost("127.0.0.1", port, name)
        await { client.connectionState.value == MultiplayerManager.State.CONNECTED && host.lobby.value?.players?.any { it.id == client.localPlayerId.value && it.nickname == name } == true }
    }
    /** Close the actual TCP socket, as when Wi-Fi disappears; do not clear the client session. */
    private fun cutTransport(client: MultiplayerManager) {
        val connection = client.javaClass.getDeclaredField("clientConnection").apply { isAccessible = true }.get(client)!!
        val socket = connection.javaClass.getDeclaredField("socket").apply { isAccessible = true }.get(connection) as Socket
        socket.close()
    }

    @Test fun automaticReconnectPreservesIdentityPrivateHandAndRejectsLateStrangers() = runBlocking {
        val host = MultiplayerManager(context)
        val client = MultiplayerManager(context)
        val stranger = MultiplayerManager(context)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val received = ConcurrentHashMap<String, MultiplayerGameProtocol.State>()
        try {
            val port = ServerSocket(0).use { it.localPort }
            host.startHost(port, 2)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                client.incomingMessages.collect { MultiplayerGameProtocol.parseState(it.payload)?.let { state -> received["client"] = state } }
            }
            join(host, client, port, "Alice")
            val id = client.localPlayerId.value!!
            assertFalse(host.lockLobbyForMatch())
            client.setReady(true); await { host.lobby.value?.canStart == true }
            assertTrue(host.lockLobbyForMatch())
            val game = MultiplayerDurakEngine().apply { start(host.lobby.value!!.players.map { MultiplayerDurakEngine.Seat(it.id, it.nickname) }) }
            val initial = game.snapshotFor(id)
            host.sendMessageToPeer(id, MultiplayerGameProtocol.state(initial))
            await { received["client"] != null }
            received.clear()
            cutTransport(client)
            await { host.lobby.value?.players?.any { it.id == id && !it.connected } == true }
            await { client.connectionState.value == MultiplayerManager.State.CONNECTED && host.lobby.value?.players?.all { it.connected } == true && received["client"] != null }
            assertEquals(id, client.localPlayerId.value)
            assertEquals(2, host.lobby.value!!.players.size)
            assertEquals(initial.localHand, received.getValue("client").hand)
            stranger.connectToHost("127.0.0.1", port, "Intruder")
            await { stranger.connectionState.value == MultiplayerManager.State.DISCONNECTED }
            assertNull(stranger.localPlayerId.value)
            assertEquals(2, host.lobby.value!!.players.size)
        } finally { host.stopAll(); client.stopAll(); stranger.stopAll(); scope.cancel() }
    }

    @Test fun recreatedClientRestoresReservedSeatAndRoomCanBeReusedWithFreshReadiness() = runBlocking {
        val host = MultiplayerManager(context)
        val original = MultiplayerManager(context)
        val restored = MultiplayerManager(context)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val received = CompletableDeferred<MultiplayerGameProtocol.State>()
        try {
            val port = ServerSocket(0).use { it.localPort }
            host.startHost(port, 2)
            join(host, original, port, "Restarted player")
            val id = original.localPlayerId.value!!
            assertTrue(host.configureLobby(2, MultiplayerLobby.Settings(52, true)))
            await { original.lobby.value?.settings?.deckSize == 52 }
            assertFalse(host.lobby.value!!.canStart)
            original.setReady(true); await { host.lobby.value?.canStart == true }
            assertTrue(host.lockLobbyForMatch())
            val game = MultiplayerDurakEngine().apply { start(host.lobby.value!!.players.map { MultiplayerDurakEngine.Seat(it.id, it.nickname) }, 52, true) }
            val snapshot = game.snapshotFor(id)
            host.sendMessageToPeer(id, MultiplayerGameProtocol.state(snapshot))
            original.stopAll()
            await { host.lobby.value?.players?.any { it.id == id && !it.connected } == true }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                restored.incomingMessages.collect { MultiplayerGameProtocol.parseState(it.payload)?.let(received::complete) }
            }
            assertTrue(restored.restoreSavedSession())
            val state = withTimeout(15000) { received.await() }
            assertEquals(id, restored.localPlayerId.value)
            assertEquals(snapshot.localHand, state.hand)
            host.returnToLobby()
            await { restored.lobby.value?.inMatch == false }
            assertFalse(host.lobby.value!!.canStart)
            assertEquals(id, restored.lobby.value!!.players.single { !it.isHost }.id)
            restored.setReady(true); await { host.lobby.value?.canStart == true }
            assertTrue(host.lockLobbyForMatch())
        } finally { original.stopAll(); restored.stopAll(); host.stopAll(); scope.cancel() }
    }
}
