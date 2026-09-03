package com.example.network

import com.example.engine.MultiplayerDurakEngine
import com.example.model.Card
import com.example.model.CardPair
import com.example.model.Suit
import java.net.URLDecoder
import java.net.URLEncoder

/** Wire format for a per-player 2–6 player game projection. */
object MultiplayerGameProtocol {
    private const val STATE = "MULTI_STATE:"
    private const val ATTACK = "MULTI_ATTACK:"
    private const val DEFEND = "MULTI_DEFEND:"
    private const val TAKE = "MULTI_TAKE"
    private const val BITO = "MULTI_BITO"
    private const val TRANSFER = "MULTI_TRANSFER:"
    private const val TAKE_BACK = "MULTI_TAKE_BACK:"

    fun state(snapshot: MultiplayerDurakEngine.Snapshot): String = STATE + listOf(
        cards(snapshot.localHand),
        players(snapshot.players),
        table(snapshot.table),
        NetworkProtocol.encodeCard(snapshot.trumpCard),
        snapshot.trumpSuit.name,
        snapshot.deckSize.toString(), snapshot.discardSize.toString(),
        snapshot.currentPlayerId, snapshot.defenderId,
        snapshot.taking.toString(), snapshot.finished.toString(), snapshot.transferMode.toString(), snapshot.loserId.orEmpty()
    ).joinToString("|") { encode(it) }

    fun parseState(raw: String): State? {
        if (!raw.startsWith(STATE)) return null
        val f = raw.removePrefix(STATE).split("|")
        if (f.size != 13) return null
        return runCatching {
            State(
                hand = parseCards(decode(f[0])), players = parsePlayers(decode(f[1])), table = parseTable(decode(f[2])),
                trumpCard = NetworkProtocol.decodeCard(decode(f[3])),
                trumpSuit = Suit.valueOf(decode(f[4])), deckSize = decode(f[5]).toInt(), discardSize = decode(f[6]).toInt(),
                currentPlayerId = decode(f[7]), defenderId = decode(f[8]), taking = decode(f[9]).toBoolean(), finished = decode(f[10]).toBoolean(),
                transferMode = decode(f[11]).toBoolean(), loserId = decode(f[12]).ifBlank { null }
            )
        }.getOrNull()
    }

    fun attack(card: Card) = ATTACK + NetworkProtocol.encodeCard(card)
    fun defend(attack: Card, defense: Card) = DEFEND + NetworkProtocol.encodeCard(attack) + ";" + NetworkProtocol.encodeCard(defense)
    fun take() = TAKE
    fun bito() = BITO
    fun transfer(card: Card) = TRANSFER + NetworkProtocol.encodeCard(card)
    fun takeBack(card: Card) = TAKE_BACK + NetworkProtocol.encodeCard(card)

    sealed interface Action {
        data class Attack(val card: Card) : Action
        data class Defend(val attack: Card, val defense: Card) : Action
        data class Transfer(val card: Card) : Action
        data class TakeBack(val card: Card) : Action
        data object Take : Action
        data object Bito : Action
    }

    fun parseAction(raw: String): Action? = when {
        raw.startsWith(ATTACK) -> NetworkProtocol.decodeCard(raw.removePrefix(ATTACK))?.let(Action::Attack)
        raw.startsWith(DEFEND) -> raw.removePrefix(DEFEND).split(";").takeIf { it.size == 2 }?.let { parts ->
            val attack = NetworkProtocol.decodeCard(parts[0]); val defense = NetworkProtocol.decodeCard(parts[1])
            if (attack != null && defense != null) Action.Defend(attack, defense) else null
        }
        raw.startsWith(TRANSFER) -> NetworkProtocol.decodeCard(raw.removePrefix(TRANSFER))?.let(Action::Transfer)
        raw.startsWith(TAKE_BACK) -> NetworkProtocol.decodeCard(raw.removePrefix(TAKE_BACK))?.let(Action::TakeBack)
        raw == TAKE -> Action.Take
        raw == BITO -> Action.Bito
        else -> null
    }

    data class State(val hand: List<Card>, val players: List<MultiplayerDurakEngine.PlayerView>, val table: List<CardPair>, val trumpCard: Card?, val trumpSuit: Suit, val deckSize: Int, val discardSize: Int, val currentPlayerId: String, val defenderId: String, val taking: Boolean, val finished: Boolean, val transferMode: Boolean, val loserId: String?)

    private fun cards(cards: List<Card>) = cards.joinToString(";") { NetworkProtocol.encodeCard(it) }
    private fun parseCards(value: String) = value.split(";").filter { it.isNotBlank() }.mapNotNull(NetworkProtocol::decodeCard)
    private fun table(table: List<CardPair>) = table.joinToString(";") { NetworkProtocol.encodeCardPair(it) }
    private fun parseTable(value: String) = value.split(";").filter { it.isNotBlank() }.mapNotNull(NetworkProtocol::decodeCardPair)
    private fun players(players: List<MultiplayerDurakEngine.PlayerView>) = players.joinToString(";") { listOf(encode(it.id), encode(it.nickname), it.handSize, it.isAttacker, it.isDefender).joinToString(",") }
    private fun parsePlayers(value: String) = value.split(";").filter { it.isNotBlank() }.map { row ->
        val f = row.split(","); require(f.size == 5); MultiplayerDurakEngine.PlayerView(decode(f[0]), decode(f[1]), f[2].toInt(), f[3].toBoolean(), f[4].toBoolean())
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
}
