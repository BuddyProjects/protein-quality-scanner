package com.proteinscannerandroid

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.net.URL

/**
 * Protein Detection Algorithm Test Suite
 *
 * This test class is designed for iterative improvement of the protein detection algorithm
 * using the "Ralph Wiggum" technique - each iteration adds new test cases and fixes issues.
 *
 * Usage:
 * 1. Run existing tests: ./gradlew test
 * 2. Add new test case: Call addTestCaseFromOpenFoodFacts(barcode) or manually add to JSON
 * 3. Run tests again - if failing, fix ProteinDatabase.kt
 * 4. Repeat until all tests pass
 */
class ProteinDetectionTest {

    data class TestCase(
        val id: String,
        val name: String,
        val source: String,
        val ingredients: String,
        val expected_detected: List<String>,
        val expected_not_detected: List<String>,
        val notes: String = "",
        val barcode: String? = null
    )

    data class TestCasesFile(
        val description: String,
        val version: String,
        val test_cases: List<TestCase>
    )

    data class TestResult(
        val testCase: TestCase,
        val passed: Boolean,
        val detectedProteins: List<String>,
        val missingProteins: List<String>,
        val unexpectedProteins: List<String>,
        val wronglyDetected: List<String>,
        val errorMessage: String? = null
    )

    private val gson = Gson()

    /**
     * Load test cases from JSON file
     */
    private fun loadTestCases(): List<TestCase> {
        val resourceStream = javaClass.classLoader?.getResourceAsStream("protein_test_cases.json")
        if (resourceStream != null) {
            val json = resourceStream.bufferedReader().use { it.readText() }
            val testFile = gson.fromJson(json, TestCasesFile::class.java)
            return testFile.test_cases
        }

        // Fallback: try to load from file path directly
        val file = File("src/test/resources/protein_test_cases.json")
        if (file.exists()) {
            val json = file.readText()
            val testFile = gson.fromJson(json, TestCasesFile::class.java)
            return testFile.test_cases
        }

        throw IllegalStateException("Could not load test cases from protein_test_cases.json")
    }

    /**
     * Run a single test case and return detailed results
     */
    private fun runTestCase(testCase: TestCase): TestResult {
        try {
            val analysis = ProteinDatabase.analyzeProteinQuality(testCase.ingredients, null)
            val detectedNames = analysis.detectedProteins.map { it.proteinSource.name }

            // Check for missing expected proteins
            val missingProteins = testCase.expected_detected.filter { expected ->
                detectedNames.none { detected ->
                    detected.contains(expected, ignoreCase = true) ||
                    expected.contains(detected, ignoreCase = true)
                }
            }

            // Check for proteins that should NOT have been detected
            val wronglyDetected = testCase.expected_not_detected.filter { notExpected ->
                detectedNames.any { detected ->
                    detected.contains(notExpected, ignoreCase = true) ||
                    notExpected.contains(detected, ignoreCase = true)
                }
            }

            // Check for unexpected proteins (detected but not in expected list)
            val unexpectedProteins = detectedNames.filter { detected ->
                testCase.expected_detected.none { expected ->
                    detected.contains(expected, ignoreCase = true) ||
                    expected.contains(detected, ignoreCase = true)
                }
            }

            val passed = missingProteins.isEmpty() && wronglyDetected.isEmpty()

            return TestResult(
                testCase = testCase,
                passed = passed,
                detectedProteins = detectedNames,
                missingProteins = missingProteins,
                unexpectedProteins = unexpectedProteins,
                wronglyDetected = wronglyDetected
            )
        } catch (e: Exception) {
            return TestResult(
                testCase = testCase,
                passed = false,
                detectedProteins = emptyList(),
                missingProteins = testCase.expected_detected,
                unexpectedProteins = emptyList(),
                wronglyDetected = emptyList(),
                errorMessage = e.message
            )
        }
    }

    /**
     * MAIN TEST: Run all test cases from JSON file
     * This is the test that the Ralph Wiggum loop will run repeatedly
     */
    @Test
    fun testAllProteinDetectionCases() {
        val testCases = loadTestCases()
        val results = testCases.map { runTestCase(it) }

        // Print detailed results
        println("\n" + "=".repeat(80))
        println("PROTEIN DETECTION TEST RESULTS")
        println("=".repeat(80))

        var passCount = 0
        var failCount = 0

        for (result in results) {
            if (result.passed) {
                passCount++
                println("\n[PASS] ${result.testCase.name} (${result.testCase.id})")
                println("       Detected: ${result.detectedProteins.joinToString(", ").ifEmpty { "none" }}")
            } else {
                failCount++
                println("\n[FAIL] ${result.testCase.name} (${result.testCase.id})")
                println("       Ingredients: ${result.testCase.ingredients.take(100)}...")
                println("       Detected: ${result.detectedProteins.joinToString(", ").ifEmpty { "none" }}")

                if (result.missingProteins.isNotEmpty()) {
                    println("       MISSING: ${result.missingProteins.joinToString(", ")}")
                }
                if (result.wronglyDetected.isNotEmpty()) {
                    println("       WRONGLY DETECTED: ${result.wronglyDetected.joinToString(", ")}")
                }
                if (result.unexpectedProteins.isNotEmpty()) {
                    println("       Unexpected (not in expected list): ${result.unexpectedProteins.joinToString(", ")}")
                }
                if (result.errorMessage != null) {
                    println("       Error: ${result.errorMessage}")
                }
                println("       Notes: ${result.testCase.notes}")
            }
        }

        println("\n" + "=".repeat(80))
        println("SUMMARY: $passCount passed, $failCount failed out of ${results.size} tests")
        println("=".repeat(80))

        // Assert all tests pass
        val failedTests = results.filter { !it.passed }
        if (failedTests.isNotEmpty()) {
            val failMessages = failedTests.map { result ->
                buildString {
                    append("${result.testCase.id}: ")
                    if (result.missingProteins.isNotEmpty()) {
                        append("missing [${result.missingProteins.joinToString(", ")}] ")
                    }
                    if (result.wronglyDetected.isNotEmpty()) {
                        append("wrongly detected [${result.wronglyDetected.joinToString(", ")}]")
                    }
                }
            }
            fail("${failedTests.size} test(s) failed:\n${failMessages.joinToString("\n")}")
        }

        println("\nALL TESTS PASSING")
    }

    /**
     * Test for trace warning detection specifically
     */
    @Test
    fun testTraceWarningsAreExcluded() {
        val traceTestCases = listOf(
            "Kann Spuren von SOJA enthalten" to listOf("Soy"),
            "May contain traces of milk and eggs" to listOf("Milk", "Egg"),
            "Produced in a facility that processes nuts" to listOf("Nut"),
            "Enthält Spuren von MILCH" to listOf("Milk"),
            "Peut contenir des traces de lait" to listOf("Milk")
        )

        for ((ingredients, shouldNotDetect) in traceTestCases) {
            val analysis = ProteinDatabase.analyzeProteinQuality(ingredients, null)
            val detectedNames = analysis.detectedProteins.map { it.proteinSource.name.lowercase() }

            for (protein in shouldNotDetect) {
                val wronglyDetected = detectedNames.any { it.contains(protein.lowercase()) }
                assertFalse(
                    "Trace warning '$ingredients' should NOT detect $protein but found: $detectedNames",
                    wronglyDetected
                )
            }
        }
        println("All trace warning exclusion tests passed!")
    }

    /**
     * Test for German compound word detection
     */
    @Test
    fun testGermanCompoundWords() {
        val compoundTestCases = listOf(
            "HARTWEIZEN-Grieß" to "Wheat",
            "Vollmilchpulver" to "Milk",
            "Sojaeiweiss" to "Soy",
            "Molkenprotein" to "Whey"
        )

        for ((ingredient, expectedProtein) in compoundTestCases) {
            val analysis = ProteinDatabase.analyzeProteinQuality(ingredient, null)
            val detectedNames = analysis.detectedProteins.map { it.proteinSource.name }

            val found = detectedNames.any { it.contains(expectedProtein, ignoreCase = true) }
            assertTrue(
                "German compound '$ingredient' should detect $expectedProtein but found: $detectedNames",
                found
            )
        }
        println("All German compound word tests passed!")
    }
}
