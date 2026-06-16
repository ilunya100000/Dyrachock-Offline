/*
 * Standalone Gradle project for the Windows desktop port of Dyrachok.
 * Open this `windows/` folder as its own project in IntelliJ IDEA or run via
 * `gradle run` from inside this folder. It is intentionally decoupled from
 * the Android `app/` module so versions and plugins do not collide.
 */
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "dyrachok-windows"
