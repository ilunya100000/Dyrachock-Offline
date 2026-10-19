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
        val loserId: String?
    )

    private val hands = linkedMapOf<String, MutableList<Card>>()
    private var seats = emptyList<Seat>()
    private val deck = mutableListOf<Card>()
    private val table = mutableListOf<CardPair>()
    private val attackOwners = mutableListOf<String>()
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
        hands.clear()
        players.forEach { hands[it.id] = mutableListOf() }
        deck.clear()
        table.clear()
        attackOwners.clear()
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
        if (finished || playerId == defenderId || !isAttackTurn(playerId)) return false
        val hand = hands[playerId] ?: return false
        if (!hand.contains(card) || table.size >= 6) return false
        if (table.isEmpty()) defenderStartHandSize = hands[defenderId]?.size ?: 0
        if (table.size >= defenderStartHandSize) return false
        if (table.isNotEmpty() && table.none { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }) return false
        hand.remove(card)
        table += CardPair(card)
        attackOwners += playerId
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

    /** The lead attacker closes the round; helpers cannot close it for them. */
    fun bito(playerId: String): Boolean {
        if (finished || playerId != attackerId || table.isEmpty()) return false
        if (!taking && table.any { it.defenseCard == null }) return false
        val defenderWasTaking = taking
        val oldDefender = defenderIndex
        if (defenderWasTaking) {
            val defenderHand = hands[defenderId] ?: return false
            table.forEach { pair -> defenderHand += pair.attackCard; pair.defenseCard?.let { defenderHand += it } }
        } else {
            discardSize += table.sumOf { if (it.defenseCard == null) 1 else 2 }
        }
        table.clear()
        attackOwners.clear()
        refillHands()
        val preferredAttacker = if (defenderWasTaking) nextActiveIndex(oldDefender) else oldDefender
        attackerIndex = if (hands[seats[preferredAttacker].id].orEmpty().isNotEmpty() || deck.isNotEmpty()) {
            preferredAttacker
        } else nextActiveIndex(preferredAttacker)
        defenderIndex = nextActiveIndex(attackerIndex)
        taking = false
        transferredThisRound = false
        evaluateFinished()
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
            taking = taking, finished = finished, transferMode = transferMode, loserId = loserId
        )
    }

    private val attackerId get() = seats[attackerIndex].id
    private val defenderId get() = seats[defenderIndex].id
    private val currentPlayerId get() = if (!taking && table.any { it.defenseCard == null }) defenderId else attackerId

    private fun isAttackTurn(playerId: String) = table.isEmpty().let { empty ->
        if (empty) playerId == attackerId else seats.any { it.id == playerId && it.id != defenderId }
    }

    private fun nextActiveIndex(from: Int): Int {
        var index = from
        repeat(seats.size) {
            index = (index + 1) % seats.size
            if (hands[seats[index].id]?.isNotEmpty() == true || deck.isNotEmpty()) return index
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
            .filter { it != defenderIndex } + defenderIndex
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
