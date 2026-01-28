package com.proteinscannerandroid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
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
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.proteinscannerandroid.databinding.ActivityResultsBinding
import kotlinx.coroutines.launch

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private var isDebugMode = false // Read from SharedPreferences
    private val debugMessages = mutableListOf<String>()
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read debug preference from SharedPreferences
        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isDebugMode = sharedPreferences.getBoolean(SettingsActivity.KEY_DEBUG_ENABLED, false)

        // Initialize native ad (only for non-premium users)
        loadNativeAd()

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
     * Load Native Advanced ad for non-premium users.
     * Ad is displayed between result sections, styled to match app design.
     */
    private fun loadNativeAd() {
        if (isPremiumUser()) {
            // Premium users don't see ads
            binding.nativeAdCard.visibility = View.GONE
            return
        }

        // Native Ad unit ID from BuildConfig (test ID for debug, real ID for release)
        val adUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID

        val adLoader = AdLoader.Builder(this, adUnitId)
            .forNativeAd { ad: NativeAd ->
                // Clean up any previous ad
                nativeAd?.destroy()
                nativeAd = ad

                // Check if activity is finishing/destroyed before showing ad
                if (isDestroyed || isFinishing) {
                    ad.destroy()
                    return@forNativeAd
                }

                // Inflate and populate the native ad view
                val adView = layoutInflater.inflate(R.layout.native_ad_layout, null) as NativeAdView
                populateNativeAdView(ad, adView)

                // Add to container and make visible
                binding.nativeAdContainer.removeAllViews()
                binding.nativeAdContainer.addView(adView)
                binding.nativeAdCard.visibility = View.VISIBLE
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    // Hide ad container if ad fails to load
                    binding.nativeAdCard.visibility = View.GONE
                    Log.d("ResultsActivity", "Native ad failed to load: ${loadAdError.message}")
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Populate the native ad view with ad content.
     * Maps ad assets to the corresponding views in the layout.
     */
    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // Set the media view (not used in this compact layout, but required for some ad types)
        // adView.mediaView = adView.findViewById(R.id.ad_media)

        // Set required asset views
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        // Populate headline (required)
        (adView.headlineView as TextView).text = nativeAd.headline

        // Populate body (optional)
        if (nativeAd.body != null) {
            (adView.bodyView as TextView).text = nativeAd.body
            adView.bodyView?.visibility = View.VISIBLE
        } else {
            adView.bodyView?.visibility = View.GONE
        }

        // Populate call to action (optional)
        if (nativeAd.callToAction != null) {
            (adView.callToActionView as Button).text = nativeAd.callToAction
            adView.callToActionView?.visibility = View.VISIBLE
        } else {
            adView.callToActionView?.visibility = View.GONE
        }

        // Populate icon (optional)
        if (nativeAd.icon != null) {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = View.GONE
        }

        // Populate advertiser (optional)
        if (nativeAd.advertiser != null) {
            (adView.advertiserView as TextView).text = nativeAd.advertiser
            adView.advertiserView?.visibility = View.VISIBLE
        } else {
            adView.advertiserView?.visibility = View.GONE
        }

        // Register the native ad view with the native ad object
        adView.setNativeAd(nativeAd)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }
}