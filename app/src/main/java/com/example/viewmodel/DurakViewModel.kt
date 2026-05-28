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

enum class AppLanguage { EN, RU }

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
        STATS_BOARD
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
        "APP_TITLE" to "DURAK",
        "PLAY_OFFLINE" to "Play Offline",
        "PLAY_ONLINE" to "Local Multiplayer",
        "BOT_SETUP_TITLE" to "Offline Match",
        "DIFFICULTY" to "Bot Difficulty",
        "EASY" to "Easy (Random)",
        "HARD" to "Hard (Analytical)",
        "START_GAME" to "Start Durak",
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
        "STATUS_TITLE_LABEL" to "Durak Classic"
    )

    private val ruTranslations = mapOf(
        "APP_TITLE" to "ДУРАК",
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
        "STATUS_TITLE_LABEL" to "Дурак Классический"
    )

    fun getString(key: String): String {
        return if (_appLanguage.value == AppLanguage.RU) {
            ruTranslations[key] ?: key
        } else {
            enTranslations[key] ?: key
        }
    }

    // Settings actions
    fun toggleLanguage() {
        _appLanguage.value = if (_appLanguage.value == AppLanguage.EN) AppLanguage.RU else AppLanguage.EN
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
        engine.startMatch("Player", "Bot", isBotGame = true)
        _gameState.value = engine.createSnapshot().copy(opponentName = "Bot")
        _currentScreen.value = Screen.GAME_TABLE
    }

    // Starts multiplayer matchmaking: Creates a Host room
    fun startHostingLobby() {
        _activeMode.value = GameMode.ONLINE_HOST
        multiplayerManager.startHost()
        _currentScreen.value = Screen.MULTIPLAYER_HUB
        
        // Listen to host connection successful
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_HOST) {
                    // Initialize game and send immediately
                    delay(300) // gentle networking stabilization delay
                    engine.startMatch("Host", "Guest", isBotGame = false)
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
    fun playCard(card: Card) {
        val snapshot = _gameState.value
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        if (_activeMode.value == GameMode.OFFLINE) {
            // Check if user is Attacking
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (engine.performAttack("player", card)) {
                    refreshLocalState()
                    triggerBotRoutineIfNeeded()
                }
            } else {
                // User is Defending.
                // Highlight or auto-match with the first undefended card on Table
                val undefendedPair = engine.tablePairs.find { it.defenseCard == null }
                if (undefendedPair != null) {
                    if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
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
            // Write to Room once safely
            viewModelScope.launch(Dispatchers.IO) {
                val modeLabel = if (_activeMode.value == GameMode.OFFLINE) "OFFLINE" else "ONLINE"
                val resultLabel = snapshot.matchStatus.name
                val oppLabel = snapshot.opponentName
                repository.insert(
                    GameStat(
                        mode = modeLabel,
                        result = resultLabel,
                        opponentName = oppLabel
                    )
                )
            }
        }
    }

    // Handles bot triggers with a slight tactical thinking visual delay (1200ms)
    private fun triggerBotRoutineIfNeeded() {
        val snapshot = engine.createSnapshot()
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        val isBotActiveTurn = (engine.attackerId == "opponent") || (engine.attackerId == "player" && engine.tablePairs.any { it.defenseCard == null })
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
