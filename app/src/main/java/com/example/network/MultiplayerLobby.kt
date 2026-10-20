package com.example.network

import java.security.SecureRandom

/** Room rules are shared by the socket host and JVM regression tests. */
class MultiplayerLobby(val roomCode: String = createRoomCode(), var capacity: Int = MAX_PLAYERS) {
    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 6
        fun createRoomCode(): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val random = SecureRandom()
            return buildString { repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) } }
        }
    }
    data class Settings(val deckSize: Int = 36, val transfer: Boolean = false, val customDeckIds: Set<String>? = null) {
        fun validFor(count: Int) = deckSize in listOf(36, 52) &&
            (customDeckIds == null || (customDeckIds.size >= count * 6 && customDeckIds.all { it in com.example.model.Suit.values().flatMap { suit -> com.example.model.Rank.values().map { rank -> "${suit.name}_${rank.name}" } } }))
    }
    data class Player(val id: String, val nickname: String, val isHost: Boolean = false, val ready: Boolean = isHost, val connected: Boolean = true)
    data class ChatMessage(val senderId: String, val senderName: String, val text: String)
    data class Snapshot(val roomCode: String, val capacity: Int, val players: List<Player>, val messages: List<ChatMessage>,
        val settings: Settings = Settings(), val inMatch: Boolean = false) {
        val canStart get() = players.size in MIN_PLAYERS..capacity && players.all { it.connected && it.ready } && settings.validFor(players.size) && !inMatch
    }
    private val members = mutableListOf<Player>()
    private val chat = mutableListOf<ChatMessage>()
    private val lastMessage = mutableMapOf<String, Long>()
    var settings = Settings(); private set
    var inMatch = false
    val players get() = members.toList()
    val messages get() = chat.toList()
    val canStart get() = snapshot().canStart
    fun snapshot() = Snapshot(roomCode, capacity, players, messages, settings, inMatch)
    fun addPlayer(id: String, nickname: String, isHost: Boolean = false): Boolean {
        if (inMatch || id.isBlank() || nickname.isBlank() || members.size >= capacity || members.any { it.id == id }) return false
        members += Player(id, nickname.trim().take(24), isHost)
        return true
    }
    fun removePlayer(id: String) = members.removeAll { it.id == id }
    private fun update(id: String, change: (Player) -> Player): Boolean {
        val i = members.indexOfFirst { it.id == id }; if (i < 0) return false
        members[i] = change(members[i]); return true
    }
    fun renamePlayer(id: String, nickname: String): Boolean = nickname.trim().isNotEmpty() && update(id) { it.copy(nickname = nickname.trim().take(24)) }
    fun setConnected(id: String, connected: Boolean) = update(id) { it.copy(connected = connected, ready = if (connected) it.ready else it.isHost) }
    fun setReady(id: String, ready: Boolean): Boolean = !inMatch && update(id) { it.copy(ready = if (it.isHost) true else ready) }
    fun configure(capacity: Int, settings: Settings): Boolean {
        if (inMatch || capacity !in 2..6 || capacity < members.size || !settings.validFor(members.size.coerceAtLeast(2))) return false
        if (this.capacity == capacity && this.settings == settings) return true
        this.capacity = capacity; this.settings = settings
        members.replaceAll { it.copy(ready = it.isHost) }
        return true
    }
    fun returnToLobby() { inMatch = false; members.replaceAll { it.copy(ready = it.isHost) } }
    fun postMessage(senderId: String, text: String, now: Long = System.nanoTime() / 1_000_000): ChatMessage? {
        val sender = members.firstOrNull { it.id == senderId && it.connected } ?: return null
        val normalized = text.trim().take(160)
        if (normalized.isEmpty() || now - (lastMessage[senderId] ?: (now - 1000)) < 1000) return null
        lastMessage[senderId] = now
        return ChatMessage(sender.id, sender.nickname, normalized).also { chat += it; if (chat.size > 50) chat.removeAt(0) }
    }
}

object LobbyWireProtocol {
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = java.net.URLDecoder.decode(s, "UTF-8")
    fun join(nickname: String) = "LOBBY_JOIN:" + enc(nickname.trim().take(24))
    fun sessionJoin(nickname: String, token: String) = "SESSION_JOIN:" + enc(nickname.take(24)) + "|" + enc(token)
    fun parseSessionJoin(line: String): Pair<String, String>? = runCatching {
        require(line.startsWith("SESSION_JOIN:")); val f = line.removePrefix("SESSION_JOIN:").split("|"); require(f.size == 2)
        dec(f[0]) to dec(f[1])
    }.getOrNull()
    fun ready(value: Boolean) = "LOBBY_READY:$value"
    fun chat(text: String) = "LOBBY_CHAT:" + enc(text.trim().take(160))
    fun isLobbyMessage(message: String) = message.startsWith("LOBBY_")
    fun decodeJoin(message: String) = if (message.startsWith("LOBBY_JOIN:")) runCatching { dec(message.removePrefix("LOBBY_JOIN:")) }.getOrNull() else null
    fun decodeChat(message: String) = if (message.startsWith("LOBBY_CHAT:")) runCatching { dec(message.removePrefix("LOBBY_CHAT:")) }.getOrNull() else null
    fun state(s: MultiplayerLobby.Snapshot): String {
        val players = s.players.joinToString(";") { listOf(enc(it.id), enc(it.nickname), it.isHost, it.ready, it.connected).joinToString(",") }
        val chat = s.messages.joinToString(";") { listOf(enc(it.senderId), enc(it.senderName), enc(it.text)).joinToString(",") }
        return "LOBBY_STATE:" + listOf(s.roomCode, s.capacity.toString(), players, chat, s.settings.deckSize.toString(), s.settings.transfer.toString(), s.settings.customDeckIds?.sorted()?.joinToString(",") ?: "-", s.inMatch.toString()).joinToString("|") { enc(it) }
    }
    fun parseState(message: String): MultiplayerLobby.Snapshot? = runCatching {
        require(message.startsWith("LOBBY_STATE:")); val f = message.removePrefix("LOBBY_STATE:").split("|").map(::dec); require(f.size == 8)
        val players = f[2].split(";").filter { it.isNotBlank() }.map { row ->
            val v = row.split(","); require(v.size == 5)
            MultiplayerLobby.Player(dec(v[0]), dec(v[1]), v[2].toBooleanStrict(), v[3].toBooleanStrict(), v[4].toBooleanStrict())
        }
        val chat = f[3].split(";").filter { it.isNotBlank() }.map { row ->
            val v = row.split(","); require(v.size == 3); MultiplayerLobby.ChatMessage(dec(v[0]), dec(v[1]), dec(v[2]))
        }
        MultiplayerLobby.Snapshot(f[0], f[1].toInt(), players, chat,
            MultiplayerLobby.Settings(f[4].toInt(), f[5].toBooleanStrict(), if (f[6] == "-") null else f[6].split(",").toSet()), f[7].toBooleanStrict())
    }.getOrNull()
}
