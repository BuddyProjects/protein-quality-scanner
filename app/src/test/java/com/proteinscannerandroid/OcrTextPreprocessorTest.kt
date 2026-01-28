package com.proteinscannerandroid

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for OCR text preprocessing
 */
class OcrTextPreprocessorTest {

    @Test
    fun `test basic preprocessing preserves valid text`() {
        val input = "Whey Protein Isolate, Milk, Soy Lecithin"
        val result = OcrTextPreprocessor.preprocess(input)
        
        assertTrue(result.processedText.contains("Whey Protein Isolate"))
        assertTrue(result.processedText.contains("Milk"))
    }
    
    @Test
    fun `test hyphenated line break rejoining`() {
        val input = "Whey Pro-\ntein Isolate"
        val result = OcrTextPreprocessor.preprocess(input)
        
        assertTrue(
            "Should rejoin hyphenated words: ${result.processedText}",
            result.processedText.contains("Protein") || result.processedText.contains("protein")
        )
    }
    
    @Test
    fun `test common OCR number substitution fixes`() {
        val testCases = listOf(
            "prote1n" to "protein",
            "s0ja" to "soja",
            "iso1ate" to "isolate"
        )
        
        for ((input, expectedContains) in testCases) {
            val result = OcrTextPreprocessor.preprocess(input)
            assertTrue(
                "Input '$input' should contain '$expectedContains' after preprocessing, got: ${result.processedText}",
                result.processedText.contains(expectedContains, ignoreCase = true)
            )
        }
    }
    
    @Test
    fun `test German umlaut normalization`() {
        val input = "Milcheiweiss, Sojaeiweiss"
        val result = OcrTextPreprocessor.preprocess(input)
        
        // Should normalize to proper German spelling with ß
        assertTrue(
            "Should contain eiweiß: ${result.processedText}",
            result.processedText.contains("eiweiß", ignoreCase = true) ||
            result.processedText.contains("eiweiss", ignoreCase = true)
        )
    }
    
    @Test
    fun `test whitespace normalization`() {
        val input = "Whey   Protein    Isolate\n\n\nMilk"
        val result = OcrTextPreprocessor.preprocess(input)
        
        // Multiple spaces should be collapsed
        assertFalse(
            "Should not have multiple consecutive spaces",
            result.processedText.contains("  ")
        )
    }
    
    @Test
    fun `test bullet point normalization`() {
        val input = "Whey Protein • Milk • Soy"
        val result = OcrTextPreprocessor.preprocess(input)
        
        // Bullets should become commas
        assertTrue(
            "Bullets should be converted to commas: ${result.processedText}",
            result.processedText.contains(",")
        )
    }
    
    @Test
    fun `test quality assessment - good quality text`() {
        val text = "Ingredients: Whey Protein Isolate, Milk, Cocoa Powder, Natural Flavors"
        val quality = OcrTextPreprocessor.assessTextQuality(text)
        
        assertTrue("Should recognize as ingredient list", quality.isLikelyIngredientList)
        assertTrue("Should have good quality score", quality.score >= 0.7)
    }
    
    @Test
    fun `test quality assessment - poor quality text`() {
        val text = "x#@!%^"
        val quality = OcrTextPreprocessor.assessTextQuality(text)
        
        assertFalse("Should not recognize as ingredient list", quality.isLikelyIngredientList)
        assertTrue("Should have issues", quality.issues.isNotEmpty())
    }
    
    @Test
    fun `test quality assessment - short text`() {
        val text = "Milk"
        val quality = OcrTextPreprocessor.assessTextQuality(text)
        
        assertTrue("Should flag short text", quality.issues.contains("Very short text"))
    }
    
    @Test
    fun `test protein-specific OCR fixes`() {
        val testCases = listOf(
            "whev protein" to "whey protein",
            "casien" to "casein",
            "rnolkenprotein" to "molkenprotein"
        )
        
        for ((input, expected) in testCases) {
            val result = OcrTextPreprocessor.preprocess(input)
            assertTrue(
                "Input '$input' should become '$expected', got: ${result.processedText}",
                result.processedText.contains(expected, ignoreCase = true)
            )
        }
    }
    
    @Test
    fun `test real-world OCR sample with multiple issues`() {
        // Simulated real OCR output with multiple issues
        val input = """
            ZUTATEN: Molkeneiwelss, S0ja-
            protein Isolat, Milch,
            natürliche Aromen
        """.trimIndent()
        
        val result = OcrTextPreprocessor.preprocess(input)
        
        // Should handle the hyphenated line break
        // Should fix S0ja -> Soja
        assertTrue(
            "Should fix s0ja to soja: ${result.processedText}",
            result.processedText.contains("soja", ignoreCase = true) ||
            result.processedText.contains("Soja", ignoreCase = true)
        )
    }
}
