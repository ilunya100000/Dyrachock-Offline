package com.example.network

import org.junit.Assert.*
import org.junit.Test

class MultiplayerRoomRulesTest {
    private fun room() = MultiplayerLobby("ROOM32", 3).apply {
        addPlayer("host", "Host", true); addPlayer("a", "Alice"); addPlayer("b", "Bob")
    }
    @Test fun `settings reset readiness but chat and unchanged settings do not`() {
        val room = room()
        room.setReady("a", true); room.setReady("b", true)
        assertTrue(room.canStart)
        room.postMessage("a", "Ready!", 1000)
        assertTrue(room.configure(3, room.settings))
        assertTrue(room.canStart)
        assertTrue(room.configure(4, MultiplayerLobby.Settings(52, true)))
        assertFalse(room.canStart)
        assertTrue(room.players.single { it.isHost }.ready)
        assertTrue(room.players.filterNot { it.isHost }.none { it.ready })
        val restored = LobbyWireProtocol.parseState(LobbyWireProtocol.state(room.snapshot()))!!
        assertEquals(room.snapshot(), restored)
        assertEquals(52, restored.settings.deckSize)
        assertTrue(restored.settings.transfer)
    }
    @Test fun `disconnect reserves seat and pauses start until readiness is renewed`() {
        val room = room()
        room.setReady("a", true); room.setReady("b", true)
        room.setConnected("a", false)
        assertEquals(3, room.players.size)
        assertFalse(room.canStart)
        room.setConnected("a", true)
        assertFalse(room.canStart)
        room.setReady("a", true)
        assertTrue(room.canStart)
        room.inMatch = true
        assertFalse(room.setReady("a", false))
        assertFalse(room.configure(6, MultiplayerLobby.Settings()))
        room.returnToLobby()
        assertFalse(room.canStart)
        assertEquals(3, room.players.size)
    }
    @Test fun `invalid settings and overflowing capacity are rejected`() {
        val room = room()
        assertFalse(room.configure(2, MultiplayerLobby.Settings()))
        assertFalse(room.configure(3, MultiplayerLobby.Settings(99)))
        assertFalse(room.configure(3, MultiplayerLobby.Settings(customDeckIds = setOf("bogus"))))
        assertEquals(3, room.capacity)
        assertEquals(MultiplayerLobby.Settings(), room.settings)
    }
    @Test fun `messages are bounded rate limited and only connected members can send`() {
        val room = room()
        assertNotNull(room.postMessage("a", "👍", 1000))
        assertNull(room.postMessage("a", "spam", 1100))
        assertNotNull(room.postMessage("a", "x".repeat(300), 2000))
        assertEquals(160, room.messages.last().text.length)
        room.setConnected("a", false)
        assertNull(room.postMessage("a", "lost", 3000))
        assertNull(room.postMessage("stranger", "no", 4000))
        repeat(60) { room.postMessage("host", "Message $it", 5000L + it * 1000) }
        assertEquals(50, room.messages.size)
        assertEquals(room.snapshot(), LobbyWireProtocol.parseState(LobbyWireProtocol.state(room.snapshot())))
    }
}
