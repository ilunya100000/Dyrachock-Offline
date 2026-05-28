package com.example.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_stats")
data class GameStat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String,          // "OFFLINE", "ONLINE"
    val result: String,        // "WON", "LOST", "DRAW"
    val opponentName: String,
    val durationSeconds: Long = 0
)

@Dao
interface GameStatDao {
    @Query("SELECT * FROM game_stats ORDER BY timestamp DESC")
    fun getAllStats(): Flow<List<GameStat>>

    @Insert
    suspend fun insertStat(stat: GameStat)

    @Query("DELETE FROM game_stats")
    suspend fun clearAllStats()
}

@Database(entities = [GameStat::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameStatDao(): GameStatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "durak_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GameStatRepository(private val dao: GameStatDao) {
    val allStats: Flow<List<GameStat>> = dao.getAllStats()

    suspend fun insert(stat: GameStat) {
        dao.insertStat(stat)
    }

    suspend fun clearAll() {
        dao.clearAllStats()
    }
}
