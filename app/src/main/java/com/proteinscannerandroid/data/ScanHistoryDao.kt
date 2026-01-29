package com.proteinscannerandroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ScanHistoryEntity): Long

    @Delete
    suspend fun delete(history: ScanHistoryEntity)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM scan_history WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ScanHistoryEntity>

    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}
