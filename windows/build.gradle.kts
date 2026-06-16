import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.2"
}

group = "com.example.desktop"
version = "0.2.2"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.components.resources)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Lightweight JSON for stats persistence
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    // MP3 decoding (Android raw resources are .mp3; javax.sound only does WAV
    // out of the box). mp3spi registers an MP3 reader with AudioSystem so we
    // can keep using SourceDataLine/Clip just like for WAV files.
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")

    // Cross-platform mDNS / Bonjour discovery so the Windows host shows up
    // in the Android NSD list (and vice versa) over Wi-Fi.
    implementation("org.jmdns:jmdns:3.5.9")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Dyrachok"
            packageVersion = "0.2.2"
            description = "Dyrachok - Classic Russian Card Game (Windows port)"
            copyright = "© 2026 Dyrachok. All rights reserved."
            vendor = "Dyrachok"

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "5d8c1c44-7c2c-4a4e-9bbb-a5ad4b8c2c0e"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}

// Автоматически копируем собранный exe-инсталлятор в windows/tools/
tasks.named("packageExe") {
    doLast {
        copy {
            from(layout.buildDirectory.dir("compose/binaries/main/exe"))
            into(project.file("tools"))
            include("*.exe")
        }
    }
}

// Автоматически вытаскиваем портативную (запускаемую) версию в windows/Dyrachok_Portable/
tasks.named("createDistributable") {
    doLast {
        copy {
            from(layout.buildDirectory.dir("compose/binaries/main/app/Dyrachok"))
            into(project.file("Dyrachok_Portable"))
        }
    }
}
