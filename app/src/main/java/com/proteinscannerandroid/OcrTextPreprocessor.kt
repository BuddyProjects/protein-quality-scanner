package com.proteinscannerandroid

import android.util.Log

/**
 * OCR Text Preprocessing Utility
 * 
 * Handles common OCR errors and normalizes ingredient text for better protein detection.
 * This is critical for real-world scanning where OCR output is imperfect.
 */
object OcrTextPreprocessor {
    private const val TAG = "OcrTextPreprocessor"

    /**
     * Main preprocessing pipeline for OCR text
     */
    fun preprocess(rawText: String): PreprocessedResult {
        val corrections = mutableListOf<String>()
        
        var text = rawText
        
        // Step 1: Normalize whitespace and line breaks
        text = normalizeWhitespace(text)
        
        // Step 2: Fix common OCR character substitutions
        val (correctedText, charCorrections) = fixOcrCharacterErrors(text)
        text = correctedText
        corrections.addAll(charCorrections)
        
        // Step 3: Rejoin hyphenated line breaks (e.g., "Pro-\ntein" -> "Protein")
        text = rejoinHyphenatedWords(text)
        
        // Step 4: Normalize ingredient separators
        text = normalizeIngredientSeparators(text)
        
        // Step 5: Fix common protein-specific OCR errors
        val (proteinCorrectedText, proteinCorrections) = fixProteinSpecificErrors(text)
        text = proteinCorrectedText
        corrections.addAll(proteinCorrections)
        
        Log.d(TAG, "Preprocessed text: ${text.take(200)}...")
        if (corrections.isNotEmpty()) {
            Log.d(TAG, "Applied corrections: ${corrections.joinToString(", ")}")
        }
        
        return PreprocessedResult(
            originalText = rawText,
            processedText = text,
            corrections = corrections
        )
    }
    
    /**
     * Normalize whitespace: collapse multiple spaces, normalize line breaks
     */
    private fun normalizeWhitespace(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("[ \t]+"), " ")  // Collapse multiple spaces/tabs
            .replace(Regex("\n\\s*\n"), "\n")  // Collapse multiple newlines
            .trim()
    }
    
    /**
     * Fix common OCR character substitution errors
     */
    private fun fixOcrCharacterErrors(text: String): Pair<String, List<String>> {
        var result = text
        val corrections = mutableListOf<String>()
        
        // Common OCR substitutions with context-aware fixes
        val replacements = listOf(
            // "rn" often misread as "m" - but we fix the other way for ingredient lists
            // Actually, "m" can be misread as "rn" - fix common cases
            Pair(Regex("\\bwherrn\\b", RegexOption.IGNORE_CASE), "wherm"),
            
            // "ii" or "ü" confusion in German
            Pair(Regex("(?i)milii"), "milü"),  // Rarely needed, most OCR handles this
            
            // "l" vs "I" vs "1" fixes for common protein terms
            Pair(Regex("(?i)prote1n"), "protein"),
            Pair(Regex("(?i)proteIn"), "protein"),
            Pair(Regex("(?i)iso1ate"), "isolate"),
            Pair(Regex("(?i)isoIate"), "isolate"),
            Pair(Regex("(?i)m1lk"), "milk"),
            Pair(Regex("(?i)mIlk"), "milk"),
            Pair(Regex("(?i)wh3y"), "whey"),
            Pair(Regex("(?i)cas3in"), "casein"),
            
            // "0" vs "O" fixes
            Pair(Regex("(?i)s0ja"), "soja"),
            Pair(Regex("(?i)s0y"), "soy"),
            Pair(Regex("(?i)prote0n"), "protein"),
            
            // French accent fixes (OCR often drops accents)
            Pair(Regex("(?i)proteine"), "protéine"),
            Pair(Regex("(?i)proteines"), "protéines"),
            Pair(Regex("(?i)lait ecreme"), "lait écrémé"),
            Pair(Regex("(?i)legumineuse"), "légumineuse"),
            
            // German umlaut fixes
            Pair(Regex("(?i)eiweiß"), "eiweiß"),  // Already correct but normalize
            Pair(Regex("(?i)eiweiss"), "eiweiß"),
            Pair(Regex("(?i)milcheiweiss"), "milcheiweiß"),
            Pair(Regex("(?i)sojaeiweiss"), "sojaeiweiß"),
            
            // Common ingredient OCR errors
            Pair(Regex("(?i)rnilk"), "milk"),  // "m" misread as "rn"
            Pair(Regex("(?i)rnolke"), "molke"),
            Pair(Regex("(?i)whev"), "whey"),  // "y" misread as "v"
            Pair(Regex("(?i)protien"), "protein"),  // Common typo/OCR error
            Pair(Regex("(?i)proetein"), "protein"),
        )
        
        for ((pattern, replacement) in replacements) {
            if (pattern.containsMatchIn(result)) {
                result = pattern.replace(result, replacement)
                corrections.add("${pattern.pattern} → $replacement")
            }
        }
        
        return Pair(result, corrections)
    }
    
    /**
     * Rejoin words that were hyphenated across line breaks
     * e.g., "Whey Pro-\ntein Isolate" -> "Whey Protein Isolate"
     */
    private fun rejoinHyphenatedWords(text: String): String {
        // Pattern: word ending with hyphen, followed by newline and continuation
        return text.replace(Regex("(\\w)-\\s*\\n\\s*(\\w)")) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}"
        }
    }
    
    /**
     * Normalize ingredient list separators
     * Different products use different separators: commas, semicolons, bullets, etc.
     */
    private fun normalizeIngredientSeparators(text: String): String {
        var result = text
        
        // Replace bullet points and similar with commas
        result = result.replace(Regex("[•·▪▸►]"), ",")
        
        // Normalize semicolons to commas (some European formats)
        result = result.replace(";", ",")
        
        // Remove parenthetical percentages like "(12%)" but keep other parentheses
        result = result.replace(Regex("\\(\\d+%?\\)"), "")
        
        // Collapse multiple commas
        result = result.replace(Regex(",\\s*,"), ",")
        
        return result
    }
    
    /**
     * Fix protein-specific OCR errors based on common misreads
     */
    private fun fixProteinSpecificErrors(text: String): Pair<String, List<String>> {
        var result = text
        val corrections = mutableListOf<String>()
        
        // Map of common OCR misreads for protein-related terms
        val proteinFixes = mapOf(
            // Whey variations
            "whev protein" to "whey protein",
            "whcy protein" to "whey protein",
            "wney protein" to "whey protein",
            
            // Casein variations
            "casien" to "casein",
            "caesin" to "casein",
            "caséine" to "caséine",
            
            // Soy variations
            "s0ja" to "soja",
            "sova" to "soya",
            
            // Pea variations
            "pca protein" to "pea protein",
            
            // Isolate variations
            "iso1ate" to "isolate",
            "lsolate" to "isolate",
            
            // Concentrate variations
            "concentratc" to "concentrate",
            
            // German terms
            "rnolkenprotein" to "molkenprotein",
            "rnilchprotein" to "milchprotein",
            "eiweib" to "eiweiß",
            
            // French terms
            "lactosérum" to "lactosérum",
            "lactoserum" to "lactosérum",
        )
        
        for ((wrong, correct) in proteinFixes) {
            if (result.contains(wrong, ignoreCase = true)) {
                result = result.replace(wrong, correct, ignoreCase = true)
                corrections.add("$wrong → $correct")
            }
        }
        
        return Pair(result, corrections)
    }
    
    /**
     * Extract confidence score based on text quality indicators
     */
    fun assessTextQuality(text: String): TextQualityAssessment {
        var score = 1.0
        val issues = mutableListOf<String>()
        
        // Check for excessive special characters (OCR noise)
        val specialCharRatio = text.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ",.;:()-'" } / text.length.toFloat()
        if (specialCharRatio > 0.1) {
            score -= 0.2
            issues.add("High noise ratio")
        }
        
        // Check for very short text
        if (text.length < 20) {
            score -= 0.3
            issues.add("Very short text")
        }
        
        // Check for lack of ingredient separators
        if (!text.contains(",") && !text.contains(";") && text.length > 50) {
            score -= 0.2
            issues.add("No ingredient separators found")
        }
        
        // Check for all caps (harder to read but not necessarily wrong)
        if (text == text.uppercase() && text.length > 20) {
            score -= 0.1
            issues.add("All uppercase")
        }
        
        // Check for common ingredient list markers
        val hasIngredientMarker = listOf(
            "ingredient", "zutaten", "ingrédient", "ingredientes",
            "contains", "enthält", "contient"
        ).any { text.contains(it, ignoreCase = true) }
        
        if (hasIngredientMarker) {
            score += 0.1  // Bonus for clear ingredient list
        }
        
        return TextQualityAssessment(
            score = score.coerceIn(0.0, 1.0),
            issues = issues,
            isLikelyIngredientList = hasIngredientMarker || text.contains(",")
        )
    }
    
    data class PreprocessedResult(
        val originalText: String,
        val processedText: String,
        val corrections: List<String>
    )
    
    data class TextQualityAssessment(
        val score: Double,
        val issues: List<String>,
        val isLikelyIngredientList: Boolean
    )
}
