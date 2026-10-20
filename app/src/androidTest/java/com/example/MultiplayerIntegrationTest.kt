package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.MultiplayerDurakEngine
import com.example.network.MultiplayerGameProtocol
import com.example.network.MultiplayerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class MultiplayerIntegrationTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        // These tests use real TCP sockets and deterministic loopback addresses.
        // Android NSD discovery is independent of transport and is not mocked as a network.
        override fun getSystemService(name: String): Any? = if (name == Context.NSD_SERVICE) null else super.getSystemService(name)
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(15000) {
        while (!condition()) delay(20)
    }

    @Test fun threeToSixPlayersReceivePrivateStatesAndAllMovesInOrder() = runBlocking {
        for (count in 3..6) {
            val host = MultiplayerManager(context)
            val clients = List(count - 1) { MultiplayerManager(context) }
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val received = ConcurrentHashMap<Int, MutableList<MultiplayerGameProtocol.State>>()
            try {
                val port = ServerSocket(0).use { it.localPort }
                host.startHost(port, capacity = count)
                // Retry connection to accommodate the asynchronous listen operation.
                clients.forEachIndexed { index, client ->
                    received[index] = java.util.Collections.synchronizedList(mutableListOf())
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        client.incomingMessages.collect { message ->
                            MultiplayerGameProtocol.parseState(message.payload)?.let { received.getValue(index).add(it) }
                        }
                    }
                    withTimeout(15000) {
                        do {
                            client.connectToHost("127.0.0.1", port, "Guest $index")
                            await { client.connectionState.value != MultiplayerManager.State.CONNECTING }
                        } while (client.connectionState.value != MultiplayerManager.State.CONNECTED)
                    }
                    await { client.localPlayerId.value != null && host.lobby.value?.players?.size == index + 2 }
                }
                await { clients.withIndex().all { (index, client) ->
                    host.lobby.value?.players?.any { it.id == client.localPlayerId.value && it.nickname == "Guest $index" } == true
                } }
                val room = host.lobby.value!!
                val ids = clients.map { it.localPlayerId.value!! }
                assertEquals(count - 1, ids.distinct().size)
                clients.forEach { it.setReady(true) }
                await { host.lobby.value?.canStart == true }
                assertTrue(host.lockLobbyForMatch())
                val game = MultiplayerDurakEngine()
                game.start(room.players.map { MultiplayerDurakEngine.Seat(it.id, it.nickname) })
                repeat(40) { sequence ->
                    ids.forEach { id ->
                        // deckSize is a sequence marker to verify transport ordering.
                        val state = game.snapshotFor(id).copy(deckSize = sequence)
                        host.sendMessageToPeer(id, MultiplayerGameProtocol.state(state))
                    }
                }
                await { received.values.all { it.size >= 40 } }
                received.forEach { (index, states) ->
                    assertEquals((0 until 40).toList(), states.map { it.deckSize })
                    assertEquals(game.snapshotFor(ids[index]).localHand, states.last().hand)
                    assertEquals(count, states.last().players.size)
                }
                val incoming = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    host.incomingMessages.first { it.payload == MultiplayerGameProtocol.take() }
                }
                clients.last().sendMessage(MultiplayerGameProtocol.take())
                assertEquals(ids.last(), withTimeout(10000) { incoming.await() }.senderId)
                // A remote EOF must remain observable, rather than being overwritten by IDLE.
                host.stopAll()
                await { clients.all { it.connectionState.value in listOf(MultiplayerManager.State.RECONNECTING, MultiplayerManager.State.DISCONNECTED) } }
            } finally {
                host.stopAll()
                clients.forEach { it.stopAll() }
                scope.cancel()
            }
        }
    }

    @Test fun restartingHostDoesNotLetOldReaderDisconnectNewRoom() = runBlocking {
        val host = MultiplayerManager(context)
        val client = MultiplayerManager(context)
        try {
            val port = ServerSocket(0).use { it.localPort }
            host.startHost(port)
            delay(200)
            client.connectToHost("127.0.0.1", port, "First")
            await { host.lobby.value?.players?.size == 2 }
            host.stopAll()
            host.startHost(port)
            delay(300)
            client.connectToHost("127.0.0.1", port, "Second")
            await { host.lobby.value?.players?.any { it.nickname == "Second" } == true }
            delay(200)
            assertEquals(2, host.lobby.value!!.players.size)
            assertEquals(MultiplayerManager.State.CONNECTED, client.connectionState.value)
        } finally {
            client.stopAll()
            host.stopAll()
        }
    }
}
