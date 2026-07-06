package com.balltrack.ai.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ──────────────────────────────────────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: String,
    val gameMode: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationSeconds: Int,
    val makes: Int,
    val attempts: Int,
    val homeScore: Int,
    val awayScore: Int,
    val avgReleaseAngle: Float?,
    val avgShotSpeedSeconds: Float?,
    val peakStreak: Int,
    val videoPath: String?,        // local file path only, never uploaded
    val courtZoneJson: String?,    // JSON map of zone → makes/attempts
    val rulesJson: String          // serialized GameRules
)

@Entity(tableName = "shots")
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val isMake: Boolean?,          // null = inconclusive (ball tracking lost it)
    val releaseAngle: Float?,
    val releaseSpeedSeconds: Float?,
    val courtZone: String?,        // "corner_left", "wing_left", "top_key", etc.
    val pointValue: Int,
    val clipStartMs: Long?,        // local video clip range for replay
    val clipEndMs: Long?
)

@Entity(tableName = "custom_rules")
data class CustomRulesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── DAOs ──────────────────────────────────────────────────────────────────

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity): Long
    @Update suspend fun update(session: SessionEntity)
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC") fun allSessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun getById(id: Long): SessionEntity?
    @Query("SELECT * FROM sessions WHERE sport = :sport ORDER BY startTimestamp DESC") fun bySport(sport: String): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE startTimestamp >= :since ORDER BY startTimestamp DESC") fun since(since: Long): Flow<List<SessionEntity>>
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface ShotDao {
    @Insert suspend fun insert(shot: ShotEntity): Long
    @Insert suspend fun insertAll(shots: List<ShotEntity>)
    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY timestampMs ASC") fun forSession(sessionId: Long): Flow<List<ShotEntity>>
    @Query("SELECT * FROM shots WHERE sessionId = :sessionId AND isMake = 1") suspend fun makesForSession(sessionId: Long): List<ShotEntity>
}

@Dao
interface CustomRulesDao {
    @Insert suspend fun insert(rules: CustomRulesEntity): Long
    @Update suspend fun update(rules: CustomRulesEntity)
    @Delete suspend fun delete(rules: CustomRulesEntity)
    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC") fun all(): Flow<List<CustomRulesEntity>>
}

// ─── Database ──────────────────────────────────────────────────────────────

@Database(entities = [SessionEntity::class, ShotEntity::class, CustomRulesEntity::class], version = 1, exportSchema = false)
abstract class SportTrackDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun customRulesDao(): CustomRulesDao

    companion object {
        @Volatile private var INSTANCE: SportTrackDatabase? = null
        fun getInstance(context: android.content.Context): SportTrackDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, SportTrackDatabase::class.java, "sporttrack.db")
                    .build().also { INSTANCE = it }
            }
    }
}
