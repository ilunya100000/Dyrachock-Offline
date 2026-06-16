package com.example.desktop.preferences

import java.util.prefs.Preferences as JvmPreferences

/**
 * Thin wrapper over `java.util.prefs.Preferences` that mimics the
 * `SharedPreferences` surface used by the Android ViewModel.
 */
class Preferences(node: String = "com/example/dyrachok") {
    private val prefs: JvmPreferences = JvmPreferences.userRoot().node(node)

    fun getString(key: String, default: String?): String? = prefs.get(key, default)
    fun putString(key: String, value: String) { prefs.put(key, value); prefs.flush() }

    fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    fun putFloat(key: String, value: Float) { prefs.putFloat(key, value); prefs.flush() }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) { prefs.putBoolean(key, value); prefs.flush() }
}
