package com.example

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.*
import com.example.network.MultiplayerLobby
import com.example.ui.BitoProgress
import com.example.ui.RoomChat
import com.example.viewmodel.AppLanguage
import com.example.viewmodel.DurakViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bitoProgressNamesTheUnconfirmedPlayers() {
        val state = GameStateSnapshot(localPlayerId = "me", defenderPlayerId = "def", attackerPlayerId = "lead",
            tablePairs = listOf(CardPair(Card(Suit.CLUBS, Rank.SIX), Card(Suit.CLUBS, Rank.SEVEN))),
            roundAttackerIds = listOf("lead", "me"), passedPlayerIds = listOf("lead"),
            opponents = listOf(RemotePlayerSnapshot("lead", "Alice", 5, true), RemotePlayerSnapshot("def", "Bob", 5, isDefender = true)))
        compose.setContent { MaterialTheme { BitoProgress(state, "Player", AppLanguage.RU) } }
        compose.onNodeWithTag("bito_progress").assertTextContains("Бито: 1/2 · ждём: Player")
    }
    @Test fun muteHidesMessagesAndPersistsInPreferences() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val prefs = app.getSharedPreferences("durak_prefs", 0)
        val previous = prefs.getBoolean("communication_muted", false)
        prefs.edit().putBoolean("communication_muted", false).commit()
        val store = ViewModelStore()
        lateinit var model: DurakViewModel
        val room = MultiplayerLobby.Snapshot("CHAT32", 2, emptyList(), listOf(MultiplayerLobby.ChatMessage("peer", "Alice", "Hello")))
        try {
            compose.runOnUiThread { model = DurakViewModel(app); store.put("chat", model) }
            compose.setContent { MaterialTheme { RoomChat(model, room, AppLanguage.RU) } }
            compose.onNodeWithText("Alice: Hello").assertIsDisplayed()
            compose.onNodeWithTag("mute_chat").performClick()
            compose.onNodeWithText("Alice: Hello").assertDoesNotExist()
            compose.onNodeWithTag("room_chat").performClick()
            compose.onNodeWithText("Сообщения и реакции приглушены").assertIsDisplayed()
            assertTrue(prefs.getBoolean("communication_muted", false))
            compose.onNodeWithText("Закрыть").performClick()
            compose.onNodeWithTag("mute_chat").performClick()
            compose.onNodeWithText("Alice: Hello").assertIsDisplayed()
        } finally {
            compose.runOnUiThread { store.clear() }
            prefs.edit().putBoolean("communication_muted", previous).commit()
        }
    }
}
