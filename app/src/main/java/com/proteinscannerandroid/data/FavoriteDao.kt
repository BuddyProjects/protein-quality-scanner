package com.proteinscannerandroid.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE barcode = :barcode)")
    suspend fun isFavorite(barcode: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE barcode = :barcode)")
    fun isFavoriteFlow(barcode: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE barcode = :barcode")
    suspend fun deleteByBarcode(barcode: String)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FavoriteEntity>
}
