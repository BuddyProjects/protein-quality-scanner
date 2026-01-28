package com.proteinscannerandroid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.app.AlertDialog
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.text.TextPaint
import android.text.Spanned
import android.util.Patterns
import android.net.Uri
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.proteinscannerandroid.databinding.ActivityResultsBinding
import kotlinx.coroutines.launch

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private val isDebugMode = true // Set to false for production
    private val debugMessages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize banner ad (only for non-premium users)
        setupBannerAd()

        // Check what type of result this is
        val isOcrResult = intent.getBooleanExtra("IS_OCR_RESULT", false)
        val proteinSource = intent.getStringExtra("PROTEIN_SOURCE")
        val barcode = intent.getStringExtra("BARCODE")
        
        when {
            isOcrResult -> {
                val ingredientText = intent.getStringExtra("INGREDIENT_TEXT")
                if (ingredientText != null) {
                    debugLog("📋 OCR Ingredient Analysis")
                    processOcrIngredients(ingredientText)
                } else {
                    showError("No ingredient text provided")
                }
            }
            proteinSource != null -> {
                debugLog("🔍 Manual Protein Lookup")
                processProteinLookup(proteinSource)
            }
            barcode != null -> {
                fetchProductData(barcode)
            }
            else -> {
                showError("No valid input provided")
            }
        }

        binding.btnScanAgain.setOnClickListener {
            finish() // Return to main screen
        }
    }

    private fun fetchProductData(barcode: String) {
        lifecycleScope.launch {
            try {
                debugLog("🔍 Starting scan for barcode: $barcode")
                
                // Fetch product data from OpenFoodFacts
                debugLog("📡 Fetching product data from OpenFoodFacts...")
                val productInfo = OpenFoodFactsService.fetchProductData(barcode)
                
                if (productInfo != null) {
                    debugLog("✅ Product found: ${productInfo.name}")
                    debugLog("🏭 Brand: ${productInfo.brand}")
                    debugLog("🥗 Ingredients: ${productInfo.ingredientsText}")
                    debugLog("💪 Protein per 100g: ${productInfo.proteinPer100g}")
                    
                    // Analyze protein quality locally
                    debugLog("🧬 Starting protein analysis...")
                    val analysis = ProteinDatabase.analyzeProteinQuality(
                        productInfo.ingredientsText ?: "",
                        productInfo.proteinPer100g
                    )
                    
                    debugLog("📊 Analysis complete:")
                    debugLog("   PDCAAS Score: ${analysis.weightedPdcaas}")
                    debugLog("   Quality: ${analysis.qualityLabel}")
                    debugLog("   Confidence: ${analysis.confidenceScore}")
                    debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                    for (protein in analysis.detectedProteins) {
                        debugLog("   • #${protein.position} ${protein.proteinSource.name} (PDCAAS: ${protein.proteinSource.pdcaas}, Weight: ${String.format("%.1f", protein.weight)}) - matched '${protein.matchedKeyword}'")
                    }

                    // Log detailed keyword matches
                    logDetailedMatches(analysis)

                    if (analysis.warnings.isNotEmpty()) {
                        debugLog("⚠️ Warnings: ${analysis.warnings.joinToString(", ")}")
                    }

                    displayResults(productInfo, analysis)
                } else {
                    debugLog("❌ Product not found in OpenFoodFacts")
                    showError(getString(R.string.product_not_found))
                }
            } catch (e: Exception) {
                debugLog("💥 Error: ${e.message}")
                e.printStackTrace()
                showError("Error: ${e.message}")
            }
        }
    }
    
    private fun debugLog(message: String) {
        if (isDebugMode) {
            Log.d("ProteinScanner", message)
            debugMessages.add(message)
        }
    }

    private fun logDetailedMatches(analysis: ProteinAnalysis) {
        if (!isDebugMode) return

        debugLog("")
        debugLog("🔍 KEYWORD MATCH DETAILS:")
        debugLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (analysis.rawIngredientText.isNotEmpty()) {
            val truncatedText = if (analysis.rawIngredientText.length > 200) {
                analysis.rawIngredientText.take(200) + "..."
            } else {
                analysis.rawIngredientText
            }
            debugLog("📝 Raw text: $truncatedText")
            debugLog("")
        }

        val acceptedMatches = analysis.debugMatches.filter { it.wasAccepted }
        val rejectedMatches = analysis.debugMatches.filter { !it.wasAccepted }

        if (acceptedMatches.isNotEmpty()) {
            debugLog("✅ ACCEPTED MATCHES (${acceptedMatches.size}):")
            for (match in acceptedMatches) {
                debugLog("   ┌─ Keyword: '${match.keyword}'")
                debugLog("   │  Protein: ${match.proteinSourceName}")
                debugLog("   │  Position: char ${match.charPosition}")
                debugLog("   └─ Context: ...${match.contextBefore}[${match.matchedText}]${match.contextAfter}...")
                debugLog("")
            }
        }

        if (rejectedMatches.isNotEmpty()) {
            debugLog("❌ REJECTED MATCHES (${rejectedMatches.size}):")
            for (match in rejectedMatches) {
                debugLog("   ┌─ Keyword: '${match.keyword}'")
                debugLog("   │  Protein: ${match.proteinSourceName}")
                debugLog("   │  Position: char ${match.charPosition}")
                debugLog("   │  Reason: ${match.rejectionReason ?: "Unknown"}")
                debugLog("   └─ Context: ...${match.contextBefore}[${match.matchedText}]${match.contextAfter}...")
                debugLog("")
            }
        }

        if (analysis.debugMatches.isEmpty()) {
            debugLog("   No keyword matches found in ingredient text")
        }

        debugLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun showDebugInfo() {
        if (isDebugMode && debugMessages.isNotEmpty()) {
            binding.debugCard.visibility = View.VISIBLE
            binding.tvDebugInfo.text = debugMessages.joinToString("\n")
        } else {
            binding.debugCard.visibility = View.GONE
        }
    }

    private fun displayResults(productInfo: ProductInfo, analysis: ProteinAnalysis) {
        binding.loadingLayout.visibility = View.GONE
        binding.resultsLayout.visibility = View.VISIBLE

        // Display product info
        binding.tvProductName.text = productInfo.name ?: "Unknown Product"
        binding.tvBarcode.text = "Barcode: ${productInfo.barcode}"
        
        val proteinContent = productInfo.proteinPer100g
        binding.tvProteinContent.text = if (proteinContent != null) {
            "Protein: ${String.format("%.1f", proteinContent)}g per 100g"
        } else {
            "Protein content: Not available"
        }

        // Display analysis results
        displayAnalysis(analysis, proteinContent)
        
        // Display detected proteins
        displayDetectedProteins(analysis.detectedProteins)
        
        // Show debug info
        showDebugInfo()
    }

    private fun displayAnalysis(analysis: ProteinAnalysis, proteinContent: Double?) {
        // Display PDCAAS score
        val pdcaas = analysis.weightedPdcaas
        binding.tvPdcaasScore.text = String.format("%.2f", pdcaas)

        // Display quality category with appropriate color
        val qualityLabel = analysis.qualityLabel
        binding.tvQualityCategory.text = qualityLabel
        
        val categoryColor = when (qualityLabel.lowercase()) {
            "excellent" -> R.color.quality_excellent
            "good" -> R.color.quality_good
            "medium" -> R.color.quality_medium
            else -> R.color.quality_low
        }
        
        binding.tvQualityCategory.setTextColor(ContextCompat.getColor(this, categoryColor))
        binding.tvPdcaasScore.setTextColor(ContextCompat.getColor(this, categoryColor))

        // Display warnings if any
        if (analysis.warnings.isNotEmpty()) {
            binding.warningsCard.visibility = View.VISIBLE
            binding.tvWarnings.text = analysis.warnings.joinToString("\n\n")
        } else {
            binding.warningsCard.visibility = View.GONE
        }

        // Calculate effective protein
        val effectiveProtein = analysis.effectiveProteinPer100g
        if (effectiveProtein != null && proteinContent != null) {
            val effectiveText = "\n\nEffective protein (quality-adjusted): ${String.format("%.1f", effectiveProtein)}g per 100g"
            binding.tvProteinContent.text = binding.tvProteinContent.text.toString() + effectiveText
        }
    }

    private fun displayDetectedProteins(detectedProteins: List<DetectedProtein>) {
        if (detectedProteins.isNotEmpty()) {
            val proteinDetails = detectedProteins.map { protein ->
                val source = protein.proteinSource
                var details = "${source.name} (PDCAAS: ${String.format("%.2f", source.pdcaas)}"
                
                // Add DIAAS if available
                source.diaas?.let { details += ", DIAAS: $it" }
                
                details += ")"
                
                // Add limiting amino acids if any
                if (source.limitingAminoAcids.isNotEmpty()) {
                    details += "\n   ⚠️ Limiting: ${source.limitingAminoAcids.joinToString(", ")}"
                }
                
                // Add digestion speed
                details += "\n   ⏱️ Digestion: ${source.digestionSpeed}"
                
                // Add notes if important
                if (source.notes.isNotEmpty()) {
                    details += "\n   💡 ${source.notes}"
                }
                
                details
            }
            
            binding.tvFoundProteins.text = "Found proteins:\n\n${proteinDetails.joinToString("\n\n")}"
        } else {
            binding.tvFoundProteins.text = "No recognizable protein sources found"
        }
    }

    private fun processProteinLookup(proteinSourceName: String) {
        lifecycleScope.launch {
            try {
                debugLog("🔍 Looking up protein source: $proteinSourceName")

                // Use the protein database to find matching protein sources
                val analysis = ProteinDatabase.analyzeProteinQuality(proteinSourceName, null)

                debugLog("📊 Lookup complete:")
                debugLog("   PDCAAS Score: ${analysis.weightedPdcaas}")
                debugLog("   Quality: ${analysis.qualityLabel}")
                debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                // Log detailed keyword matches
                logDetailedMatches(analysis)

                // Display results
                binding.loadingLayout.visibility = View.GONE
                binding.resultsLayout.visibility = View.VISIBLE

                // Set UI for protein lookup mode
                binding.tvProductName.text = "Protein Source: $proteinSourceName"
                binding.tvBarcode.text = "Manual Lookup"
                binding.tvProteinContent.text = if (analysis.detectedProteins.isNotEmpty()) {
                    "Analysis based on protein database"
                } else {
                    "No matching protein sources found"
                }

                // Display analysis results
                displayAnalysis(analysis, null)

                // Display detected proteins with full details
                displayDetectedProteins(analysis.detectedProteins)

                // Show debug info
                showDebugInfo()

            } catch (e: Exception) {
                Log.e("ResultsActivity", "Protein lookup failed", e)
                debugLog("❌ Protein lookup failed: ${e.message}")
                showError("Failed to lookup protein: ${e.message}")
            }
        }
    }

    private fun processOcrIngredients(ingredientText: String) {
        lifecycleScope.launch {
            try {
                debugLog("📋 Processing OCR ingredient text...")
                debugLog("🥗 Extracted text: $ingredientText")

                // Analyze ingredients using existing algorithm
                debugLog("🔬 Starting protein analysis...")
                val analysis = ProteinDatabase.analyzeProteinQuality(ingredientText, null)

                debugLog("✅ Analysis complete")
                debugLog("   PDCAAS Score: ${analysis.weightedPdcaas}")
                debugLog("   Quality: ${analysis.qualityLabel}")
                debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                for (protein in analysis.detectedProteins) {
                    debugLog("   • #${protein.position} ${protein.proteinSource.name} (PDCAAS: ${protein.proteinSource.pdcaas}) - matched '${protein.matchedKeyword}'")
                }

                // Log detailed keyword matches
                logDetailedMatches(analysis)

                // Display results (no product info for OCR)
                binding.loadingLayout.visibility = View.GONE
                binding.resultsLayout.visibility = View.VISIBLE

                // Update UI - Set product info for OCR mode
                binding.tvProductName.text = "Ingredient Analysis"
                binding.tvBarcode.text = "OCR Scan Result"
                binding.tvProteinContent.text = "Protein content: Not available (photo scan)"

                // Display protein analysis results
                displayAnalysis(analysis, null)

                // Display detected proteins
                displayDetectedProteins(analysis.detectedProteins)

                // Display debug info if enabled
                showDebugInfo()

            } catch (e: Exception) {
                Log.e("ResultsActivity", "OCR processing failed", e)
                debugLog("❌ OCR analysis failed: ${e.message}")
                showError("Failed to analyze ingredients: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.resultsLayout.visibility = View.GONE
        
        // Check if message contains URLs to make them clickable
        if (message.contains("http")) {
            showErrorDialog(message)
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun showErrorDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Product Not Found")
        
        // Create clickable text
        val spannableString = SpannableString(message)
        val urlPattern = Patterns.WEB_URL
        val matcher = urlPattern.matcher(message)
        
        while (matcher.find()) {
            val url = matcher.group()
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
                
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@ResultsActivity, android.R.color.holo_blue_bright)
                    ds.isUnderlineText = true
                }
            }
            spannableString.setSpan(clickableSpan, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        val textView = TextView(this)
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.setPadding(50, 50, 50, 50)
        textView.textSize = 16f
        
        builder.setView(textView)
        builder.setPositiveButton("OK") { _, _ -> finish() }
        builder.setNegativeButton("Scan Ingredients") { _, _ -> 
            // Launch camera for ingredient scanning
            val intent = Intent(this, IngredientCameraActivity::class.java)
            startActivity(intent)
            finish()
        }
        builder.setNeutralButton("Manual Lookup") { _, _ ->
            // Launch protein lookup
            val intent = Intent(this, ProteinLookupActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        builder.show()
    }

    /**
     * Check if the user has premium status (ad-free experience).
     * TODO: Implement actual premium/subscription check when payment is added.
     * @return true if user is premium, false otherwise
     */
    private fun isPremiumUser(): Boolean {
        // Placeholder for premium check - returns false for now
        // Future implementation will check subscription status, in-app purchase, etc.
        return false
    }

    /**
     * Setup and load banner ad for non-premium users.
     * Ad is displayed at the bottom of the results screen.
     */
    private fun setupBannerAd() {
        if (isPremiumUser()) {
            // Premium users don't see ads
            binding.adView.visibility = View.GONE
            return
        }

        // Load and show the banner ad
        binding.adView.visibility = View.VISIBLE
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
    }

    override fun onPause() {
        binding.adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.adView.resume()
    }

    override fun onDestroy() {
        binding.adView.destroy()
        super.onDestroy()
    }
}