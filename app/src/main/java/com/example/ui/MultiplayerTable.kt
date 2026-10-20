package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

fun roadmapAnnouncement(language: AppLanguage): Pair<String, String> = when (language) {
    AppLanguage.RU -> "📊 Анонс: Версия 0.4\nСтатистическое обновление" to """
        Планируем в Statistics Update:

        • Обновлённая статистика: более подробные показатели побед и поражений, результаты по режимам и отдельный учёт одиночных и сетевых партий.

        • Динамика твоей игры: наглядные графики и сравнение показателей за разные периоды, чтобы видеть свой прогресс.

        • Удобные логи: читаемая история ходов, поиск и фильтры в архиве, быстрый доступ к нужной партии.

        • Разбор игр: просмотр ключевых ходов и поворотных моментов партии, чтобы легче замечать ошибки и удачные решения.

        • Улучшения интерфейса: более удобные экраны статистики, архива и просмотра партии, понятная навигация и представление данных.

        • Полноценные порты для Windows и Linux: настольные версии с интерфейсом и управлением, адаптированными под мышь, клавиатуру и большие экраны.
    """.trimIndent()
    AppLanguage.EN -> "📊 Announcement: Version 0.4\nStatistics Update" to """
        Planned for Statistics Update:

        • Updated statistics: more detailed win and loss figures, results by game mode, and separate records for solo and multiplayer matches.

        • Your performance over time: clear charts and comparisons across different periods to help you track your progress.

        • Better logs: readable move histories, archive search and filters, and quick access to the match you need.

        • Game analysis: review key moves and turning points to better understand mistakes and good decisions.

        • Interface improvements: more convenient statistics, archive and match review screens, clearer navigation and data presentation.

        • Full Windows and Linux ports: desktop versions with interfaces and controls adapted for mouse, keyboard and larger screens.
    """.trimIndent()
    AppLanguage.IT -> "📊 Annuncio: Versione 0.4\nAggiornamento delle statistiche" to """
        In programma per Statistics Update:

        • Statistiche aggiornate: dati più dettagliati su vittorie e sconfitte, risultati per modalità e registri separati per partite in solitaria e multigiocatore.

        • Andamento del tuo gioco: grafici chiari e confronti tra periodi diversi per seguire i tuoi progressi.

        • Registri più comodi: cronologia delle mosse leggibile, ricerca e filtri nell’archivio, accesso rapido alla partita desiderata.

        • Analisi delle partite: revisione delle mosse chiave e dei momenti decisivi per capire meglio errori e buone scelte.

        • Miglioramenti dell’interfaccia: schermate più comode per statistiche, archivio e revisione delle partite, navigazione e presentazione dei dati più chiare.

        • Versioni complete per Windows e Linux: edizioni desktop con interfaccia e comandi adattati a mouse, tastiera e schermi più grandi.
    """.trimIndent()
    AppLanguage.UA -> "📊 Анонс: Версія 0.4\nСтатистичне оновлення" to """
        Плануємо у Statistics Update:

        • Оновлена статистика: докладніші показники перемог і поразок, результати за режимами та окремий облік одиночних і мережевих партій.

        • Динаміка твоєї гри: наочні графіки й порівняння показників за різні періоди, щоб бачити свій прогрес.

        • Зручні логи: читабельна історія ходів, пошук і фільтри в архіві, швидкий доступ до потрібної партії.

        • Розбір ігор: перегляд ключових ходів і поворотних моментів партії, щоб легше помічати помилки та вдалі рішення.

        • Поліпшення інтерфейсу: зручніші екрани статистики, архіву й перегляду партії, зрозуміла навігація та подання даних.

        • Повноцінні порти для Windows і Linux: настільні версії з інтерфейсом і керуванням, адаптованими для миші, клавіатури та великих екранів.
    """.trimIndent()
}

@Composable
fun RoadmapNotice(language: AppLanguage) {
    val (title, description) = roadmapAnnouncement(language)
    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).testTag("roadmap_notice"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.testTag("roadmap_title"))
        Text(description, modifier = Modifier.testTag("roadmap_description"))
    }
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
                            player.id in snapshot.withdrawnPlayerIds -> if (russian) "вышел" else "left"
                            player.id in snapshot.passedPlayerIds -> "Бито ✓"
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
        snapshot.roomPaused -> mpText(language, "ПАУЗА · ВОССТАНОВЛЕНИЕ СВЯЗИ", "PAUSED · RECONNECTING", "PAUSA · RICONNESSIONE", "ПАУЗА · ВІДНОВЛЕННЯ ЗВ’ЯЗКУ")
        snapshot.isDefenderTaking && snapshot.defenderPlayerId == snapshot.localPlayerId ->
            if (russian) "ВЫ БЕРЁТЕ · ОЖИДАЙТЕ ЗАВЕРШЕНИЯ" else "YOU ARE TAKING · WAIT FOR ROUND END"
        snapshot.isDefenderTaking && !snapshot.canBito && !snapshot.isLocalTurn ->
            if (russian) "$defenderName БЕРЁТ · ОЖИДАЙТЕ ПОДКИДЫВАЮЩИХ" else "$defenderName IS TAKING · WAIT FOR ATTACKERS"
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
