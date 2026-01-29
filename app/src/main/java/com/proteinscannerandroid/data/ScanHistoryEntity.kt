package com.proteinscannerandroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val productName: String,
    val pdcaasScore: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val proteinSourcesJson: String,  // JSON array of protein source names
    val proteinPer100g: Double?
)
