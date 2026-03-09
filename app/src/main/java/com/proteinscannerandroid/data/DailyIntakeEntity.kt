package com.proteinscannerandroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_intake")
data class DailyIntakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,                    // "2026-03-09" format
    val productName: String,
    val proteinGrams: Double,
    val pdcaasScore: Double?,
    val effectiveProteinGrams: Double?,  // proteinGrams * pdcaasScore
    val barcode: String?,
    val timestamp: Long = System.currentTimeMillis()
)
