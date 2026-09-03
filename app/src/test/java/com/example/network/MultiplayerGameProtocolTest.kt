package com.example.network

import com.example.engine.MultiplayerDurakEngine
import com.example.model.Card
import com.example.model.CardPair
import com.example.model.Rank
import com.example.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerGameProtocolTest {
    @Test fun `state round trip supports six players and delimiter characters`() {
        val players = (1..6).map {
            MultiplayerDurakEngine.PlayerView("peer~$it", "Игрок; $it ~", 6 + it, it == 1, it == 2)
        }
        val source = MultiplayerDurakEngine.Snapshot(
            localHand = listOf(Card(Suit.HEARTS, Rank.ACE)),
            players = players,
            table = listOf(CardPair(Card(Suit.CLUBS, Rank.SIX), Card(Suit.CLUBS, Rank.SEVEN))),
            trumpCard = Card(Suit.DIAMONDS, Rank.KING), trumpSuit = Suit.DIAMONDS,
            deckSize = 12, discardSize = 8, currentPlayerId = "peer~1", defenderId = "peer~2",
            taking = false, finished = false, transferMode = true, loserId = null
        )
        val decoded = MultiplayerGameProtocol.parseState(MultiplayerGameProtocol.state(source))!!
        assertEquals(source.localHand, decoded.hand)
        assertEquals(source.players, decoded.players)
        assertEquals(source.table, decoded.table)
        assertTrue(decoded.transferMode)
    }

    @Test fun `all multiplayer actions round trip`() {
        val a = Card(Suit.SPADES, Rank.SIX)
        val d = Card(Suit.SPADES, Rank.SEVEN)
        assertEquals(MultiplayerGameProtocol.Action.Attack(a), MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.attack(a)))
        assertEquals(MultiplayerGameProtocol.Action.Defend(a, d), MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.defend(a, d)))
        assertEquals(MultiplayerGameProtocol.Action.Transfer(a), MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.transfer(a)))
        assertEquals(MultiplayerGameProtocol.Action.TakeBack(a), MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.takeBack(a)))
        assertEquals(MultiplayerGameProtocol.Action.Take, MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.take()))
        assertEquals(MultiplayerGameProtocol.Action.Bito, MultiplayerGameProtocol.parseAction(MultiplayerGameProtocol.bito()))
    }
}
