package com.example.engine

import com.example.model.Card
import com.example.network.MultiplayerGameProtocol
import com.example.network.toGameSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class MultiplayerRegressionTest {
    private class Random(seed: Long) : SecureRandom() {
        private val delegate = java.util.Random(seed)
        override fun nextInt(bound: Int) = delegate.nextInt(bound)
    }

    private fun engine(count: Int, seed: Long = 1, transfer: Boolean = false, size: Int = 36) =
        MultiplayerDurakEngine(Random(seed)).apply {
            start((0 until count).map { MultiplayerDurakEngine.Seat("p$it", "Игрок $it") }, size, transfer)
        }

    @Test fun `six player trump remains the bottom card dealt into a hand`() {
        val suits = mutableSetOf<com.example.model.Suit>()
        for (seed in 1L..20L) {
            val game = engine(6, seed)
            val state = game.snapshotFor("p0")
            assertNotNull(state.trumpCard)
            assertEquals(state.trumpCard!!.suit, state.trumpSuit)
            assertEquals(state.trumpCard, game.snapshotFor("p5").localHand.last())
            suits += state.trumpSuit
        }
        assertTrue("Trump must not be hardcoded to spades", suits.size > 1)
    }

    @Test fun `defender owns the turn after any attack not the next helper`() {
        for (count in 3..6) {
            val game = engine(count)
            val state = game.snapshotFor("p0")
            val attacker = state.players.single { it.isAttacker }.id
            assertTrue(game.attack(attacker, game.snapshotFor(attacker).localHand.first()))
            assertEquals(state.defenderId, game.snapshotFor(attacker).currentPlayerId)
        }
    }

    @Test fun `helpers can throw matching cards after defender declares take`() {
        for (count in 3..6) {
            var verified = false
            for (seed in 1L..100L) {
                val game = engine(count, seed)
                val state = game.snapshotFor("p0")
                val attacker = state.players.single { it.isAttacker }.id
                val helper = state.players.first { it.id != attacker && it.id != state.defenderId }.id
                val hand = game.snapshotFor(helper).localHand
                val card = game.snapshotFor(attacker).localHand.firstOrNull { a -> hand.any { it.rank == a.rank } } ?: continue
                val extra = hand.first { it.rank == card.rank }
                assertTrue(game.attack(attacker, card))
                assertTrue(game.take(state.defenderId))
                val view = MultiplayerGameProtocol.parseState(MultiplayerGameProtocol.state(game.snapshotFor(helper)))!!.toGameSnapshot(helper)
                assertTrue("Helper must see throw-in availability", view.isLocalTurn)
                assertTrue(game.attack(helper, extra))
                assertFalse("Only lead attacker may finish", game.bito(helper))
                assertFalse(game.bito("unknown"))
                assertTrue(game.bito(attacker))
                verified = true
                break
            }
            assertTrue("Expected matching-rank deal for $count players", verified)
        }
    }

    @Test fun `taking rotates attack past defender for three to six players`() {
        for (count in 2..6) {
            val game = engine(count)
            val before = game.snapshotFor("p0")
            val attacker = before.players.single { it.isAttacker }.id
            val defenderIndex = before.players.indexOfFirst { it.id == before.defenderId }
            game.attack(attacker, game.snapshotFor(attacker).localHand.first())
            game.take(before.defenderId)
            assertTrue(game.bito(attacker))
            assertEquals(before.players[(defenderIndex + 1) % count].id, game.snapshotFor(attacker).currentPlayerId)
        }
    }

    @Test fun `complete games preserve all cards and private player perspectives`() {
        for (count in 2..6) for (size in listOf(36, 52)) for (transfer in listOf(false, true)) for (seed in 1L..5L) {
            val game = engine(count, seed, transfer, size)
            var steps = 0
            while (!game.snapshotFor("p0").finished && steps++ < 6000) {
                val state = game.snapshotFor("p0")
                val attacker = state.players.single { it.isAttacker }.id
                val defender = state.defenderId
                val hands = state.players.flatMap { game.snapshotFor(it.id).localHand }
                val onTable = state.table.flatMap { listOfNotNull(it.attackCard, it.defenseCard) }
                assertEquals("Card count in $count-player game", size, hands.size + state.deckSize + state.discardSize + onTable.size)
                assertEquals("No hand or table duplicates", (hands + onTable).size, (hands + onTable).distinct().size)
                state.players.forEach { seat ->
                    val wire = MultiplayerGameProtocol.parseState(MultiplayerGameProtocol.state(game.snapshotFor(seat.id)))!!
                    val view = wire.toGameSnapshot(seat.id)
                    assertEquals(count - 1, view.opponents.size)
                    assertEquals(seat.id, view.localPlayerId)
                    assertEquals(seat.handSize, view.localHand.size)
                    assertEquals(state.currentPlayerId, view.currentPlayerId)
                    assertEquals(state.defenderId, view.defenderPlayerId)
                    if (view.canBito) assertEquals(attacker, seat.id)
                }
                fun lowest(id: String) = game.snapshotFor(id).localHand.sortedBy { it.rank.value + if (it.suit == state.trumpSuit) 20 else 0 }
                if (state.table.isEmpty()) {
                    assertTrue(game.attack(attacker, lowest(attacker).first()))
                } else if (!state.taking && state.table.any { it.defenseCard == null }) {
                    val hand = lowest(defender)
                    if (transfer && hand.any { game.transfer(defender, it) }) continue
                    val attack = state.table.first { it.defenseCard == null }.attackCard
                    val defense = hand.firstOrNull { it.canBeat(attack, state.trumpSuit) }
                    if (defense == null) assertTrue(game.take(defender))
                    else assertTrue(game.defend(defender, attack, defense))
                } else {
                    val added = state.players.filter { it.id != defender }.any { seat ->
                        lowest(seat.id).any { game.attack(seat.id, it) }
                    }
                    if (!added) assertTrue(game.bito(attacker))
                }
            }
            assertTrue("Game must finish: players=$count deck=$size transfer=$transfer seed=$seed", game.snapshotFor("p0").finished)
        }
    }
}
