package com.proteinscannerandroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a barcode scan that failed due to network error and is queued for retry.
 */
@Entity(tableName = "pending_scans")
data class PendingScan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val errorReason: String? = null
)
