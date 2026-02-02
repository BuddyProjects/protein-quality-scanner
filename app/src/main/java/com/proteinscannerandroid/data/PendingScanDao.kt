package com.proteinscannerandroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingScanDao {
    @Query("SELECT * FROM pending_scans ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PendingScan>>

    @Query("SELECT * FROM pending_scans ORDER BY timestamp ASC")
    suspend fun getAllForRetry(): List<PendingScan>

    @Query("SELECT COUNT(*) FROM pending_scans")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_scans")
    suspend fun getCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingScan: PendingScan): Long

    @Delete
    suspend fun delete(pendingScan: PendingScan)

    @Query("DELETE FROM pending_scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_scans WHERE barcode = :barcode")
    suspend fun deleteByBarcode(barcode: String)

    @Query("DELETE FROM pending_scans")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM pending_scans WHERE barcode = :barcode)")
    suspend fun exists(barcode: String): Boolean
}
