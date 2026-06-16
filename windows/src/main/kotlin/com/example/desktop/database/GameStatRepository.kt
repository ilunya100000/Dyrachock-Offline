package com.example.desktop.database

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Simple data class that mirrors the Android `GameStat` entity. */
data class GameStat(
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String,
    val result: String,
    val opponentName: String,
    val durationSeconds: Long = 0,
    val matchLogEn: String = "",
    val matchLogRu: String = ""
)

/**
 * File-based repository that persists game statistics to a JSON file in the
 * user home directory (e.g. `~/.dyrachok/stats.json`). All reads/writes are
 * synchronous because the dataset is tiny.
 */
class GameStatRepository(
    storageFile: File = File(System.getProperty("user.home"), ".dyrachok/stats.json")
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, GameStat::class.java)
    private val adapter: JsonAdapter<List<GameStat>> = moshi.adapter(listType)

    private val file: File = storageFile.also { it.parentFile?.mkdirs() }

    private val _allStats = MutableStateFlow<List<GameStat>>(loadFromDisk())
    val allStats: StateFlow<List<GameStat>> = _allStats

    private fun loadFromDisk(): List<GameStat> {
        return try {
            if (!file.exists()) emptyList()
            else adapter.fromJson(file.readText()) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(items: List<GameStat>) {
        try {
            file.writeText(adapter.toJson(items))
        } catch (_: Exception) { /* ignored */ }
    }

    @Synchronized
    fun insert(stat: GameStat) {
        val nextId = (_allStats.value.maxOfOrNull { it.id } ?: 0) + 1
        val updated = listOf(stat.copy(id = nextId)) + _allStats.value
        _allStats.value = updated
        saveToDisk(updated)
    }

    @Synchronized
    fun clearAll() {
        _allStats.value = emptyList()
        saveToDisk(emptyList())
    }
}
