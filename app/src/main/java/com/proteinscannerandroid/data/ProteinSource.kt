package com.proteinscannerandroid.data

enum class ProteinQuality {
    HIGH, MEDIUM, LOW
}

data class ProteinSource(
    val name: String,
    val pdcaas: Double,
    val keywords: List<String>,
    val category: ProteinQuality
)