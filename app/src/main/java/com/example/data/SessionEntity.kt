package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One completed speaking session (F2). Demo-mode sessions (no API key) are kept
 * for goal/streak tracking but flagged so score averages ignore them.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val durationSec: Int,
    val score: Int,
    val wpm: Int,
    val pauses: Int,
    val fillers: Int,
    val accuracy: Int,
    val cefr: String,
    val promptId: String,
    val isDemo: Boolean
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM sessions")
    suspend fun totalDurationSec(): Long

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM sessions WHERE epochDay = :day")
    suspend fun durationSecOn(day: Long): Long

    @Query("SELECT COALESCE(AVG(wpm), 0) FROM sessions WHERE isDemo = 0")
    suspend fun avgWpmReal(): Double

    @Query("SELECT COALESCE(AVG(score), 0) FROM sessions WHERE isDemo = 0")
    suspend fun avgScoreReal(): Double

    @Query("SELECT COALESCE(MAX(score), 0) FROM sessions WHERE isDemo = 0")
    suspend fun bestScoreReal(): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE isDemo = 0 AND wpm BETWEEN 120 AND 150")
    suspend fun pacingSessionCount(): Int

    @Query("SELECT COALESCE(MAX(durationSec), 0) FROM sessions")
    suspend fun longestSessionSec(): Int

    @Query("SELECT DISTINCT epochDay FROM sessions ORDER BY epochDay DESC")
    suspend fun activeEpochDays(): List<Long>
}
