package com.proteinscannerandroid.data

object PdcaasDatabase {
    
    private val proteinSources = listOf(
        // High quality proteins (PDCAAS ≥ 0.9)
        ProteinSource("Whey Protein", 1.0, listOf("whey", "molke"), ProteinQuality.HIGH),
        ProteinSource("Casein", 1.0, listOf("casein", "kasein"), ProteinQuality.HIGH),
        ProteinSource("Milk Protein", 1.0, listOf("milk protein", "milchprotein", "milk", "milch", "quark", "cottage cheese", "buttermilk", "joghurt", "yogurt", "cheese", "käse"), ProteinQuality.HIGH),
        ProteinSource("Egg Protein", 1.0, listOf("egg protein", "eiprotein", "albumin", "whole egg", "ganzes ei"), ProteinQuality.HIGH),
        ProteinSource("Chicken", 0.95, listOf("chicken", "huhn", "hähnchen"), ProteinQuality.HIGH),
        ProteinSource("Beef", 0.92, listOf("beef", "rindfleisch"), ProteinQuality.HIGH),
        ProteinSource("Fish", 0.94, listOf("fish", "fisch", "salmon", "lachs", "tuna", "thunfisch"), ProteinQuality.HIGH),
        
        // Medium quality proteins (PDCAAS 0.7-0.89)
        ProteinSource("Soy Protein", 0.85, listOf("soy", "soja", "tofu"), ProteinQuality.MEDIUM),
        ProteinSource("Pea Protein", 0.73, listOf("pea", "erbse", "erbsenprotein"), ProteinQuality.MEDIUM),
        ProteinSource("Rice Protein", 0.75, listOf("rice", "reis", "reisprotein"), ProteinQuality.MEDIUM),
        ProteinSource("Hemp Protein", 0.63, listOf("hemp", "hanf", "hanfprotein"), ProteinQuality.MEDIUM),
        ProteinSource("Chickpeas", 0.71, listOf("chickpea", "kichererbse"), ProteinQuality.MEDIUM),
        ProteinSource("Lentils", 0.69, listOf("lentil", "linse", "linsen"), ProteinQuality.MEDIUM),
        
        // Low quality proteins (PDCAAS < 0.7)
        ProteinSource("Wheat Protein", 0.42, listOf("wheat", "weizen", "gluten"), ProteinQuality.LOW),
        ProteinSource("Collagen", 0.08, listOf("collagen", "kollagen", "gelatin", "gelatine"), ProteinQuality.LOW),
        ProteinSource("Corn Protein", 0.44, listOf("corn", "mais", "maisprotein"), ProteinQuality.LOW),
        ProteinSource("Nuts", 0.52, listOf("nuts", "nüsse", "almond", "mandel", "walnut", "walnuss"), ProteinQuality.LOW),
        ProteinSource("Seeds", 0.58, listOf("seed", "samen", "sunflower", "sonnenblume"), ProteinQuality.LOW),
        ProteinSource("Spirulina", 0.62, listOf("spirulina", "algae", "algen"), ProteinQuality.LOW)
    )

    fun findProteinSources(ingredients: String): List<ProteinSource> {
        val ingredientsLower = ingredients.lowercase()
        return proteinSources.filter { proteinSource ->
            proteinSource.keywords.any { keyword ->
                ingredientsLower.contains(keyword.lowercase())
            }
        }
    }

    fun calculateOverallProteinQuality(proteinSources: List<ProteinSource>): ProteinAnalysisResult {
        if (proteinSources.isEmpty()) {
            return ProteinAnalysisResult(
                averagePdcaas = 0.0,
                qualityCategory = ProteinQuality.LOW,
                warnings = listOf("No identifiable protein sources found in ingredients."),
                foundProteins = emptyList()
            )
        }

        val averagePdcaas = proteinSources.map { it.pdcaas }.average()
        val qualityCategory = when {
            averagePdcaas >= 0.9 -> ProteinQuality.HIGH
            averagePdcaas >= 0.7 -> ProteinQuality.MEDIUM
            else -> ProteinQuality.LOW
        }

        val warnings = mutableListOf<String>()
        
        // Check for low-quality proteins
        val lowQualityProteins = proteinSources.filter { it.category == ProteinQuality.LOW }
        if (lowQualityProteins.isNotEmpty()) {
            warnings.add("Contains low-quality proteins: ${lowQualityProteins.joinToString { it.name }}")
        }

        // Special warning for collagen
        val collagenProteins = proteinSources.filter { it.name.contains("Collagen", ignoreCase = true) }
        if (collagenProteins.isNotEmpty()) {
            warnings.add("⚠️ Collagen has very low PDCAAS (0.08) - missing essential amino acids for muscle building")
        }

        return ProteinAnalysisResult(
            averagePdcaas = averagePdcaas,
            qualityCategory = qualityCategory,
            warnings = warnings,
            foundProteins = proteinSources
        )
    }
}

data class ProteinAnalysisResult(
    val averagePdcaas: Double,
    val qualityCategory: ProteinQuality,
    val warnings: List<String>,
    val foundProteins: List<ProteinSource>
)