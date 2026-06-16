package com.example.desktop.viewmodel

import com.example.desktop.audio.DurakAudioManager
import com.example.desktop.database.GameStat
import com.example.desktop.database.GameStatRepository
import com.example.desktop.engine.DurakEngine
import com.example.desktop.model.*
import com.example.desktop.network.MultiplayerManager
import com.example.desktop.network.NetworkProtocol
import com.example.desktop.preferences.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppLanguage { EN, RU, IT, UA }

/**
 * Desktop port of the original Android ViewModel. All Android-only types
 * (`AndroidViewModel`, `SharedPreferences`, `Application`, Room repositories)
 * have been replaced with desktop equivalents but the public surface — the
 * StateFlows consumed by the Compose UI — is intentionally preserved.
 */
class DurakViewModel(
    private val repository: GameStatRepository = GameStatRepository(),
    private val prefs: Preferences = Preferences(),
    private val multiplayerManager: MultiplayerManager = MultiplayerManager()
) {

    val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val gameHistory: StateFlow<List<GameStat>> = repository.allStats
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.allStats.value)

    private val engine = DurakEngine()

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

    enum class OfflineSubMode {
        CLASSIC,
        TRANSFER
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
            if (current.size > 12) current.remove(cardId)
        } else {
            current.add(cardId)
        }
        _customDeckIds.value = current
    }

    private val _currentScreen = MutableStateFlow(Screen.MAIN_MENU)
    val currentScreen = _currentScreen.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("lang", AppLanguage.RU.name) ?: AppLanguage.RU.name)
        } catch (_: Exception) { AppLanguage.RU }
    )
    val appLanguage = _appLanguage.asStateFlow()

    private val _isBotHard = MutableStateFlow(false)
    val isBotHard = _isBotHard.asStateFlow()

    private val _gameState = MutableStateFlow(GameStateSnapshot())
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
        DurakAudioManager.musicVolume = value
        prefs.putFloat("music_volume", value)
    }

    fun setSfxVolume(value: Float) {
        _sfxVolume.value = value
        DurakAudioManager.sfxVolume = value
        prefs.putFloat("sfx_volume", value)
    }

    fun setPlayerNickname(name: String) {
        val trimmed = name.take(15)
        _playerNickname.value = trimmed
        prefs.putString("nickname", trimmed)
    }

    val networkState = multiplayerManager.connectionState
    val discoveredHosts = multiplayerManager.discoveredHosts
    val localIp = multiplayerManager.localIpAddress

    private val _activeMode = MutableStateFlow(GameMode.OFFLINE)
    val activeMode = _activeMode.asStateFlow()

    private val _botThinking = MutableStateFlow(false)
    val botThinking = _botThinking.asStateFlow()

    private val _mpLobbyDeckOption = MutableStateFlow(OfflineDeckOption.DECK_36)
    val mpLobbyDeckOption = _mpLobbyDeckOption.asStateFlow()

    private val _mpLobbySubMode = MutableStateFlow(OfflineSubMode.CLASSIC)
    val mpLobbySubMode = _mpLobbySubMode.asStateFlow()

    private val _mpLobbyPlayersCount = MutableStateFlow(2)
    val mpLobbyPlayersCount = _mpLobbyPlayersCount.asStateFlow()

    fun setMpLobbyDeckOption(option: OfflineDeckOption) { _mpLobbyDeckOption.value = option }
    fun setMpLobbySubMode(subMode: OfflineSubMode) { _mpLobbySubMode.value = subMode }
    fun setMpLobbyPlayersCount(count: Int) { _mpLobbyPlayersCount.value = count }

    private val _offlineSubMode = MutableStateFlow(OfflineSubMode.CLASSIC)
    val offlineSubMode = _offlineSubMode.asStateFlow()

    fun setOfflineSubMode(subMode: OfflineSubMode) { _offlineSubMode.value = subMode }

    private var hostStateJob: Job? = null
    private var clientStateJob: Job? = null

    init {
        DurakAudioManager.initialize()
        DurakAudioManager.musicVolume = _musicVolume.value
        DurakAudioManager.sfxVolume = _sfxVolume.value

        viewModelScope.launch {
            combine(_currentScreen, _gameState) { screen, state -> Pair(screen, state) }
                .collect { (screen, state) ->
                    if (screen == Screen.GAME_TABLE && state.matchStatus == MatchStatus.PLAYING && state.deckSize == 0) {
                        DurakAudioManager.startMusic(6)
                    } else if (screen == Screen.MAIN_MENU || screen == Screen.OFFLINE_SETUP ||
                               screen == Screen.MULTIPLAYER_HUB || screen == Screen.STATS_BOARD ||
                               screen == Screen.GAME_TABLE) {
                        DurakAudioManager.startMusic(5)
                    } else {
                        DurakAudioManager.stopMusic()
                    }
                }
        }

        viewModelScope.launch {
            multiplayerManager.incomingMessages.collect { rawMsg ->
                if (rawMsg != null) {
                    handleNetworkMessage(rawMsg)
                    multiplayerManager.clearReceivedMessage()
                }
            }
        }

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

    // --- Localization ---
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
        "NSD_STATUS" to "LAN Hub Discovery",
        "HOST_LOBBY" to "Host a Lobby",
        "MY_IP" to "Your Host IP:",
        "DISCOVERY_ACTIVE" to "Searching for local hosts...",
        "TAP_TO_CONNECT" to "Click to Connect",
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
        "CHANGELOG_TEXT" to "Version 0.2.2 (Grand Milestone Update)\n\n• Late Game Theme: Enhanced table aesthetics and adapted musical accompaniment when no cards remain in the deck.\n\n• Roadmap Updated: May and 0.2 milestones removed. June milestone redesigned to showcase the upcoming 0.3 Multiplayer Update with WiFi support up to 6 players, quick chat, and lobby improvements.\n\n• Battle Logs: Active match logs removed from the gameplay arena; they are now safely accessible via the Match Archives.\n\n• Multiplayer Improvements: Fixed the matchmaking bug that omitted opponent nicknames in archives and resolved host name visibility issues.",
        "BOT_DECENT_TITLE" to "DECENT AMATEUR BOT",
        "BOT_DECENT_DESC" to "Plays casual valid combinations. Excellent for beginners learning basic durak sequencing.",
        "BOT_AI_TITLE" to "AI ANALYTICAL BOT",
        "BOT_AI_DESC" to "Defends with cold calculation. Tracks all played cards, saves trumps for endgame clutches.",
        "SETTINGS" to "Settings",
        "SOUND_TAB" to "Sound",
        "LANG_TAB" to "Language",
        "MUSIC_VOL" to "Music",
        "SFX_VOL" to "Effects"
    )

    private val ruTranslations = mapOf(
        "APP_TITLE" to "Дурачок Оффлайн",
        "PLAY_OFFLINE" to "Офлайн Игра",
        "PLAY_ONLINE" to "Мультиплеер (LAN)",
        "BOT_SETUP_TITLE" to "Офлайн Настройки",
        "DIFFICULTY" to "Сложность Бота",
        "EASY" to "Легкий (Случайный)",
        "HARD" to "Сложный (Аналитик)",
        "START_GAME" to "Начать Игру",
        "P2P_TITLE" to "Локальная сеть",
        "NSD_STATUS" to "Поиск по локальной сети",
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
        "CHANGELOG_TEXT" to "Версия 0.2.2 (Глобальное обновление)\n\n• Тема поздней игры: Улучшено визуальное оформление стола и изменено музыкальное сопровождение при розыгрыше финальной стадии, когда колода опустела.\n\n• Дорожная карта: Удалены прошедшие этапы Май и 0.2. Июньский этап переработан под будущее обновление «0.3 - Многопользовательское обновление» с поддержкой Wi-Fi до 6 игроков, быстрым чатом и улучшениями лобби.\n\n• Логи боя: Кнопка логов убрана из игровых экранов; вся история ходов теперь доступна только через Архивы матчей.\n\n• Оптимизация сети: Исправлен баг, из-за которого никнеймы оппонентов не сохранялись в архивах, а также исправлено отображение имени хоста у клиентов.",
        "BOT_DECENT_TITLE" to "ЛЮБИТЕЛЬСКИЙ БОТ",
        "BOT_DECENT_DESC" to "Разыгрывает простые допустимые комбинации. Отлично подходит для начинающих.",
        "BOT_AI_TITLE" to "АНАЛИТИЧЕСКИЙ ИИ-БОТ",
        "BOT_AI_DESC" to "Защищается с холодным расчетом. Отслеживает все сыгранные карты, бережет козыри.",
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
        "NSD_STATUS" to "Rilevamento LAN",
        "HOST_LOBBY" to "Crea una Stanza",
        "MY_IP" to "Tuo IP Host:",
        "DISCOVERY_ACTIVE" to "Ricerca host locali...",
        "TAP_TO_CONNECT" to "Clicca per connetterti",
        "MANUAL_CONNECT" to "Connessione IP Diretta",
        "ENTER_HOST_IP" to "Inserisci IP dell'host",
        "CONNECT_BTN" to "Connetti",
        "WAITING_LOBBY" to "Stanza aperta. In attesa...",
        "DISCONNECTED" to "Disconnesso",
        "CONNECTING" to "Connessione...",
        "BACK" to "Indietro",
        "TAKE" to "Prendi Tutto",
        "BITO" to "Bito / Passa",
        "RESTART" to "Gioca Ancora",
        "MENU" to "Menu Principale",
        "DECK" to "Mazzo",
        "TRUMP" to "Trionfo",
        "STATS_TITLE" to "Archivi Partite",
        "CLEAR_STATS" to "Cancella Archivi",
        "EMPTY_STATS" to "Nessuna cronologia.",
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
        "CHANGELOG_TEXT" to "Versione 0.2.2 (Grande aggiornamento della Roadmap)\n\n• Tema fine partita: Estetica del tavolo e accompagnamento musicale modificati per la fase finale, quando il mazzo si esaurisce.\n\n• Roadmap aggiornata: Rimossi gli scaglioni di Maggio e 0.2. Riprogettato il traguardo di Giugno per introdurre il prossimo grande «0.3 - Aggiornamento multigiocatore» con supporto Wi-Fi fino a 6 partecipanti, chat rapida e miglioramenti alle lobby.\n\n• Registro della partita: Rimosso il pulsante dei log durante il gioco; ora il registro è visualizzabile solo all'interno degli Archivi delle partite.\n\n• Ottimizzazione di rete: Risolto il problema della mancata registrazione dei nickname degli avversari negli archivi storici e del placeholder per l'host sui client connessi.",
        "BOT_DECENT_TITLE" to "BOT AMATORIALE",
        "BOT_DECENT_DESC" to "Gioca combinazioni semplici e valide.",
        "BOT_AI_TITLE" to "BOT ANALITICO IA",
        "BOT_AI_DESC" to "Difende con freddo calcolo.",
        "SETTINGS" to "Impostazioni",
        "SOUND_TAB" to "Audio",
        "LANG_TAB" to "Lingua",
        "MUSIC_VOL" to "Musica",
        "SFX_VOL" to "Effetti"
    )

    private val uaTranslations = mapOf(
        "APP_TITLE" to "Дурник Офлайн",
        "PLAY_OFFLINE" to "Грати Офлайн",
        "PLAY_ONLINE" to "Мультиплеєр (LAN)",
        "BOT_SETUP_TITLE" to "Офлайн Налаштування",
        "DIFFICULTY" to "Складність Бота",
        "EASY" to "Легкий (Випадковий)",
        "HARD" to "Складний (Аналітик)",
        "START_GAME" to "Почати Гру",
        "P2P_TITLE" to "Локальна мережа",
        "NSD_STATUS" to "Пошук в LAN",
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
        "CHANGELOG_TEXT" to "Версія 0.2.2 (Глобальне оновлення)\n\n• Тема пізньої гри: Покращено візуальне оновлення ігрового столу та змінено музичний супровід на фінальній стадії, коли колода закінчилася.\n\n• Дорожня карта: Вилучено пройдешні етапи Травень та 0.2. Червневий етап перероблено під майбутнє оновлення «0.3 - Багатокористувацьке оновлення» з підтримкою Wi-Fi до 6 гравців, швидким чатом та покращеннями лобі.\n\n• Логи битви: Кнопку логів битви прибрано з ігрового екрану; вся історія ходів тепер доступна виключно в Архіві матчів.\n\n• Мережеві виправлення: Виправлено баг, через який нікнейми опонентів не записувалися в архів матчів, а також покращено відображення імені хоста у підключених клієнтів.",
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

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
        prefs.putString("lang", lang.name)
    }

    fun toggleLanguage() {
        val next = when (_appLanguage.value) {
            AppLanguage.EN -> AppLanguage.RU
            AppLanguage.RU -> AppLanguage.IT
            AppLanguage.IT -> AppLanguage.UA
            AppLanguage.UA -> AppLanguage.EN
        }
        _appLanguage.value = next
        prefs.putString("lang", next.name)
    }

    fun setDifficulty(hard: Boolean) { _isBotHard.value = hard }

    fun navigateTo(screen: Screen) {
        if (screen != Screen.GAME_TABLE && screen != Screen.MULTIPLAYER_HUB) {
            multiplayerManager.stopAll()
        }
        _currentScreen.value = screen
    }

    // --- GAME CONTROL ACTIONS ---

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
        DurakAudioManager.playSFX(1)
        _currentScreen.value = Screen.GAME_TABLE
        triggerBotRoutineIfNeeded()
    }

    fun startHostingLobby() {
        _activeMode.value = GameMode.ONLINE_HOST
        hasPersistedThisGame = false
        _opponentNickname.value = "Guest"
        multiplayerManager.startHost()
        _currentScreen.value = Screen.MULTIPLAYER_HUB

        hostStateJob?.cancel()
        hostStateJob = viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_HOST) {
                    val pName = if (_playerNickname.value.isBlank()) "Player" else _playerNickname.value
                    multiplayerManager.sendMessage("NICKNAME:$pName")

                    delay(300)
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
                    DurakAudioManager.playSFX(1)
                    pushHostStateToClient()
                    _currentScreen.value = Screen.GAME_TABLE
                }
            }
        }
    }

    fun startSearchingHosts() {
        _activeMode.value = GameMode.ONLINE_CLIENT
        _opponentNickname.value = "Host"
        multiplayerManager.startHostDiscovery()
        _currentScreen.value = Screen.MULTIPLAYER_HUB

        clientStateJob?.cancel()
        clientStateJob = viewModelScope.launch {
            multiplayerManager.connectionState.collect { netState ->
                if (netState == MultiplayerManager.State.CONNECTED && _activeMode.value == GameMode.ONLINE_CLIENT) {
                    val pName = if (_playerNickname.value.isBlank()) "Player" else _playerNickname.value
                    multiplayerManager.sendMessage("NICKNAME:$pName")
                    DurakAudioManager.playSFX(1)
                    _currentScreen.value = Screen.GAME_TABLE
                }
            }
        }
    }

    fun connectToIpAddress(ip: String) {
        multiplayerManager.connectToHost(ip)
    }

    // --- PLAYER ACTION ROUTERS ---

    fun playCard(card: Card, forceDefenseOnly: Boolean = false, intentTransferOnly: Boolean = false, targetAttackCard: Card? = null) {
        val snapshot = _gameState.value
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        if (_activeMode.value == GameMode.OFFLINE) {
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (!intentTransferOnly) {
                    if (engine.performAttack("player", card)) {
                        DurakAudioManager.playSFX(2)
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
                    }
                }
            } else {
                val canTransfer = engine.isTransferMode &&
                        engine.tablePairs.isNotEmpty() &&
                        engine.tablePairs.all { it.defenseCard == null } &&
                        engine.tablePairs.any { it.attackCard.rank == card.rank }

                if (canTransfer && intentTransferOnly) {
                    if (engine.performTransfer("player", card)) {
                        DurakAudioManager.playSFX(4)
                        refreshLocalState()
                        triggerBotRoutineIfNeeded()
                    }
                } else if (!intentTransferOnly) {
                    val undefendedPair = if (targetAttackCard != null) {
                        engine.tablePairs.find { it.attackCard == targetAttackCard && it.defenseCard == null }
                    } else {
                        engine.tablePairs.find { it.defenseCard == null }
                    }
                    if (undefendedPair != null) {
                        if (engine.performDefense("player", undefendedPair.attackCard, card)) {
                            DurakAudioManager.playSFX(2)
                            refreshLocalState()
                            triggerBotRoutineIfNeeded()
                        }
                    }
                }
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            val isUserAttacking = (engine.attackerId == "player")
            if (isUserAttacking) {
                if (!intentTransferOnly) {
                    if (engine.performAttack("player", card)) {
                        DurakAudioManager.playSFX(2)
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
                        DurakAudioManager.playSFX(4)
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
                            DurakAudioManager.playSFX(2)
                            pushHostStateToClient()
                        }
                    }
                }
            }
        } else {
            val isClientAttacking = (snapshot.attackerPlayerId == "opponent")
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
                if (intentTransferOnly) DurakAudioManager.playSFX(4) else DurakAudioManager.playSFX(2)
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
                    engine.log(
                        "You took back card ${card.rank.symbol}${card.suit.symbol}",
                        "Вы забрали назад карту ${card.rank.symbol}${card.suit.symbol}"
                    )
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

    fun pressBito() {
        if (_activeMode.value == GameMode.OFFLINE) {
            if (engine.performBito("player")) {
                refreshLocalState()
                triggerBotRoutineIfNeeded()
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            if (engine.performBito("player")) pushHostStateToClient()
        } else {
            multiplayerManager.sendMessage(NetworkProtocol.encodeActionBito())
        }
    }

    fun pressTakeAll() {
        if (_activeMode.value == GameMode.OFFLINE) {
            if (engine.performTakeAll("player")) {
                refreshLocalState()
                triggerBotRoutineIfNeeded()
            }
        } else if (_activeMode.value == GameMode.ONLINE_HOST) {
            if (engine.performTakeAll("player")) pushHostStateToClient()
        } else {
            multiplayerManager.sendMessage(NetworkProtocol.encodeActionTake())
        }
    }

    private fun pushHostStateToClient() {
        val hostSnapshot = engine.createSnapshot(opponentName = _opponentNickname.value)
        _gameState.value = hostSnapshot

        val clientHand = engine.opponentHand.toList()
        val hostHand = engine.playerHand.toList()

        val clientStatus = when (hostSnapshot.matchStatus) {
            MatchStatus.WON -> MatchStatus.LOST
            MatchStatus.LOST -> MatchStatus.WON
            else -> hostSnapshot.matchStatus
        }

        val clientSnapshot = hostSnapshot.copy(
            matchStatus = clientStatus,
            isLocalTurn = (engine.attackerId == "opponent"),
            localHand = clientHand,
            opponentHandSize = hostHand.size,
            opponentName = _playerNickname.value,
            canBito = (engine.attackerId == "opponent") && engine.tablePairs.isNotEmpty() && engine.tablePairs.all { it.defenseCard != null },
            canTake = (engine.attackerId == "player") && engine.tablePairs.isNotEmpty() && engine.tablePairs.any { it.defenseCard == null }
        )

        val serializedMsg = NetworkProtocol.serializeState(clientSnapshot, hostHand, clientHand, _playerNickname.value)
        multiplayerManager.sendMessage(serializedMsg)

        checkAndPersistRoomResult(hostSnapshot)
    }

    private fun refreshLocalState() {
        val snapshot = engine.createSnapshot().copy(opponentName = "AI Bot")
        _gameState.value = snapshot
        checkAndPersistRoomResult(snapshot)
    }

    private fun checkAndPersistRoomResult(snapshot: GameStateSnapshot) {
        if (snapshot.matchStatus == MatchStatus.WON ||
            snapshot.matchStatus == MatchStatus.LOST ||
            snapshot.matchStatus == MatchStatus.DRAW
        ) {
            if (hasPersistedThisGame) return
            hasPersistedThisGame = true

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

    private fun triggerBotRoutineIfNeeded() {
        val snapshot = engine.createSnapshot()
        if (snapshot.matchStatus != MatchStatus.PLAYING) return

        val isBotActiveTurn = (engine.attackerId == "opponent" && (engine.tablePairs.isEmpty() || engine.tablePairs.all { it.defenseCard != null })) ||
                              (engine.attackerId == "player" && engine.tablePairs.any { it.defenseCard == null })
        if (isBotActiveTurn && !_botThinking.value) {
            _botThinking.value = true
            viewModelScope.launch {
                delay(1200)
                val prevAttacker = engine.attackerId
                val isTransferPossible = engine.isTransferMode && engine.tablePairs.isNotEmpty() && engine.tablePairs.all { it.defenseCard == null }
                val botSuccess = engine.makeBotMove(_isBotHard.value)
                _botThinking.value = false
                refreshLocalState()
                if (botSuccess) {
                    if (isTransferPossible && engine.attackerId != prevAttacker) {
                        DurakAudioManager.playSFX(4)
                    } else if (engine.tablePairs.isEmpty() && prevAttacker != engine.attackerId) {
                        // Bito happened
                    } else {
                        DurakAudioManager.playSFX(3)
                    }
                    triggerBotRoutineIfNeeded()
                }
            }
        }
    }

    private fun handleNetworkMessage(msg: String) {
        if (msg.startsWith("NICKNAME:")) {
            val name = msg.removePrefix("NICKNAME:").trim()
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
            var stateChanged = false
            when {
                msg.startsWith("ACTION_ATTACK:") -> {
                    val card = NetworkProtocol.decodeCard(msg.removePrefix("ACTION_ATTACK:"))
                    if (card != null && engine.performAttack("opponent", card)) {
                        DurakAudioManager.playSFX(3)
                        stateChanged = true
                    }
                }
                msg.startsWith("ACTION_TAKE_BACK:") -> {
                    val card = NetworkProtocol.decodeCard(msg.removePrefix("ACTION_TAKE_BACK:"))
                    if (card != null && engine.attackerId == "opponent") {
                        val pairIndex = engine.tablePairs.indexOfFirst { it.attackCard == card && it.defenseCard == null }
                        if (pairIndex != -1) {
                            engine.tablePairs.removeAt(pairIndex)
                            engine.opponentHand.add(card)
                            engine.log(
                                "Opponent took back card ${card.rank.symbol}${card.suit.symbol}",
                                "Оппонент забрал назад карту ${card.rank.symbol}${card.suit.symbol}"
                            )
                            stateChanged = true
                        }
                    }
                }
                msg.startsWith("ACTION_DEFEND:") -> {
                    val parts = msg.removePrefix("ACTION_DEFEND:").split("|")
                    if (parts.size == 2) {
                        val attackCard = NetworkProtocol.decodeCard(parts[0])
                        val defenseCard = NetworkProtocol.decodeCard(parts[1])
                        if (attackCard != null && defenseCard != null && engine.performDefense("opponent", attackCard, defenseCard)) {
                            DurakAudioManager.playSFX(3)
                            stateChanged = true
                        }
                    }
                }
                msg.startsWith("ACTION_TRANSFER:") -> {
                    val card = NetworkProtocol.decodeCard(msg.removePrefix("ACTION_TRANSFER:"))
                    if (card != null && engine.performTransfer("opponent", card)) {
                        DurakAudioManager.playSFX(4)
                        stateChanged = true
                    }
                }
                msg == "ACTION_TAKE" -> { if (engine.performTakeAll("opponent")) stateChanged = true }
                msg == "ACTION_BITO" -> { if (engine.performBito("opponent")) stateChanged = true }
            }
            if (stateChanged) pushHostStateToClient()
        } else {
            val oldSnapshot = _gameState.value
            val statePayload = NetworkProtocol.deserializeState(msg)
            if (statePayload != null) {
                val isClientAttacking = (statePayload.attackerId == "opponent")
                _opponentNickname.value = statePayload.hostNick

                val currentSnapshot = GameStateSnapshot(
                    trumpCard = statePayload.trumpCard,
                    trumpSuit = statePayload.trumpSuit,
                    deckSize = statePayload.deckSize,
                    tablePairs = statePayload.tablePairs,
                    discardPileSize = statePayload.discardPileSize,
                    isLocalTurn = if (isClientAttacking) statePayload.tablePairs.all { it.defenseCard != null } || statePayload.tablePairs.isEmpty() else statePayload.tablePairs.any { it.defenseCard == null },
                    localHand = statePayload.clientHand,
                    opponentHandSize = statePayload.hostHand.size,
                    opponentName = statePayload.hostNick,
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

                if (oldSnapshot.matchStatus == MatchStatus.PLAYING) {
                    val oldSize = oldSnapshot.tablePairs.size
                    val newSize = currentSnapshot.tablePairs.size
                    val oldDefCount = oldSnapshot.tablePairs.count { it.defenseCard != null }
                    val newDefCount = currentSnapshot.tablePairs.count { it.defenseCard != null }

                    if (newSize > oldSize) {
                        val attackerChanged = oldSnapshot.attackerPlayerId != currentSnapshot.attackerPlayerId
                        if (currentSnapshot.isTransferMode && attackerChanged && oldSize > 0) {
                            DurakAudioManager.playSFX(4)
                        } else if (!currentSnapshot.isLocalTurn) {
                            DurakAudioManager.playSFX(3)
                        }
                    } else if (newDefCount > oldDefCount && !currentSnapshot.isLocalTurn) {
                        DurakAudioManager.playSFX(3)
                    }
                }
            }
        }
    }

    fun clearMatchHistory() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAll() }
    }

    fun onClosed() {
        multiplayerManager.stopAll()
        DurakAudioManager.stopMusic()
        viewModelScope.coroutineContext[Job]?.cancel()
    }
}
