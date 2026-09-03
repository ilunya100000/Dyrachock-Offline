package com.example.engine

import com.example.model.Card
import com.example.model.MatchStatus
import com.example.model.Rank
import com.example.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurakEngineTest {
    private fun preparedEngine(): DurakEngine = DurakEngine().apply {
        deck.clear(); playerHand.clear(); opponentHand.clear(); tablePairs.clear()
        trumpSuit = Suit.SPADES
        matchStatus = MatchStatus.PLAYING
        attackerId = "player"
        defenderId = "opponent"
    }

    @Test fun `same suit higher card beats attack`() {
        val engine = preparedEngine()
        val attack = Card(Suit.HEARTS, Rank.NINE)
        val defense = Card(Suit.HEARTS, Rank.JACK)
        engine.playerHand += attack; engine.opponentHand += defense
        assertTrue(engine.performAttack("player", attack))
        assertTrue(engine.performDefense("opponent", attack, defense))
        assertEquals(defense, engine.tablePairs.single().defenseCard)
    }

    @Test fun `lower card of same suit cannot beat attack`() {
        val engine = preparedEngine()
        val attack = Card(Suit.HEARTS, Rank.JACK)
        val defense = Card(Suit.HEARTS, Rank.NINE)
        engine.playerHand += attack; engine.opponentHand += defense
        assertTrue(engine.performAttack("player", attack))
        assertFalse(engine.performDefense("opponent", attack, defense))
    }

    @Test fun `trump beats non trump attack`() {
        val engine = preparedEngine()
        val attack = Card(Suit.DIAMONDS, Rank.ACE)
        val defense = Card(Suit.SPADES, Rank.SIX)
        engine.playerHand += attack; engine.opponentHand += defense
        assertTrue(engine.performAttack("player", attack))
        assertTrue(engine.performDefense("opponent", attack, defense))
    }

    @Test fun `transfer changes roles with matching rank`() {
        val engine = preparedEngine().apply { isTransferMode = true }
        val attack = Card(Suit.HEARTS, Rank.TEN)
        val transfer = Card(Suit.CLUBS, Rank.TEN)
        engine.playerHand += attack
        engine.playerHand += Card(Suit.DIAMONDS, Rank.SIX)
        engine.playerHand += Card(Suit.CLUBS, Rank.SEVEN)
        engine.opponentHand += transfer
        assertTrue(engine.performAttack("player", attack))
        assertTrue(engine.performTransfer("opponent", transfer))
        assertEquals("opponent", engine.attackerId)
        assertEquals("player", engine.defenderId)
        assertEquals(2, engine.tablePairs.size)
    }

    @Test fun `taking moves table cards to defender`() {
        val engine = preparedEngine()
        val attack = Card(Suit.HEARTS, Rank.SIX)
        engine.playerHand += attack
        engine.opponentHand += Card(Suit.CLUBS, Rank.SEVEN)
        assertTrue(engine.performAttack("player", attack))
        assertTrue(engine.performTakeAll("opponent"))
        assertTrue(engine.performBito("player"))
        assertTrue(engine.tablePairs.isEmpty())
        assertTrue(engine.opponentHand.contains(attack))
    }
}
