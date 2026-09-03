package com.example.engine

import com.example.model.Card
import com.example.model.Rank
import com.example.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class MultiplayerDurakEngineTest {
    @Test fun `three player match exposes only local hand and all seat sizes`() {
        val engine = MultiplayerDurakEngine(SeededRandom())
        engine.start(listOf(
            MultiplayerDurakEngine.Seat("a", "A"),
            MultiplayerDurakEngine.Seat("b", "B"),
            MultiplayerDurakEngine.Seat("c", "C")
        ))
        val snapshot = engine.snapshotFor("b")
        assertEquals(3, snapshot.players.size)
        assertEquals(6, snapshot.localHand.size)
        assertEquals(6, snapshot.players.single { it.id == "a" }.handSize)
        assertEquals(6, snapshot.players.single { it.id == "c" }.handSize)
    }

    @Test fun `defender cannot attack and other player can add matching rank`() {
        val engine = MultiplayerDurakEngine(SeededRandom())
        engine.start(listOf(
            MultiplayerDurakEngine.Seat("a", "A"),
            MultiplayerDurakEngine.Seat("b", "B"),
            MultiplayerDurakEngine.Seat("c", "C")
        ))
        val attacker = engine.snapshotFor("a").players.single { it.isAttacker }.id
        val defender = engine.snapshotFor("a").defenderId
        val first = engine.snapshotFor(attacker).localHand.first()
        assertTrue(engine.attack(attacker, first))
        assertFalse(engine.attack(defender, engine.snapshotFor(defender).localHand.first()))
        val helper = engine.snapshotFor(attacker).players.first { it.id != attacker && it.id != defender }.id
        val matching = engine.snapshotFor(helper).localHand.firstOrNull { it.rank == first.rank }
        if (matching != null) assertTrue(engine.attack(helper, matching))
    }

    @Test fun `six players are dealt six cards from a 36 card deck`() {
        val engine = MultiplayerDurakEngine(SeededRandom())
        engine.start((1..6).map { MultiplayerDurakEngine.Seat("p$it", "Player $it") })
        val state = engine.snapshotFor("p1")
        assertEquals(6, state.players.size)
        assertEquals(0, state.deckSize)
        assertTrue(state.players.all { it.handSize == 6 })
    }

    @Test fun `taking collects the table and keeps attacker for next round`() {
        val engine = MultiplayerDurakEngine(SeededRandom())
        engine.start((1..3).map { MultiplayerDurakEngine.Seat("p$it", "P$it") })
        val initial = engine.snapshotFor("p1")
        val attacker = initial.players.single { it.isAttacker }.id
        val defender = initial.defenderId
        assertTrue(engine.attack(attacker, engine.snapshotFor(attacker).localHand.first()))
        assertTrue(engine.take(defender))
        assertTrue(engine.bito(attacker))
        val next = engine.snapshotFor(defender)
        assertTrue(next.table.isEmpty())
        assertEquals(attacker, next.players.single { it.isAttacker }.id)
        assertTrue(next.players.single { it.id == defender }.handSize >= 7)
    }

    @Test fun `transfer mode moves defense to the next seat`() {
        var verified = false
        for (seed in 1..100) {
            val engine = MultiplayerDurakEngine(SeededRandom(seed))
            engine.start((1..3).map { MultiplayerDurakEngine.Seat("p$it", "P$it") }, transferMode = true)
            val initial = engine.snapshotFor("p1")
            val attacker = initial.players.single { it.isAttacker }.id
            val defender = initial.defenderId
            val attackCard = engine.snapshotFor(attacker).localHand.firstOrNull { attack ->
                engine.snapshotFor(defender).localHand.any { it.rank == attack.rank }
            } ?: continue
            val transferCard = engine.snapshotFor(defender).localHand.first { it.rank == attackCard.rank }
            assertTrue(engine.attack(attacker, attackCard))
            assertTrue(engine.transfer(defender, transferCard))
            val state = engine.snapshotFor(defender)
            assertEquals(defender, state.players.single { it.isAttacker }.id)
            assertFalse(state.defenderId == defender)
            assertEquals(2, state.table.size)
            verified = true
            break
        }
        assertTrue("Expected at least one deterministic transferable deal", verified)
    }

    private class SeededRandom(seed: Int = 1) : SecureRandom() {
        private var state = seed.toLong()
        override fun nextInt(bound: Int): Int {
            state = (state * 1103515245L + 12345L) and 0x7fffffff
            return (state % bound).toInt()
        }
    }
}
