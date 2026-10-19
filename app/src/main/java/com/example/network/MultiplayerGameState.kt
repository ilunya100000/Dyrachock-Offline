package com.example.network

import com.example.model.GameStateSnapshot
import com.example.model.MatchStatus
import com.example.model.RemotePlayerSnapshot

/** One shared projection for the host and every client, including spectators. */
fun MultiplayerGameProtocol.State.toGameSnapshot(localId: String): GameStateSnapshot {
    require(players.any { it.id == localId }) { "Unknown local player" }
    val localIndex = players.indexOfFirst { it.id == localId }
    val others = (players.drop(localIndex + 1) + players.take(localIndex))
    val attacker = players.single { it.isAttacker }
    val localIsDefender = defenderId == localId
    val hasUndefended = table.any { it.defenseCard == null }
    val primary = others.firstOrNull { it.id == currentPlayerId } ?: others.first()
    val status = when {
        !finished -> MatchStatus.PLAYING
        loserId == null -> MatchStatus.DRAW
        loserId == localId -> MatchStatus.LOST
        else -> MatchStatus.WON
    }
    val canAttack = !localIsDefender && hand.isNotEmpty() && table.size < 6 &&
        (table.isNotEmpty() || attacker.id == localId) &&
        table.size < (players.single { it.id == defenderId }.handSize + table.count { it.defenseCard != null }) &&
        (table.isEmpty() || hand.any { card -> table.any { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank } })
    return GameStateSnapshot(
        trumpCard = trumpCard, trumpSuit = trumpSuit, deckSize = deckSize,
        tablePairs = table, discardPileSize = discardSize,
        isLocalTurn = !finished && if (localIsDefender) hasUndefended && !taking else canAttack,
        localHand = hand, opponentHandSize = primary.handSize, opponentName = primary.nickname,
        matchStatus = status, attackerPlayerId = attacker.id,
        canTake = !finished && localIsDefender && hasUndefended && !taking,
        canBito = !finished && localId == attacker.id && table.isNotEmpty() && (taking || !hasUndefended),
        isTransferMode = transferMode, isDefenderTaking = taking,
        localPlayerId = localId, currentPlayerId = currentPlayerId, defenderPlayerId = defenderId,
        opponents = others.map { RemotePlayerSnapshot(it.id, it.nickname, it.handSize, it.isAttacker, it.isDefender) }
    )
}
