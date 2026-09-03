package com.example.network

import java.security.SecureRandom

/**
 * Host-authoritative lobby state for a local Wi-Fi match.  It deliberately has
 * no Android dependencies, so the room rules can be unit-tested separately
 * from sockets and Compose.
 */
class MultiplayerLobby(
    val roomCode: String = createRoomCode(),
    val capacity: Int = MAX_PLAYERS
) {
    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 6
        private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun createRoomCode(): String {
            val random = SecureRandom()
            return buildString(6) {
                repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
        }
    }

    data class Player(val id: String, val nickname: String, val isHost: Boolean = false)
    data class ChatMessage(val senderId: String, val senderName: String, val text: String)
    data class Snapshot(
        val roomCode: String,
        val capacity: Int,
        val players: List<Player>,
        val messages: List<ChatMessage>
    ) {
        val canStart: Boolean get() = players.size >= MIN_PLAYERS
    }

    private val mutablePlayers = mutableListOf<Player>()
    private val mutableMessages = mutableListOf<ChatMessage>()

    val players: List<Player> get() = mutablePlayers.toList()
    val messages: List<ChatMessage> get() = mutableMessages.toList()
    val canStart: Boolean get() = mutablePlayers.size >= MIN_PLAYERS

    fun snapshot() = Snapshot(roomCode, capacity, players, messages)

    fun addPlayer(id: String, nickname: String, isHost: Boolean = false): Boolean {
        if (id.isBlank() || nickname.isBlank() || mutablePlayers.size >= capacity) return false
        if (mutablePlayers.any { it.id == id }) return false
        mutablePlayers += Player(id, nickname.take(24), isHost)
        return true
    }

    fun removePlayer(id: String): Boolean = mutablePlayers.removeAll { it.id == id }

    fun renamePlayer(id: String, nickname: String): Boolean {
        val index = mutablePlayers.indexOfFirst { it.id == id }
        val normalized = nickname.trim().take(24)
        if (index == -1 || normalized.isEmpty()) return false
        val current = mutablePlayers[index]
        mutablePlayers[index] = current.copy(nickname = normalized)
        return true
    }

    fun postMessage(senderId: String, text: String): ChatMessage? {
        val sender = mutablePlayers.firstOrNull { it.id == senderId } ?: return null
        val normalized = text.trim().take(160)
        if (normalized.isEmpty()) return null
        return ChatMessage(sender.id, sender.nickname, normalized).also { message ->
            mutableMessages += message
            if (mutableMessages.size > 50) mutableMessages.removeAt(0)
        }
    }
}

/**
 * A tiny line-oriented codec for lobby-only messages.  The match protocol is
 * intentionally kept separate so chat and member updates cannot be confused
 * with a game action.
 */
object LobbyWireProtocol {
    private const val PREFIX_JOIN = "LOBBY_JOIN:"
    private const val PREFIX_CHAT = "LOBBY_CHAT:"
    private const val PREFIX_STATE = "LOBBY_STATE:"
    private const val FIELD = ","
    private const val ITEM = ";"

    fun join(nickname: String) = PREFIX_JOIN + encode(nickname.trim().take(24))
    fun chat(text: String) = PREFIX_CHAT + encode(text.trim().take(160))
    fun isLobbyMessage(message: String) = message.startsWith("LOBBY_")
    fun decodeJoin(message: String): String? = decodePrefixed(message, PREFIX_JOIN)
    fun decodeChat(message: String): String? = decodePrefixed(message, PREFIX_CHAT)

    fun state(snapshot: MultiplayerLobby.Snapshot): String {
        val players = snapshot.players.joinToString(ITEM) { player ->
            listOf(encode(player.id), encode(player.nickname), if (player.isHost) "1" else "0").joinToString(FIELD)
        }
        val messages = snapshot.messages.joinToString(ITEM) { message ->
            listOf(encode(message.senderId), encode(message.senderName), encode(message.text)).joinToString(FIELD)
        }
        return PREFIX_STATE + listOf(
            encode(snapshot.roomCode),
            snapshot.capacity.toString(),
            encode(players),
            encode(messages)
        ).joinToString("|")
    }

    fun parseState(message: String): MultiplayerLobby.Snapshot? {
        if (!message.startsWith(PREFIX_STATE)) return null
        val values = message.removePrefix(PREFIX_STATE).split("|", limit = 4)
        if (values.size != 4) return null
        return runCatching {
            val players = decode(values[2]).orEmpty().split(ITEM).filter { it.isNotBlank() }.map { row ->
                val fields = row.split(FIELD)
                require(fields.size == 3)
                MultiplayerLobby.Player(decode(fields[0]).orEmpty(), decode(fields[1]).orEmpty(), fields[2] == "1")
            }
            val messages = decode(values[3]).orEmpty().split(ITEM).filter { it.isNotBlank() }.map { row ->
                val fields = row.split(FIELD)
                require(fields.size == 3)
                MultiplayerLobby.ChatMessage(decode(fields[0]).orEmpty(), decode(fields[1]).orEmpty(), decode(fields[2]).orEmpty())
            }
            MultiplayerLobby.Snapshot(decode(values[0]).orEmpty(), values[1].toInt(), players, messages)
        }.getOrNull()
    }

    private fun decodePrefixed(message: String, prefix: String): String? =
        if (message.startsWith(prefix)) decode(message.removePrefix(prefix)) else null

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String) = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull()
}
