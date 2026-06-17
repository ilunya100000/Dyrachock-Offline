package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

sealed interface TableFlowItem {
    data class PairItem(val pair: CardPair) : TableFlowItem
    object TransferItem : TableFlowItem
}

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
                DurakViewModel.Screen.SPLASH -> SplashScreen(viewModel)
                DurakViewModel.Screen.MAIN_MENU -> MainMenuScreen(viewModel, statsList)
                DurakViewModel.Screen.OFFLINE_SETUP -> OfflineSetupScreen(viewModel)
                DurakViewModel.Screen.MULTIPLAYER_HUB -> MultiplayerHubScreen(viewModel)
                DurakViewModel.Screen.GAME_TABLE -> GameTableScreen(viewModel)
                DurakViewModel.Screen.STATS_BOARD -> StatsBoardScreen(viewModel, statsList)
                DurakViewModel.Screen.CUSTOM_DECK -> CustomDeckSelectionScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SplashScreen(viewModel: DurakViewModel) {
    val progress by viewModel.splashProgress.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Animating scale and rotation of elements for eye-catching UI entrance
    val infiniteTransition = rememberInfiniteTransition(label = "SplashAnimation")
    val cardsPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val suitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotate"
    )

    // Tips translated beautifully
    val tips = when (appLanguage) {
        AppLanguage.RU -> listOf(
            "Совет: Берёте карты? Помните, соперник может подкинуть ещё!",
            "Совет: Приберегите козыри крупного достоинства для финала партии.",
            "Совет: В переводном режиме переведите атаку картой того же ранга.",
            "Совет: Игра на высокой сложности ИИ проверит ваши лучшие навыки!"
        )
        AppLanguage.UA -> listOf(
            "Порада: Берете карти? Пам'ятайте, суперник може підкинути ще!",
            "Порада: Збережіть великі козирі для фіналу партії.",
            "Порада: У перевідному режимі переведіть атаку картою того ж рангу.",
            "Порада: Гра на високій складності ШІ перевірить ваші найкращі навички!"
        )
        AppLanguage.IT -> listOf(
            "Consiglio: Prendi le carte? Ricorda, l'avversario può scartarne altre!",
            "Consiglio: Conserva le briscole di alto valore per la fase finale.",
            "Consiglio: Nella modalità trasferimento, passa l'attacco con una carta dello stesso valore.",
            "Consiglio: Giocare a difficoltà Difficile metterà alla prova le tue abilità!"
        )
        else -> listOf(
            "Tip: Taking cards? Remember, your opponent can toss extra matching ranks!",
            "Tip: Save high-value trump cards for the crucial late-game phase.",
            "Tip: In Transfer mode, pass the attack to the next player using a matching rank.",
            "Tip: The Hard AI Bot difficulty level will fully test your strategy!"
        )
    }

    val currentTipIndex = when {
        progress < 0.25f -> 0
        progress < 0.50f -> 1
        progress < 0.75f -> 2
        else -> 3
    }
    val currentTip = tips[currentTipIndex]

    val titleText = when (appLanguage) {
        AppLanguage.RU -> "Дурачок Оффлайн"
        AppLanguage.UA -> "Дурник Офлайн"
        AppLanguage.IT -> "Durak Offline"
        else -> "Dyrachok Offline"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D2516), // Extra deep forest casino green
                        Color(0xFF06150C), // Cosmic almost black green
                        Color(0xFF030D08)
                    )
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Rounded and styled Game Icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = cardsPulseScale
                        scaleY = cardsPulseScale
                    }
                    .shadow(16.dp, RoundedCornerShape(24.dp), clip = true)
                    .background(Color(0xFF14301B), RoundedCornerShape(24.dp))
                    .border(2.dp, Color(0xFFE2C974), RoundedCornerShape(24.dp)) // Royal gold border
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.durak_logo_1779977268973),
                    contentDescription = "Durak Championship Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Game Title
            Text(
                text = titleText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2C974), // Royal gold Accent Color desaturated
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Version 0.2.3_01",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF909094).copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Smooth high-end loading bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                // Percentage representation
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2C974)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom premium progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF1C2C21), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF2C4A36), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFFA500), // Orange gold
                                        Color(0xFFE2C974)  // Royal yellow gold
                                    )
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Spinning suit elements representing loading activity
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val suits = listOf("♥", "♦", "♣", "♠")
                    suits.forEachIndexed { sIdx, suitSymbol ->
                        val spinFactor = if (sIdx % 2 == 0) 1f else -1f
                        Text(
                            text = suitSymbol,
                            color = if (suitSymbol == "♥" || suitSymbol == "♦") Color(0xFFE53935) else Color(0xFFDCDCDC),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = suitRotation * spinFactor
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Info Tip Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .background(Color(0x2214301B), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x33E2C974), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentTip,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(300))
                    },
                    label = "TipTransition"
                ) { tip ->
                    Text(
                        text = tip,
                        fontSize = 13.sp,
                        color = Color(0xFFD1E4FF),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        // Creator/Developer mention at the bottom of splash screen
        Text(
            text = "©Ilunya",
            fontSize = 12.sp,
            color = Color(0xFFE2C974).copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

// --- SCREEN: MAIN MENU ---
@Composable
fun MainMenuScreen(viewModel: DurakViewModel, statsList: List<GameStat>) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val musicVol by viewModel.musicVolume.collectAsStateWithLifecycle()
    val sfxVol by viewModel.sfxVolume.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(0) } // 0 = Sound, 1 = Language
    var showRoadmapDialog by remember { mutableStateOf(false) }
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
        // TOP: Dynamic Language Switching, Brand Glow & Roadmap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showRoadmapDialog = true },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("roadmap_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Roadmap",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    selectedLangTemp = appLanguage
                    showSettingsDialog = true
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = viewModel.getString("SETTINGS"),
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
                            text = if (appLanguage == AppLanguage.RU) "Матчей" else if (appLanguage == AppLanguage.IT) "Partite" else "Games",
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
                            text = if (appLanguage == AppLanguage.RU) "Побед" else if (appLanguage == AppLanguage.IT) "Vittorie" else "Winrate",
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
                    text = "0.2.3_01",
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
                                    text = when (viewModel.appLanguage.value) {
                                        AppLanguage.RU -> "Закрыть"
                                        AppLanguage.UA -> "Закрити"
                                        AppLanguage.IT -> "Chiudi"
                                        else -> "Close"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                if (showRoadmapDialog) {
                    AlertDialog(
                        onDismissRequest = { showRoadmapDialog = false },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = when (appLanguage) {
                                        AppLanguage.RU -> "Дорожная карта"
                                        AppLanguage.UA -> "Дорожня карта"
                                        AppLanguage.IT -> "Tabella di marcia"
                                        else -> "Future Roadmap"
                                    },
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val milestones = listOf(
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Май (0.1.x)"
                                            AppLanguage.UA -> "Травень (0.1.x)"
                                            AppLanguage.IT -> "Maggio (0.1.x)"
                                            else -> "May (0.1.x)"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Конструктор колоды и оптимизации интерфейса"
                                            AppLanguage.UA -> "Конструктор колоди та оптимізації інтерфейсу"
                                            AppLanguage.IT -> "Costruttore di mazzi e ottimizzazione dell'interfaccia"
                                            else -> "Custom deck builder & interface optimizations"
                                        },
                                        Icons.Default.Build
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "0.2 - Музыкальное обновление"
                                            AppLanguage.UA -> "0.2 - Музичне оновлення"
                                            AppLanguage.IT -> "0.2 - Aggiornamento audio"
                                            else -> "0.2 - Sound Update"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Релиз режима Переводной дурак, Добавление Переводного дурака в Мультиплеер, добавление в игру музыки и звуков, Полноценный выход Итальянского языка, начало Бета тестирования Украинского языка"
                                            AppLanguage.UA -> "Реліз режиму Перекладний дурак, додавання Перекладного дурака в Мультиплеєр, додавання в гру музики та звуків, Повноцінний вихід Італійської мови, початок Бета тестування Української мови"
                                            AppLanguage.IT -> "Rilascio della modalità Durak del Trasferimento, aggiunta del Durak del Trasferimento al Multiplayer, integrazione di musica ed effetti sonori, rilascio completo della localizzazione italiana e inizio del beta testing ucraino."
                                            else -> "Release of Transfer Durak mode, addition of Transfer Durak to Multiplayer, integration of music and sound effects, full release of the Italian localization, and start of Ukrainian beta testing."
                                        },
                                        Icons.Default.VolumeUp
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Июнь"
                                            AppLanguage.UA -> "Червень"
                                            AppLanguage.IT -> "Giugno"
                                            else -> "June"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Тестовые версии(0.3.x, 0.4.x, 0.5.x)"
                                            AppLanguage.UA -> "Тестові версії(0.3.x, 0.4.x, 0.5.x)"
                                            AppLanguage.IT -> "Versioni di test(0.3.x, 0.4.x, 0.5.x)"
                                            else -> "Testing versions(0.3.x, 0.4.x, 0.5.x)"
                                        },
                                        Icons.Default.BugReport
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Июль"
                                            AppLanguage.UA -> "Липень"
                                            AppLanguage.IT -> "Luglio"
                                            else -> "July"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Обновление интерфейса(0.6.x, 0.7.x)"
                                            AppLanguage.UA -> "Оновлення інтерфейсу(0.6.x, 0.7.x)"
                                            AppLanguage.IT -> "Aggiornamento dell'interfaccia(0.6.x, 0.7.x)"
                                            else -> "Interface design overhaul(0.6.x, 0.7.x)"
                                        },
                                        Icons.Default.Palette
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Август"
                                            AppLanguage.UA -> "Серпень"
                                            AppLanguage.IT -> "Agosto"
                                            else -> "August"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Подготовка к релизу(0.8, 0.9.x)"
                                            AppLanguage.UA -> "Підготовка до релізу(0.8, 0.9.x)"
                                            AppLanguage.IT -> "Preparazione al rilascio(0.8, 0.9.x)"
                                            else -> "Preparing for release(0.8, 0.9.x)"
                                        },
                                        Icons.Default.RocketLaunch
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Сентябрь"
                                            AppLanguage.UA -> "Вересень"
                                            AppLanguage.IT -> "Settembre"
                                            else -> "September"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Релиз(1.0) 🎉"
                                            AppLanguage.UA -> "Реліз(1.0) 🎉"
                                            AppLanguage.IT -> "Rilascio ufficiale(1.0) 🎉"
                                            else -> "Official Release(1.0) 🎉"
                                        },
                                        Icons.Default.Star
                                    )
                                )

                                val activeMilestones = listOf(
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Июнь"
                                            AppLanguage.UA -> "Червень"
                                            AppLanguage.IT -> "Giugno"
                                            else -> "June"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Версии, дорабатывающие базовую игру(0.3.x, 0.4.x, 0.5.x)"
                                            AppLanguage.UA -> "Версії, що доопрацьовують базову гру(0.3.x, 0.4.x, 0.5.x)"
                                            AppLanguage.IT -> "Versioni che perfezionano il gioco base(0.3.x, 0.4.x, 0.5.x)"
                                            else -> "Versions refactoring & polishing the base game(0.3.x, 0.4.x, 0.5.x)"
                                        },
                                        Icons.Default.BugReport
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                             AppLanguage.RU -> "0.3 - Многопользовательское обновление 📶"
                                             AppLanguage.UA -> "0.3 - Багатокористувацьке оновлення 📶"
                                             AppLanguage.IT -> "0.3 - Aggiornamento multigiocatore 📶"
                                             else -> "0.3 - Multiplayer Update 📶"
                                        },
                                        when (appLanguage) {
                                             AppLanguage.RU -> "В этом обновлении будет наконец-то доступна игра до 6 человек, быстрый чат во время игры, улучшение меню лобби."
                                             AppLanguage.UA -> "У цьому оновленні нарешті буде доступна гра до 6 осіб, швидкий чат під час гри, покращення меню лобі."
                                             AppLanguage.IT -> "Questo aggiornamento porterà finalmente il gioco fino a 6 giocatori, la chat rapida durante la partita e miglioramenti al menu della lobby."
                                             else -> "This update will finally bring play up to 6 players, quick chat during the game, and lobby menu improvements."
                                        },
                                        Icons.Default.Wifi
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Июль"
                                            AppLanguage.UA -> "Липень"
                                            AppLanguage.IT -> "Luglio"
                                            else -> "July"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Обновление интерфейса(0.6.x, 0.7.x)"
                                            AppLanguage.UA -> "Оновлення інтерфейсу(0.6.x, 0.7.x)"
                                            AppLanguage.IT -> "Aggiornamento dell'interfaccia(0.6.x, 0.7.x)"
                                            else -> "Interface design overhaul(0.6.x, 0.7.x)"
                                        },
                                        Icons.Default.Palette
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Август"
                                            AppLanguage.UA -> "Серпень"
                                            AppLanguage.IT -> "Agosto"
                                            else -> "August"
                                        },
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Подготовка к релизу(0.8, 0.9.x)"
                                            AppLanguage.UA -> "Підготовка до релізу(0.8, 0.9.x)"
                                            AppLanguage.IT -> "Preparazione al rilascio(0.8, 0.9.x)"
                                            else -> "Preparing for release(0.8, 0.9.x)"
                                        },
                                        Icons.Default.RocketLaunch
                                    ),
                                    Triple(
                                        when (appLanguage) {
                                            AppLanguage.RU -> "Сентябрь"
                                            AppLanguage.UA -> "Вересень"
                                            AppLanguage.IT -> "Settembre"
                                            else -> "September"
                                         },
                                         when (appLanguage) {
                                             AppLanguage.RU -> "Релиз(1.0) 🎉"
                                             AppLanguage.UA -> "Реліз(1.0) 🎉"
                                             AppLanguage.IT -> "Rilascio ufficiale(1.0) 🎉"
                                             else -> "Official Release(1.0) 🎉"
                                         },
                                         Icons.Default.Star
                                     )
                                 )
 
                                 activeMilestones.forEach { (month, task, icon) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = month,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = task,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showRoadmapDialog = false }) {
                                Text(
                                    text = when (viewModel.appLanguage.value) {
                                        AppLanguage.RU -> "Закрыть"
                                        AppLanguage.UA -> "Закрити"
                                        AppLanguage.IT -> "Chiudi"
                                        else -> "Close"
                                    },
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

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = viewModel.getString("SETTINGS"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Custom navigation tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tab 0: Sound
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (settingsTab == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { settingsTab = 0 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (settingsTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = viewModel.getString("SOUND_TAB"),
                                    color = if (settingsTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        // Tab 1: Language
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (settingsTab == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { settingsTab = 1 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (settingsTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = viewModel.getString("LANG_TAB"),
                                    color = if (settingsTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Content based on tab
                    if (settingsTab == 0) {
                        // Sound volume controls
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Music Volume
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = viewModel.getString("MUSIC_VOL"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(musicVol * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = musicVol,
                                    onValueChange = { viewModel.setMusicVolume(it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // SFX Volume
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = viewModel.getString("SFX_VOL"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(sfxVol * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = sfxVol,
                                    onValueChange = { viewModel.setSfxVolume(it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Language Picker
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val languages = listOf(
                                AppLanguage.EN to "English",
                                AppLanguage.RU to "Русский",
                                AppLanguage.IT to "Italiano",
                                AppLanguage.UA to "Українська (Beta)"
                            )
                            languages.forEach { (lang, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
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
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsTab == 1) {
                            viewModel.setLanguage(selectedLangTemp)
                        }
                        showSettingsDialog = false
                    }
                ) {
                    Text(
                        text = when (appLanguage) {
                            AppLanguage.RU -> "Подтвердить"
                            AppLanguage.UA -> "Підтвердити"
                            AppLanguage.IT -> "Conferma"
                            else -> "Confirm"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text(
                        text = when (appLanguage) {
                            AppLanguage.RU -> "Отмена"
                            AppLanguage.UA -> "Скасувати"
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
                text = if (appLanguage == AppLanguage.RU) "Режим игры" else if (appLanguage == AppLanguage.IT) "Modalità di gioco" else "Game mode",
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
                        text = if (appLanguage == AppLanguage.RU) "Классический" else if (appLanguage == AppLanguage.IT) "Classico" else "Classic",
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
                        text = if (appLanguage == AppLanguage.RU) "Переводной" else if (appLanguage == AppLanguage.IT) "Trasferimento" else "Passing",
                        color = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (appLanguage == AppLanguage.RU) "Выбор колоды" else if (appLanguage == AppLanguage.IT) "Mazzo" else "Deck Size",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val deckOption by viewModel.offlineDeckOption.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                listOf(
                    com.example.viewmodel.DurakViewModel.OfflineDeckOption.DECK_36 to "36",
                    com.example.viewmodel.DurakViewModel.OfflineDeckOption.DECK_52 to "52",
                    com.example.viewmodel.DurakViewModel.OfflineDeckOption.CUSTOM to (if (appLanguage == AppLanguage.RU) "Своя 🛠️" else if (appLanguage == AppLanguage.IT) "Pers. 🛠️" else "Custom 🛠️")
                ).forEach { (option, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (deckOption == option) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.setOfflineDeckOption(option) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (deckOption == option) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (deckOption == com.example.viewmodel.DurakViewModel.OfflineDeckOption.CUSTOM) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.navigateTo(com.example.viewmodel.DurakViewModel.Screen.CUSTOM_DECK) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Настроить колоду" else if (appLanguage == AppLanguage.IT) "Configura mazzo" else "Configure Custom Deck",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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
                        text = if (subMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) "TRANSFER RULES" else if (isHard) viewModel.getString("BOT_AI_TITLE") else viewModel.getString("BOT_DECENT_TITLE"),
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
                            } else if (appLanguage == AppLanguage.IT) {
                                "Regole del trasferimento applicate! Tu o il bot potete trasferire il turno di difesa accoppiando il valore della carta d'attacco. Il bot è ottimizzato per trasferire in modo tattico!"
                            } else {
                                "Transfer rules applied! You or the bot can transfer defending duty by matching the rank of the attacking cards. The bot is fully optimized to transfer tactically!"
                            }
                        } else if (isHard) {
                            viewModel.getString("BOT_AI_DESC")
                        } else {
                            viewModel.getString("BOT_DECENT_DESC")
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
                        text = if (appLanguage == AppLanguage.RU) "Раздать лобби" else if (appLanguage == AppLanguage.IT) "Crea Stanza" else "Host Match",
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
                        text = if (appLanguage == AppLanguage.RU) "Войти в лобби" else if (appLanguage == AppLanguage.IT) "Cerca / Entra" else "Search/Join",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentNickname by viewModel.playerNickname.collectAsStateWithLifecycle()
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "nickname",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    OutlinedTextField(
                        value = currentNickname,
                        onValueChange = { viewModel.setPlayerNickname(it) },
                        maxLines = 1,
                        singleLine = true,
                        label = {
                            Text(
                                text = if (appLanguage == AppLanguage.RU) "Ваш никнейм" 
                                       else if (appLanguage == AppLanguage.UA) "Ваш нікнейм" 
                                       else if (appLanguage == AppLanguage.IT) "Tuo Nickname" 
                                       else "Your Nickname",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        val selectedDeckOption by viewModel.mpLobbyDeckOption.collectAsStateWithLifecycle()

                        // Multiplayer Game mode configuration block
                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Режим игры" else if (appLanguage == AppLanguage.IT) "Modalità di gioco" else "Game mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val mpSubMode by viewModel.mpLobbySubMode.collectAsStateWithLifecycle()
                        val optionModeEnabled = networkState != MultiplayerManager.State.HOSTING

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (mpSubMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(enabled = optionModeEnabled) { viewModel.setMpLobbySubMode(com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (appLanguage == AppLanguage.RU) "Классический" else if (appLanguage == AppLanguage.IT) "Classico" else "Classic",
                                    color = if (mpSubMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.CLASSIC) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (mpSubMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(enabled = optionModeEnabled) { viewModel.setMpLobbySubMode(com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (appLanguage == AppLanguage.RU) "Переводной" else if (appLanguage == AppLanguage.IT) "Trasferimento" else "Passing",
                                    color = if (mpSubMode == com.example.viewmodel.DurakViewModel.OfflineSubMode.TRANSFER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (appLanguage == AppLanguage.RU) "Размер колоды" else if (appLanguage == AppLanguage.IT) "Dimensione mazzo" else "Deck Size",
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
                            val optionDeckEnabled = networkState != MultiplayerManager.State.HOSTING
                            listOf(
                                com.example.viewmodel.DurakViewModel.OfflineDeckOption.DECK_36 to "36",
                                com.example.viewmodel.DurakViewModel.OfflineDeckOption.DECK_52 to "52",
                                com.example.viewmodel.DurakViewModel.OfflineDeckOption.CUSTOM to (if (appLanguage == AppLanguage.RU) "Своя 🛠️" else if (appLanguage == AppLanguage.IT) "Pers. 🛠️" else "Custom 🛠️")
                            ).forEach { (option, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedDeckOption == option) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable(enabled = optionDeckEnabled) { viewModel.setMpLobbyDeckOption(option) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selectedDeckOption == option) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (selectedDeckOption == com.example.viewmodel.DurakViewModel.OfflineDeckOption.CUSTOM) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.navigateTo(com.example.viewmodel.DurakViewModel.Screen.CUSTOM_DECK) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.RU) "Настроить колоду" else if (appLanguage == AppLanguage.IT) "Configura mazzo" else "Configure Custom Deck",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val noticeText = if (appLanguage == AppLanguage.RU) {
                            "Больше двух игроков в разработке"
                        } else if (appLanguage == AppLanguage.IT) {
                            "Più di due giocatori in sviluppo"
                        } else {
                            "More than two players in development"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "info",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = noticeText,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
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
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val isTransferEnabled = snapshot.isTransferMode
    val canPlayerTransferNow = isTransferEnabled && 
            snapshot.tablePairs.isNotEmpty() && 
            snapshot.tablePairs.all { it.defenseCard == null } &&
            (if (activeMode == GameMode.ONLINE_CLIENT) snapshot.attackerPlayerId == "player" else snapshot.attackerPlayerId == "opponent") &&
            snapshot.opponentHandSize >= snapshot.tablePairs.size + 1

    var showLogs by remember { mutableStateOf(false) }

    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cardPositions = remember { mutableStateMapOf<String, Offset>() }
    val tableCardBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var activeDraggedCard by remember { mutableStateOf<Card?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(snapshot.tablePairs) {
        tableCardBounds.clear()
    }

    val animatedDragOffsetX by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetX else 0f,
        animationSpec = if (isDragging) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        },
        label = "DragX"
    )
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetY else 0f,
        animationSpec = if (isDragging) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        },
        finishedListener = { value ->
            if (value == 0f && !isDragging) {
                activeDraggedCard = null
            }
        },
        label = "DragY"
    )

    var transferZoneBounds by remember { mutableStateOf<Rect?>(null) }
    var handScrollOffsetPx by remember { mutableStateOf(0f) }

    val lateGameActive = snapshot.deckSize == 0
    val bgColors = if (lateGameActive) {
        listOf(Color(0xFF2C1612), Color(0xFF0E0706)) // Deep warm embers for the late game climax
    } else {
        listOf(Color(0xFF1E2124), Color(0xFF0F1113)) // Classic slate
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = bgColors,
                    center = Offset(500f, 300f),
                    radius = 1200f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .onGloballyPositioned { rootLayoutCoordinates = it }
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
                        val tableLayoutItems = remember(snapshot.tablePairs, canPlayerTransferNow) {
                            val items = mutableListOf<TableFlowItem>()
                            snapshot.tablePairs.forEach { items.add(TableFlowItem.PairItem(it)) }
                            if (canPlayerTransferNow) {
                                items.add(TableFlowItem.TransferItem)
                            }
                            items
                        }

                        val chunkedRows = tableLayoutItems.chunked(3)

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (rowItems in chunkedRows) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (item in rowItems) {
                                            when (item) {
                                                is TableFlowItem.PairItem -> {
                                                    val pair = item.pair
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
                                                                .onGloballyPositioned { coordinates ->
                                                                    rootLayoutCoordinates?.let { root ->
                                                                        if (root.isAttached && coordinates.isAttached) {
                                                                            val localOffset = root.localPositionOf(coordinates, Offset.Zero)
                                                                            val size = coordinates.size
                                                                            tableCardBounds[pair.attackCard.id] = androidx.compose.ui.geometry.Rect(
                                                                                localOffset.x,
                                                                                localOffset.y,
                                                                                localOffset.x + size.width,
                                                                                localOffset.y + size.height
                                                                            )
                                                                        }
                                                                    }
                                                                }
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
                                                is TableFlowItem.TransferItem -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(92.dp, 124.dp)
                                                            .padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(76.dp, 108.dp)
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .background(Color(0xFF3B1A1E))
                                                                .border(
                                                                    width = 1.5.dp,
                                                                    brush = Brush.linearGradient(listOf(Color(0xFFFF5D5D), Color(0xFFFF3333))),
                                                                    shape = RoundedCornerShape(14.dp)
                                                                )
                                                                .onGloballyPositioned { coordinates ->
                                                                    rootLayoutCoordinates?.let { root ->
                                                                        if (root.isAttached && coordinates.isAttached) {
                                                                            val rootPos = root.localPositionOf(coordinates, Offset.Zero)
                                                                            transferZoneBounds = Rect(
                                                                                left = rootPos.x,
                                                                                top = rootPos.y,
                                                                                right = rootPos.x + coordinates.size.width,
                                                                                bottom = rootPos.y + coordinates.size.height
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                modifier = Modifier.padding(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.SwapHoriz,
                                                                    contentDescription = "Transfer Zone",
                                                                    tint = Color(0xFFFF8282),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = if (appLanguage == AppLanguage.RU) "ПАС /\nПЕРЕВОД" else if (appLanguage == AppLanguage.IT) "TRASFERISCI" else "TRANSFER\nZONE",
                                                                    color = Color(0xFFFFB4B4),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    textAlign = TextAlign.Center,
                                                                    lineHeight = 11.sp
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
                            val trumpLabel = if (appLanguage == AppLanguage.RU) snapshot.trumpSuit.ruLabel else if (appLanguage == AppLanguage.IT) snapshot.trumpSuit.itLabel else snapshot.trumpSuit.enLabel
                            Text(
                                text = "${viewModel.getString("TRUMP")}: ${snapshot.trumpSuit.symbol} ($trumpLabel)",
                                color = if (snapshot.trumpSuit.colorRed) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
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

                        if (false) Row(
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
                    .background(Color(0xFF1A1C1E), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
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
                    text = if (snapshot.isDefenderTaking) {
                        if (snapshot.isLocalTurn) {
                            when (appLanguage) {
                                AppLanguage.RU -> "СОПЕРНИК БЕРЕТ КАРТЫ (ДОКИНУТЬ)"
                                AppLanguage.UA -> "СУПЕРНИК БЕРЕ КАРТИ (ДОКИНУТИ)"
                                AppLanguage.IT -> "L'AVVERSARIO PRENDE LE CARTE (SCARTA)"
                                else -> "OPPONENT IS TAKING (TOSS CARDS)"
                            }
                        } else {
                            when (appLanguage) {
                                AppLanguage.RU -> "ВЫ БЕРЕТЕ КАРТЫ (ОЖИДАНИЕ)"
                                AppLanguage.UA -> "ВИ БЕРЕТЕ КАРТИ (ОЧІКУВАННЯ)"
                                AppLanguage.IT -> "PRENDI LE CARTE (ATTESA)"
                                else -> "YOU ARE TAKING (WAITING)"
                            }
                        }
                    } else if (snapshot.isLocalTurn) {
                        if (appLanguage == AppLanguage.RU) "ВАШ ХОД" 
                        else if (appLanguage == AppLanguage.UA) "ВАШ ХІД"
                        else if (appLanguage == AppLanguage.IT) "TUO TURNO" 
                        else "YOUR TURN"
                    } else {
                        val isMultiplayer = viewModel.activeMode.value != GameMode.OFFLINE
                        val opponentNick = snapshot.opponentName
                        if (appLanguage == AppLanguage.RU) "ХОД: ${if (isMultiplayer) opponentNick.uppercase() else "БОТ"}"
                        else if (appLanguage == AppLanguage.UA) "ХІД: ${if (isMultiplayer) opponentNick.uppercase() else "БОТ"}"
                        else if (appLanguage == AppLanguage.IT) "TURNO DI: ${if (isMultiplayer) opponentNick.uppercase() else "BOT"}"
                        else "TURN: ${if (isMultiplayer) opponentNick.uppercase() else "BOT"}"
                    },
                    color = if (snapshot.isLocalTurn) Color(0xFFD1E4FF) else Color(0xFF909094),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val sortedHand = remember(snapshot.localHand, snapshot.trumpSuit) {
                    val (trumps, nonTrumps) = snapshot.localHand.partition { it.suit == snapshot.trumpSuit }
                    val sortedNonTrumps = nonTrumps.sortedWith(
                        compareBy<Card> { it.suit.ordinal }
                            .thenBy { it.rank.value }
                    )
                    val sortedTrumps = trumps.sortedBy { it.rank.value }
                    sortedNonTrumps + sortedTrumps
                }

                // 5. PLAYER CARDS HAND: Custom fanned horizontal deck inside footer with real physics drag and drop
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val containerWidth = maxWidth
                    val density = LocalDensity.current
                    val containerWidthPx = with(density) { containerWidth.toPx() }

                    val cardWidth = 74.dp
                    val cardHeight = 110.dp
                    val cardWidthPx = with(density) { cardWidth.toPx() }

                    // We overlap cards beautifully.
                    val cardSpacing = 32.dp
                    val cardSpacingPx = with(density) { cardSpacing.toPx() }

                    val totalDeckWidthPx = if (sortedHand.isNotEmpty()) {
                        (sortedHand.size - 1) * cardSpacingPx + cardWidthPx
                    } else 0f

                    val minScroll = if (totalDeckWidthPx > containerWidthPx) {
                        -(totalDeckWidthPx - containerWidthPx) - 60f
                    } else 0f
                    val maxScroll = if (totalDeckWidthPx > containerWidthPx) {
                        60f
                    } else 0f

                    // Automatically fit scroll within bounds if deck size changes
                    LaunchedEffect(sortedHand.size) {
                        handScrollOffsetPx = handScrollOffsetPx.coerceIn(minScroll, maxScroll)
                    }

                    // Centering of the deck if too small for container
                    val deckStartX = if (totalDeckWidthPx <= containerWidthPx) {
                        (containerWidthPx - totalDeckWidthPx) / 2f
                    } else {
                        0f
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        sortedHand.forEachIndexed { index, card ->
                            val isThisCardDragged = (activeDraggedCard?.id == card.id)

                            // Position and rotation math
                            val cardStaticXPx = deckStartX + handScrollOffsetPx + index * cardSpacingPx
                            val cardCenterX = cardStaticXPx + cardWidthPx / 2f
                            val containerCenterX = containerWidthPx / 2f
                            val distanceFromCenter = cardCenterX - containerCenterX
                            
                            val maxRange = containerWidthPx / 1.6f
                            val normalized = (distanceFromCenter / maxRange).coerceIn(-1.1f, 1.1f)

                            val rotationAngle = normalized * 15f
                            val downwardArcPx = normalized * normalized * with(density) { 15.dp.toPx() }

                            var localDragOffsetX by remember(card.id) { mutableStateOf(0f) }
                            var localDragOffsetY by remember(card.id) { mutableStateOf(0f) }
                            var isDraggingThisCardUp by remember(card.id) { mutableStateOf(false) }

                            CardComponent(
                                card = card,
                                faceUp = true,
                                onClick = null,
                                modifier = Modifier
                                    .size(cardWidth, cardHeight)
                                    .offset {
                                        IntOffset(
                                            x = cardStaticXPx.toInt(),
                                            y = downwardArcPx.toInt()
                                        )
                                    }
                                    .graphicsLayer {
                                        alpha = if (isThisCardDragged) 0f else 1f
                                        rotationZ = rotationAngle
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        rootLayoutCoordinates?.let { root ->
                                            if (root.isAttached && coordinates.isAttached) {
                                                cardPositions[card.id] = root.localPositionOf(coordinates, Offset.Zero)
                                            }
                                        }
                                    }
                                    .pointerInput(card.id) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                localDragOffsetX = 0f
                                                localDragOffsetY = 0f
                                                isDraggingThisCardUp = false
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (!isDraggingThisCardUp) {
                                                    localDragOffsetX += dragAmount.x
                                                    localDragOffsetY += dragAmount.y

                                                    if (localDragOffsetY < -30f && kotlin.math.abs(localDragOffsetY) > kotlin.math.abs(localDragOffsetX)) {
                                                        isDraggingThisCardUp = true
                                                        activeDraggedCard = card
                                                        isDragging = true
                                                        dragOffsetX = localDragOffsetX
                                                        dragOffsetY = localDragOffsetY
                                                        change.consume()
                                                    } else if (kotlin.math.abs(localDragOffsetX) > 6f) {
                                                        handScrollOffsetPx = (handScrollOffsetPx + dragAmount.x).coerceIn(minScroll, maxScroll)
                                                        change.consume()
                                                    }
                                                } else {
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                    change.consume()
                                                }
                                            },
                                            onDragEnd = {
                                                if (isDraggingThisCardUp) {
                                                    val currentDensity = this.density
                                                    val dragYDp = dragOffsetY / currentDensity

                                                    val origPos = cardPositions[card.id] ?: Offset.Zero
                                                    val currentCardX = origPos.x + dragOffsetX
                                                    val currentCardY = origPos.y + dragOffsetY

                                                    val cardWidthPx = with(currentDensity) { cardWidth.toPx() }
                                                    val cardHeightPx = with(currentDensity) { cardHeight.toPx() }
                                                    val centerX = currentCardX + cardWidthPx / 2f
                                                    val centerY = currentCardY + cardHeightPx / 2f

                                                    val isOverTransferZone = canPlayerTransferNow && transferZoneBounds?.let { bounds ->
                                                        currentCardX >= bounds.left - 80 && currentCardX <= bounds.right + 80 &&
                                                        currentCardY >= bounds.top - 120 && currentCardY <= bounds.bottom + 120
                                                    } ?: false

                                                    // Find if the dragged card is dropped on any undefended attack card on the table
                                                    val overlappedAttackCardId = tableCardBounds.entries.filter { entry ->
                                                        snapshot.tablePairs.any { it.attackCard.id == entry.key && it.defenseCard == null }
                                                    }.find { entry ->
                                                        val rect = entry.value
                                                        centerX >= rect.left - 40 && centerX <= rect.right + 40 &&
                                                        centerY >= rect.top - 60 && centerY <= rect.bottom + 60
                                                    }?.key

                                                    val targetAttackCard = if (overlappedAttackCardId != null) {
                                                        snapshot.tablePairs.find { it.attackCard.id == overlappedAttackCardId }?.attackCard
                                                    } else null

                                                    if (isOverTransferZone) {
                                                        viewModel.playCard(card, intentTransferOnly = true)
                                                        isDragging = false
                                                        activeDraggedCard = null
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                    } else if (targetAttackCard != null) {
                                                        viewModel.playCard(card, targetAttackCard = targetAttackCard)
                                                        isDragging = false
                                                        activeDraggedCard = null
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                    } else if (dragYDp < -100f) {
                                                        viewModel.playCard(card)
                                                        isDragging = false
                                                        activeDraggedCard = null
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                    } else {
                                                        isDragging = false
                                                    }
                                                }
                                                isDraggingThisCardUp = false
                                            },
                                            onDragCancel = {
                                                if (isDraggingThisCardUp) {
                                                    isDragging = false
                                                    activeDraggedCard = null
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                                isDraggingThisCardUp = false
                                            }
                                        )
                                    }
                            )
                        }
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



        if (activeDraggedCard != null) {
            val origPos = cardPositions[activeDraggedCard!!.id] ?: Offset.Zero
            val floatingX = origPos.x + animatedDragOffsetX
            val floatingY = origPos.y + animatedDragOffsetY

            Box(
                modifier = Modifier
                    .offset { IntOffset(floatingX.roundToInt(), floatingY.roundToInt()) }
                    .zIndex(1000f)
            ) {
                CardComponent(
                    card = activeDraggedCard!!,
                    faceUp = true,
                    onClick = null,
                    modifier = Modifier
                        .size(74.dp, 110.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

// --- CARD RENDERING COMPONENT (Plastic style deck design) ---
@Composable
fun CardComponent(
    card: Card,
    faceUp: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var animTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animTriggered = true
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animTriggered) 1f else 0.5f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
        ),
        label = "CardScale"
    )

    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animTriggered) 0f else 60f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
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
                .let { if (onClick != null) it.clickable { onClick() } else it }
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
            .let { if (onClick != null) it.clickable { onClick() } else it }
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
                                    val oppPrefix = if (appLanguage == AppLanguage.RU) "Оппонент" else if (appLanguage == AppLanguage.IT) "Avversario" else "Opponent"
                                    val modePrefix = if (appLanguage == AppLanguage.RU) "Режим" else if (appLanguage == AppLanguage.IT) "Modalità" else "Mode"
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
                                        "WON" -> if (appLanguage == AppLanguage.RU) "ПОБЕДА" else if (appLanguage == AppLanguage.IT) "VITTORIA" else "WON"
                                        "LOST" -> if (appLanguage == AppLanguage.RU) "ПОРАЖЕНИЕ" else if (appLanguage == AppLanguage.IT) "SCONFITTA" else "LOST"
                                        else -> if (appLanguage == AppLanguage.RU) "НИЧЬЯ" else if (appLanguage == AppLanguage.IT) "PAREGGIO" else "DRAW"
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
                                        text = if (appLanguage == AppLanguage.RU) "Ход игры:" else if (appLanguage == AppLanguage.IT) "Cronologia Battaglia:" else "Battle History:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    if (logLines.isEmpty()) {
                                        Text(
                                            text = if (appLanguage == AppLanguage.RU) "Логи отсутствуют для этого матча" else if (appLanguage == AppLanguage.IT) "Nessun registro per questa partita" else "No logs captured for this game",
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

@Composable
fun CustomDeckSelectionScreen(viewModel: DurakViewModel) {
    val currentSelectedCards by viewModel.customDeckIds.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val fullStandardDeck = remember {
        val list = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                list.add(Card(suit, rank))
            }
        }
        list.sortedWith(compareBy({ it.suit }, { it.rank.value }))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP: One UI 8.x Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.OFFLINE_SETUP) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (appLanguage == AppLanguage.RU) "Конструктор колоды" else if (appLanguage == AppLanguage.IT) "Mazzo personalizzato" else "Custom Deck Builder",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (appLanguage == AppLanguage.RU) "Выберите карты для игры" else if (appLanguage == AppLanguage.IT) "Seleziona le carte per giocare" else "Choose the cards to play with",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CENTER: The Overlapping Card Carousel occupying the center strictly
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center
            ) {
                val parentMaxWidth = maxWidth
                val viewportWidthPx = with(LocalDensity.current) { parentMaxWidth.toPx() }
                val cardWidthDp = 90.dp
                val cardHeightDp = 140.dp
                val overlapDp = (-54).dp
                val scrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                        .testTag("custom_deck_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(overlapDp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Spacer(modifier = Modifier.width(parentMaxWidth / 2 - cardWidthDp / 2))
                    fullStandardDeck.forEachIndexed { index, card ->
                        val isSelected = currentSelectedCards.contains(card.id)

                        var cardDragOffsetY by remember(card.id) { mutableStateOf(0f) }
                        var isDraggingCard by remember(card.id) { mutableStateOf(false) }

                        val animatedDragOffsetY by animateFloatAsState(
                            targetValue = if (isDraggingCard) cardDragOffsetY else 0f,
                            animationSpec = if (isDraggingCard) androidx.compose.animation.core.snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
                            label = "CardDragY"
                        )

                        val density = LocalDensity.current
                        val cardWidthPx = with(density) { cardWidthDp.toPx() }
                        val overlapPx = with(density) { overlapDp.toPx() }

                        val cardCenterPosInContent = index * (cardWidthPx + overlapPx) + cardWidthPx / 2f
                        val viewportCenterInContent = scrollState.value + viewportWidthPx / 2f
                        val delta = cardCenterPosInContent - viewportCenterInContent
                        val normalizedDiff = (delta / (viewportWidthPx / 1.8f)).coerceIn(-1.5f, 1.5f)

                        val rotationAngle = normalizedDiff * 25f
                        val downwardArc = (normalizedDiff * normalizedDiff * 35f).dp
                        val selectedLift = if (isSelected) (-28).dp else 0.dp

                        Box(
                            modifier = Modifier
                                .size(cardWidthDp, cardHeightDp)
                                .graphicsLayer {
                                    rotationZ = rotationAngle
                                    translationY = downwardArc.toPx() + selectedLift.toPx() + animatedDragOffsetY
                                    val scale = if (isSelected) 1.05f else 0.92f
                                    scaleX = scale
                                    scaleY = scale
                                    shadowElevation = if (isSelected) 12.dp.toPx() else 3.dp.toPx()
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(
                                    width = if (isSelected) 3.dp else 1.5.dp,
                                    brush = if (isSelected) {
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.15f),
                                                Color.Black.copy(alpha = 0.05f)
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .pointerInput(card.id) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isDraggingCard = true
                                            cardDragOffsetY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            cardDragOffsetY += dragAmount
                                        },
                                        onDragEnd = {
                                            isDraggingCard = false
                                            if (cardDragOffsetY < -140f) {
                                                viewModel.toggleCustomDeckCard(card.id)
                                            }
                                            cardDragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            isDraggingCard = false
                                            cardDragOffsetY = 0f
                                        }
                                    )
                                }
                                .clickable {
                                    viewModel.toggleCustomDeckCard(card.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val displayColor = if (card.suit.colorRed) SuitRed else SuitBlack

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.align(Alignment.TopStart),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = card.rank.symbol,
                                        color = displayColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        text = card.suit.symbol,
                                        color = displayColor,
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp
                                    )
                                }

                                Text(
                                    text = card.suit.symbol,
                                    color = displayColor.copy(alpha = 0.12f),
                                    fontSize = 54.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .align(Alignment.TopEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(parentMaxWidth / 2 - cardWidthDp / 2))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BOTTOM: Status and Action controllers
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Карт выбрано: ${currentSelectedCards.size}" else if (appLanguage == AppLanguage.IT) "Carte selezionate: ${currentSelectedCards.size}" else "Selected: ${currentSelectedCards.size} cards",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.RU) "Необходимо минимум 12 карт для полноценного раунда" else if (appLanguage == AppLanguage.IT) "Minimo 12 carte necessarie per giocare" else "Requires at least 12 cards to host a proper round.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = { viewModel.navigateTo(DurakViewModel.Screen.OFFLINE_SETUP) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("confirm_custom_deck_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = if (appLanguage == AppLanguage.RU) "Подтвердить колоду" else if (appLanguage == AppLanguage.IT) "Conferma mazzo" else "Confirm Deck Configuration",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
