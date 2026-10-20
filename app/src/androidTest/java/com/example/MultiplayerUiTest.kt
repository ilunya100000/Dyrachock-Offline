package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.*
import com.example.ui.MultiplayerRoster
import com.example.ui.RoadmapNotice
import com.example.ui.multiplayerTurnText
import com.example.viewmodel.AppLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MultiplayerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSixSeatsAreVisibleOnNarrowPhoneAndRoadmapShowsAnnouncement() {
        val state = GameStateSnapshot(
            localPlayerId = "p0", defenderPlayerId = "p2", attackerPlayerId = "p1", currentPlayerId = "p2",
            localHand = listOf(Card(Suit.CLUBS, Rank.SIX)),
            opponents = (1..5).map { RemotePlayerSnapshot("p$it", "Игрок $it", it + 1, it == 1, it == 2) }
        )
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    MultiplayerRoster(state, "Хост", AppLanguage.RU)
                    RoadmapNotice(AppLanguage.RU)
                }
            }
        }
        for (id in 0..5) compose.onNodeWithTag("seat_p$id").assertIsDisplayed()
        compose.onNodeWithTag("roadmap_title").assertTextContains("Статистическое обновление", substring = true)
        compose.onNodeWithText("Хост (вы)").assertIsDisplayed()
        val image = compose.onNodeWithTag("multiplayer_roster").captureToImage().asAndroidBitmap()
        val destination = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "six-player-roster.png")
        destination.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun observerNeverSeesMessageClaimingTheyAreTakingAnotherPlayersCards() {
        val state = GameStateSnapshot(
            localPlayerId = "helper", defenderPlayerId = "defender", isDefenderTaking = true,
            isLocalTurn = false, opponents = listOf(RemotePlayerSnapshot("defender", "Илья", 6))
        )
        assertTrue(multiplayerTurnText(state, AppLanguage.RU).contains("Илья"))
        assertFalse(multiplayerTurnText(state, AppLanguage.RU).contains("ВЫ БЕРЁТЕ"))
    }
}
