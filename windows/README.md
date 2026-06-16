# Dyrachok — Windows Port

Полноценный десктоп-порт Android-игры **Dyrachok Offline** на базе
[JetBrains Compose Multiplatform for Desktop](https://www.jetbrains.com/lp/compose-multiplatform/).
Использует тот же UI и игровой движок, что и Android-версия, но с
платформо-специфичными адаптерами для звука, БД и сети.

---

## Что внутри

| Компонент          | Android-версия                | Windows-версия                                           |
|--------------------|-------------------------------|----------------------------------------------------------|
| UI                 | Jetpack Compose               | Compose Multiplatform (тот же код)                       |
| Игровой движок     | `DurakEngine.kt` (Kotlin)     | `DurakEngine.kt` (идентичный)                            |
| ViewModel          | `AndroidViewModel`            | Plain class + собственный `CoroutineScope`               |
| Звук               | `MediaPlayer` + `R.raw`       | `javax.sound.sampled` + ресурсы `/sfx/`, `/music/`       |
| Хранение статистики| Room SQLite                   | JSON-файл в `%USERPROFILE%\.dyrachok\stats.json`         |
| Настройки          | `SharedPreferences`           | `java.util.prefs.Preferences` (системный реестр)         |
| Multiplayer (TCP)  | `java.net` Socket             | `java.net` Socket (тот же протокол)                      |
| Discovery          | Android NSD (mDNS)            | UDP broadcast на порту 8889 (LAN)                        |

---

## Требования к сборке

- **JDK 17** или новее (рекомендуется JetBrains Runtime / Temurin 17).
- **Gradle 8.5+** — либо системный, либо через wrapper из Android-проекта.

> Гарантированно совместимая связка зафиксирована в `build.gradle.kts`:
> Kotlin **2.1.20** + Compose Multiplatform **1.8.2**.

---

## Запуск из исходников

Откройте папку `windows/` как отдельный проект в **IntelliJ IDEA**
(Community или Ultimate) и выполните `Run` на `Main.kt`. Либо из терминала:

```powershell
cd windows
gradle run                       # запуск в дев-режиме
```

---

## Сборка дистрибутива Windows

Compose Desktop умеет упаковывать приложение в нативный установщик через jpackage
(требует JDK 17 с `jpackage` и WiX Toolset 3.x для MSI).

```powershell
cd windows
gradle packageMsi                # одиночный .msi инсталлятор
gradle packageExe                # одиночный .exe инсталлятор
gradle packageDistributionForCurrentOS
```

Готовые артефакты появятся в `build/compose/binaries/main/`.

Для добавления иконки положите `icon.ico` в `src/main/resources/` и
раскомментируйте строку `iconFile.set(...)` в `build.gradle.kts`.

---

## Звуковые ресурсы

В этой ветке audio-файлы не входят в комплект (Android-версия использует
`R.raw.*`). Чтобы включить музыку и SFX:

1. Создайте папки `src/main/resources/sfx/` и `src/main/resources/music/`.
2. Положите туда WAV-файлы с именами:
   - `sfx/playing_cards_shuffle.wav`
   - `sfx/you_play.wav`
   - `sfx/opponent.wav`
   - `sfx/playing_cards_transfer.wav`
   - `music/bah_shutka.wav`
   - `music/lunnaya_sonata.wav`
3. Пересоберите проект. При отсутствии файлов игра запустится без звука.

---

## Multiplayer

- Хостинг открывает TCP-сервер на порту **8888** и UDP-broadcast «маяк» на **8889**.
- Клиент слушает UDP на 8889 и подхватывает все хосты в одной подсети.
- Если discovery не работает (фаервол / VPN), используйте «Direct IP Connection»
  и введите IP сервера вручную.
- Брандмауэр Windows при первом запуске спросит разрешение для Java —
  разрешите для **Private networks**.

---

## Известные ограничения

- Текстовое поле никнейма допускает любые символы — это родное поведение
  Compose, на десктопе виртуальной клавиатуры нет.
- Жесты drag-and-drop карт работают мышью; multi-touch не используется.
- Drag-обработка калибрована под Android-плотность экрана; на 4K-мониторе
  пороги перетаскивания (`-100f`, `-140f`) можно подкорректировать в
  `DurakUi.kt`.
