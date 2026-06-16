package com.example.desktop.engine

import com.example.desktop.model.*
import java.security.SecureRandom

class DurakEngine {
    private val random = SecureRandom()

    var deck: MutableList<Card> = mutableListOf()
    var trumpCard: Card? = null
    var trumpSuit: Suit = Suit.SPADES
    var discardPileSize: Int = 0

    var playerHand: MutableList<Card> = mutableListOf()
    var opponentHand: MutableList<Card> = mutableListOf()

    val tablePairs: MutableList<CardPair> = mutableListOf()

    var attackerId: String = "player"
    var defenderId: String = "opponent"

    var matchStatus: MatchStatus = MatchStatus.NOT_STARTED
    var isTransferMode: Boolean = false
    val gameLogEn: MutableList<String> = mutableListOf()
    val gameLogRu: MutableList<String> = mutableListOf()

    fun log(en: String, ru: String) {
        gameLogEn.add(en)
        gameLogRu.add(ru)
        if (gameLogEn.size > 200) {
            gameLogEn.removeAt(0)
            gameLogRu.removeAt(0)
        }
    }

    fun log(message: String) {
        log(message, message)
    }

    fun startMatch(player1Name: String, player2Name: String, isBotGame: Boolean, deckSize: Int = 36, isTransferMode: Boolean = false, customDeckIds: Set<String>? = null) {
        this.isTransferMode = isTransferMode
        deck.clear()
        tablePairs.clear()
        playerHand.clear()
        opponentHand.clear()
        gameLogEn.clear()
        gameLogRu.clear()
        discardPileSize = 0

        if (customDeckIds != null && customDeckIds.isNotEmpty()) {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    val cardId = "${suit.name}_${rank.name}"
                    if (customDeckIds.contains(cardId)) {
                        deck.add(Card(suit, rank))
                    }
                }
            }
        } else {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    if (deckSize == 52 || rank.value >= 6) {
                        deck.add(Card(suit, rank))
                    }
                }
            }
        }

        repeat(7) { deck.shuffle(random) }

        dealCards()

        if (deck.isNotEmpty()) {
            val trump = deck.removeAt(deck.size - 1)
            trumpCard = trump
            trumpSuit = trump.suit
            deck.add(0, trump)
            log(
                "Trump suit is ${trumpSuit.enLabel} (${trumpSuit.symbol}). Trump card: ${trump.rank.symbol}${trumpSuit.symbol}",
                "Козырная масть - ${trumpSuit.ruLabel} (${trumpSuit.symbol}). Козырь: ${trump.rank.symbol}${trumpSuit.symbol}"
            )
        } else {
            trumpCard = null
            trumpSuit = Suit.SPADES
            log("Trump suit is ${trumpSuit.enLabel}", "Козыри - ${trumpSuit.ruLabel}")
        }

        determineFirstPlayer()

        matchStatus = MatchStatus.PLAYING
        log(
            "Match started! Attacker: ${if (attackerId == "player") player1Name else player2Name}",
            "Матч начался! Нападающий: ${if (attackerId == "player") player1Name else player2Name}"
        )
    }

    private fun dealCards() {
        while (playerHand.size < 6 && deck.isNotEmpty()) {
            playerHand.add(deck.removeAt(deck.size - 1))
        }
        while (opponentHand.size < 6 && deck.isNotEmpty()) {
            opponentHand.add(deck.removeAt(deck.size - 1))
        }
    }

    private fun determineFirstPlayer() {
        val playerTrumps = playerHand.filter { it.suit == trumpSuit }.map { it.rank.value }
        val opponentTrumps = opponentHand.filter { it.suit == trumpSuit }.map { it.rank.value }

        val minPlayerTrump = playerTrumps.minOrNull() ?: 999
        val minOpponentTrump = opponentTrumps.minOrNull() ?: 999

        if (minPlayerTrump < minOpponentTrump) {
            attackerId = "player"
            defenderId = "opponent"
            log(
                "You have the lowest trump (${minPlayerTrump.toRankSymbol()}${trumpSuit.symbol}). You attack first.",
                "У вас самый маленький козырь (${minPlayerTrump.toRankSymbol()}${trumpSuit.symbol}). Вы ходите первым."
            )
        } else if (minOpponentTrump < minPlayerTrump) {
            attackerId = "opponent"
            defenderId = "player"
            log(
                "Opponent has the lowest trump (${minOpponentTrump.toRankSymbol()}${trumpSuit.symbol}). Opponent attacks first.",
                "У оппонента самый маленький козырь (${minOpponentTrump.toRankSymbol()}${trumpSuit.symbol}). Оппонент ходит первым."
            )
        } else {
            attackerId = "player"
            defenderId = "opponent"
            log("No trumps in hand. You attack first.", "Козырей в руках нет. Вы ходите первым.")
        }
    }

    private fun Int.toRankSymbol(): String {
        return Rank.values().find { it.value == this }?.symbol ?: this.toString()
    }

    fun performAttack(playerId: String, card: Card): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (attackerId != playerId) return false

        val hand = if (playerId == "player") playerHand else opponentHand
        if (!hand.contains(card)) return false

        val defenderHand = if (playerId == "player") opponentHand else playerHand

        val undefendedAndNewCount = tablePairs.size + 1
        if (undefendedAndNewCount > 6 || undefendedAndNewCount > defenderHand.size + tablePairs.size) {
            return false
        }

        val isValid = if (tablePairs.isEmpty()) {
            true
        } else {
            tablePairs.any { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }
        }

        if (isValid) {
            hand.remove(card)
            tablePairs.add(CardPair(attackCard = card))
            log(
                "${if (playerId == "player") "You" else "Opponent"} threw ${card.rank.symbol}${card.suit.symbol}",
                "${if (playerId == "player") "Вы бросили" else "Оппонент бросил"} ${card.rank.symbol}${card.suit.symbol}"
            )
            return true
        }
        return false
    }

    fun performDefense(playerId: String, attackCard: Card, defenseCard: Card): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (defenderId != playerId) return false

        val hand = if (playerId == "player") playerHand else opponentHand
        if (!hand.contains(defenseCard)) return false

        val pairIndex = tablePairs.indexOfFirst { it.attackCard == attackCard && it.defenseCard == null }
        if (pairIndex == -1) return false

        if (defenseCard.canBeat(attackCard, trumpSuit)) {
            hand.remove(defenseCard)
            tablePairs[pairIndex] = CardPair(attackCard, defenseCard)
            log(
                "${if (playerId == "player") "You" else "Opponent"} beat ${attackCard.rank.symbol}${attackCard.suit.symbol} with ${defenseCard.rank.symbol}${defenseCard.suit.symbol}",
                "${if (playerId == "player") "Вы побили" else "Оппонент побил"} ${attackCard.rank.symbol}${attackCard.suit.symbol} картой ${defenseCard.rank.symbol}${defenseCard.suit.symbol}"
            )
            return true
        }
        return false
    }

    fun performTakeAll(playerId: String): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (defenderId != playerId) return false
        if (tablePairs.isEmpty()) return false

        log(
            "${if (playerId == "player") "You" else "Opponent"} took the cards.",
            "${if (playerId == "player") "Вы взяли" else "Оппонент взял"} карты."
        )

        val defenderHand = if (playerId == "player") playerHand else opponentHand
        for (pair in tablePairs) {
            defenderHand.add(pair.attackCard)
            pair.defenseCard?.let { defenderHand.add(it) }
        }
        tablePairs.clear()

        drawCardsLifecycle()
        checkWinCondition()
        return true
    }

    fun performBito(playerId: String): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (attackerId != playerId) return false
        if (tablePairs.isEmpty()) return false

        val allDefended = tablePairs.all { it.defenseCard != null }
        if (!allDefended) return false

        log("Bito! Cards discarded.", "Бито! Карты сброшены.")
        discardPileSize += tablePairs.size * 2
        tablePairs.clear()

        drawCardsLifecycle()

        val temp = attackerId
        attackerId = defenderId
        defenderId = temp

        checkWinCondition()
        return true
    }

    private fun drawCardsLifecycle() {
        val firstToDraw = if (attackerId == "player") playerHand else opponentHand
        val secondToDraw = if (attackerId == "player") opponentHand else playerHand

        while (firstToDraw.size < 6 && deck.isNotEmpty()) {
            firstToDraw.add(deck.removeAt(deck.size - 1))
        }
        while (secondToDraw.size < 6 && deck.isNotEmpty()) {
            secondToDraw.add(deck.removeAt(deck.size - 1))
        }
    }

    fun checkWinCondition() {
        if (deck.isEmpty()) {
            val playerEmpty = playerHand.isEmpty()
            val opponentEmpty = opponentHand.isEmpty()

            if (playerEmpty && opponentEmpty) {
                matchStatus = MatchStatus.DRAW
                log("Draw! Beautiful game, both ran out of cards.", "Ничья! Отличная игра, у обоих закончились карты.")
            } else if (playerEmpty) {
                matchStatus = MatchStatus.WON
                log("You won! Victory is yours!", "Вы победили! Победа за вами!")
            } else if (opponentEmpty) {
                matchStatus = MatchStatus.LOST
                log("Opponent won! You are the Durak!", "Оппонент победил! Вы остались в дураках!")
            }
        }
    }

    fun performTransfer(playerId: String, card: Card): Boolean {
        if (!isTransferMode) return false
        if (defenderId != playerId) return false
        if (tablePairs.isEmpty()) return false
        if (!tablePairs.all { it.defenseCard == null }) return false

        val hand = if (playerId == "player") playerHand else opponentHand
        if (!hand.contains(card)) return false

        val matchesRank = tablePairs.any { it.attackCard.rank == card.rank }
        if (!matchesRank) return false

        val recipientHand = if (playerId == "player") opponentHand else playerHand
        if (recipientHand.size < tablePairs.size + 1) return false

        hand.remove(card)
        tablePairs.add(CardPair(attackCard = card))

        log(
            "${if (playerId == "player") "You" else "Opponent"} transferred with ${card.rank.symbol}${card.suit.symbol}!",
            "${if (playerId == "player") "Вы" else "Оппонент"} перевели картой ${card.rank.symbol}${card.suit.symbol}!"
        )

        val temp = attackerId
        attackerId = defenderId
        defenderId = temp

        return true
    }

    fun makeBotMove(isHard: Boolean): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false

        val isBotAttacking = (attackerId == "opponent")

        if (isBotAttacking) {
            if (tablePairs.isEmpty()) {
                val cardToPlay = findBestBotAttackCard(isHard)
                if (cardToPlay != null) {
                    return performAttack("opponent", cardToPlay)
                }
            } else {
                val allDefended = tablePairs.all { it.defenseCard != null }
                if (allDefended) {
                    val validTossCards = opponentHand.filter { card ->
                        tablePairs.any { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }
                    }

                    if (validTossCards.isNotEmpty() && tablePairs.size < 6 && playerHand.size > 0) {
                        val cardToss = if (isHard) {
                            validTossCards.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rank.value })).first()
                        } else {
                            validTossCards.first()
                        }
                        return performAttack("opponent", cardToss)
                    } else {
                        return performBito("opponent")
                    }
                } else {
                    return false
                }
            }
        } else {
            val canBotTransfer = isTransferMode && tablePairs.isNotEmpty() && tablePairs.all { it.defenseCard == null }
            if (canBotTransfer) {
                val transferCard = opponentHand.find { card ->
                    tablePairs.any { it.attackCard.rank == card.rank }
                }
                if (transferCard != null) {
                    return performTransfer("opponent", transferCard)
                }
            }

            val undefendedPair = tablePairs.find { it.defenseCard == null }
            if (undefendedPair != null) {
                val attackCard = undefendedPair.attackCard
                val validDefenseCards = opponentHand.filter { it.canBeat(attackCard, trumpSuit) }

                if (validDefenseCards.isNotEmpty()) {
                    val defenseCard = if (isHard) {
                        val nonTrumps = validDefenseCards.filter { it.suit != trumpSuit }
                        val trumps = validDefenseCards.filter { it.suit == trumpSuit }
                        if (nonTrumps.isNotEmpty()) {
                            nonTrumps.minByOrNull { it.rank.value }!!
                        } else {
                            trumps.minByOrNull { it.rank.value }!!
                        }
                    } else {
                        validDefenseCards.minByOrNull { it.rank.value }!!
                    }
                    return performDefense("opponent", attackCard, defenseCard)
                } else {
                    return performTakeAll("opponent")
                }
            }
        }
        return false
    }

    private fun findBestBotAttackCard(isHard: Boolean): Card? {
        if (opponentHand.isEmpty()) return null
        return if (isHard) {
            opponentHand.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rank.value })).firstOrNull()
        } else {
            opponentHand.randomOrNull()
        }
    }

    fun createSnapshot(opponentName: String = "Opponent"): GameStateSnapshot {
        val isLocalAttacking = (attackerId == "player")
        val canBito = isLocalAttacking && tablePairs.isNotEmpty() && tablePairs.all { it.defenseCard != null }
        val canTake = !isLocalAttacking && tablePairs.isNotEmpty() && tablePairs.any { it.defenseCard == null }

        return GameStateSnapshot(
            trumpCard = trumpCard,
            trumpSuit = trumpSuit,
            deckSize = deck.size,
            tablePairs = tablePairs.toList(),
            discardPileSize = discardPileSize,
            isLocalTurn = if (isLocalAttacking) tablePairs.all { it.defenseCard != null } || tablePairs.isEmpty() else tablePairs.any { it.defenseCard == null },
            localHand = playerHand.toList(),
            opponentHandSize = opponentHand.size,
            opponentName = opponentName,
            matchStatus = matchStatus,
            attackerPlayerId = attackerId,
            gameLog = gameLogEn.toList(),
            gameLogEn = gameLogEn.toList(),
            gameLogRu = gameLogRu.toList(),
            canTake = canTake,
            canBito = canBito,
            isTransferMode = isTransferMode
        )
    }

    fun loadFromSnapshot(snapshotState: GameStateSnapshot, localIsAttacker: Boolean, oppHandSize: Int) {
        this.trumpCard = snapshotState.trumpCard
        this.trumpSuit = snapshotState.trumpSuit
        this.discardPileSize = snapshotState.discardPileSize
        this.matchStatus = snapshotState.matchStatus

        this.tablePairs.clear()
        this.tablePairs.addAll(snapshotState.tablePairs)

        this.playerHand.clear()
        this.playerHand.addAll(snapshotState.localHand)

        this.opponentHand.clear()
        repeat(oppHandSize) {
            this.opponentHand.add(Card(Suit.SPADES, Rank.SIX))
        }

        if (localIsAttacker) {
            this.attackerId = "player"
            this.defenderId = "opponent"
        } else {
            this.attackerId = "opponent"
            this.defenderId = "player"
        }

        this.gameLogEn.clear()
        this.gameLogEn.addAll(snapshotState.gameLogEn)
        this.gameLogRu.clear()
        this.gameLogRu.addAll(snapshotState.gameLogRu)
    }
}
