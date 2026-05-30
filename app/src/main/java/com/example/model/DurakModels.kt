package com.example.model

enum class Suit(val symbol: String, val colorRed: Boolean, val ruLabel: String, val enLabel: String, val itLabel: String) {
    SPADES("♠", false, "Пики", "Spades", "Picche"),
    CLUBS("♣", false, "Трефы", "Clubs", "Fiori"),
    HEARTS("♥", true, "Черви", "Hearts", "Cuori"),
    DIAMONDS("♦", true, "Бубны", "Diamonds", "Quadri")
}

enum class Rank(val value: Int, val symbol: String) {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A")
}

data class Card(val suit: Suit, val rank: Rank) {
    val id: String = "${suit.name}_${rank.name}"

    // Compares if this card can beat another card
    fun canBeat(other: Card, trumpSuit: Suit): Boolean {
        return if (this.suit == other.suit) {
            this.rank.value > other.rank.value
        } else {
            this.suit == trumpSuit
        }
    }
}

enum class GameMode {
    OFFLINE,
    ONLINE_HOST,
    ONLINE_CLIENT
}

enum class MatchStatus {
    NOT_STARTED,
    PLAYING,
    WON,          // Local player won
    LOST,         // Local player lost (is Durak)
    DRAW,         // Both ran out of cards on Bito
    PLAYER_DISCONNECTED
}

data class Player(
    val id: String,
    val name: String,
    val isBot: Boolean = false,
    val cards: List<Card> = emptyList()
)

data class CardPair(
    val attackCard: Card,
    val defenseCard: Card? = null
)

// The immutable game snapshot for rendering or syncing over socket
data class GameStateSnapshot(
    val trumpCard: Card? = null,
    val trumpSuit: Suit = Suit.SPADES,
    val deckSize: Int = 0,
    val tablePairs: List<CardPair> = emptyList(),
    val discardPileSize: Int = 0,
    val isLocalTurn: Boolean = true, // Is it the local player's attacking turn?
    val localHand: List<Card> = emptyList(),
    val opponentHandSize: Int = 0,
    val opponentName: String = "Bot",
    val matchStatus: MatchStatus = MatchStatus.NOT_STARTED,
    val attackerPlayerId: String = "", // ID of the player currently attacking
    val gameLog: List<String> = emptyList(),
    val gameLogEn: List<String> = emptyList(),
    val gameLogRu: List<String> = emptyList(),
    val canTake: Boolean = false,      // Can the defender take cards from table?
    val canBito: Boolean = false       // Can the attacker finish attack? (Bito / Pass)
)
