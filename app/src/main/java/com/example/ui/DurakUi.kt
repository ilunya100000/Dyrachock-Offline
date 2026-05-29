package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.GameStat
import com.example.model.*
import com.example.network.MultiplayerManager
import com.example.ui.theme.PrimaryLight
import com.example.ui.theme.SuitBlack
import com.example.ui.theme.SuitBlackOnDark
import com.example.ui.theme.SuitRed
import com.example.viewmodel.AppLanguage
import com.example.viewmodel.DurakViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DurakApp(viewModel: DurakViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val statsList by viewModel.gameHistory.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() with
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                DurakViewModel.Screen.MAIN_MENU -> MainMenuScreen(viewModel, statsList)
                DurakViewModel.Screen.OFFLINE_SETUP -> OfflineSetupScreen(viewModel)
                DurakViewModel.Screen.MULTIPLAYER_HUB -> MultiplayerHubScreen(viewModel)
                DurakViewModel.Screen.GAME_TABLE -> GameTableScreen(viewModel)
                DurakViewModel.Screen.STATS_BOARD -> StatsBoardScreen(viewModel, statsList)
            }
        }
    }
}

// --- SCREEN: MAIN MENU ---
@Composable
fun MainMenuScreen(viewModel: DurakViewModel, statsList: List<GameStat>) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLangTemp by remember { mutableStateOf(appLanguage) }

    // Calculate quick stats summary
    val totalGames = statsList.size
    val wins = statsList.count { it.result == "WON" }
    val winsPercent = if (totalGames > 0) (wins * 100) / totalGames else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP: Dynamic Language Switching & Brand Glow
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    selectedLangTemp = appLanguage
                    showLanguageDialog = true
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("lang_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Change Language",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // CENTER: Royal Banner & Dynamic Stats Board
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Deck Cards Icon Design in Canvas
                Canvas(modifier = Modifier.size(64.dp)) {
                    val cardWidth = 36.dp.toPx()
                    val cardHeight = 52.dp.toPx()

                    // Card 1 (Rotated)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(8.dp.toPx(), 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(cardWidth, cardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Card 2 (Front)
                    drawRoundRect(
                        color = Color(0xFFFF5252),
                        topLeft = Offset(20.dp.toPx(), 12.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(cardWidth, cardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = viewModel.getString("APP_TITLE"),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = viewModel.getString("STATUS_TITLE_LABEL"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Stats Board
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalGames.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Матчей" else "Games",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalGames >= 5) "$winsPercent%" else "—",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Побед" else "Winrate",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.navigateTo(DurakViewModel.Screen.STATS_BOARD) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Stats",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = viewModel.getString("STATS_TITLE"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // BOTTOM: Samsung Ergonomic comfort thumb zone buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.OFFLINE_SETUP) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("play_offline_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewModel.getString("PLAY_OFFLINE"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MULTIPLAYER_HUB) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("play_online_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewModel.getString("PLAY_ONLINE"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom footer containing Version on the left, and Changelog button on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "0.1.1",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )

                var showChangelogDialog by remember { mutableStateOf(false) }

                Text(
                    text = viewModel.getString("CHANGELOG_BTN"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showChangelogDialog = true }
                        .padding(8.dp)
                )

                if (showChangelogDialog) {
                    AlertDialog(
                        onDismissRequest = { showChangelogDialog = false },
                        title = {
                            Text(
                                text = viewModel.getString("CHANGELOG_TITLE"),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = viewModel.getString("CHANGELOG_TEXT"),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showChangelogDialog = false }) {
                                Text(
                                    text = if (viewModel.appLanguage.value == AppLanguage.RU) "Закрыть" else "Close",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = when (appLanguage) {
                        AppLanguage.RU -> "Выберите язык"
                        AppLanguage.IT -> "Seleziona lingua"
                        else -> "Select Language"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val languages = listOf(
                        AppLanguage.EN to "English",
                        AppLanguage.RU to "Русский",
                        AppLanguage.IT to "Italiano (Beta)"
                    )
                    languages.forEach { (lang, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLangTemp = lang }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedLangTemp == lang),
                                onClick = { selectedLangTemp = lang }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontWeight = if (selectedLangTemp == lang) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedLangTemp == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setLanguage(selectedLangTemp)
                        showLanguageDialog = false
                    }
                ) {
                    Text(
                        text = when (appLanguage) {
                            AppLanguage.RU -> "Подтвердить"
                            AppLanguage.IT -> "Conferma"
                            else -> "Confirm"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLanguageDialog = false }
                ) {
                    Text(
                        text = when (appLanguage) {
                            AppLanguage.RU -> "Отмена"
                            AppLanguage.IT -> "Annulla"
                            else -> "Cancel"
                        }
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// --- SCREEN: OFFLINE SETUP ---
@Composable
fun OfflineSetupScreen(viewModel: DurakViewModel) {
    val isHard by viewModel.isBotHard.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Back Navigation Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = viewModel.getString("BOT_SETUP_TITLE"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Center Option Selectors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = viewModel.getString("DIFFICULTY"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Tactile Segmented Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isHard) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setDifficulty(false) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.getString("EASY"),
                        color = if (!isHard) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isHard) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setDifficulty(true) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.getString("HARD"),
                        color = if (isHard) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Text(
                text = if (appLanguage == AppLanguage.RU) "Режим Игры" else "Game Variant",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )

            val subMode by viewModel.offlineSubMode.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setOfflineSubMode(com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Классический" else "Classic",
                        color = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setOfflineSubMode(com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Переводной (Beta)" else "Passing (Beta)",
                        color = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bot behavior descriptive Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) "TRANSFER RULES" else if (isHard) "AI ANALYTICAL BOT" else "DECENT AMATEUR BOT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) {
                            if (appLanguage == AppLanguage.RU) {
                                "Режим Переводного Дурака активирован! Вы или ИИ-бoт можете перевести защитную обязанность на оппонента, подкинув карту совпадающего достоинства. Бот умеет грамотно оценивать риски перевода."
                            } else {
                                "Transfer rules applied! You or the bot can transfer defending duty by matching the rank of the attacking cards. The bot is fully optimized to transfer tactically!"
                            }
                        } else if (isHard) {
                            "Defends with cold calculation. Tracks all played cards, saves trumps for endgame clutches, and prioritizes strategic discard sequences."
                        } else {
                            "Plays casual valid combinations. Excellent for beginners looking to learn basic durak card sequencing."
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Bottom Call to Action
        Button(
            onClick = { viewModel.startOfflineMatch() },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("start_game_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = viewModel.getString("START_GAME"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- SCREEN: MULTIPLAYER HUB ---
@Composable
fun MultiplayerHubScreen(viewModel: DurakViewModel) {
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val discoveredList by viewModel.discoveredHosts.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var activeTabHost by remember { mutableStateOf(true) }
    var inputIp by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.startSearchingHosts()
        onDispose {
            // Stops discover on screen exit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = viewModel.getString("P2P_TITLE"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // CENTER: Mode Selector (Host vs Join)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (activeTabHost) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { activeTabHost = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Раздать лобби" else "Host Match",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!activeTabHost) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { activeTabHost = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Войти в лобби" else "Search/Join",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (activeTabHost) {
                // PANEL: HOSTING CONFIG
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = viewModel.getString("MY_IP"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localIp,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = "Port: 8888",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // ADVANCED CREATION OPTIONS (Only selectable if we haven't started hosting yet!)
                        val selectedDeckSize by viewModel.mpLobbyDeckSize.collectAsStateWithLifecycle()
                        val selectedPlayersCount by viewModel.mpLobbyPlayersCount.collectAsStateWithLifecycle()

                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Размер колоды" else "Deck Size",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp)
                        ) {
                            val optionDeck36Enabled = networkState != MultiplayerManager.State.HOSTING
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedDeckSize == 36) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(enabled = optionDeck36Enabled) { viewModel.setMpLobbyDeckSize(36) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "36 (6 - A)",
                                    color = if (selectedDeckSize == 36) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedDeckSize == 52) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(enabled = optionDeck36Enabled) { viewModel.setMpLobbyDeckSize(52) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "52 (2 - A)",
                                    color = if (selectedDeckSize == 52) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Количество игроков" else "Max Players",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val optionPlayersEnabled = networkState != MultiplayerManager.State.HOSTING
                            for (pCount in 2..6) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedPlayersCount == pCount) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                        .clickable(enabled = optionPlayersEnabled) { viewModel.setMpLobbyPlayersCount(pCount) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pCount.toString(),
                                        color = if (selectedPlayersCount == pCount) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (networkState == MultiplayerManager.State.HOSTING) {
                            Text(
                                text = viewModel.getString("WAITING_LOBBY"),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = { viewModel.startHostingLobby() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = viewModel.getString("HOST_LOBBY"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // PANEL: CLIENT CONFIG (DISCOVERY & MANUAL)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = viewModel.getString("NSD_STATUS"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (discoveredList.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.secondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = viewModel.getString("DISCOVERY_ACTIVE"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredList) { service ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.connectToIpAddress(
                                                service.host?.hostAddress ?: "127.0.0.1"
                                            )
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Host Lobby",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "IP: ${service.host?.hostAddress}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }

                                        Text(
                                            text = viewModel.getString("CONNECT_BTN"),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = viewModel.getString("MANUAL_CONNECT"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Manual Connection Outlines
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = inputIp,
                                onValueChange = { inputIp = it },
                                label = { Text(viewModel.getString("ENTER_HOST_IP")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (inputIp.isNotBlank()) {
                                        viewModel.connectToIpAddress(inputIp)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(text = viewModel.getString("CONNECT_BTN"))
                            }
                        }
                    }
                }
            }
        }

        // Network Status Alerts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val statusLabel = when (networkState) {
                MultiplayerManager.State.CONNECTING -> viewModel.getString("CONNECTING")
                MultiplayerManager.State.CONNECTED -> "CONNECTED!"
                MultiplayerManager.State.DISCONNECTED -> viewModel.getString("DISCONNECTED")
                else -> ""
            }

            if (statusLabel.isNotEmpty()) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private fun Modifier.fillLogLevel(): Modifier = this.fillMaxWidth()

// --- SCREEN: GAME TABLE ---
@Composable
fun GameTableScreen(viewModel: DurakViewModel) {
    val snapshot by viewModel.gameState.collectAsStateWithLifecycle()
    val isThinking by viewModel.botThinking.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val offlineSubMode by viewModel.offlineSubMode.collectAsStateWithLifecycle()
    val isTransferEnabled = (viewModel.activeMode.collectAsStateWithLifecycle().value == GameMode.OFFLINE) && 
                            (offlineSubMode == DurakViewModel.OfflineSubMode.TRANSFER)
    val canPlayerTransferNow = isTransferEnabled && 
            snapshot.tablePairs.isNotEmpty() && 
            snapshot.tablePairs.all { it.defenseCard == null } &&
            snapshot.attackerPlayerId == "opponent"

    var showLogs by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E2124), Color(0xFF0F1113)),
                    center = Offset(500f, 300f),
                    radius = 1200f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Top Table Arena (covers active battlefield and info cards)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TOP CONTROL: Opponent Header & Deck Details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Profile Circle using Clean Minimalism layout
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF3F474F), CircleShape)
                                .border(2.dp, Color(0xFFD1E4FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (snapshot.opponentName.contains("Bot", ignoreCase = true) || snapshot.opponentName.contains("AI", ignoreCase = true)) "🤖" else snapshot.opponentName.take(2).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = snapshot.opponentName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${snapshot.opponentHandSize} Cards",
                                    color = Color(0xFF909094),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isThinking) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CircularProgressIndicator(
                                        color = Color(0xFFD1E4FF),
                                        strokeWidth = 1.5.dp,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mode badge from design specs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD1E4FF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (viewModel.activeMode.value == com.example.model.GameMode.OFFLINE) "OFFLINE VS AI" else "P2P ONLINE",
                                color = Color(0xFF00315B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Small Exit button to menu safely
                        IconButton(
                            onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 2. MIDDLE TOP: Immersive Opponent Hand (Minimalist cards backs)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    val visibleBotsCount = minOf(snapshot.opponentHandSize, 6)
                    repeat(visibleBotsCount) {
                        CardBackComponent(modifier = Modifier.offset(x = (it * -10).dp))
                    }
                    if (snapshot.opponentHandSize > 6) {
                        Box(
                            modifier = Modifier
                                .size(38.dp, 56.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .offset(x = (visibleBotsCount * -10).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${snapshot.opponentHandSize - 6}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. TABLE BOARD CONTENT (Active battle cards row)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (snapshot.tablePairs.isEmpty()) {
                        // Empty board overlay instructions
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (snapshot.isLocalTurn) viewModel.getString("TURN_PLAYER") else viewModel.getString("TURN_OPPONENT"),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (snapshot.isLocalTurn) "Select card from hand to start attack" else "Awaiting opponent move...",
                                fontSize = 11.sp,
                                color = Color(0xFF909094)
                            )
                        }
                    } else {
                        // Display table active card pairs (chunked beautifully into rows of up to 3 pairs to wrap neatly)
                        val chunkedPairs = snapshot.tablePairs.chunked(3)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (rowPairs in chunkedPairs) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (pair in rowPairs) {
                                        Box(
                                            modifier = Modifier
                                                .size(92.dp, 124.dp)
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            // Underneath card (The attacking card)
                                            CardComponent(
                                                card = pair.attackCard,
                                                faceUp = true,
                                                onClick = {
                                                    val isMp = (viewModel.activeMode.value == com.example.model.GameMode.ONLINE_HOST || viewModel.activeMode.value == com.example.model.GameMode.ONLINE_CLIENT)
                                                    if (isMp && pair.defenseCard == null) {
                                                        viewModel.takeBackCard(pair.attackCard)
                                                    } else {
                                                        viewModel.playCard(pair.attackCard)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .size(70.dp, 102.dp)
                                            )

                                            // Overlap card (The defensive card, if beat)
                                            if (pair.defenseCard != null) {
                                                CardComponent(
                                                    card = pair.defenseCard,
                                                    faceUp = true,
                                                    onClick = {},
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(70.dp, 102.dp)
                                                        .shadow(6.dp, RoundedCornerShape(12.dp))
                                                )
                                            } else {
                                                // Visual highlight helper pointing that card needs matching defense
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(70.dp, 102.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .border(
                                                            1.5.dp,
                                                            Brush.linearGradient(listOf(Color(0xFFD1E4FF).copy(alpha = 0.4f), Color.Transparent)),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .background(Color.White.copy(alpha = 0.03f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (appLanguage == AppLanguage.RU) "БЕЙ" else "DEFEND",
                                                        color = Color.White.copy(alpha = 0.35f),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.sp,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. FLOATING ROW: Trump card status, Deck count & Discard piles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Deck / Trump panel
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.padding(end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val trump = snapshot.trumpCard
                            if (trump != null && snapshot.deckSize > 0) {
                                // 1. Trump card horizontal bottom base
                                CardComponent(
                                    card = trump,
                                    faceUp = true,
                                    onClick = {},
                                    modifier = Modifier
                                        .size(80.dp, 56.dp)
                                        .align(Alignment.Center)
                                        .offset(x = (-14).dp)
                                )
                            }

                            if (snapshot.deckSize > 0) {
                                // 2. Stack of card backs on top of it
                                if (snapshot.deckSize > 1) {
                                    CardBackComponent(
                                        modifier = Modifier
                                            .size(56.dp, 80.dp)
                                            .offset(x = 2.dp, y = (-2).dp)
                                    )
                                }
                                CardBackComponent(
                                    modifier = Modifier
                                        .size(56.dp, 80.dp)
                                    )
                            }
                        }

                        Column {
                            Text(
                                text = "${viewModel.getString("DECK")}: ${snapshot.deckSize}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            val trumpLabel = if (appLanguage == AppLanguage.RU) snapshot.trumpSuit.ruLabel else snapshot.trumpSuit.enLabel
                            Text(
                                text = "${viewModel.getString("TRUMP")}: ${snapshot.trumpSuit.symbol} ($trumpLabel)",
                                color = if (snapshot.trumpSuit.colorRed) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Transfer Area if player can transfer
                    if (canPlayerTransferNow) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp, 88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(listOf(Color(0xFFFF5D5D), Color(0xFFFF3333))),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(Color(0xFF3B1A1E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Transfer Zone",
                                        tint = Color(0xFFFF8282),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.RU) "ПАС / ПЕРЕВОД" else "TRANSFER ZONE",
                                        color = Color(0xFFFFB4B4),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Discard size & Relocated Battle Logs under Out Box
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Bito Pile",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Out: ${snapshot.discardPileSize}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable { showLogs = !showLogs }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Logs",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = viewModel.getString("GAME_LOGS"),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5 and 6. Bottom Player Footer Panel (Samsung Ergonomics / One UI style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF1A1C1E))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Turn Status Text inside Footer
                Text(
                    text = if (snapshot.isLocalTurn) {
                        if (appLanguage == AppLanguage.RU) "ВАШ ХОД" else "YOUR TURN"
                    } else {
                        if (appLanguage == AppLanguage.RU) "ХОД БОТА" else "BOT'S TURN"
                    },
                    color = if (snapshot.isLocalTurn) Color(0xFFD1E4FF) else Color(0xFF909094),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 5. PLAYER CARDS HAND: Horizontal swipeable deck inside footer with real physics drag and drop
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp),
                    horizontalArrangement = Arrangement.spacedBy((-16).dp), // beautiful card overlay fan
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(snapshot.localHand, key = { it.id }) { card ->
                        var offsetX by remember { mutableStateOf(0f) }
                        var offsetY by remember { mutableStateOf(0f) }
                        val animatedOffsetX by animateFloatAsState(targetValue = offsetX)
                        val animatedOffsetY by animateFloatAsState(targetValue = offsetY)

                        CardComponent(
                            card = card,
                            faceUp = true,
                            onClick = { viewModel.playCard(card) },
                            modifier = Modifier
                                .size(74.dp, 110.dp)
                                .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                                .shadow(6.dp, RoundedCornerShape(12.dp))
                                .pointerInput(card.id) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            offsetX += dragAmount.x
                                            offsetY += dragAmount.y
                                        },
                                        onDragEnd = {
                                            if (offsetY < -130f) {
                                                if (canPlayerTransferNow && offsetX < -50f) {
                                                    // Dragged towards the red Transfer Drop Zone specifically
                                                    viewModel.playCard(card, intentTransferOnly = true)
                                                } else {
                                                    // Normal play throw
                                                    viewModel.playCard(card)
                                                }
                                            }
                                            offsetX = 0f
                                            offsetY = 0f
                                        },
                                        onDragCancel = {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    )
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. BOTTOM ACTIONS: Taking and Bito buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (snapshot.canTake) {
                        Button(
                            onClick = { viewModel.pressTakeAll() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F474F),     // Attain medium slate grey background
                                contentColor = Color(0xFFD1E4FF)        // Light Cyber Ice Text
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("take_button")
                        ) {
                            Text(
                                text = viewModel.getString("TAKE"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    if (snapshot.canBito) {
                        Button(
                            onClick = { viewModel.pressBito() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD1E4FF),     // Ice blue bright container
                                contentColor = Color(0xFF00315B)      // Contrast deep navy text
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("bito_button")
                        ) {
                            Text(
                                text = viewModel.getString("BITO"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (showLogs) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xF2121513)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .height(280.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = viewModel.getString("GAME_LOGS"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val gameLogsToDraw = if (appLanguage == AppLanguage.RU) snapshot.gameLogRu else snapshot.gameLogEn
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(gameLogsToDraw.reversed()) { logText ->
                            Text(
                                text = "• $logText",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { showLogs = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Close", color = Color.White)
                    }
                }
            }
        }

        // Overlays: Match Results Cards
        if (snapshot.matchStatus == MatchStatus.WON || snapshot.matchStatus == MatchStatus.LOST || snapshot.matchStatus == MatchStatus.DRAW || snapshot.matchStatus == MatchStatus.PLAYER_DISCONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val overlayTitle = when (snapshot.matchStatus) {
                            MatchStatus.WON -> viewModel.getString("WIN_TITLE")
                            MatchStatus.LOST -> viewModel.getString("LOST_TITLE")
                            MatchStatus.DRAW -> viewModel.getString("DRAW_TITLE")
                            else -> viewModel.getString("DISC_TITLE")
                        }

                        val titleColor = if (snapshot.matchStatus == MatchStatus.WON) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }

                        Text(
                            text = overlayTitle,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Game details summary
                        Text(
                            text = if (snapshot.matchStatus == MatchStatus.WON) {
                                "Excellent match played. Results persist offline to your archve history board."
                            } else {
                                "Better luck next round! Review stats archive tracker to analyze wins count."
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (viewModel.activeMode.value == com.example.model.GameMode.OFFLINE) {
                                Button(
                                    onClick = { viewModel.startOfflineMatch() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(text = viewModel.getString("RESTART"))
                                }
                            }

                            Button(
                                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(text = viewModel.getString("MENU"))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CARD RENDERING COMPONENT (Plastic style deck design) ---
@Composable
fun CardComponent(
    card: Card,
    faceUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animTriggered = true
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animTriggered) 1f else 0.5f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "CardScale"
    )

    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animTriggered) 0f else 60f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "CardTranslationY"
    )

    if (!faceUp) {
        CardBackComponent(
            modifier = modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationY = translationY
                )
                .clickable { onClick() }
        )
        return
    }

    Box(
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationY = translationY
            )
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        val displayColor = if (card.suit.colorRed) SuitRed else SuitBlack

        // Corner Indicator (Top Left)
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.rank.symbol,
                color = displayColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 14.sp
            )
            Text(
                text = card.suit.symbol,
                color = displayColor,
                fontSize = 11.sp,
                lineHeight = 11.sp
            )
        }

        // Center Royal graphic
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = card.suit.symbol,
                color = displayColor,
                fontSize = 28.sp,
                lineHeight = 28.sp
            )
        }

        // Corner Indicator (Bottom Right)
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.suit.symbol,
                color = displayColor,
                fontSize = 11.sp,
                lineHeight = 11.sp
            )
            Text(
                text = card.rank.symbol,
                color = displayColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 14.sp
            )
        }
    }
}

// --- CARD BACK RENDERING COMPONENT ---
@Composable
fun CardBackComponent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.5.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f), CircleShape)
        )
    }
}

// --- SCREEN: STATS ARCHIVES ---
@Composable
fun StatsBoardScreen(viewModel: DurakViewModel, statsList: List<GameStat>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = viewModel.getString("STATS_TITLE"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (statsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.getString("EMPTY_STATS"),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(statsList) { stat ->
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val oppPrefix = if (appLanguage == AppLanguage.RU) "Оппонент" else "Opponent"
                                    val modePrefix = if (appLanguage == AppLanguage.RU) "Режим" else "Mode"
                                    Text(
                                        text = "$oppPrefix: ${stat.opponentName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = dateFormat.format(Date(stat.timestamp)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "$modePrefix: ${stat.mode}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val labelRes = when (stat.result) {
                                        "WON" -> if (appLanguage == AppLanguage.RU) "ПОБЕДА" else "WON"
                                        "LOST" -> if (appLanguage == AppLanguage.RU) "ПОРАЖЕНИЕ" else "LOST"
                                        else -> if (appLanguage == AppLanguage.RU) "НИЧЬЯ" else "DRAW"
                                    }
                                    Text(
                                        text = labelRes,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (stat.result == "WON") PrimaryLight else SuitRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Logs toggle",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (expanded) {
                                val logContent = if (appLanguage == AppLanguage.RU) {
                                    stat.matchLogRu.ifEmpty { stat.matchLogEn }
                                } else {
                                    stat.matchLogEn.ifEmpty { stat.matchLogRu }
                                }

                                val logLines = logContent.split("\n").filter { it.isNotBlank() }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.RU) "Ход игры:" else "Battle History:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    if (logLines.isEmpty()) {
                                        Text(
                                            text = if (appLanguage == AppLanguage.RU) "Логи отсутствуют для этого матча" else "No logs captured for this game",
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        logLines.forEach { line ->
                                            Text(
                                                text = "• $line",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.clearMatchHistory() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = viewModel.getString("CLEAR_STATS"))
            }

            Button(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.MAIN_MENU) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = viewModel.getString("BACK"))
            }
        }
    }
}
