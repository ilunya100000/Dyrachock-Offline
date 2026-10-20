package com.example.engine

import com.example.network.MultiplayerGameProtocol
import com.example.network.toGameSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class MultiplayerBitoTest {
    private class Random(seed: Long) : SecureRandom() {
        private val source = java.util.Random(seed)
        override fun nextInt(bound: Int) = source.nextInt(bound)
    }
    private fun game(count: Int, seed: Long = 1, transfer: Boolean = false) =
        MultiplayerDurakEngine(Random(seed)).apply {
            start((1..count).map { MultiplayerDurakEngine.Seat("p$it", "Player $it") }, transferMode = transfer)
        }
    private fun view(game: MultiplayerDurakEngine, id: String) =
        MultiplayerGameProtocol.parseState(MultiplayerGameProtocol.state(game.snapshotFor(id)))!!.toGameSnapshot(id)

    @Test fun `lead passes first then every helper must confirm without clearing the table early`() {
        for (count in 2..6) {
            val game = game(count)
            val initial = game.snapshotFor("p1")
            val lead = initial.players.single { it.isAttacker }.id
            val helpers = initial.players.filter { it.id != lead && it.id != initial.defenderId }.map { it.id }
            val attack = game.snapshotFor(lead).localHand.first()
            assertTrue(game.attack(lead, attack))
            helpers.forEach { id ->
                assertFalse(view(game, id).isLocalTurn)
                assertFalse(view(game, id).canBito)
                assertFalse(game.bito(id))
            }
            assertFalse(game.bito(lead)) // Still undefended.
            assertTrue(game.take(initial.defenderId))
            val before = game.snapshotFor(lead)
            assertTrue(view(game, lead).canBito)
            assertTrue(game.bito(lead))
            helpers.reversed().forEachIndexed { index, id ->
                val state = game.snapshotFor(id)
                assertEquals(before.table, state.table)
                assertEquals(before.deckSize, state.deckSize)
                assertEquals(before.players, state.players)
                assertFalse(view(game, lead).canBito)
                assertTrue(view(game, id).canBito)
                assertTrue(game.bito(id))
                if (index < helpers.lastIndex) {
                    assertFalse(view(game, id).canBito)
                    assertFalse(game.bito(id))
                }
            }
            assertTrue(game.snapshotFor(lead).table.isEmpty())
            assertFalse(game.bito("unknown"))
        }
    }

    @Test fun `helper can throw after lead passes and must wait for defense before confirming`() {
        var verified = false
        for (seed in 1L..500L) {
            val game = game(3, seed)
            val state = game.snapshotFor("p1")
            val lead = state.players.single { it.isAttacker }.id
            val defender = state.defenderId
            val helper = state.players.single { it.id != lead && it.id != defender }.id
            val helperHand = game.snapshotFor(helper).localHand
            val defenderHand = game.snapshotFor(defender).localHand
            val first = game.snapshotFor(lead).localHand.firstOrNull { card ->
                helperHand.any { it.rank == card.rank } && defenderHand.any { it.canBeat(card, state.trumpSuit) }
            } ?: continue
            val defense = defenderHand.first { it.canBeat(first, state.trumpSuit) }
            val extra = helperHand.first { it.rank == first.rank }
            assertTrue(game.attack(lead, first))
            assertFalse(game.attack(helper, extra))
            assertTrue(game.defend(defender, first, defense))
            assertFalse(view(game, helper).canBito)
            assertTrue(game.bito(lead))
            assertTrue(view(game, helper).canBito)
            assertTrue(view(game, helper).isLocalTurn)
            assertFalse(game.attack(lead, game.snapshotFor(lead).localHand.first()))
            assertTrue(game.attack(helper, extra))
            assertEquals(defender, game.snapshotFor(helper).currentPlayerId)
            assertFalse(view(game, helper).canBito)
            assertFalse(game.bito(helper))
            assertFalse(view(game, lead).canBito)
            assertFalse(game.bito(lead))
            assertTrue(game.take(defender))
            assertTrue(view(game, helper).canBito)
            assertTrue(view(game, lead).canBito)
            assertTrue(game.bito(helper))
            assertFalse("The helper cannot close the round before lead reconfirms", game.snapshotFor(helper).table.isEmpty())
            assertTrue(game.bito(lead))
            assertTrue(game.snapshotFor(helper).table.isEmpty())
            assertEquals(defenderHand.size + 2, game.snapshotFor(defender).localHand.size)
            verified = true
            break
        }
        assertTrue("A matching defended deal must be exercised", verified)
    }

    @Test fun `each actual helper throw resets lead confirmation without restarting helper phase`() {
        var verified = false
        for (seed in 1L..500L) {
            val game = game(4, seed)
            val state = game.snapshotFor("p1")
            val lead = state.players.single { it.isAttacker }.id
            val helpers = state.players.filter { it.id != lead && it.id != state.defenderId }.map { it.id }
            val first = game.snapshotFor(lead).localHand.firstOrNull { card ->
                helpers.all { id -> game.snapshotFor(id).localHand.any { it.rank == card.rank } }
            } ?: continue
            val extra = helpers.map { id -> game.snapshotFor(id).localHand.first { it.rank == first.rank } }
            assertTrue(game.attack(lead, first))
            assertTrue(game.take(state.defenderId))
            assertTrue(game.bito(lead))
            assertFalse(view(game, lead).canBito)
            assertFalse(game.attack(helpers.first(), first)) // Not in that hand.
            assertFalse(view(game, lead).canBito)
            assertTrue(game.attack(helpers[0], extra[0]))
            assertTrue(view(game, lead).canBito)
            assertTrue(game.bito(lead))
            assertFalse(view(game, lead).canBito)
            assertTrue(view(game, helpers[1]).canBito)
            assertTrue(game.attack(helpers[1], extra[1]))
            assertTrue(view(game, lead).canBito)
            assertTrue(game.bito(helpers[0]))
            assertTrue(game.bito(lead))
            assertEquals(3, game.snapshotFor(lead).table.size)
            assertTrue(game.bito(helpers[1]))
            assertTrue(game.snapshotFor(lead).table.isEmpty())
            verified = true
            break
        }
        assertTrue("Both helpers must exercise confirmation invalidation", verified)
    }

    @Test fun `covered helper throw waits for lead reconfirmation before discarding cards`() {
        var verified = false
        for (seed in 1L..500L) {
            val game = game(3, seed)
            val state = game.snapshotFor("p1")
            val lead = state.players.single { it.isAttacker }.id
            val defender = state.defenderId
            val helper = state.players.single { it.id != lead && it.id != defender }.id
            val defenderHand = game.snapshotFor(defender).localHand
            val first = game.snapshotFor(lead).localHand.firstOrNull { card ->
                val extra = game.snapshotFor(helper).localHand.firstOrNull { it.rank == card.rank }
                extra != null && defenderHand.any { d ->
                    d.canBeat(card, state.trumpSuit) && defenderHand.any { it != d && it.canBeat(extra, state.trumpSuit) }
                }
            } ?: continue
            val extra = game.snapshotFor(helper).localHand.first { it.rank == first.rank }
            val defense = defenderHand.first { d ->
                d.canBeat(first, state.trumpSuit) && defenderHand.any { it != d && it.canBeat(extra, state.trumpSuit) }
            }
            val extraDefense = defenderHand.first { it != defense && it.canBeat(extra, state.trumpSuit) }
            assertTrue(game.attack(lead, first))
            assertTrue(game.defend(defender, first, defense))
            assertTrue(game.bito(lead))
            assertTrue(game.attack(helper, extra))
            assertFalse(game.bito(lead))
            assertTrue(game.defend(defender, extra, extraDefense))
            assertTrue(view(game, lead).canBito)
            assertTrue(game.bito(helper))
            assertEquals(2, game.snapshotFor(lead).table.size)
            assertEquals(0, game.snapshotFor(lead).discardSize)
            assertTrue(game.bito(lead))
            assertTrue(game.snapshotFor(lead).table.isEmpty())
            assertEquals(4, game.snapshotFor(lead).discardSize)
            assertEquals(defender, game.snapshotFor(lead).players.single { it.isAttacker }.id)
            verified = true
            break
        }
        assertTrue("A covered helper throw must be exercised", verified)
    }

    @Test fun `transferred defender becomes the lead who must pass before helpers`() {
        var verified = false
        for (seed in 1L..100L) {
            val game = game(4, seed, transfer = true)
            val state = game.snapshotFor("p1")
            val lead = state.players.single { it.isAttacker }.id
            val defenderHand = game.snapshotFor(state.defenderId).localHand
            val attack = game.snapshotFor(lead).localHand.firstOrNull { a -> defenderHand.any { it.rank == a.rank } } ?: continue
            assertTrue(game.attack(lead, attack))
            assertTrue(game.transfer(state.defenderId, defenderHand.first { it.rank == attack.rank }))
            val after = game.snapshotFor(lead)
            assertEquals(listOf(state.defenderId), after.attackPlayerIds)
            assertTrue(game.take(after.defenderId))
            assertFalse(game.bito(lead))
            assertTrue(game.bito(state.defenderId))
            assertTrue(game.snapshotFor(lead).attackPlayerIds.contains(lead))
            while (game.snapshotFor(lead).table.isNotEmpty()) {
                assertTrue(game.bito(game.snapshotFor(lead).attackPlayerIds.first()))
            }
            verified = true
            break
        }
        assertTrue(verified)
    }
}
