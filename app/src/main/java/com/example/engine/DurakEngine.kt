package com.example.engine

import com.example.model.*
import java.security.SecureRandom

class DurakEngine {
    private val random = SecureRandom()

    // Base state
    var deck: MutableList<Card> = mutableListOf()
    var trumpCard: Card? = null
    var trumpSuit: Suit = Suit.SPADES
    var discardPileSize: Int = 0

    var playerHand: MutableList<Card> = mutableListOf()
    var opponentHand: MutableList<Card> = mutableListOf()

    // Table state
    val tablePairs: MutableList<CardPair> = mutableListOf()

    // Turn tracking
    var attackerId: String = "player" // the ID of player who is attacking
    var defenderId: String = "opponent" // the ID of player who is defending

    var matchStatus: MatchStatus = MatchStatus.NOT_STARTED
    var isTransferMode: Boolean = false
    var isDefenderTaking: Boolean = false
    var defenderStartHandSize: Int = 0
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

    // Start a new match
    fun startMatch(player1Name: String, player2Name: String, isBotGame: Boolean, deckSize: Int = 36, isTransferMode: Boolean = false, customDeckIds: Set<String>? = null) {
        this.isTransferMode = isTransferMode
        this.isDefenderTaking = false
        this.defenderStartHandSize = 0
        deck.clear()
        tablePairs.clear()
        playerHand.clear()
        opponentHand.clear()
        gameLogEn.clear()
        gameLogRu.clear()
        discardPileSize = 0

        if (customDeckIds != null && customDeckIds.isNotEmpty()) {
            // Create deck from user's custom card selections
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    val cardId = "${suit.name}_${rank.name}"
                    if (customDeckIds.contains(cardId)) {
                        deck.add(Card(suit, rank))
                    }
                }
            }
        } else {
            // Create deck of specified size
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    if (deckSize == 52 || rank.value >= 6) {
                        deck.add(Card(suit, rank))
                    }
                }
            }
        }

        // Shuffle deck 7 times thoroughly to prevent single-suit patterns and clustering
        repeat(7) {
            deck.shuffle(random)
        }

        // Deal 6 cards
        dealCards()

        // Reveal trump card
        if (deck.isNotEmpty()) {
            val trump = deck.removeAt(deck.size - 1)
            trumpCard = trump
            trumpSuit = trump.suit
            // Place trump back to the bottom of the deck
            deck.add(0, trump)
            log("Trump suit is ${trumpSuit.enLabel} (${trumpSuit.symbol}). Trump card: ${trump.rank.symbol}${trumpSuit.symbol}", "Козырная масть - ${trumpSuit.ruLabel} (${trumpSuit.symbol}). Козырь: ${trump.rank.symbol}${trumpSuit.symbol}")
        } else {
            // Unlikely to be empty, but fallback
            trumpCard = null
            trumpSuit = Suit.SPADES
            log("Trump suit is ${trumpSuit.enLabel}", "Козыри - ${trumpSuit.ruLabel}")
        }

        // Determine who plays first (lowest trump)
        determineFirstPlayer()

        matchStatus = MatchStatus.PLAYING
        log("Match started! Attacker: ${if (attackerId == "player") player1Name else player2Name}", "Матч начался! Нападающий: ${if (attackerId == "player") player1Name else player2Name}")
    }

    private fun dealCards() {
        // Feed up to 6 cards
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
            log("You have the lowest trump (${minPlayerTrump.toRankSymbol()}${trumpSuit.symbol}). You attack first.", "У вас самый маленький козырь (${minPlayerTrump.toRankSymbol()}${trumpSuit.symbol}). Вы ходите первым.")
        } else if (minOpponentTrump < minPlayerTrump) {
            attackerId = "opponent"
            defenderId = "player"
            log("Opponent has the lowest trump (${minOpponentTrump.toRankSymbol()}${trumpSuit.symbol}). Opponent attacks first.", "У оппонента самый маленький козырь (${minOpponentTrump.toRankSymbol()}${trumpSuit.symbol}). Оппонент ходит первым.")
        } else {
            // Default to player if nobody has trumps or tied
            attackerId = "player"
            defenderId = "opponent"
            log("No trumps in hand. You attack first.", "Козырей в руках нет. Вы ходите первым.")
        }
    }

    private fun Int.toRankSymbol(): String {
        return Rank.values().find { it.value == this }?.symbol ?: this.toString()
    }

    // Attempt attack action
    fun performAttack(playerId: String, card: Card): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (attackerId != playerId) return false

        // Card validation
        val hand = if (playerId == "player") playerHand else opponentHand
        if (!hand.contains(card)) return false

        val defenderHand = if (playerId == "player") opponentHand else playerHand

        if (tablePairs.isEmpty()) {
            defenderStartHandSize = defenderHand.size
        }

        // Can't throw more cards than defender had at the start of the round (max target's hand size, and max 6)
        val undefendedAndNewCount = tablePairs.size + 1
        if (undefendedAndNewCount > 6 || undefendedAndNewCount > defenderStartHandSize) {
            return false
        }

        val isValid = if (tablePairs.isEmpty()) {
            true // First attack card is always valid
        } else {
            // Rank must match at least one rank already present on the table
            tablePairs.any { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }
        }

        if (isValid) {
            hand.remove(card)
            tablePairs.add(CardPair(attackCard = card))
            log("${if (playerId == "player") "You" else "Opponent"} threw ${card.rank.symbol}${card.suit.symbol}", "${if (playerId == "player") "Вы бросили" else "Оппонент бросил"} ${card.rank.symbol}${card.suit.symbol}")
            return true
        }
        return false
    }

    // Attempt defense action
    fun performDefense(playerId: String, attackCard: Card, defenseCard: Card): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (defenderId != playerId) return false
        if (isDefenderTaking) return false

        val hand = if (playerId == "player") playerHand else opponentHand
        if (!hand.contains(defenseCard)) return false

        // Must find appropriate undefended pair
        val pairIndex = tablePairs.indexOfFirst { it.attackCard == attackCard && it.defenseCard == null }
        if (pairIndex == -1) return false

        if (defenseCard.canBeat(attackCard, trumpSuit)) {
            hand.remove(defenseCard)
            tablePairs[pairIndex] = CardPair(attackCard, defenseCard)
            log("${if (playerId == "player") "You" else "Opponent"} beat ${attackCard.rank.symbol}${attackCard.suit.symbol} with ${defenseCard.rank.symbol}${defenseCard.suit.symbol}", "${if (playerId == "player") "Вы побили" else "Оппонент побил"} ${attackCard.rank.symbol}${attackCard.suit.symbol} картой ${defenseCard.rank.symbol}${defenseCard.suit.symbol}")
            return true
        }
        return false
    }

    // Defender takes all cards on the table
    fun performTakeAll(playerId: String): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (defenderId != playerId) return false
        if (tablePairs.isEmpty()) return false
        if (isDefenderTaking) return false

        isDefenderTaking = true
        log("${if (playerId == "player") "You" else "Opponent"} decided to take cards.", "${if (playerId == "player") "Вы решили взять" else "Оппонент решил взять"} карты.")
        return true
    }

    // Attacker passes / ends round on successful defense
    fun performBito(playerId: String): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false
        if (attackerId != playerId) return false
        if (tablePairs.isEmpty()) return false

        if (isDefenderTaking) {
            log("End of turn: Defender took all cards.", "Конец хода: Защищающийся забрал все карты.")

            // Transfer all cards (attack & defense) to the defender's hand
            val defenderHand = if (defenderId == "player") playerHand else opponentHand
            for (pair in tablePairs) {
                defenderHand.add(pair.attackCard)
                pair.defenseCard?.let { defenderHand.add(it) }
            }
            tablePairs.clear()
            isDefenderTaking = false

            // End of round: deal cards from deck up to 6
            drawCardsLifecycle()

            // Attacker remains the same since the defender took the cards and lost their attack turn!
            checkWinCondition()
            return true
        }

        // All cards on table must be defended
        val allDefended = tablePairs.all { it.defenseCard != null }
        if (!allDefended) return false

        log("Bito! Cards discarded.", "Бито! Карты сброшены.")
        discardPileSize += tablePairs.size * 2
        tablePairs.clear()

        // End of round: deal cards from deck up to 6
        drawCardsLifecycle()

        // Turn rotates: defender becomes attacker, attacker becomes defender!
        val temp = attackerId
        attackerId = defenderId
        defenderId = temp

        checkWinCondition()
        return true
    }

    private fun drawCardsLifecycle() {
        // Draw logic: Attacker draws first up to 6, then Defender
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

        // Standard Transfer Limit constraint: Recipient must have enough cards to defend
        val recipientHand = if (playerId == "player") opponentHand else playerHand
        if (recipientHand.size < tablePairs.size + 1) return false

        hand.remove(card)
        tablePairs.add(CardPair(attackCard = card))

        // Update the defender starting hand count lock for the new defender
        defenderStartHandSize = recipientHand.size

        log("${if (playerId == "player") "You" else "Opponent"} transferred with ${card.rank.symbol}${card.suit.symbol}!",
            "${if (playerId == "player") "Вы" else "Оппонент"} перевели картой ${card.rank.symbol}${card.suit.symbol}!")

        val temp = attackerId
        attackerId = defenderId
        defenderId = temp

        return true
    }

    // Easy: plays first valid move
    // Hard: tracks cards and thinks.
    // Let's implement BOT AI directly inside engine to be totally robust.
    fun makeBotMove(isHard: Boolean): Boolean {
        if (matchStatus != MatchStatus.PLAYING) return false

        val isBotAttacking = (attackerId == "opponent")

        if (isBotAttacking) {
            // Bot is attacking
            if (tablePairs.isEmpty()) {
                // First move: pick lowest non-trump, if none, lowest trump
                val cardToPlay = findBestBotAttackCard(isHard)
                if (cardToPlay != null) {
                    return performAttack("opponent", cardToPlay)
                }
            } else {
                // Throw additional valid cards or Bito
                val allDefended = tablePairs.all { it.defenseCard != null }
                if (allDefended || isDefenderTaking) {
                    // Check if can toss some more valid cards
                    val validTossCards = opponentHand.filter { card ->
                        tablePairs.any { it.attackCard.rank == card.rank || it.defenseCard?.rank == card.rank }
                    }

                    if (validTossCards.isNotEmpty() && tablePairs.size < 6 && tablePairs.size < defenderStartHandSize && playerHand.size > 0) {
                        // Toss cards based on difficulty
                        val cardToss = if (isHard) {
                            // Toss lowest rank first (and avoid passing trumps if rank matched trump unless necessary)
                            validTossCards.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rank.value })).first()
                        } else {
                            validTossCards.first()
                        }
                        return performAttack("opponent", cardToss)
                    } else {
                        // End of turn (or pass and defender takes all)
                        return performBito("opponent")
                    }
                } else {
                    // Bot has undefended cards, is waiting for player defense
                    return false
                }
            }
        } else {
            // Bot is defending
            val canBotTransfer = isTransferMode && tablePairs.isNotEmpty() && tablePairs.all { it.defenseCard == null }
            if (canBotTransfer) {
                val transferCard = opponentHand.find { card ->
                    tablePairs.any { it.attackCard.rank == card.rank }
                }
                if (transferCard != null) {
                    if (performTransfer("opponent", transferCard)) {
                        return true
                    }
                }
            }

            // Find an undefended card
            val undefendedPair = tablePairs.find { it.defenseCard == null }
            if (undefendedPair != null) {
                val attackCard = undefendedPair.attackCard
                val validDefenseCards = opponentHand.filter { it.canBeat(attackCard, trumpSuit) }

                if (validDefenseCards.isNotEmpty()) {
                    var chosenDefenseCard: Card? = null
                    
                    if (isHard) {
                        // EXPERT BOT ANALYZES IF IT'S BETTER TO TAKE!
                        // The expert bot knows all of player's cards and hand size!
                        val lowestDefense = validDefenseCards.minByOrNull { it.rank.value }!!
                        val isLowestDefenseTrump = lowestDefense.suit == trumpSuit
                        
                        var wantToTake = false
                        
                        // Heuristic 1: If we must defend with a high trump (e.g. Queen, King, Ace) against a non-trump low card,
                        // and the player has several cards remaining, it's wiser to take the card and keep our trump.
                        if (isLowestDefenseTrump && lowestDefense.rank.value >= 12 && attackCard.suit != trumpSuit) {
                            if (opponentHand.size <= 4) {
                                wantToTake = true
                            }
                        }
                        
                        // Heuristic 2: If player has significantly more trumps than us, and our only option to defend is a trump,
                        // taking the cards will build our hand size to defend later.
                        val playerTrumps = playerHand.count { it.suit == trumpSuit }
                        val botTrumps = opponentHand.count { it.suit == trumpSuit }
                        if (playerTrumps > botTrumps + 1 && isLowestDefenseTrump && opponentHand.size < 5) {
                            wantToTake = true
                        }
                        
                        // Heuristic 3: If there's an Ace or Trump on the table, and we have very few cards,
                        // taking can be strategically beneficial in the late game to enrich our hand.
                        val tableValueCount = tablePairs.count { it.attackCard.suit == trumpSuit || it.attackCard.rank == Rank.ACE }
                        if (tableValueCount >= 1 && opponentHand.size <= 3 && deck.isEmpty()) {
                            wantToTake = true
                        }

                        if (wantToTake) {
                            return performTakeAll("opponent")
                        }

                        // Otherwise, sort to choose the lowest non-trump first to defend, then lowest trump
                        val nonTrumps = validDefenseCards.filter { it.suit != trumpSuit }
                        val trumps = validDefenseCards.filter { it.suit == trumpSuit }
                        chosenDefenseCard = if (nonTrumps.isNotEmpty()) {
                            nonTrumps.minByOrNull { it.rank.value }!!
                        } else {
                            trumps.minByOrNull { it.rank.value }!!
                        }
                    } else {
                        chosenDefenseCard = validDefenseCards.minByOrNull { it.rank.value }!!
                    }

                    if (chosenDefenseCard != null) {
                        return performDefense("opponent", attackCard, chosenDefenseCard)
                    }
                }
                
                // If we reach here, we cannot beat the card (validDefenseCards is empty)
                // or we analytically chose to take. WE MUST TAKE!
                return performTakeAll("opponent")
            }
        }
        return false
    }

    private fun findBestBotAttackCard(isHard: Boolean): Card? {
        if (opponentHand.isEmpty()) return null
        return if (isHard) {
            // Sort by non-trump first, then lowest rank
            opponentHand.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rank.value })).firstOrNull()
        } else {
            opponentHand.randomOrNull()
        }
    }

    // Return the state snapshot for the UI View
    fun createSnapshot(opponentName: String = "Opponent"): GameStateSnapshot {
        val isLocalAttacking = (attackerId == "player")
        val canBito = isLocalAttacking && tablePairs.isNotEmpty() && (tablePairs.all { it.defenseCard != null } || isDefenderTaking)
        val canTake = !isLocalAttacking && tablePairs.isNotEmpty() && tablePairs.any { it.defenseCard == null } && !isDefenderTaking

        val isLocalTurnAction = if (isLocalAttacking) {
            tablePairs.all { it.defenseCard != null } || tablePairs.isEmpty() || isDefenderTaking
        } else {
            tablePairs.any { it.defenseCard == null } && !isDefenderTaking
        }

        return GameStateSnapshot(
            trumpCard = trumpCard,
            trumpSuit = trumpSuit,
            deckSize = deck.size,
            tablePairs = tablePairs.toList(),
            discardPileSize = discardPileSize,
            isLocalTurn = isLocalTurnAction,
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
            isTransferMode = isTransferMode,
            isDefenderTaking = isDefenderTaking
        )
    }

    // Load full engine state (used to sync client/host)
    fun loadFromSnapshot(snapshotState: GameStateSnapshot, localIsAttacker: Boolean, oppHandSize: Int) {
        this.trumpCard = snapshotState.trumpCard
        this.trumpSuit = snapshotState.trumpSuit
        this.discardPileSize = snapshotState.discardPileSize
        this.matchStatus = snapshotState.matchStatus

        this.tablePairs.clear()
        this.tablePairs.addAll(snapshotState.tablePairs)

        this.playerHand.clear()
        this.playerHand.addAll(snapshotState.localHand)

        // Sync opponent hand sizes
        this.opponentHand.clear()
        repeat(oppHandSize) {
            this.opponentHand.add(Card(Suit.SPADES, Rank.SIX)) // placeholder mock cards for opponent hand size
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
