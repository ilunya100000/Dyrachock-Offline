package com.example.network

import com.example.model.*

object NetworkProtocol {
    
    // Encodes a Card into a simple string payload (e.g. "HEARTS-ACE")
    fun encodeCard(card: Card?): String {
        if (card == null) return "NULL"
        return "${card.suit.name}-${card.rank.name}"
    }

    // Decodes a Card from a string payload
    fun decodeCard(payload: String): Card? {
        if (payload == "NULL" || payload.isBlank()) return null
        val parts = payload.split("-")
        if (parts.size != 2) return null
        val suit = Suit.values().find { it.name == parts[0] } ?: return null
        val rank = Rank.values().find { it.name == parts[1] } ?: return null
        return Card(suit, rank)
    }

    // Encodes a CardPair
    fun encodeCardPair(pair: CardPair): String {
        val attackStr = encodeCard(pair.attackCard)
        val defenseStr = encodeCard(pair.defenseCard)
        return "$attackStr|$defenseStr"
    }

    // Decodes a CardPair
    fun decodeCardPair(payload: String): CardPair? {
        val parts = payload.split("|")
        if (parts.size != 2) return null
        val attack = decodeCard(parts[0]) ?: return null
        val defense = decodeCard(parts[1])
        return CardPair(attack, defense)
    }

    // Encodes a full GameStateSnapshot to send from Host to Client
    // We send: TrumpCard, TrumpSuit, DeckSize, DiscardPileSize, AttackerId, MatchStatus, GameLog(list), TablePairs(list), HostHandSize(for Client to render as opponentHandSize), ClientHand(list)
    fun serializeState(
        snapshot: GameStateSnapshot,
        hostHand: List<Card>,
        clientHand: List<Card>
    ): String {
        val trumpStr = encodeCard(snapshot.trumpCard)
        val trumpSuitStr = snapshot.trumpSuit.name
        val deckSizeStr = snapshot.deckSize.toString()
        val discardSizeStr = snapshot.discardPileSize.toString()
        val attackerIdStr = snapshot.attackerPlayerId
        val matchStatusStr = snapshot.matchStatus.name
        
        val logsStr = snapshot.gameLog.joinToString(";") { it.replace(";", "").replace("\n", "") }
        val logsEnStr = snapshot.gameLogEn.joinToString(";") { it.replace(";", "").replace("\n", "") }
        val logsRuStr = snapshot.gameLogRu.joinToString(";") { it.replace(";", "").replace("\n", "") }
        val tablePairsStr = snapshot.tablePairs.joinToString(";") { encodeCardPair(it) }
        val hostHandStr = hostHand.joinToString(";") { encodeCard(it) }
        val clientHandStr = clientHand.joinToString(";") { encodeCard(it) }

        return buildString {
            append("TRUMP:").append(trumpStr).append("\n")
            append("TRUMPSUIT:").append(trumpSuitStr).append("\n")
            append("DECKSIZE:").append(deckSizeStr).append("\n")
            append("DISCARDSIZE:").append(discardSizeStr).append("\n")
            append("ATTACKERID:").append(attackerIdStr).append("\n")
            append("MATCHSTATUS:").append(matchStatusStr).append("\n")
            append("LOGS:").append(logsStr).append("\n")
            append("LOGSEN:").append(logsEnStr).append("\n")
            append("LOGSRU:").append(logsRuStr).append("\n")
            append("TABLE:").append(tablePairsStr).append("\n")
            append("HOST_HAND:").append(hostHandStr).append("\n")
            append("CLIENT_HAND:").append(clientHandStr).append("\n")
        }
    }

    // Decodes a serialized screen state back into a usable form
    // Let's return a map or a structured packet
    fun deserializeState(payload: String): StatePayload? {
        val lines = payload.split("\n")
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf(":")
            if (idx != -1) {
                val key = line.substring(0, idx)
                val value = line.substring(idx + 1)
                map[key] = value
            }
        }

        try {
            val trumpCard = decodeCard(map["TRUMP"] ?: "NULL")
            val trumpSuit = Suit.values().find { it.name == (map["TRUMPSUIT"] ?: "SPADES") } ?: Suit.SPADES
            val deckSize = map["DECKSIZE"]?.toIntOrNull() ?: 0
            val discardPileSize = map["DISCARDSIZE"]?.toIntOrNull() ?: 0
            val attackerId = map["ATTACKERID"] ?: "player"
            val matchStatus = MatchStatus.values().find { it.name == (map["MATCHSTATUS"] ?: "PLAYING") } ?: MatchStatus.PLAYING
            
            val logsRaw = map["LOGS"] ?: ""
            val logs = if (logsRaw.isEmpty()) emptyList() else logsRaw.split(";")
            
            val logsEnRaw = map["LOGSEN"] ?: ""
            val logsEn = if (logsEnRaw.isEmpty()) emptyList() else logsEnRaw.split(";")

            val logsRuRaw = map["LOGSRU"] ?: ""
            val logsRu = if (logsRuRaw.isEmpty()) emptyList() else logsRuRaw.split(";")
            
            val tableRaw = map["TABLE"] ?: ""
            val tablePairs = if (tableRaw.isEmpty()) emptyList() else {
                tableRaw.split(";").mapNotNull { decodeCardPair(it) }
            }

            val hostHandRaw = map["HOST_HAND"] ?: ""
            val hostHand = if (hostHandRaw.isEmpty()) emptyList() else {
                hostHandRaw.split(";").mapNotNull { decodeCard(it) }
            }

            val clientHandRaw = map["CLIENT_HAND"] ?: ""
            val clientHand = if (clientHandRaw.isEmpty()) emptyList() else {
                clientHandRaw.split(";").mapNotNull { decodeCard(it) }
            }

            return StatePayload(
                trumpCard = trumpCard,
                trumpSuit = trumpSuit,
                deckSize = deckSize,
                discardPileSize = discardPileSize,
                attackerId = attackerId,
                matchStatus = matchStatus,
                gameLog = logs,
                gameLogEn = logsEn,
                gameLogRu = logsRu,
                tablePairs = tablePairs,
                hostHand = hostHand,
                clientHand = clientHand
            )
        } catch (e: Exception) {
            return null
        }
    }

    data class StatePayload(
        val trumpCard: Card?,
        val trumpSuit: Suit,
        val deckSize: Int,
        val discardPileSize: Int,
        val attackerId: String,
        val matchStatus: MatchStatus,
        val gameLog: List<String>,
        val gameLogEn: List<String>,
        val gameLogRu: List<String>,
        val tablePairs: List<CardPair>,
        val hostHand: List<Card>,
        val clientHand: List<Card>
    )

    // Action types to send from Client to Host OR Host to Client
    // Represented by clean formatted strings "ACTION_ATTACK:card" or "ACTION_DEFEND:attack_card|defend_card"
    fun encodeActionAttack(card: Card): String = "ACTION_ATTACK:${encodeCard(card)}"
    fun encodeActionDefend(attackCard: Card, defenseCard: Card): String = "ACTION_DEFEND:${encodeCard(attackCard)}|${encodeCard(defenseCard)}"
    fun encodeActionTake(): String = "ACTION_TAKE"
    fun encodeActionBito(): String = "ACTION_BITO"
}
