package com.example.engine

import com.example.model.Card
import com.example.model.CardPair
import com.example.model.Rank
import com.example.model.Suit
import java.security.SecureRandom

/**
 * Host-authoritative classic Durak engine for a local room of two to six
 * players.  Network/UI code receives a projection for one player so no hand
 * is ever exposed to another device.
 */
class MultiplayerDurakEngine(private val random: SecureRandom = SecureRandom()) {
    data class Seat(val id: String, val nickname: String)
    data class PlayerView(val id: String, val nickname: String, val handSize: Int, val isAttacker: Boolean, val isDefender: Boolean)
    data class Snapshot(
        val localHand: List<Card>,
        val players: List<PlayerView>,
        val table: List<CardPair>,
        val trumpCard: Card?,
        val trumpSuit: Suit,
        val deckSize: Int,
        val discardSize: Int,
        val currentPlayerId: String,
        val defenderId: String,
        val taking: Boolean,
        val finished: Boolean,
        val transferMode: Boolean,
        val loserId: String?,
        val attackPlayerIds: List<String> = players.filter { it.isAttacker }.map { it.id },
        val passedPlayerIds: List<String> = emptyList(),
        val roundAttackerIds: List<String> = emptyList(),
        val withdrawnPlayerIds: List<String> = emptyList()
    )

    private val hands = linkedMapOf<String, MutableList<Card>>()
    private var seats = emptyList<Seat>()
    private val deck = mutableListOf<Card>()
    private val table = mutableListOf<CardPair>()
    private val attackOwners = mutableListOf<String>()
    private val roundAttackers = linkedSetOf<String>()
    private val passedAttackers = mutableSetOf<String>()
    private val withdrawn = mutableSetOf<String>()
    private var leadPassed = false
    private var attackerIndex = 0
    private var defenderIndex = 1
    private var transferredThisRound = false
    private var defenderStartHandSize = 0
    private var taking = false
    private var discardSize = 0
    private var finished = false
    private var transferMode = false
    private var loserId: String? = null
    private var trumpCard: Card? = null
    private var trumpSuit = Suit.SPADES

    fun start(players: List<Seat>, deckSize: Int = 36, transferMode: Boolean = false, customDeckIds: Set<String>? = null) {
        require(players.size in 2..6) { "Durak needs from two to six players" }
        require(players.map { it.id }.distinct().size == players.size) { "Player ids must be unique" }
        seats = players
        withdrawn.clear()
        hands.clear()
        players.forEach { hands[it.id] = mutableListOf() }
        deck.clear()
        table.clear()
        attackOwners.clear()
        roundAttackers.clear()
        passedAttackers.clear()
        leadPassed = false
        discardSize = 0
        taking = false
        transferredThisRound = false
        defenderStartHandSize = 0
        finished = false
        loserId = null
        this.transferMode = transferMode
        for (suit in Suit.values()) for (rank in Rank.values()) {
            val card = Card(suit, rank)
            if (customDeckIds?.contains(card.id) == true || (customDeckIds == null && (deckSize == 52 || rank.value >= 6))) deck += card
        }
        require(deck.size >= players.size * 6) { "The selected deck is too small for all players" }
        repeat(7) { deck.shuffle(random) }
        // The bottom card defines trump even when six players exhaust the deck.
        trumpCard = deck.first()
        trumpSuit = trumpCard!!.suit
        repeat(6) {
            seats.forEach { seat -> drawOne(seat.id) }
        }
        attackerIndex = firstAttackerIndex()
        defenderIndex = nextActiveIndex(attackerIndex)
    }

    fun attack(playerId: String, card: Card): Boolean {
        if (finished || playerId in withdrawn || playerId == defenderId || !isAttackTurn(playerId)) return false
        val hand = hands[playerId] ?: return false
        if (!hand.contains(card) || table.size >= 6) return false
        if (table.isEmpty()) {
            defenderStartHandSize = hands[defenderId]?.size ?: 0
            roundAttackers.clear()
            seats.filter { hands[it.id].orEmpty().isNotEmpty() }.forEach { roundAttackers += it.id }
        }
        if (table.size >= defenderStartHandSize) return false
        if (table.isNotEmpty() && table.none { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }) return false
        hand.remove(card)
        table += CardPair(card)
        attackOwners += playerId
        // A helper's new card invalidates the lead's confirmation, but keeps
        // the throw-in phase open. Passing without adding cards does not.
        if (leadPassed && playerId != attackerId) passedAttackers.remove(attackerId)
        return true
    }

    fun transfer(playerId: String, card: Card): Boolean {
        if (!transferMode || finished || taking || playerId != defenderId || table.isEmpty()) return false
        if (table.size >= 6 || table.any { it.defenseCard != null } || table.none { it.attackCard.rank == card.rank }) return false
        val hand = hands[playerId] ?: return false
        val nextDefender = nextActiveIndex(defenderIndex)
        if (nextDefender == defenderIndex) return false
        if (table.size + 1 > (hands[seats[nextDefender].id]?.size ?: 0)) return false
        if (!hand.remove(card)) return false
        table += CardPair(card)
        attackOwners += playerId
        attackerIndex = defenderIndex
        defenderIndex = nextDefender
        transferredThisRound = true
        defenderStartHandSize = hands[defenderId]?.size ?: 0
        return true
    }

    fun takeBack(playerId: String, card: Card): Boolean {
        // Retract only the most recent uncommitted attack. A transfer changes
        // roles, and removing an earlier card would invalidate other players' moves.
        if (finished || taking || transferredThisRound || table.any { it.defenseCard != null }) return false
        val index = table.lastIndex
        if (index < 0 || table[index].attackCard != card || attackOwners.getOrNull(index) != playerId) return false
        table.removeAt(index)
        attackOwners.removeAt(index)
        hands[playerId]?.add(card) ?: return false
        return true
    }

    fun defend(playerId: String, attackCard: Card, defenseCard: Card): Boolean {
        if (finished || taking || playerId != defenderId) return false
        val hand = hands[playerId] ?: return false
        val index = table.indexOfFirst { it.attackCard == attackCard && it.defenseCard == null }
        if (index == -1 || !hand.contains(defenseCard) || !defenseCard.canBeat(attackCard, trumpSuit)) return false
        hand.remove(defenseCard)
        table[index] = CardPair(attackCard, defenseCard)
        return true
    }

    fun take(playerId: String): Boolean {
        if (finished || playerId != defenderId || table.isEmpty() || taking) return false
        taking = true
        return true
    }

    /** Lead attacker passes first; the remaining attackers each throw in or pass. */
    fun bito(playerId: String): Boolean {
        if (finished || playerId !in attackPlayerIds || table.isEmpty()) return false
        if (!taking && table.any { it.defenseCard == null }) return false
        passedAttackers += playerId
        if (playerId == attackerId) leadPassed = true
        if (attackPlayerIds.isNotEmpty()) return true
        closeRound()
        return true
    }

    private fun closeRound() {
        val defenderWasTaking = taking
        val oldDefender = defenderIndex
        if (defenderWasTaking) {
            val defenderHand = hands.getValue(defenderId)
            table.forEach { pair -> defenderHand += pair.attackCard; pair.defenseCard?.let { defenderHand += it } }
        } else {
            discardSize += table.sumOf { if (it.defenseCard == null) 1 else 2 }
        }
        table.clear()
        attackOwners.clear()
        roundAttackers.clear()
        passedAttackers.clear()
        leadPassed = false
        refillHands()
        val preferredAttacker = if (defenderWasTaking) nextActiveIndex(oldDefender) else oldDefender
        attackerIndex = if (hands[seats[preferredAttacker].id].orEmpty().isNotEmpty() || deck.isNotEmpty()) {
            preferredAttacker
        } else nextActiveIndex(preferredAttacker)
        defenderIndex = nextActiveIndex(attackerIndex)
        taking = false
        transferredThisRound = false
        evaluateFinished()
    }

    /** Explicit departure discards that hand; a temporary connection loss never calls this. */
    fun forfeit(playerId: String): Boolean {
        val index = seats.indexOfFirst { it.id == playerId }
        if (finished || index < 0 || playerId in withdrawn) return false
        withdrawn += playerId
        discardSize += hands.getValue(playerId).size
        hands.getValue(playerId).clear()
        roundAttackers.remove(playerId); passedAttackers.remove(playerId)
        if (index == defenderIndex) {
            discardSize += table.sumOf { if (it.defenseCard == null) 1 else 2 }
            table.clear(); attackOwners.clear(); roundAttackers.clear(); passedAttackers.clear()
            leadPassed = false; taking = false; transferredThisRound = false
            refillHands()
            if (attackerId in withdrawn || hands[attackerId].isNullOrEmpty() && deck.isEmpty()) attackerIndex = nextActiveIndex(attackerIndex)
            defenderIndex = nextActiveIndex(attackerIndex)
        } else if (index == attackerIndex) {
            attackerIndex = seats.indices.firstOrNull { it != defenderIndex && seats[it].id !in withdrawn && (hands[seats[it].id].orEmpty().isNotEmpty() || deck.isNotEmpty()) }
                ?: defenderIndex
            if (attackerIndex == defenderIndex) defenderIndex = nextActiveIndex(attackerIndex)
            leadPassed = false; passedAttackers.clear()
        } else if (table.isNotEmpty() && leadPassed && attackPlayerIds.isEmpty()) closeRound()
        if (seats.count { it.id !in withdrawn } <= 1) {
            finished = true; loserId = playerId
        } else evaluateFinished()
        return true
    }

    fun snapshotFor(playerId: String): Snapshot {
        val localHand = hands[playerId]?.toList() ?: emptyList()
        return Snapshot(
            localHand = localHand,
            players = seats.map { seat -> PlayerView(seat.id, seat.nickname, hands[seat.id]?.size ?: 0, seat.id == attackerId, seat.id == defenderId) },
            table = table.toList(), trumpCard = trumpCard, trumpSuit = trumpSuit,
            deckSize = deck.size, discardSize = discardSize,
            currentPlayerId = currentPlayerId, defenderId = defenderId,
            taking = taking, finished = finished, transferMode = transferMode, loserId = loserId,
            attackPlayerIds = attackPlayerIds, passedPlayerIds = passedAttackers.toList(),
            roundAttackerIds = roundAttackers.filter { it != defenderId && it !in withdrawn }, withdrawnPlayerIds = withdrawn.toList()
        )
    }

    private val attackerId get() = seats[attackerIndex].id
    private val defenderId get() = seats[defenderIndex].id
    private val currentPlayerId get() = if (!taking && table.any { it.defenseCard == null }) defenderId else attackPlayerIds.firstOrNull() ?: attackerId

    private val attackPlayerIds: List<String> get() = if (!leadPassed) listOf(attackerId) else {
        seats.indices.map { seats[(attackerIndex + it) % seats.size].id }
            .filter { it != defenderId && it in roundAttackers && it !in passedAttackers }
    }

    private fun isAttackTurn(playerId: String) = playerId in attackPlayerIds

    private fun nextActiveIndex(from: Int): Int {
        var index = from
        repeat(seats.size) {
            index = (index + 1) % seats.size
            if (seats[index].id !in withdrawn && (hands[seats[index].id]?.isNotEmpty() == true || deck.isNotEmpty())) return index
        }
        return (from + 1) % seats.size
    }

    private fun firstAttackerIndex(): Int {
        return seats.indices.minByOrNull { index ->
            hands[seats[index].id].orEmpty().filter { it.suit == trumpSuit }.minOfOrNull { it.rank.value } ?: Int.MAX_VALUE
        } ?: 0
    }

    private fun refillHands() {
        // Attackers draw clockwise first; the defender always draws last.
        val order = seats.indices.map { (attackerIndex + it) % seats.size }
            .filter { it != defenderIndex && seats[it].id !in withdrawn } + listOf(defenderIndex).filter { seats[it].id !in withdrawn }
        order.forEach { index ->
            val id = seats[index].id
            while (hands.getValue(id).size < 6 && deck.isNotEmpty()) drawOne(id)
        }
    }

    private fun drawOne(id: String) { if (deck.isNotEmpty()) hands[id]!!.add(deck.removeAt(deck.lastIndex)) }

    private fun evaluateFinished() {
        if (deck.isNotEmpty()) return
        val remaining = seats.filter { hands[it.id].orEmpty().isNotEmpty() }
        finished = remaining.size <= 1
        loserId = remaining.singleOrNull()?.id
    }
}
