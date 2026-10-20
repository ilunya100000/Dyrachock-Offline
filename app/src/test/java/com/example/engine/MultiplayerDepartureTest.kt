package com.example.engine

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class MultiplayerDepartureTest {
    private class Random(seed: Long) : SecureRandom() {
        private val random = java.util.Random(seed)
        override fun nextInt(bound: Int) = random.nextInt(bound)
    }
    private fun check(game: MultiplayerDurakEngine, size: Int) {
        val state = game.snapshotFor("p0")
        val cards = state.players.flatMap { game.snapshotFor(it.id).localHand } + state.table.flatMap { listOfNotNull(it.attackCard, it.defenseCard) }
        assertEquals(cards.size, cards.distinct().size)
        assertEquals(size, cards.size + state.deckSize + state.discardSize)
    }
    @Test fun `departures in every role preserve cards and allow remaining players to finish`() {
        for (count in 3..6) for (size in listOf(36, 52)) for (role in 0..2) for (taking in listOf(false, true)) {
            val game = MultiplayerDurakEngine(Random((count + size + role).toLong()))
            game.start((0 until count).map { MultiplayerDurakEngine.Seat("p$it", "Player $it") }, size)
            val before = game.snapshotFor("p0")
            val lead = before.players.single { it.isAttacker }.id
            val helper = before.players.first { it.id != lead && it.id != before.defenderId }.id
            val departed = listOf(lead, before.defenderId, helper)[role]
            assertTrue(game.attack(lead, game.snapshotFor(lead).localHand.first()))
            if (taking) { assertTrue(game.take(before.defenderId)); assertTrue(game.bito(lead)) }
            assertTrue(game.forfeit(departed))
            assertFalse(game.forfeit(departed))
            check(game, size)
            val policy = java.util.Random(32)
            var steps = 0
            while (!game.snapshotFor("p0").finished && steps++ < 8000) {
                val state = game.snapshotFor("p0")
                assertTrue(departed in state.withdrawnPlayerIds)
                assertTrue(game.snapshotFor(departed).localHand.isEmpty())
                check(game, size)
                val attacker = state.players.single { it.isAttacker }.id
                val defender = state.defenderId
                assertNotEquals(departed, attacker)
                assertNotEquals(departed, defender)
                fun hand(id: String) = game.snapshotFor(id).localHand.shuffled(policy)
                if (state.table.isEmpty()) assertTrue(game.attack(attacker, hand(attacker).first()))
                else if (!state.taking && state.table.any { it.defenseCard == null }) {
                    val attack = state.table.first { it.defenseCard == null }.attackCard
                    val defense = hand(defender).firstOrNull { it.canBeat(attack, state.trumpSuit) }
                    if (defense == null) assertTrue(game.take(defender)) else assertTrue(game.defend(defender, attack, defense))
                } else if (!state.attackPlayerIds.any { id -> hand(id).any { game.attack(id, it) } }) {
                    assertTrue(game.bito(state.attackPlayerIds.first()))
                }
            }
            assertTrue("Departure role=$role players=$count deck=$size taking=$taking", game.snapshotFor("p0").finished)
            check(game, size)
        }
    }
}
