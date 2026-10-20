package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.GameMode
import com.example.model.GameStateSnapshot
import com.example.network.MultiplayerLobby
import com.example.network.MultiplayerManager
import com.example.viewmodel.AppLanguage
import com.example.viewmodel.DurakViewModel

fun mpText(l: AppLanguage, ru: String, en: String, it: String, ua: String): String = when(l) {
    AppLanguage.RU -> ru; AppLanguage.IT -> it; AppLanguage.UA -> ua; else -> en
}

@Composable
fun RoomChat(model: DurakViewModel, room: MultiplayerLobby.Snapshot?, language: AppLanguage) {
    val muted by model.communicationMuted.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { opened = true }, modifier = Modifier.testTag("room_chat")) {
            Text(mpText(language, "Чат и реакции", "Chat & reactions", "Chat e reazioni", "Чат і реакції"))
        }
        TextButton(onClick = { model.toggleCommunicationMuted() }, modifier = Modifier.testTag("mute_chat")) {
            Text(if (muted) "🔇" else "🔊")
        }
    }
    if (!muted) room?.messages?.lastOrNull()?.let { Text("${it.senderName}: ${it.text}", maxLines = 2, fontSize = 12.sp) }
    if (opened) AlertDialog(
        onDismissRequest = { opened = false },
        title = { Text(mpText(language, "Чат комнаты", "Room chat", "Chat della stanza", "Чат кімнати")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (muted) Text(mpText(language, "Сообщения и реакции приглушены", "Messages and reactions are muted", "Messaggi e reazioni silenziati", "Повідомлення та реакції вимкнено"))
                else {
                    if (room?.messages.isNullOrEmpty()) Text(mpText(language, "Пока нет сообщений", "No messages yet", "Ancora nessun messaggio", "Ще немає повідомлень"))
                    room?.messages?.forEach { Text("${it.senderName}: ${it.text}", fontSize = 13.sp) }
                    Row {
                        listOf("👍", "👋", "😄", "🤔", "🍀").forEach { emoji ->
                            TextButton(onClick = { model.sendLobbyChat(emoji) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.weight(1f)) { Text(emoji) }
                        }
                    }
                    val quick = listOf(
                        mpText(language, "Привет!", "Hello!", "Ciao!", "Привіт!"),
                        mpText(language, "Хороший ход!", "Nice move!", "Bella mossa!", "Гарний хід!"),
                        mpText(language, "Секунду…", "One moment…", "Un momento…", "Секунду…"),
                        mpText(language, "Удачи!", "Good luck!", "Buona fortuna!", "Удачі!")
                    )
                    quick.forEach { text -> TextButton(onClick = { model.sendLobbyChat(text) }) { Text(text) } }
                    OutlinedTextField(draft, { draft = it.take(160) }, label = { Text(mpText(language, "Сообщение", "Message", "Messaggio", "Повідомлення")) }, modifier = Modifier.fillMaxWidth().testTag("chat_input"), maxLines = 3)
                    Text("${draft.length}/160", fontSize = 11.sp)
                    Button(onClick = { model.sendLobbyChat(draft); draft = "" }, enabled = draft.isNotBlank(), modifier = Modifier.testTag("send_chat")) {
                        Text(mpText(language, "Отправить", "Send", "Invia", "Надіслати"))
                    }
                    Text(mpText(language, "Не чаще одного сообщения в секунду", "Up to one message per second", "Al massimo un messaggio al secondo", "Не частіше одного повідомлення на секунду"), fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { opened = false }) { Text(mpText(language, "Закрыть", "Close", "Chiudi", "Закрити")) } }
    )
}

@Composable
fun RoomConnectionStatus(model: DurakViewModel, room: MultiplayerLobby.Snapshot?, language: AppLanguage) {
    val network by model.networkState.collectAsStateWithLifecycle()
    val mode by model.activeMode.collectAsStateWithLifecycle()
    val missing = room?.players?.filter { !it.connected }.orEmpty()
    if (network in listOf(MultiplayerManager.State.RECONNECTING, MultiplayerManager.State.CONNECTING, MultiplayerManager.State.DISCONNECTED)) {
        Text(mpText(language, "Связь потеряна. Партия сохранена, восстанавливаем подключение…", "Connection lost. Your game is saved; reconnecting…", "Connessione persa. Partita salvata, riconnessione…", "Зв’язок втрачено. Партію збережено, відновлюємо підключення…"), modifier = Modifier.testTag("reconnecting"), fontSize = 13.sp)
        TextButton(onClick = { model.retryMultiplayerConnection() }) { Text(mpText(language, "Повторить подключение", "Retry connection", "Riprova connessione", "Повторити підключення")) }
    } else if (missing.isNotEmpty()) {
        Text(mpText(language, "Пауза — ждём: ", "Paused — waiting for: ", "Pausa — in attesa di: ", "Пауза — чекаємо: ") + missing.joinToString { it.nickname }, modifier = Modifier.testTag("room_paused"), fontSize = 13.sp)
        if (mode == GameMode.ONLINE_HOST) missing.forEach { player ->
            TextButton(onClick = { model.removeRoomPlayer(player.id) }) { Text(mpText(language, "Исключить ", "Remove ", "Rimuovi ", "Виключити ") + player.nickname) }
        }
    }
}

@Composable
fun BitoProgress(state: GameStateSnapshot, localName: String, language: AppLanguage) {
    if (state.tablePairs.isEmpty() || state.roundAttackerIds.isEmpty()) return
    fun name(id: String) = if (id == state.localPlayerId) localName else state.opponents.firstOrNull { it.id == id }?.name.orEmpty()
    val confirmed = state.roundAttackerIds.count { it in state.passedPlayerIds }
    val pending = state.roundAttackerIds.filter { it !in state.passedPlayerIds }
    val defensePending = !state.isDefenderTaking && state.tablePairs.any { it.defenseCard == null }
    Text(
        mpText(language, "Бито", "Bito", "Bito", "Біто") + ": $confirmed/${state.roundAttackerIds.size} · " + if (defensePending)
            mpText(language, "ожидается защита", "waiting for defense", "in attesa della difesa", "очікується захист")
        else mpText(language, "ждём: ", "waiting for: ", "in attesa di: ", "чекаємо: ") + pending.joinToString { name(it) },
        modifier = Modifier.fillMaxWidth().testTag("bito_progress"), fontSize = 12.sp
    )
}
