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

enum class AppLanguage { EN, RU, IT, UA }

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

    private val prefs = application.getSharedPreferences("durak_prefs", android.content.Context.MODE_PRIVATE)

    // Preferences & Settings
    private val _appLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("lang", AppLanguage.RU.name) ?: AppLanguage.RU.name)
        } catch (e: Exception) {
            AppLanguage.RU
        }
    )
    val appLanguage = _appLanguage.asStateFlow()

    private val _isBotHard = MutableStateFlow(false)
    val isBotHard = _isBotHard.asStateFlow()

    // Rendered game state
    private val _gameState = MutableStateFlow<GameStateSnapshot>(GameStateSnapshot())
    val gameState = _gameState.asStateFlow()

    private var hasPersistedThisGame = false

    private val _playerNickname = MutableStateFlow(prefs.getString("nickname", "Player") ?: "Player")
    val playerNickname = _playerNickname.asStateFlow()

    private val _opponentNickname = MutableStateFlow("Opponent")
    val opponentNickname = _opponentNickname.asStateFlow()

    private val _musicVolume = MutableStateFlow(prefs.getFloat("music_volume", 0.5f))
    val musicVolume = _musicVolume.asStateFlow()

    private val _sfxVolume = MutableStateFlow(prefs.getFloat("sfx_volume", 0.5f))
    val sfxVolume = _sfxVolume.asStateFlow()

    fun setMusicVolume(value: Float) {
        _musicVolume.value = value
        com.example.audio.DurakAudioManager.musicVolume = value
        prefs.edit().putFloat("music_volume", value).apply()
    }

    fun setSfxVolume(value: Float) {
        _sfxVolume.value = value
        com.example.audio.DurakAudioManager.sfxVolume = value
        prefs.edit().putFloat("sfx_volume", value).apply()
    }

    fun setPlayerNickname(name: String) {
        val trimmed = name.take(15)
        _playerNickname.value = trimmed
        prefs.edit().putString("nickname", trimmed).apply()
    }

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
    private val _mpLobbyDeckOption = MutableStateFlow(OfflineDeckOption.DECK_36)
    val mpLobbyDeckOption = _mpLobbyDeckOption.asStateFlow()

    private val _mpLobbySubMode = MutableStateFlow(OfflineSubMode.CLASSIC)
    val mpLobbySubMode = _mpLobbySubMode.asStateFlow()

    private val _mpLobbyPlayersCount = MutableStateFlow(2)
    val mpLobbyPlayersCount = _mpLobbyPlayersCount.asStateFlow()

    fun setMpLobbyDeckOption(option: OfflineDeckOption) {
        _mpLobbyDeckOption.value = option
    }

    fun setMpLobbySubMode(subMode: OfflineSubMode) {
        _mpLobbySubMode.value = subMode
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
        // Initialize AudioManager with application context and volumes
        com.example.audio.DurakAudioManager.initialize(application)
        com.example.audio.DurakAudioManager.musicVolume = _musicVolume.value
        com.example.audio.DurakAudioManager.sfxVolume = _sfxVolume.value

        // Setup background music dynamic track state engine observer
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_currentScreen, _gameState) { screen, state ->
                Pair(screen, state)
            }.collect { (screen, state) ->
                if (screen == Screen.GAME_TABLE && state.matchStatus == MatchStatus.PLAYING && state.deckSize == 0) {
                    com.example.audio.DurakAudioManager.startMusic(6)
                } else if (screen == Screen.MAIN_MENU || screen == Screen.OFFLINE_SETUP || screen == Screen.MULTIPLAYER_HUB || screen == Screen.STATS_BOARD || screen == Screen.GAME_TABLE) {
                    com.example.audio.DurakAudioManager.startMusic(5)
                } else {
                    com.example.audio.DurakAudioManager.stopMusic()
                }
            }
        }

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
        "STATUS_TITLE_LABEL" to "Sound Update",
        "CHANGELOG_BTN" to "Changelog",
        "CHANGELOG_TITLE" to "Version Changelog",
        "CHANGELOG_TEXT" to "Version 0.2.1_01 (Lifecycle Audio Fix)\n\n• Lifecycle Music Fix: Resolved the issue where background music kept playing after minimizing the application or navigating to the home screen. Music now correctly pauses and resumes with the application lifecycle.",
        "BOT_DECENT_TITLE" to "DECENT AMATEUR BOT",
        "BOT_DECENT_DESC" to "Plays casual valid combinations. Excellent for beginners looking to learn basic durak card sequencing.",
        "BOT_AI_TITLE" to "AI ANALYTICAL BOT",
        "BOT_AI_DESC" to "Defends with cold calculation. Tracks all played cards, saves trumps for endgame clutches, and prioritizes strategic discard sequences.",
        "SETTINGS" to "Settings",
        "SOUND_TAB" to "Sound",
        "LANG_TAB" to "Language",
        "MUSIC_VOL" to "Music",
        "SFX_VOL" to "Effects"
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
        "STATUS_TITLE_LABEL" to "Музыкальное обновление",
        "CHANGELOG_BTN" to "Изменения",
        "CHANGELOG_TITLE" to "История изменений",
        "CHANGELOG_TEXT" to "Версия 0.2.1_01 (Исправление фонового аудио)\n\n• Исправление фонового аудио: Устранена проблема, из-за которой музыка продолжала играть после сворачивания приложения на рабочий экран. Теперь музыка автоматически ставится на паузу при выходе из игры и возобновляется при возвращении.",
        "BOT_DECENT_TITLE" to "ЛЮБИТЕЛЬСКИЙ БОТ",
        "BOT_DECENT_DESC" to "Разыгрывает простые допустимые комбинации. Отлично подходит для начинающих, желающих освоить базовый порядок карт в дураке.",
        "BOT_AI_TITLE" to "АНАЛИТИЧЕСКИЙ ИИ-БОТ",
        "BOT_AI_DESC" to "Защищается с холодным расчетом. Отслеживает все сыгранные карты, бережет козыри для решающих моментов в конце игры и отдает приоритет стратегическому сбросу.",
        "SETTINGS" to "Настройки",
        "SOUND_TAB" to "Звук",
        "LANG_TAB" to "Язык",
        "MUSIC_VOL" to "Музыка",
        "SFX_VOL" to "Эффекты"
    )

    private val itTranslations = mapOf(
        "APP_TITLE" to "Durak Offline",
        "PLAY_OFFLINE" to "Gioca Offline",
        "PLAY_ONLINE" to "Multiplayer Locale",
        "BOT_SETUP_TITLE" to "Partita Offline",
        "DIFFICULTY" to "Difficoltà Bot",
        "EASY" to "Facile (Casuale)",
        "HARD" to "Difficile (Analitico)",
        "START_GAME" to "Inizia Gioco",
        "P2P_TITLE" to "Online Locale",
        "NSD_STATUS" to "Rilevamento Wi-Fi Hub",
        "HOST_LOBBY" to "Crea una Stanza",
        "MY_IP" to "Tuo IP Host:",
        "DISCOVERY_ACTIVE" to "Ricerca host locali in corso...",
        "TAP_TO_CONNECT" to "Tocca per connetterti",
        "MANUAL_CONNECT" to "Connessione IP Diretta",
        "ENTER_HOST_IP" to "Inserisci l'indirizzo IP dell'host",
        "CONNECT_BTN" to "Connetti",
        "WAITING_LOBBY" to "Stanza aperta. In attesa del giocatore...",
        "DISCONNECTED" to "Disconnesso",
        "CONNECTING" to "Connessione in corso...",
        "BACK" to "Indietro",
        "TAKE" to "Prendi Tutto",
        "BITO" to "Bito / Passa",
        "RESTART" to "Gioca Ancora",
        "MENU" to "Menu Principale",
        "DECK" to "Mazzo",
        "TRUMP" to "Trionfo",
        "STATS_TITLE" to "Archivi Partite",
        "CLEAR_STATS" to "Cancella Archivi",
        "EMPTY_STATS" to "Nessuna cronologia registrata.",
        "GAME_LOGS" to "Registro di Battaglia",
        "TURN_PLAYER" to "Tuo Turno",
        "TURN_BOT" to "Il Bot sta pensando...",
        "TURN_OPPONENT" to "Turno dell'Avversario",
        "WIN_TITLE" to "VITTORIA!",
        "LOST_TITLE" to "SCONFITTA! SEI IL DURAK!",
        "DRAW_TITLE" to "PAREGGIO!",
        "DISC_TITLE" to "Avversario disconnesso!",
        "STATUS_TITLE_LABEL" to "Aggiornamento audio",
        "CHANGELOG_BTN" to "Registro",
        "CHANGELOG_TITLE" to "Registro Modifiche",
        "CHANGELOG_TEXT" to "Versione 0.2.1_01 (Risoluzione audio in background)\n\n• Risoluzione audio in background: Risolto bug per cui la musica continuava a riprodursi dopo aver ridotto l'applicazione a icona o essere andati alla schermata principale. Ora si mette in pausa e riprende con il ciclo di vita dell'app.",
        "BOT_DECENT_TITLE" to "BOT AMATORIALE",
        "BOT_DECENT_DESC" to "Gioca combinazioni semplici e valide. Ottimo per i principianti che vogliono imparare la sequenza base delle carte del durak.",
        "BOT_AI_TITLE" to "BOT ANALITICO IA",
        "BOT_AI_DESC" to "Difende con freddo calcolo. Tiene traccia di tutte le carte giocate, conserva i trionfi per le fasi finali e dà priorità a scarti strategici.",
        "SETTINGS" to "Impostazioni",
        "SOUND_TAB" to "Audio",
        "LANG_TAB" to "Lingua",
        "MUSIC_VOL" to "Musica",
        "SFX_VOL" to "Effetti"
    )

    private val uaTranslations = mapOf(
        "APP_TITLE" to "Дурник Офлайн",
        "PLAY_OFFLINE" to "Грати Офлайн",
        "PLAY_ONLINE" to "Мультиплеєр (Wi-Fi)",
        "BOT_SETUP_TITLE" to "Офлайн Налаштування",
        "DIFFICULTY" to "Складність Бота",
        "EASY" to "Легкий (Випадковий)",
        "HARD" to "Складний (Аналітик)",
        "START_GAME" to "Почати Гру",
        "P2P_TITLE" to "Локальна мережа",
        "NSD_STATUS" to "Пошук Wi-Fi",
        "HOST_LOBBY" to "Створити Лоббі",
        "MY_IP" to "Ваша IP адреса:",
        "DISCOVERY_ACTIVE" to "Пошук хостів...",
        "TAP_TO_CONNECT" to "Натисніть для підключення",
        "MANUAL_CONNECT" to "Підключення по IP",
        "ENTER_HOST_IP" to "Введіть IP адресу хоста",
        "CONNECT_BTN" to "Увійти",
        "WAITING_LOBBY" to "Лоббі відкрито. Очікування гравця...",
        "DISCONNECTED" to "Відключено",
        "CONNECTING" to "Підключення...",
        "BACK" to "Назад",
        "TAKE" to "Взяти Все",
        "BITO" to "Бито / Пас",
        "RESTART" to "Грати Знову",
        "MENU" to "Головне Меню",
        "DECK" to "Колода",
        "TRUMP" to "Козир",
        "STATS_TITLE" to "Архів Матчів",
        "CLEAR_STATS" to "Очистити Списки",
        "EMPTY_STATS" to "Історія матчів відсутня.",
        "GAME_LOGS" to "Лог Битви",
        "TURN_PLAYER" to "Ваш Хід",
        "TURN_BOT" to "Бот думает...",
        "TURN_OPPONENT" to "Хід Опонента",
        "WIN_TITLE" to "ПЕРЕМОГА!",
        "LOST_TITLE" to "ПОРАЗКА! ВИ ДУРНИК!",
        "DRAW_TITLE" to "НІЧИЯ!",
        "DISC_TITLE" to "Гравець відключився!",
        "STATUS_TITLE_LABEL" to "Музичне оновлення",
        "CHANGELOG_BTN" to "Список змін",
        "CHANGELOG_TITLE" to "Список змін",
        "CHANGELOG_TEXT" to "Версія 0.2.1_01 (Виправлення фонової музики)\n\n• Виправлення фонового звуку: Усунено помилку, через яку музика продовжувала грати після згортання додатка на головний екран. Тепер аудіо автоматично стає на паузу та відновлюється разом із додатком.",
        "SETTINGS" to "Налаштування",
        "SOUND_TAB" to "Звук",
        "LANG_TAB" to "Мова",
        "MUSIC_VOL" to "Музика",
        "SFX_VOL" to "Ефекти"
    )

    fun getString(key: String): String {
        return when (_appLanguage.value) {
            AppLanguage.RU -> ruTranslations[key] ?: key
            AppLanguage.IT -> itTranslations[key] ?: enTranslations[key] ?: key
            AppLanguage.UA -> uaTranslations[key] ?: ruTranslations[key] ?: key
            else -> enTranslations[key] ?: key
        }
    }

    // Settings actions
    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
        prefs.edit().putString("lang", lang.name).apply()
    }

    fun toggleLanguage() {
        val next = when (_appLanguage.value) {
            AppLanguage.EN -> AppLanguage.RU
            AppLanguage.RU -> AppLanguage.IT
            AppLanguage.IT -> AppLanguage.UA
            AppLanguage.UA -> AppLanguage.EN
        }
        _appLanguage.value = next
        prefs.edit().putString("lang", next.name).apply()
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
        
        val pName = if (_playerNickname.value.isBlank()) "Player" else _playerNickname.value
        engine.startMatch(
            player1Name = pName,
            player2Name = if (_isBotHard.value) "AI Bot" else "Bot",
            isBotGame = true,
            deckSize = deckSize,
            isTransferMode = transferEnabled,
            customDeckIds = customDeck
        )
        _gameState.value = engine.createSnapshot().copy(opponentName = if (_isBotHard.value) "AI Bot" else "Bot")
        com.example.audio.DurakAudioManager.playSFX(1)
        _currentScreen.value = Screen.GAME_TABLE
        triggerBotRoutineIfNeeded() // Fixes bot not making first move if bot goes first!
    }

    // Starts multiplayer matchmaking: Creates a Host room
    fun startHostingLobby() {
        _activeMode.value = GameMode.ONLINE_HOST
        hasPersistedThisGame = false
        _opponentNickname.value = "Guest"
        multiplayerManager.startHost()
        _currentScreen.value = Screen.MULTIPLAYER_HUB
        
        // Listen to host connection successful
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_HOST) {
                    val pName = if (_playerNickname.value.isBlank()) "Player" else _playerNickname.value
                    // Send nickname immediately on connection
                    multiplayerManager.sendMessage("NICKNAME:$pName")
                    
                    // Initialize game and send immediately
                    delay(300) // gentle networking stabilization delay
                    hasPersistedThisGame = false
                    val deckOption = _mpLobbyDeckOption.value
                    val deckSize = if (deckOption == OfflineDeckOption.DECK_52) 52 else 36
                    val customDeck = if (deckOption == OfflineDeckOption.CUSTOM) _customDeckIds.value else null
                    val transferEnabled = (_mpLobbySubMode.value == OfflineSubMode.TRANSFER)
                    engine.startMatch(
                        player1Name = pName,
                        player2Name = _opponentNickname.value,
                        isBotGame = false,
                        deckSize = deckSize,
                        isTransferMode = transferEnabled,
                        customDeckIds = customDeck
                    )
                    com.example.audio.DurakAudioManager.playSFX(1)
                    pushHostStateToClient()
                    _currentScreen.value = Screen.GAME_TABLE
                }
            }
        }
    }

    // Starts client discovery and searches hosts
    fun startSearchingHosts() {
        _activeMode.value = GameMode.ONLINE_CLIENT
        _opponentNickname.value = "Host"
        multiplayerManager.startHostDiscovery()
        _currentScreen.value = Screen.MULTIPLAYER_HUB

        // Listen for client connection successful
        viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_CLIENT) {
                    val pName = if (_playerNickname.value.isBlank()) "Player" else _playerNickname.value
                    // Send nickname immediately on connection
                    multiplayerManager.sendMessage("NICKNAME:$pName")
                    com.example.audio.DurakAudioManager.playSFX(1)
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
    fun playCard(card: Card, forceDefenseOnly: Boolean = false, intentTransferOnly: Boolean = false, targetAttackCard: Card? = null) {
        val snapshot = _gameState.value
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        if (_activeMode.value == GameMode.OFFLINE) {
            // Check if user is Attacking
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (!intentTransferOnly) {
                    if (engine.performAttack("player", card)) {
                        com.example.audio.DurakAudioManager.playSFX(2)
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
                        com.example.audio.DurakAudioManager.playSFX(4)
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
                    }
                } else if (!intentTransferOnly) {
                    // Highlight or auto-match with the specified/first undefended card on Table
                    val undefendedPair = if (targetAttackCard != null) {
                        engine.tablePairs.find { it.attackCard == targetAttackCard && it.defenseCard == null }
                    } else {
                        engine.tablePairs.find { it.defenseCard == null }
                    }
                    if (undefendedPair != null) {
                        if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                            com.example.audio.DurakAudioManager.playSFX(2)
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
                if (!intentTransferOnly) {
                    if (engine.performAttack("player", card)) {
                        com.example.audio.DurakAudioManager.playSFX(2)
                        pushHostStateToClient()
                    }
                }
            } else {
                val canTransfer = engine.isTransferMode && 
                        engine.tablePairs.isNotEmpty() && 
                        engine.tablePairs.all { it.defenseCard == null } &&
                        engine.tablePairs.any { it.attackCard.rank == card.rank }

                if (canTransfer && intentTransferOnly) {
                    if (engine.performTransfer("player", card)) {
                        com.example.audio.DurakAudioManager.playSFX(4)
                        pushHostStateToClient()
                    }
                } else if (!intentTransferOnly) {
                    val undefendedPair = if (targetAttackCard != null) {
                        engine.tablePairs.find { it.attackCard == targetAttackCard && it.defenseCard == null }
                    } else {
                        engine.tablePairs.find { it.defenseCard == null }
                    }
                    if (undefendedPair != null) {
                        if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                            com.example.audio.DurakAudioManager.playSFX(2)
                            pushHostStateToClient()
                        }
                    }
                }
            }
        } else {
            // Client plays card (Submit intent action to Host)
            val isClientAttacking = (snapshot.attackerPlayerId == "opponent") // From client perspective, host is 'player' and attacker id means opponent is attacking (which is client)
            val payload = if (intentTransferOnly) {
                NetworkProtocol.encodeActionTransfer(card)
            } else if (isClientAttacking) {
                NetworkProtocol.encodeActionAttack(card)
            } else {
                val undefendedPair = if (targetAttackCard != null) {
                    snapshot.tablePairs.find { it.attackCard == targetAttackCard && it.defenseCard == null }
                } else {
                    snapshot.tablePairs.find { it.defenseCard == null }
                }
                if (undefendedPair != null) {
                    NetworkProtocol.encodeActionDefend(undefendedPair.attackCard, card)
                } else null
            }
            if (payload != null) {
                multiplayerManager.sendMessage(payload)
                if (intentTransferOnly) {
                    com.example.audio.DurakAudioManager.playSFX(4)
                } else {
                    com.example.audio.DurakAudioManager.playSFX(2)
                }
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
        _gameState.value = hostSnapshot.copy(opponentName = _opponentNickname.value)
        
        // Client perspective is reversed!
        // Client's 'localHand' must be host's 'opponentHand' (Guest hand).
        // Let's create client snapshot
        val clientHand = engine.opponentHand.toList()
        val hostHand = engine.playerHand.toList()
        
        val clientStatus = when (hostSnapshot.matchStatus) {
            MatchStatus.WON -> MatchStatus.LOST
            MatchStatus.LOST -> MatchStatus.WON
            else -> hostSnapshot.matchStatus
        }
        
        // Switch attacker perspective flag correctly for client
        val clientSnapshot = hostSnapshot.copy(
            matchStatus = clientStatus,
            isLocalTurn = (engine.attackerId == "opponent"), // Client is 'opponent' inside Host Engine
            localHand = clientHand,
            opponentHandSize = hostHand.size,
            opponentName = _playerNickname.value,
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
                val prevAttacker = engine.attackerId
                val isTransferPossible = engine.isTransferMode && engine.tablePairs.isNotEmpty() && engine.tablePairs.all { it.defenseCard == null }
                val BotSuccess = engine.makeBotMove(_isBotHard.value)
                _botThinking.value = false
                refreshLocalState()
                if (BotSuccess) {
                    if (isTransferPossible && engine.attackerId != prevAttacker) {
                        com.example.audio.DurakAudioManager.playSFX(4) // Bot transferred cards
                    } else if (engine.tablePairs.isEmpty() && prevAttacker != engine.attackerId) {
                        // Bito happened, no card was thrown by bot on table in this sub-step
                    } else {
                        com.example.audio.DurakAudioManager.playSFX(3) // Bot threw/beat card
                    }
                    // Loop bot moves (e.g., if bot defended, it might also attack next right away)
                    triggerBotRoutineIfNeeded()
                }
            }
        }
    }

    // Handles Client-to-Host parsed action payloads
    private fun handleNetworkMessage(msg: String) {
        if (msg.startsWith("NICKNAME:")) {
            val name = msg.replace("NICKNAME:", "").trim()
            if (name.isNotEmpty()) {
                _opponentNickname.value = name
                if (_activeMode.value == GameMode.ONLINE_HOST) {
                    multiplayerManager.sendMessage("NICKNAME:${_playerNickname.value}")
                    pushHostStateToClient()
                } else if (_activeMode.value == GameMode.ONLINE_CLIENT) {
                    val current = _gameState.value
                    if (current.matchStatus == MatchStatus.PLAYING) {
                        _gameState.value = current.copy(opponentName = name)
                    }
                }
            }
            return
        }

        if (_activeMode.value == GameMode.ONLINE_HOST) {
            // Host evaluates actions submitted by client
            var stateChanged = false
            when {
                msg.startsWith("ACTION_ATTACK:") -> {
                    val cardData = msg.replace("ACTION_ATTACK:", "")
                    val card = NetworkProtocol.decodeCard(cardData)
                    if (card != null && engine.performAttack("opponent", card)) {
                        com.example.audio.DurakAudioManager.playSFX(3)
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
                            com.example.audio.DurakAudioManager.playSFX(3)
                            stateChanged = true
                        }
                    }
                }
                msg.startsWith("ACTION_TRANSFER:") -> {
                    val cardData = msg.replace("ACTION_TRANSFER:", "")
                    val card = NetworkProtocol.decodeCard(cardData)
                    if (card != null && engine.performTransfer("opponent", card)) {
                        com.example.audio.DurakAudioManager.playSFX(4)
                        stateChanged = true
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
            val oldSnapshot = _gameState.value
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
                    opponentName = _opponentNickname.value,
                    matchStatus = statePayload.matchStatus,
                    attackerPlayerId = statePayload.attackerId,
                    gameLog = statePayload.gameLog,
                    gameLogEn = statePayload.gameLogEn,
                    gameLogRu = statePayload.gameLogRu,
                    canTake = !isClientAttacking && statePayload.tablePairs.isNotEmpty() && statePayload.tablePairs.any { it.defenseCard == null },
                    canBito = isClientAttacking && statePayload.tablePairs.isNotEmpty() && statePayload.tablePairs.all { it.defenseCard != null },
                    isTransferMode = statePayload.isTransferMode
                )
                
                _gameState.value = currentSnapshot
                checkAndPersistRoomResult(currentSnapshot)

                // Let's trigger opponent/host plays on Client device
                if (oldSnapshot.matchStatus == MatchStatus.PLAYING) {
                    val oldSize = oldSnapshot.tablePairs.size
                    val newSize = currentSnapshot.tablePairs.size
                    
                    val oldDefCount = oldSnapshot.tablePairs.count { it.defenseCard != null }
                    val newDefCount = currentSnapshot.tablePairs.count { it.defenseCard != null }
                    
                    if (newSize > oldSize) {
                        val attackerChanged = oldSnapshot.attackerPlayerId != currentSnapshot.attackerPlayerId
                        if (currentSnapshot.isTransferMode && attackerChanged && oldSize > 0) {
                            com.example.audio.DurakAudioManager.playSFX(4) // Opponent transferred cards
                        } else if (!currentSnapshot.isLocalTurn) {
                            com.example.audio.DurakAudioManager.playSFX(3) // Opponent attacks
                        }
                    } else if (newDefCount > oldDefCount && !currentSnapshot.isLocalTurn) {
                        com.example.audio.DurakAudioManager.playSFX(3) // Opponent defends
                    }
                }
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
