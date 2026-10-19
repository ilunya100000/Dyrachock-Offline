package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameStateSnapshot
import com.example.model.RemotePlayerSnapshot
import com.example.viewmodel.AppLanguage

@Composable
fun RoadmapNotice(language: AppLanguage) {
    Text(
        text = when (language) {
            AppLanguage.RU -> "Временно меняется"
            AppLanguage.UA -> "Тимчасово змінюється"
            AppLanguage.IT -> "In fase di aggiornamento"
            AppLanguage.EN -> "Temporarily being updated"
        },
        modifier = Modifier.testTag("roadmap_notice")
    )
}

@Composable
fun MultiplayerRoster(snapshot: GameStateSnapshot, localName: String, language: AppLanguage) {
    val russian = language == AppLanguage.RU
    val players = listOf(RemotePlayerSnapshot(
        snapshot.localPlayerId, localName, snapshot.localHand.size,
        snapshot.attackerPlayerId == snapshot.localPlayerId,
        snapshot.defenderPlayerId == snapshot.localPlayerId
    )) + snapshot.opponents
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("multiplayer_roster")) {
        val columns = if (maxWidth < 360.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            players.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { player ->
                        val local = player.id == snapshot.localPlayerId
                        val role = when {
                            player.isDefender -> if (russian) "защита" else "defender"
                            player.isAttacker -> if (russian) "атака" else "attacker"
                            player.handSize == 0 && snapshot.deckSize == 0 -> if (russian) "без карт" else "no cards"
                            snapshot.tablePairs.isNotEmpty() -> if (russian) "подкидывает" else "throw-in"
                            else -> if (russian) "ожидает" else "waiting"
                        }
                        Column(
                            Modifier.weight(1f)
                                .background(Color.White.copy(alpha = if (local) 0.12f else 0.06f), RoundedCornerShape(10.dp))
                                .border(1.dp, if (player.id == snapshot.currentPlayerId) Color(0xFFD1E4FF) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                .testTag("seat_${player.id}")
                        ) {
                            Text(
                                player.name + if (local) (if (russian) " (вы)" else " (you)") else "",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("${player.handSize} · $role", color = Color(0xFFB7BDC5), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

fun multiplayerTurnText(snapshot: GameStateSnapshot, language: AppLanguage): String {
    val russian = language == AppLanguage.RU
    val defenderName = snapshot.opponents.firstOrNull { it.id == snapshot.defenderPlayerId }?.name.orEmpty()
    return when {
        snapshot.isDefenderTaking && snapshot.defenderPlayerId == snapshot.localPlayerId ->
            if (russian) "ВЫ БЕРЁТЕ · ОЖИДАЙТЕ ЗАВЕРШЕНИЯ" else "YOU ARE TAKING · WAIT FOR ROUND END"
        snapshot.isDefenderTaking ->
            if (russian) "$defenderName БЕРЁТ · ${if (snapshot.canBito) "ЗАВЕРШИТЕ ХОД" else "ДОБРОС"}"
            else "$defenderName IS TAKING · ${if (snapshot.canBito) "FINISH ROUND" else "THROW-IN"}"
        snapshot.canBito -> if (russian) "МОЖНО ПОДКИНУТЬ ИЛИ НАЖАТЬ БИТО" else "THROW IN OR FINISH ROUND"
        snapshot.isLocalTurn && snapshot.defenderPlayerId == snapshot.localPlayerId ->
            if (russian) "ВАША ЗАЩИТА" else "YOUR DEFENSE"
        snapshot.isLocalTurn ->
            if (russian) (if (snapshot.tablePairs.isEmpty()) "ВАША АТАКА" else "МОЖНО ПОДКИНУТЬ")
            else (if (snapshot.tablePairs.isEmpty()) "YOUR ATTACK" else "YOU MAY THROW IN")
        snapshot.localHand.isEmpty() && snapshot.deckSize == 0 ->
            if (russian) "У ВАС НЕТ КАРТ · НАБЛЮДЕНИЕ" else "NO CARDS · SPECTATING"
        else -> {
            val current = snapshot.opponents.firstOrNull { it.id == snapshot.currentPlayerId }?.name.orEmpty()
            if (russian) "ХОД: $current" else "TURN: $current"
        }
    }
}
