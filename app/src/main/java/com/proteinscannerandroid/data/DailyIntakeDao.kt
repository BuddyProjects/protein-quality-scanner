package com.proteinscannerandroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyIntakeDao {
    @Query("SELECT * FROM daily_intake WHERE date = :date ORDER BY timestamp DESC")
    fun getEntriesForDate(date: String): Flow<List<DailyIntakeEntity>>

    @Query("SELECT SUM(proteinGrams) FROM daily_intake WHERE date = :date")
    fun getTotalForDate(date: String): Flow<Double?>

    @Query("SELECT DISTINCT date FROM daily_intake ORDER BY date DESC")
    suspend fun getAllDatesWithEntries(): List<String>

    @Query("SELECT date FROM daily_intake GROUP BY date HAVING SUM(proteinGrams) >= :goal ORDER BY date DESC")
    suspend fun getDatesWhereGoalMet(goal: Double): List<String>

    @Insert
    suspend fun insert(entry: DailyIntakeEntity): Long

    @Delete
    suspend fun delete(entry: DailyIntakeEntity)

    @Query("DELETE FROM daily_intake WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM daily_intake WHERE date = :date")
    suspend fun deleteAllForDate(date: String)
}
