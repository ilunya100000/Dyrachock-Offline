package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.GameStat
import com.example.database.GameStatRepository
import com.example.engine.DurakEngine
import com.example.model.*
import com.example.network.MultiplayerManager
import com.example.network.NetworkProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppLanguage { EN, RU, IT }

class DurakViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = GameStatRepository(db.gameStatDao())

    val gameHistory = repository.allStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val engine = DurakEngine()
    private val multiplayerManager = MultiplayerManager(application)

    // Current screen
    enum class Screen {
        MAIN_MENU,
        OFFLINE_SETUP,
        MULTIPLAYER_HUB,
        GAME_TABLE,
        STATS_BOARD,
        CUSTOM_DECK
    }

    enum class OfflineDeckOption {
        DECK_36,
        DECK_52,
        CUSTOM
    }

    private val _offlineDeckOption = MutableStateFlow(OfflineDeckOption.DECK_36)
    val offlineDeckOption = _offlineDeckOption.asStateFlow()

    private val _customDeckIds = MutableStateFlow<Set<String>>(
        Suit.values().flatMap { s ->
            Rank.values().filter { r -> r.value >= 6 }.map { r -> "${s.name}_${r.name}" }
        }.toSet()
    )
    val customDeckIds = _customDeckIds.asStateFlow()

    fun setOfflineDeckOption(option: OfflineDeckOption) {
        _offlineDeckOption.value = option
    }

    fun toggleCustomDeckCard(cardId: String) {
        val current = _customDeckIds.value.toMutableSet()
        if (current.contains(cardId)) {
            // Guarantee there's at least 12 cards to play, otherwise it's invalid
            if (current.size > 12) {
                current.remove(cardId)
            }
        } else {
            current.add(cardId)
        }
        _customDeckIds.value = current
    }

    private val _currentScreen = MutableStateFlow(Screen.MAIN_MENU)
    val currentScreen = _currentScreen.asStateFlow()

    // Preferences & Settings
    private val _appLanguage = MutableStateFlow(AppLanguage.EN)
    val appLanguage = _appLanguage.asStateFlow()

    private val _isBotHard = MutableStateFlow(false)
    val isBotHard = _isBotHard.asStateFlow()

    // Rendered game state
    private val _gameState = MutableStateFlow<GameStateSnapshot>(GameStateSnapshot())
    val gameState = _gameState.asStateFlow()

    private var hasPersistedThisGame = false

    // Multiplayer properties
    val networkState = multiplayerManager.connectionState
    val discoveredHosts = multiplayerManager.discoveredHosts
    val localIp = multiplayerManager.localIpAddress

    // Active mode (Offline vs Host vs Client)
    private val _activeMode = MutableStateFlow(GameMode.OFFLINE)
    val activeMode = _activeMode.asStateFlow()

    // Bot move lock to avoid triple actions during animated delay
    private val _botThinking = MutableStateFlow(false)
    val botThinking = _botThinking.asStateFlow()

    // MP Lobby Custom Configuration
    private val _mpLobbyDeckSize = MutableStateFlow(36)
    val mpLobbyDeckSize = _mpLobbyDeckSize.asStateFlow()

    private val _mpLobbyPlayersCount = MutableStateFlow(2)
    val mpLobbyPlayersCount = _mpLobbyPlayersCount.asStateFlow()

    fun setMpLobbyDeckSize(size: Int) {
        _mpLobbyDeckSize.value = size
    }

    enum class OfflineSubMode {
        CLASSIC,
        TRANSFER
    }

    private val _offlineSubMode = MutableStateFlow(OfflineSubMode.CLASSIC)
    val offlineSubMode = _offlineSubMode.asStateFlow()

    fun setOfflineSubMode(subMode: OfflineSubMode) {
        _offlineSubMode.value = subMode
    }

    fun setMpLobbyPlayersCount(count: Int) {
        _mpLobbyPlayersCount.value = count
    }

    init {
        // Monitor socket incoming network actions
        viewModelScope.launch {
            multiplayerManager.incomingMessages.collect { rawMsg ->
                if (rawMsg != null) {
                    handleNetworkMessage(rawMsg)
                    multiplayerManager.clearReceivedMessage()
                }
            }
        }

        // Monitor connection drops
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.DISCONNECTED && _currentScreen.value == Screen.GAME_TABLE) {
                    _gameState.value = _gameState.value.copy(
                        matchStatus = MatchStatus.PLAYER_DISCONNECTED,
                        gameLog = _gameState.value.gameLog + "Multiplayer connection lost!"
                    )
                }
            }
        }
    }

    // Dynamic runtime translation maps
    private val enTranslations = mapOf(
        "APP_TITLE" to "Dyrachok Offline",
        "PLAY_OFFLINE" to "Play Offline",
        "PLAY_ONLINE" to "Local Multiplayer",
        "BOT_SETUP_TITLE" to "Offline Match",
        "DIFFICULTY" to "Bot Difficulty",
        "EASY" to "Easy (Random)",
        "HARD" to "Hard (Analytical)",
        "START_GAME" to "Start Game",
        "P2P_TITLE" to "Local Online",
        "NSD_STATUS" to "Wi-Fi Hub Discovery",
        "HOST_LOBBY" to "Host a Lobby",
        "MY_IP" to "Your Host IP:",
        "DISCOVERY_ACTIVE" to "Searching for local hosts...",
        "TAP_TO_CONNECT" to "Tap to Connect",
        "MANUAL_CONNECT" to "Direct IP Connection",
        "ENTER_HOST_IP" to "Enter Host IP address",
        "CONNECT_BTN" to "Connect",
        "WAITING_LOBBY" to "Lobby opened. Waiting for player...",
        "DISCONNECTED" to "Disconnected",
        "CONNECTING" to "Connecting...",
        "BACK" to "Back",
        "TAKE" to "Take All",
        "BITO" to "Bito / Pass",
        "RESTART" to "Play Again",
        "MENU" to "Main Menu",
        "DECK" to "Deck",
        "TRUMP" to "Trump",
        "STATS_TITLE" to "Match Archives",
        "CLEAR_STATS" to "Clear Archives",
        "EMPTY_STATS" to "No history recorded yet.",
        "GAME_LOGS" to "Battle Log",
        "TURN_PLAYER" to "Your Turn",
        "TURN_BOT" to "Bot Thinking...",
        "TURN_OPPONENT" to "Opponent's Turn",
        "WIN_TITLE" to "VICTORY!",
        "LOST_TITLE" to "DEFEAT! YOU ARE THE DURAK!",
        "DRAW_TITLE" to "DRAW GAME!",
        "DISC_TITLE" to "Opponent disconnected!",
        "STATUS_TITLE_LABEL" to "Animation Update",
        "CHANGELOG_BTN" to "Changelog",
        "CHANGELOG_TITLE" to "Version Changelog",
        "CHANGELOG_TEXT" to "Version 0.1.2 (Patch)\n\n• Future Roadmap Tab: Added an interactive timeline showing future releases directly from the main menu top-left map icon.\n• High-Performance Overlapping Deck Selection Screen: Fully customized deck builder featuring overlapping cards, responsive fling gestures, and a modern Material 3/One UI 8.x layout.\n• Transfer Mode Container Wrap: Resolved grid squeezing on defensive transfer indicators by integrating the flow container alongside table item groups.\n• Drag overlay refinement: Kept dragging cards physically on top of background borders and overlay dialogue stacks."
    )

    private val ruTranslations = mapOf(
        "APP_TITLE" to "Дурачок Оффлайн",
        "PLAY_OFFLINE" to "Офлайн Игра",
        "PLAY_ONLINE" to "Мультиплеер (Wi-Fi)",
        "BOT_SETUP_TITLE" to "Офлайн Настройки",
        "DIFFICULTY" to "Сложность Бота",
        "EASY" to "Легкий (Случайный)",
        "HARD" to "Сложный (Аналитик)",
        "START_GAME" to "Начать Игру",
        "P2P_TITLE" to "Локальная сеть",
        "NSD_STATUS" to "Поиск по Wi-Fi",
        "HOST_LOBBY" to "Создать Лобби",
        "MY_IP" to "Ваш IP Хоста:",
        "DISCOVERY_ACTIVE" to "Поиск хостов в сети...",
        "TAP_TO_CONNECT" to "Нажмите для подключения",
        "MANUAL_CONNECT" to "Подключение по IP",
        "ENTER_HOST_IP" to "Введите IP адрес хоста",
        "CONNECT_BTN" to "Войти",
        "WAITING_LOBBY" to "Лобби открыто. Ожидание игрока...",
        "DISCONNECTED" to "Отключено",
        "CONNECTING" to "Подключение...",
        "BACK" to "Назад",
        "TAKE" to "Взять Все",
        "BITO" to "Бито / Пас",
        "RESTART" to "Играть Снова",
        "MENU" to "Главное Меню",
        "DECK" to "Колода",
        "TRUMP" to "Козырь",
        "STATS_TITLE" to "Архив Матчей",
        "CLEAR_STATS" to "Очистить Архив",
        "EMPTY_STATS" to "История матчей отсутствует.",
        "GAME_LOGS" to "Лог Битвы",
        "TURN_PLAYER" to "Ваш Ход",
        "TURN_BOT" to "Бот думает...",
        "TURN_OPPONENT" to "Ход Оппонента",
        "WIN_TITLE" to "ПОБЕДА!",
        "LOST_TITLE" to "ПОРАЖЕНИЕ! ВЫ ДУРАК!",
        "DRAW_TITLE" to "НИЧЬЯ!",
        "DISC_TITLE" to "Игрок отключился!",
        "STATUS_TITLE_LABEL" to "Анимационное обновление",
        "CHANGELOG_BTN" to "Изменения",
        "CHANGELOG_TITLE" to "История изменений",
        "CHANGELOG_TEXT" to "Версия 0.1.2 (Патч)\n\n• Дорожная Карта Будущего: Добавлена интерактивная вкладка Roadmap на главном экране (иконка карты на панели слева вверху), показывающая этапы развития игры.\n• Настройка Пользовательской Колоды: Создание индивидуальной игровой колоды в горизонтальной карусели со стильным эффектом перекрытия карт, тактильным откликом в духе One UI 8.x.\n• Исправление Зоны Перевода: Устранено сжатие стола при переводе/пассе карт. Кнопка перевода теперь интегрирована прямо в сетку стола и оборачивается вместе с активными картами.\n• Плавное взаимодействие: Улучшены слои поверхностей при перетаскивании со стабильной физикой возврата."
    )

    private val itTranslations = mapOf(
        "CHANGELOG_BTN" to "Registro",
        "CHANGELOG_TITLE" to "Registro Modifiche (Beta)",
        "CHANGELOG_TEXT" to "Versione 0.1.2 (Patch)\n\n• Tabella di marcia del futuro: Aggiunto un calendario interattivo di sviluppo nell'angolo in alto a sinistra della schermata principale.\n• Schermata di selezione mazzo personalizzato: Generatore di mazzi con carte sovrapposte in un layout scattante, gesti fluidi e ottimizzato per l'uso a una mano (One UI 8.x).\n• Allineamento della zona di passaggio: Risolto il bug di ridimensionamento del tavolo integrando la zona di trasferimento direttamente nella griglia delle carte.\n• Perfezionamento del drag-and-drop: Controllo del trascinamento fluido senza interruzioni e ritorno immediato delle carte."
    )

    fun getString(key: String): String {
        return when (_appLanguage.value) {
            AppLanguage.RU -> ruTranslations[key] ?: key
            AppLanguage.IT -> itTranslations[key] ?: enTranslations[key] ?: key
            else -> enTranslations[key] ?: key
        }
    }

    // Settings actions
    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
    }

    fun toggleLanguage() {
        _appLanguage.value = when (_appLanguage.value) {
            AppLanguage.EN -> AppLanguage.RU
            AppLanguage.RU -> AppLanguage.IT
            AppLanguage.IT -> AppLanguage.EN
        }
    }

    fun setDifficulty(hard: Boolean) {
        _isBotHard.value = hard
    }

    fun navigateTo(screen: Screen) {
        // Handle closing networking resources if leaving multiplayer hubs
        if (screen != Screen.GAME_TABLE && screen != Screen.MULTIPLAYER_HUB) {
            multiplayerManager.stopAll()
        }
        _currentScreen.value = screen
    }

    // --- GAME CONTROL ACTIONS ---

    // Starts offline bot match
    fun startOfflineMatch() {
        _activeMode.value = GameMode.OFFLINE
        hasPersistedThisGame = false
        val transferEnabled = (_offlineSubMode.value == OfflineSubMode.TRANSFER)
        val selectedOption = _offlineDeckOption.value
        val deckSize = if (selectedOption == OfflineDeckOption.DECK_52) 52 else 36
        val customDeck = if (selectedOption == OfflineDeckOption.CUSTOM) _customDeckIds.value else null
        
        engine.startMatch(
            player1Name = "Player",
            player2Name = "Bot",
            isBotGame = true,
            deckSize = deckSize,
            isTransferMode = transferEnabled,
            customDeckIds = customDeck
        )
        _gameState.value = engine.createSnapshot().copy(opponentName = "Bot")
        _currentScreen.value = Screen.GAME_TABLE
        triggerBotRoutineIfNeeded() // Fixes bot not making first move if bot goes first!
    }

    // Starts multiplayer matchmaking: Creates a Host room
    fun startHostingLobby() {
        _activeMode.value = GameMode.ONLINE_HOST
        hasPersistedThisGame = false
        multiplayerManager.startHost()
        _currentScreen.value = Screen.MULTIPLAYER_HUB
        
        // Listen to host connection successful
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_HOST) {
                    // Initialize game and send immediately
                    delay(300) // gentle networking stabilization delay
                    hasPersistedThisGame = false
                    engine.startMatch("Host", "Guest", isBotGame = false, deckSize = _mpLobbyDeckSize.value)
                    pushHostStateToClient()
                    _currentScreen.value = Screen.GAME_TABLE
                }
            }
        }
    }

    // Starts client discovery and searches hosts
    fun startSearchingHosts() {
        _activeMode.value = GameMode.ONLINE_CLIENT
        multiplayerManager.startHostDiscovery()
        _currentScreen.value = Screen.MULTIPLAYER_HUB

        // Listen for client connection successful
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_CLIENT) {
                    _currentScreen.value = Screen.GAME_TABLE
                }
            }
        }
    }

    fun connectToIpAddress(ip: String) {
        multiplayerManager.connectToHost(ip)
    }

    // --- PLAYER ACTION ROUTERS ---

    // Handles physical card play action
    fun playCard(card: Card, forceDefenseOnly: Boolean = false, intentTransferOnly: Boolean = false) {
        val snapshot = _gameState.value
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        if (_activeMode.value == GameMode.OFFLINE) {
            // Check if user is Attacking
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (!intentTransferOnly) {
                    if (engine.performAttack("player", card)) {
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
                    }
                }
            } else {
                // User is Defending.
                val canTransfer = engine.isTransferMode && 
                        engine.tablePairs.isNotEmpty() && 
                        engine.tablePairs.all { it.defenseCard == null } &&
                        engine.tablePairs.any { it.attackCard.rank == card.rank }

                if (canTransfer && intentTransferOnly) {
                    if (engine.performTransfer("player", card)) {
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
                    }
                } else if (!intentTransferOnly) {
                    // Highlight or auto-match with the first undefended card on Table
                    val undefendedPair = engine.tablePairs.find { it.defenseCard == null }
                    if (undefendedPair != null) {
                        if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                            refreshLocalState()
                            triggerBotRoutineIfNeeded()
                        }
                    }
                }
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            // Host plays card
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (engine.performAttack("player", card)) {
                    pushHostStateToClient()
                }
            } else {
                val undefendedPair = engine.tablePairs.find { it.defenseCard == null }
                if (undefendedPair != null) {
                    if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                        pushHostStateToClient()
                    }
                }
            }
        } else {
            // Client plays card (Submit intent action to Host)
            val isClientAttacking = (snapshot.attackerPlayerId == "opponent") // From client perspective, host is 'player' and attacker id means opponent is attacking (which is client)
            val payload = if (isClientAttacking) {
                NetworkProtocol.encodeActionAttack(card)
            } else {
                val undefendedPair = snapshot.tablePairs.find { it.defenseCard == null }
                if (undefendedPair != null) {
                    NetworkProtocol.encodeActionDefend(undefendedPair.attackCard, card)
                } else null
            }
            if (payload != null) {
                multiplayerManager.sendMessage(payload)
            }
        }
    }

    fun takeBackCard(card: Card) {
        val snapshot = _gameState.value
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        if (_activeMode.value == GameMode.ONLINE_HOST) {
            if (engine.attackerId == "player") {
                val pairIndex = engine.tablePairs.indexOfFirst { it.attackCard == card && it.defenseCard == null }
                if (pairIndex != -1) {
                    engine.tablePairs.removeAt(pairIndex)
                    engine.playerHand.add(card)
                    engine.log("You took back card ${card.rank.symbol}${card.suit.symbol}", "Вы забрали назад карту ${card.rank.symbol}${card.suit.symbol}")
                    pushHostStateToClient()
                }
            }
        } else if (_activeMode.value == GameMode.ONLINE_CLIENT) {
            val isClientAttacking = (snapshot.attackerPlayerId == "opponent")
            if (isClientAttacking) {
                val payload = "ACTION_TAKE_BACK:${NetworkProtocol.encodeCard(card)}"
                multiplayerManager.sendMessage(payload)
            }
        }
    }

    // Handles user pressing Bito / Done
    fun pressBito() {
        if (_activeMode.value == GameMode.OFFLINE) {
            if (engine.performBito("player")) {
                refreshLocalState()
                triggerBotRoutineIfNeeded()
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            if (engine.performBito("player")) {
                pushHostStateToClient()
            }
        } else {
            multiplayerManager.sendMessage(NetworkProtocol.encodeActionBito())
        }
    }

    // Handles defender pressing "Take All"
    fun pressTakeAll() {
        if (_activeMode.value == GameMode.OFFLINE) {
            if (engine.performTakeAll("player")) {
                refreshLocalState()
                triggerBotRoutineIfNeeded()
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            if (engine.performTakeAll("player")) {
                pushHostStateToClient()
            }
        } else {
            multiplayerManager.sendMessage(NetworkProtocol.encodeActionTake())
        }
    }

    // Triggered after host updates their game engine, pushing synchronized state across socket
    private fun pushHostStateToClient() {
        val hostSnapshot = engine.createSnapshot()
        _gameState.value = hostSnapshot.copy(opponentName = "Client Player")
        
        // Client perspective is reversed!
        // Client's 'localHand' must be host's 'opponentHand' (Guest hand).
        // Let's create client snapshot
        val clientHand = engine.opponentHand.toList()
        val hostHand = engine.playerHand.toList()
        
        // Switch attacker perspective flag correctly for client
        val clientSnapshot = hostSnapshot.copy(
            isLocalTurn = (engine.attackerId == "opponent"), // Client is 'opponent' inside Host Engine
            localHand = clientHand,
            opponentHandSize = hostHand.size,
            opponentName = "Host Player",
            canBito = (engine.attackerId == "opponent") && engine.tablePairs.isNotEmpty() && engine.tablePairs.all { it.defenseCard != null },
            canTake = (engine.attackerId == "player") && engine.tablePairs.isNotEmpty() && engine.tablePairs.any { it.defenseCard == null }
        )

        val serializedMsg = NetworkProtocol.serializeState(clientSnapshot, hostHand, clientHand)
        multiplayerManager.sendMessage(serializedMsg)

        checkAndPersistRoomResult(hostSnapshot)
    }

    // Triggered in offline mode to sync visual views and evaluate bots
    private fun refreshLocalState() {
        val snapshot = engine.createSnapshot().copy(opponentName = "AI Bot")
        _gameState.value = snapshot
        checkAndPersistRoomResult(snapshot)
    }

    private fun checkAndPersistRoomResult(snapshot: GameStateSnapshot) {
        if (snapshot.matchStatus == MatchStatus.WON || snapshot.matchStatus == MatchStatus.LOST || snapshot.matchStatus == MatchStatus.DRAW) {
            if (hasPersistedThisGame) return
            hasPersistedThisGame = true
            
            // Write to Room once safely
            viewModelScope.launch(Dispatchers.IO) {
                val modeLabel = if (_activeMode.value == GameMode.OFFLINE) "OFFLINE" else "ONLINE"
                val resultLabel = snapshot.matchStatus.name
                val oppLabel = snapshot.opponentName
                repository.insert(
                    GameStat(
                        mode = modeLabel,
                        result = resultLabel,
                        opponentName = oppLabel,
                        matchLogEn = snapshot.gameLogEn.joinToString("\n"),
                        matchLogRu = snapshot.gameLogRu.joinToString("\n")
                    )
                )
            }
        }
    }

    // Handles bot triggers with a slight tactical thinking visual delay (1200ms)
    private fun triggerBotRoutineIfNeeded() {
        val snapshot = engine.createSnapshot()
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        val isBotActiveTurn = (engine.attackerId == "opponent" && (engine.tablePairs.isEmpty() || engine.tablePairs.all { it.defenseCard != null })) ||
                              (engine.attackerId == "player" && engine.tablePairs.any { it.defenseCard == null })
        if (isBotActiveTurn && !_botThinking.value) {
            _botThinking.value = true
            viewModelScope.launch {
                delay(1200) // Thinking aesthetic lapse
                val BotSuccess = engine.makeBotMove(_isBotHard.value)
                _botThinking.value = false
                refreshLocalState()
                if (BotSuccess) {
                    // Loop bot moves (e.g., if bot defended, it might also attack next right away)
                    triggerBotRoutineIfNeeded()
                }
            }
        }
    }

    // Handles Client-to-Host parsed action payloads
    private fun handleNetworkMessage(msg: String) {
        if (_activeMode.value == GameMode.ONLINE_HOST) {
            // Host evaluates actions submitted by client
            var stateChanged = false
            when {
                msg.startsWith("ACTION_ATTACK:") -> {
                    val cardData = msg.replace("ACTION_ATTACK:", "")
                    val card = NetworkProtocol.decodeCard(cardData)
                    if (card != null && engine.performAttack("opponent", card)) {
                        stateChanged = true
                    }
                }
                msg.startsWith("ACTION_TAKE_BACK:") -> {
                    val cardData = msg.replace("ACTION_TAKE_BACK:", "")
                    val card = NetworkProtocol.decodeCard(cardData)
                    if (card != null && engine.attackerId == "opponent") {
                        val pairIndex = engine.tablePairs.indexOfFirst { it.attackCard == card && it.defenseCard == null }
                        if (pairIndex != -1) {
                            engine.tablePairs.removeAt(pairIndex)
                            engine.opponentHand.add(card)
                            engine.log("Opponent took back card ${card.rank.symbol}${card.suit.symbol}", "Оппонент забрал назад карту ${card.rank.symbol}${card.suit.symbol}")
                            stateChanged = true
                        }
                    }
                }
                msg.startsWith("ACTION_DEFEND:") -> {
                    val parts = msg.replace("ACTION_DEFEND:", "").split("|")
                    if (parts.size == 2) {
                        val attackCard = NetworkProtocol.decodeCard(parts[0])
                        val defenseCard = NetworkProtocol.decodeCard(parts[1])
                        if (attackCard != null && defenseCard != null && engine.performDefense("opponent", attackCard, defenseCard)) {
                            stateChanged = true
                        }
                    }
                }
                msg == "ACTION_TAKE" -> {
                    if (engine.performTakeAll("opponent")) {
                        stateChanged = true
                    }
                }
                msg == "ACTION_BITO" -> {
                    if (engine.performBito("opponent")) {
                        stateChanged = true
                    }
                }
            }
            if (stateChanged) {
                pushHostStateToClient()
            }
        } else {
            // Client processes full state push from Host
            val statePayload = NetworkProtocol.deserializeState(msg)
            if (statePayload != null) {
                val isClientAttacking = (statePayload.attackerId == "opponent")
                
                val currentSnapshot = GameStateSnapshot(
                    trumpCard = statePayload.trumpCard,
                    trumpSuit = statePayload.trumpSuit,
                    deckSize = statePayload.deckSize,
                    tablePairs = statePayload.tablePairs,
                    discardPileSize = statePayload.discardPileSize,
                    isLocalTurn = if (isClientAttacking) statePayload.tablePairs.all { it.defenseCard != null } || statePayload.tablePairs.isEmpty() else statePayload.tablePairs.any { it.defenseCard == null },
                    localHand = statePayload.clientHand,
                    opponentHandSize = statePayload.hostHand.size,
                    opponentName = "Host Player",
                    matchStatus = statePayload.matchStatus,
                    attackerPlayerId = statePayload.attackerId,
                    gameLog = statePayload.gameLog,
                    gameLogEn = statePayload.gameLogEn,
                    gameLogRu = statePayload.gameLogRu,
                    canTake = !isClientAttacking && statePayload.tablePairs.isNotEmpty() && statePayload.tablePairs.any { it.defenseCard == null },
                    canBito = isClientAttacking && statePayload.tablePairs.isNotEmpty() && statePayload.tablePairs.all { it.defenseCard != null }
                )
                
                _gameState.value = currentSnapshot
                checkAndPersistRoomResult(currentSnapshot)
            }
        }
    }

    // Stats functions
    fun clearMatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        multiplayerManager.stopAll()
    }
}
