package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.MatchStatus
import com.example.network.MultiplayerGameProtocol
import com.example.network.MultiplayerManager
import com.example.ui.GameTableScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DurakViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Real ViewModel + TCP clients + the full Compose table, without test state injection. */
@RunWith(AndroidJUnit4::class)
class MultiplayerTableFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixPlayersCanEnterTableAttackAndSeeCorrectDefender() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var model: DurakViewModel
        val clients = List(5) { MultiplayerManager(app) }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val states = ConcurrentHashMap<Int, MultiplayerGameProtocol.State>()
        try {
            compose.runOnUiThread { model = DurakViewModel(app); store.put("match", model) }
            compose.setContent { MyApplicationTheme { GameTableScreen(model) } }
            compose.waitUntil(10000) { model.currentScreen.value == DurakViewModel.Screen.MAIN_MENU }
            compose.runOnIdle {
                model.setPlayerNickname("Хост")
                model.setMpLobbyPlayersCount(6)
                model.startHostingLobby()
            }
            clients.forEachIndexed { index, client ->
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    client.incomingMessages.collect { message ->
                        MultiplayerGameProtocol.parseState(message.payload)?.let { states[index] = it }
                    }
                }
                runBlocking {
                    withTimeout(15000) {
                        do {
                            client.connectToHost("127.0.0.1", nickname = "Игрок ${index + 1}")
                            while (client.connectionState.value == MultiplayerManager.State.CONNECTING) delay(20)
                        } while (client.connectionState.value != MultiplayerManager.State.CONNECTED)
                    }
                }
            }
            compose.waitUntil(15000) {
                model.multiplayerLobby.value?.players?.size == 6 && clients.withIndex().all { (index, client) ->
                    model.multiplayerLobby.value?.players?.any { it.id == client.localPlayerId.value && it.nickname == "Игрок ${index + 1}" } == true
                }
            }
            compose.runOnIdle { assertTrue(model.startHostedMatch()) }
            compose.waitUntil(10000) { states.size == 5 && model.gameState.value.opponents.size == 5 }
            compose.onNodeWithTag("seat_host").assertIsDisplayed()
            clients.forEach { compose.onNodeWithTag("seat_${it.localPlayerId.value}").assertIsDisplayed() }
            val before = model.gameState.value
            val attacker = before.attackerPlayerId
            val attackerIndex = clients.indexOfFirst { it.localPlayerId.value == attacker }
            if (attacker == "host") compose.runOnIdle { model.playCard(before.localHand.first()) }
            else clients[attackerIndex].sendMessage(MultiplayerGameProtocol.attack(states.getValue(attackerIndex).hand.first()))
            compose.waitUntil(10000) { model.gameState.value.tablePairs.size == 1 }
            val after = model.gameState.value
            assertEquals(MatchStatus.PLAYING, after.matchStatus)
            assertEquals(after.defenderPlayerId, after.currentPlayerId)
            compose.waitUntil(10000) { states.values.all { it.table.size == 1 && it.currentPlayerId == after.defenderPlayerId } }
            clients.forEach { compose.onNodeWithTag("seat_${it.localPlayerId.value}").assertIsDisplayed() }
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val destination = File(app.getExternalFilesDir(null), "six-player-full-table.png")
            destination.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            clients.forEach { it.stopAll() }
            scope.cancel()
            compose.runOnUiThread { store.clear() }
        }
    }
}
