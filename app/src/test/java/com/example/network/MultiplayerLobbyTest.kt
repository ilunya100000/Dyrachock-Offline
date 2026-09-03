package com.example.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerLobbyTest {
    @Test fun `room accepts host and five guests only`() {
        val lobby = MultiplayerLobby(roomCode = "TEST01")
        assertTrue(lobby.addPlayer("host", "Host", isHost = true))
        repeat(5) { assertTrue(lobby.addPlayer("p$it", "Player $it")) }
        assertFalse(lobby.addPlayer("overflow", "Overflow"))
        assertEquals(6, lobby.players.size)
    }

    @Test fun `room starts with at least two players`() {
        val lobby = MultiplayerLobby(roomCode = "TEST01")
        lobby.addPlayer("host", "Host", isHost = true)
        assertFalse(lobby.canStart)
        lobby.addPlayer("guest", "Guest")
        assertTrue(lobby.canStart)
    }

    @Test fun `chat accepts member messages and rejects strangers`() {
        val lobby = MultiplayerLobby(roomCode = "TEST01")
        lobby.addPlayer("host", "Host", isHost = true)
        assertNotNull(lobby.postMessage("host", "hello"))
        assertEquals("hello", lobby.messages.single().text)
        assertEquals(null, lobby.postMessage("stranger", "no"))
    }

    @Test fun `lobby wire state keeps names unicode and chat`() {
        val lobby = MultiplayerLobby(roomCode = "TEST01", capacity = 4)
        lobby.addPlayer("host", "Илья", isHost = true)
        lobby.addPlayer("guest", "Marta")
        lobby.postMessage("host", "Привет, готова?")

        val restored = LobbyWireProtocol.parseState(LobbyWireProtocol.state(lobby.snapshot()))

        assertNotNull(restored)
        assertEquals("TEST01", restored!!.roomCode)
        assertEquals(4, restored.capacity)
        assertEquals(listOf("Илья", "Marta"), restored.players.map { it.nickname })
        assertEquals("Привет, готова?", restored.messages.single().text)
    }
}
