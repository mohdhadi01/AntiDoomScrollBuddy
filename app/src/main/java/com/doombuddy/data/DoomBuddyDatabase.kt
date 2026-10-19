package com.doombuddy.data

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    /** Duration attributed to the LOCAL CALENDAR DAY of startedAtEpochMs (see SessionSplitter). */
    val durationMs: Long,
    val interventionCount: Int,
    /** True when the user left within a short window after the last intervention. */
    val quickExitAfterIntervention: Boolean,
    /** False for midnight-split continuation rows — only true rows count as an "open". */
    val isSessionStart: Boolean = true,
    /** Checkpoint rows are invisible until finalized (replaced by split rows). */
    val isCheckpoint: Boolean = false,
)

data class PackageOpens(val packageName: String, val opens: Int)

@Entity(tableName = "interventions")
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val level: Int,
    val startedAtEpochMs: Long,
    /** Null while the intervention is still on screen (persistent until hold-dismiss/exit). */
    val dismissedAtEpochMs: Long? = null,
    val characterId: String,
)

data class PackageUsage(val packageName: String, val totalMs: Long)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity)

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAtEpochMs = :endedAtEpochMs, durationMs = :durationMs WHERE id = :id")
    suspend fun updateSessionDuration(id: Long, endedAtEpochMs: Long, durationMs: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    /** Completed sessions of a calendar day, newest first (checkpoints excluded). */
    @Query(
        """
        SELECT * FROM sessions
        WHERE isCheckpoint = 0 AND startedAtEpochMs >= :sinceEpochMs AND durationMs > 0
        ORDER BY startedAtEpochMs DESC
        LIMIT 50
        """
    )
    fun sessionsSince(sinceEpochMs: Long): Flow<List<SessionEntity>>

    /** Opens today per package: only true session starts count. */
    @Query(
        """
        SELECT packageName, COUNT(*) as opens FROM sessions
        WHERE isCheckpoint = 0 AND isSessionStart = 1 AND startedAtEpochMs >= :sinceEpochMs
        GROUP BY packageName
        ORDER BY opens DESC
        """
    )
    fun opensSince(sinceEpochMs: Long): Flow<List<PackageOpens>>

    @Insert
    suspend fun insertIntervention(intervention: InterventionEntity): Long

    @Query("UPDATE interventions SET dismissedAtEpochMs = :atMs WHERE id = :id")
    suspend fun markInterventionDismissed(id: Long, atMs: Long)

    @Query("SELECT SUM(durationMs) FROM sessions WHERE startedAtEpochMs >= :sinceEpochMs AND packageName IN (:packages)")
    fun totalUsageSince(sinceEpochMs: Long, packages: List<String>): Flow<Long?>

    /** Non-flow snapshot for the service's live-today baseline (read once per session). */
    @Query("SELECT SUM(durationMs) FROM sessions WHERE startedAtEpochMs >= :sinceEpochMs AND packageName IN (:packages)")
    suspend fun totalUsageSinceOnce(sinceEpochMs: Long, packages: List<String>): Long?

    @Query(
        """
        SELECT packageName, SUM(durationMs) as totalMs FROM sessions
        WHERE startedAtEpochMs >= :sinceEpochMs
        GROUP BY packageName
        ORDER BY totalMs DESC
        """
    )
    fun usageByPackageSince(sinceEpochMs: Long): Flow<List<PackageUsage>>

    @Query(
        """
        SELECT SUM(durationMs) FROM sessions
        WHERE startedAtEpochMs >= :sinceEpochMs AND startedAtEpochMs < :untilEpochMs
        """
    )
    suspend fun totalUsageBetween(sinceEpochMs: Long, untilEpochMs: Long): Long?
}

@Database(entities = [SessionEntity::class, InterventionEntity::class], version = 3, exportSchema = false)
abstract class DoomBuddyDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: DoomBuddyDatabase? = null

        fun get(context: Context): DoomBuddyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, DoomBuddyDatabase::class.java, "doombuddy.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Attributes session duration to local calendar days. A session crossing midnight
 * stays continuous as an intervention/session experience, but its duration is
 * persisted as ONE ROW PER DAY so daily totals split correctly:
 *
 *   23:59:30 → 00:00:30  ⇒  Sep 29: 30_000ms, Sep 30: 30_000ms
 */
object SessionSplitter {

    /** (dayStartEpochMs, durationMs) pairs, one per local calendar day touched. */
    fun split(startEpochMs: Long, endEpochMs: Long, zone: ZoneId): List<Pair<Long, Long>> {
        if (endEpochMs <= startEpochMs) return emptyList()
        val days = mutableListOf<Pair<Long, Long>>()
        var cursor = startEpochMs
        while (cursor < endEpochMs) {
            val dayStart = LocalDate.ofInstant(Instant.ofEpochMilli(cursor), zone)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDayStart = dayStart + 24 * 60 * 60 * 1000L
            val chunkEnd = minOf(endEpochMs, nextDayStart)
            days += dayStart to (chunkEnd - cursor)
            cursor = chunkEnd
        }
        return days
    }

    fun todayStart(zone: ZoneId, nowEpochMs: Long = System.currentTimeMillis()): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowEpochMs), zone)
            .atStartOfDay(zone).toInstant().toEpochMilli()
}

/** "Time reclaimed" = today's tracked usage vs the trailing 7-day daily average. */
object ReclaimedCalculator {

    fun reclaimedMs(todayMs: Long, trailing7DayAverageMs: Long): Long =
        (trailing7DayAverageMs - todayMs).coerceAtLeast(0)

    /** Live today = what's already persisted for this calendar day + the running session. */
    fun liveTodayMs(persistedTodayMs: Long, currentSessionElapsedMs: Long): Long =
        persistedTodayMs + currentSessionElapsedMs.coerceAtLeast(0)

    /** Reward ladder: reclaimed hours -> unlock ids (procedural cosmetics, no loot boxes). */
    val rewardLadder: List<Pair<Long, String>> = listOf(
        (30L * 60 * 1000) to "unlock_hat",
        (2L * 60 * 60 * 1000) to "unlock_glasses",
        (5L * 60 * 60 * 1000) to "unlock_scarf",
        (10L * 60 * 60 * 1000) to "unlock_sunglasses",
        (25L * 60 * 60 * 1000) to "unlock_bow",
    )

    fun unlocksFor(cumulativeReclaimedMs: Long): List<String> =
        rewardLadder.filter { cumulativeReclaimedMs >= it.first }.map { it.second }
}

/** Today-total milestones the buddy reacts to — rare and meaningful, never shaming. */
object TodayMilestones {
    /** Tracked-usage today (ms) thresholds worth a single reaction. */
    val milestonesMs: List<Long> = listOf(
        30L * 60 * 1000,   // 30m
        60L * 60 * 1000,   // 1h
        2L * 60 * 60 * 1000,
        3L * 60 * 60 * 1000,
        4L * 60 * 60 * 1000,
    )

    /** Returns the first milestone strictly above [previousMs] that [currentMs] reached. */
    fun crossed(previousMs: Long, currentMs: Long): Long? =
        milestonesMs.firstOrNull { it > previousMs && currentMs >= it }
}
