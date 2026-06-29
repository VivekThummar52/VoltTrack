package com.codecraft.volttrack.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val startPct: Double,
    val endPct: Double,
    val maxWatts: Double,
    /** Wall-clock instant when the OS reported full/100% while still plugged, if observed. */
    val chargeCompletedAtMs: Long? = null,
    val maxTemp: Double = 0.0
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ChargingSession): Long

    @Query("UPDATE sessions SET endPct = :pct, maxWatts = :watts, maxTemp = :temp WHERE startTime = :start")
    suspend fun updateActiveSession(start: Long, pct: Double, watts: Double, temp: Double)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<ChargingSession>>
}

@Database(entities = [ChargingSession::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN chargeCompletedAtMs INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN maxTemp REAL NOT NULL DEFAULT 0.0")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "volt_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}