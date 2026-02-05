package com.proteinscannerandroid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.google.gson.Gson
import com.google.android.material.snackbar.Snackbar
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.data.FavoriteEntity
import com.proteinscannerandroid.data.PendingScan
import com.proteinscannerandroid.data.ScanHistoryEntity
import com.proteinscannerandroid.databinding.ActivityResultsBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.launch

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private var isDebugMode = false // Read from SharedPreferences
    private val debugMessages = mutableListOf<String>()
    private var nativeAd: NativeAd? = null

    // Database and current scan data for history/favorites
    private val database by lazy { AppDatabase.getInstance(this) }
    private val gson = Gson()
    private var currentBarcode: String? = null
    private var currentProductName: String? = null
    private var currentPdcaasScore: Double = 0.0
    private var currentProteinSources: List<String> = emptyList()
    private var currentProteinPer100g: Double? = null
    private var isFavorite = false

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
        val isCachedResult = intent.getBooleanExtra("IS_CACHED_RESULT", false)
        val isPrefetchedProduct = intent.getBooleanExtra("PREFETCHED_PRODUCT", false)
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
            isCachedResult && barcode != null -> {
                // Load from cached data (history/favorites) without API call
                debugLog("📦 Loading cached result")
                displayCachedResult(
                    barcode = barcode,
                    productName = intent.getStringExtra("CACHED_PRODUCT_NAME") ?: "Unknown Product",
                    pdcaasScore = intent.getDoubleExtra("CACHED_PDCAAS_SCORE", 0.0),
                    proteinSourcesJson = intent.getStringExtra("CACHED_PROTEIN_SOURCES_JSON") ?: "[]",
                    proteinPer100g = intent.getDoubleExtra("CACHED_PROTEIN_PER_100G", -1.0).takeIf { it >= 0 }
                )
            }
            isPrefetchedProduct && barcode != null -> {
                // Product data already fetched (e.g., from offline queue retry)
                // Analyze without making another API call
                debugLog("📦 Processing prefetched product data")
                val productInfo = ProductInfo(
                    barcode = barcode,
                    name = intent.getStringExtra("PRODUCT_NAME"),
                    brand = intent.getStringExtra("PRODUCT_BRAND"),
                    ingredientsText = intent.getStringExtra("PRODUCT_INGREDIENTS"),
                    proteinPer100g = intent.getDoubleExtra("PRODUCT_PROTEIN_100G", -1.0).takeIf { it >= 0 }
                )
                processPrefetchedProduct(productInfo)
            }
            barcode != null -> {
                fetchProductData(barcode)
            }
            else -> {
                showError("No valid input provided")
            }
        }

        binding.btnScanAgain.setOnClickListener {
            // Navigate to MainActivity (scanner) clearing the back stack
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFavorite.setOnClickListener {
            toggleFavorite()
        }

        // Info button click handlers
        binding.btnInfoPdcaas.setOnClickListener {
            showInfoDialog(
                title = "What is PDCAAS?",
                content = "PDCAAS (Protein Digestibility Corrected Amino Acid Score) is the gold standard for measuring protein quality, developed by the WHO/FAO.\n\n" +
                    "It measures two things:\n" +
                    "• How complete the amino acid profile is\n" +
                    "• How well your body can digest and absorb the protein\n\n" +
                    "Scores range from 0 to 1.0:\n" +
                    "• 0.90-1.0 = Excellent (complete proteins)\n" +
                    "• 0.75-0.89 = Good\n" +
                    "• 0.50-0.74 = Medium\n" +
                    "• Below 0.50 = Low quality",
                note = "A score of 1.0 means the protein provides all essential amino acids in optimal ratios for human nutrition."
            )
        }

        binding.btnInfoEffectiveProtein.setOnClickListener {
            showInfoDialog(
                title = "Effective Protein",
                content = "Effective protein is the quality-adjusted protein content. It's calculated by multiplying the total protein by the PDCAAS score.\n\n" +
                    "Example: 25g protein × 0.85 PDCAAS = 21.3g effective protein\n\n" +
                    "This gives you a better idea of how much protein your body can actually use for muscle building and repair.",
                note = "⚠️ Important: If there are several protein sources in the scanned product, the effective amount of protein per 100g is an estimate based on the ingredient list order, as ingredients are listed by weight. The actual protein proportions may vary by product."
            )
        }

        binding.btnInfoDetectedProteins.setOnClickListener {
            showInfoDialog(
                title = "Understanding Protein Details",
                content = "For each detected protein source, we show:\n\n" +
                    "📊 PDCAAS Score - Quality rating from 0-1\n\n" +
                    "📊 DIAAS Score - A newer, more precise measure (when available)\n\n" +
                    "⚠️ Limiting Amino Acids - The amino acids that limit the protein's quality. These are in short supply relative to human needs.\n\n" +
                    "⏱️ Digestion Speed - How quickly the protein is absorbed:\n" +
                    "• Fast: 30-60 min (whey, hydrolysates)\n" +
                    "• Medium: 2-3 hours (most proteins)\n" +
                    "• Slow: 6-8 hours (casein)\n\n" +
                    "💡 Notes - Additional info about the protein source.",
                note = "Proteins listed earlier in the ingredients contribute more to the overall score, as ingredients are listed by weight."
            )
        }

        // True Protein Intake Calculator (Premium Feature)
        binding.btnInfoIntakeCalculator.setOnClickListener {
            showInfoDialog(
                title = "True Protein Intake Calculator",
                content = "This calculator shows you the true amount of usable protein you've consumed based on:\n\n" +
                    "• The amount of product you ate\n" +
                    "• The protein content per 100g\n" +
                    "• The PDCAAS quality score\n\n" +
                    "Formula:\n" +
                    "Effective protein = (amount eaten ÷ 100) × protein per 100g × PDCAAS",
                note = "⚠️ This is an estimation. The PDCAAS score is based on the relative weight of protein sources as indicated by their position in the ingredient list. Actual proportions may vary."
            )
        }

        binding.btnCalculateIntake.setOnClickListener {
            calculateProteinIntake()
        }
    }

    private fun calculateProteinIntake() {
        val amountText = binding.etIntakeAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val proteinPer100g = currentProteinPer100g
        if (proteinPer100g == null || proteinPer100g <= 0) {
            Toast.makeText(this, "Protein content not available for this product", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate effective protein consumed
        val labelProtein = (amount / 100.0) * proteinPer100g
        val effectiveProtein = labelProtein * currentPdcaasScore

        // Display result
        binding.intakeResultLayout.visibility = View.VISIBLE
        binding.tvIntakeResult.text = String.format("Effective protein: %.1fg", effectiveProtein)
        binding.tvIntakeComparison.text = String.format("(vs %.1fg on label)", labelProtein)
    }

    private fun setupIntakeCalculator() {
        // Only show for premium users when we have valid data
        if (PremiumManager.checkPremium() && currentProteinPer100g != null && currentProteinPer100g!! > 0) {
            binding.intakeCalculatorCard.visibility = View.VISIBLE
            // Reset any previous calculation result, keep default 100g
            binding.intakeResultLayout.visibility = View.GONE
            if (binding.etIntakeAmount.text.isNullOrEmpty()) {
                binding.etIntakeAmount.setText("100")
            }
        } else {
            binding.intakeCalculatorCard.visibility = View.GONE
        }
    }

    private fun toggleFavorite() {
        val barcode = currentBarcode ?: return
        val name = currentProductName ?: return

        // Check if user is premium
        if (!PremiumManager.checkPremium()) {
            showPremiumUpsellDialog("Save Favorites")
            return
        }

        lifecycleScope.launch {
            if (isFavorite) {
                database.favoriteDao().deleteByBarcode(barcode)
                isFavorite = false
                Toast.makeText(this@ResultsActivity, "Removed from favorites", Toast.LENGTH_SHORT).show()
            } else {
                val favorite = FavoriteEntity(
                    barcode = barcode,
                    productName = name,
                    pdcaasScore = currentPdcaasScore,
                    proteinSourcesJson = gson.toJson(currentProteinSources),
                    proteinPer100g = currentProteinPer100g
                )
                database.favoriteDao().insert(favorite)
                isFavorite = true
                Toast.makeText(this@ResultsActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
            }
            updateFavoriteIcon()
        }
    }

    private fun showPremiumUpsellDialog(featureName: String) {
        AlertDialog.Builder(this)
            .setTitle("⭐ Unlock $featureName")
            .setMessage("For less than the price of a coffee ☕, get:\n\n✓ Save favorite products\n✓ Unlimited scan history\n✓ Compare products side-by-side\n✓ Ad-free experience\n\n🙏 Support an indie developer and help keep this app growing!")
            .setPositiveButton("Upgrade - \$1.99") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun updateFavoriteIcon() {
        val icon = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        binding.btnFavorite.setImageResource(icon)
    }

    /**
     * Show an info dialog with title, content, and optional note.
     */
    private fun showInfoDialog(title: String, content: String, note: String? = null) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_info)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Set width to 90% of screen width
        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)

        // Populate content
        dialog.findViewById<TextView>(R.id.tvInfoTitle).text = title
        dialog.findViewById<TextView>(R.id.tvInfoContent).text = content
        
        val noteView = dialog.findViewById<TextView>(R.id.tvInfoNote)
        if (note != null) {
            noteView.text = note
            noteView.visibility = View.VISIBLE
        } else {
            noteView.visibility = View.GONE
        }

        // Close button
        dialog.findViewById<Button>(R.id.btnCloseInfo).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkIfFavorite(barcode: String) {
        lifecycleScope.launch {
            isFavorite = database.favoriteDao().isFavorite(barcode)
            updateFavoriteIcon()
        }
    }

    /**
     * Display cached results from history/favorites without API call.
     */
    private fun displayCachedResult(
        barcode: String,
        productName: String,
        pdcaasScore: Double,
        proteinSourcesJson: String,
        proteinPer100g: Double?
    ) {
        binding.loadingLayout.visibility = View.GONE
        binding.resultsLayout.visibility = View.VISIBLE

        // Display product info
        binding.tvProductName.text = productName
        binding.tvBarcode.text = "Barcode: $barcode"
        binding.tvProteinContent.text = if (proteinPer100g != null) {
            "Protein: ${String.format("%.1f", proteinPer100g)}g per 100g"
        } else {
            "Protein content: Not available"
        }

        // Store scan data
        currentBarcode = barcode
        currentProductName = productName
        currentPdcaasScore = pdcaasScore
        currentProteinSources = try {
            gson.fromJson(proteinSourcesJson, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
        currentProteinPer100g = proteinPer100g

        // Check if favorite
        checkIfFavorite(barcode)

        // Display PDCAAS score
        binding.tvPdcaasScore.text = String.format("%.2f", pdcaasScore)

        // Determine quality label from score
        val qualityLabel = when {
            pdcaasScore >= 0.9 -> "Excellent"
            pdcaasScore >= 0.75 -> "Good"
            pdcaasScore >= 0.5 -> "Medium"
            else -> "Low"
        }
        binding.tvQualityCategory.text = qualityLabel

        val categoryColor = when (qualityLabel.lowercase()) {
            "excellent" -> R.color.quality_excellent
            "good" -> R.color.quality_good
            "medium" -> R.color.quality_medium
            else -> R.color.quality_low
        }
        binding.tvQualityCategory.setTextColor(ContextCompat.getColor(this, categoryColor))
        binding.tvPdcaasScore.setTextColor(ContextCompat.getColor(this, categoryColor))

        // Display found proteins (from cached list)
        if (currentProteinSources.isNotEmpty()) {
            binding.tvFoundProteins.text = "Found proteins:\n\n${currentProteinSources.joinToString("\n") { "• $it" }}"
        } else {
            binding.tvFoundProteins.text = "No protein source information cached"
        }

        // Calculate and display effective protein
        if (proteinPer100g != null && pdcaasScore > 0) {
            val effectiveProtein = proteinPer100g * pdcaasScore
            binding.tvEffectiveProtein.text = "Effective protein (quality-adjusted): ${String.format("%.1f", effectiveProtein)}g per 100g"
            binding.effectiveProteinLayout.visibility = View.VISIBLE
        } else {
            binding.effectiveProteinLayout.visibility = View.GONE
        }

        // Hide warnings card for cached results (we don't have the warning data cached)
        binding.warningsCard.visibility = View.GONE

        debugLog("✅ Displayed cached result for $productName")
        showDebugInfo()

        // Setup protein intake calculator (premium feature)
        setupIntakeCalculator()
    }

    private fun saveToHistory() {
        val barcode = currentBarcode ?: return
        val name = currentProductName ?: return

        lifecycleScope.launch {
            val history = ScanHistoryEntity(
                barcode = barcode,
                productName = name,
                pdcaasScore = currentPdcaasScore,
                proteinSourcesJson = gson.toJson(currentProteinSources),
                proteinPer100g = currentProteinPer100g
            )
            database.scanHistoryDao().insert(history)
            
            // Record scan for rate app prompt
            RateAppManager.recordScan(this@ResultsActivity)
            
            // Check if we should show rate prompt (with slight delay so results show first)
            if (RateAppManager.shouldShowRatePrompt(this@ResultsActivity)) {
                binding.root.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        RateAppManager.showRatePrompt(this@ResultsActivity)
                    }
                }, 1500) // 1.5 second delay
            }
        }
    }

    private fun fetchProductData(barcode: String) {
        lifecycleScope.launch {
            try {
                debugLog("🔍 Starting scan for barcode: $barcode")
                
                // Fetch product data from OpenFoodFacts with detailed error handling
                debugLog("📡 Fetching product data from OpenFoodFacts...")
                val fetchResult = OpenFoodFactsService.fetchProductWithStatus(barcode)
                
                when (fetchResult) {
                    is FetchResult.Success -> {
                        val productInfo = fetchResult.product
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
                        debugLog("   PDCAAS Score: ${String.format("%.2f", analysis.weightedPdcaas)}")
                        debugLog("   Quality: ${analysis.qualityLabel}")
                        debugLog("   Confidence: ${analysis.confidenceScore}")
                        debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                        for (protein in analysis.detectedProteins) {
                            debugLog("   • #${protein.position} ${protein.proteinSource.name} (PDCAAS: ${String.format("%.2f", protein.proteinSource.pdcaas)}, Weight: ${String.format("%.1f", protein.weight)}) - matched '${protein.matchedKeyword}'")
                        }

                        // Log detailed keyword matches
                        logDetailedMatches(analysis)

                        if (analysis.warnings.isNotEmpty()) {
                            debugLog("⚠️ Warnings: ${analysis.warnings.joinToString(", ")}")
                        }

                        displayResults(productInfo, analysis)
                    }
                    is FetchResult.ProductNotFound -> {
                        debugLog("❌ Product not found in OpenFoodFacts database")
                        showError(getString(R.string.product_not_found))
                    }
                    is FetchResult.ApiUnavailable -> {
                        debugLog("🚫 OpenFoodFacts API unavailable: ${fetchResult.reason}")
                        showError("⚠️ OpenFoodFacts service is temporarily unavailable.\n\n${fetchResult.reason}\n\nPlease try again later, or use the camera to scan the ingredient list directly.")
                    }
                    is FetchResult.NetworkError -> {
                        debugLog("🌐 Network error: ${fetchResult.reason}")
                        // Save barcode for later retry
                        saveForLaterRetry(barcode, fetchResult.reason)
                    }
                }
            } catch (e: Exception) {
                debugLog("💥 Error: ${e.message}")
                e.printStackTrace()
                showError("Error: ${e.message}")
            }
        }
    }

    /**
     * Process product data that was already fetched (e.g., from offline queue retry).
     * Skips the API call since we already have the data.
     */
    private fun processPrefetchedProduct(productInfo: ProductInfo) {
        lifecycleScope.launch {
            try {
                debugLog("✅ Processing prefetched product: ${productInfo.name}")
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
                debugLog("   PDCAAS Score: ${String.format("%.2f", analysis.weightedPdcaas)}")
                debugLog("   Quality: ${analysis.qualityLabel}")
                debugLog("   Confidence: ${analysis.confidenceScore}")
                debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                for (protein in analysis.detectedProteins) {
                    debugLog("   • #${protein.position} ${protein.proteinSource.name} (PDCAAS: ${String.format("%.2f", protein.proteinSource.pdcaas)}, Weight: ${String.format("%.1f", protein.weight)}) - matched '${protein.matchedKeyword}'")
                }

                logDetailedMatches(analysis)

                if (analysis.warnings.isNotEmpty()) {
                    debugLog("⚠️ Warnings: ${analysis.warnings.joinToString(", ")}")
                }

                displayResults(productInfo, analysis)
            } catch (e: Exception) {
                debugLog("💥 Error processing prefetched product: ${e.message}")
                e.printStackTrace()
                showError("Error: ${e.message}")
            }
        }
    }

    /**
     * Save a barcode for later retry when network error occurs.
     */
    private fun saveForLaterRetry(barcode: String, errorReason: String) {
        lifecycleScope.launch {
            try {
                // Check if already in queue
                val alreadyQueued = database.pendingScanDao().exists(barcode)
                if (!alreadyQueued) {
                    val pendingScan = PendingScan(
                        barcode = barcode,
                        errorReason = errorReason
                    )
                    database.pendingScanDao().insert(pendingScan)
                }

                // Show snackbar with action to view pending queue
                binding.loadingLayout.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    "📡 Offline - Barcode saved for later",
                    Snackbar.LENGTH_LONG
                ).setAction("View Queue") {
                    startActivity(Intent(this@ResultsActivity, PendingScansActivity::class.java))
                    finish()
                }.setActionTextColor(ContextCompat.getColor(this@ResultsActivity, R.color.primary_accent))
                .show()

                // Delayed finish to let user see the snackbar
                binding.root.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }, 3000)
            } catch (e: Exception) {
                debugLog("Failed to save for later: ${e.message}")
                showError("📡 Connection problem\n\n$errorReason\n\nCheck your internet connection and try again.")
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

        // Show isolated vs base filtering info
        if (analysis.filteredProteins.isNotEmpty()) {
            debugLog("")
            debugLog("🔬 ISOLATED vs BASE FILTERING:")
            debugLog("   Has isolated protein: ${if (analysis.hasIsolatedProtein) "YES" else "NO"}")
            debugLog("")
            for (filtered in analysis.filteredProteins) {
                val icon = if (filtered.wasFiltered) "❌" else "✓"
                debugLog("   $icon ${filtered.proteinName}")
                debugLog("      └─ ${filtered.reason}")
                debugLog("")
            }
            
            val filteredCount = analysis.filteredProteins.count { it.wasFiltered }
            if (filteredCount > 0) {
                debugLog("   📊 Result: $filteredCount protein(s) filtered out")
            }
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

        // Store scan data for history/favorites
        currentBarcode = productInfo.barcode
        currentProductName = productInfo.name ?: "Unknown Product"
        currentPdcaasScore = analysis.weightedPdcaas
        // Include proteins sorted by weight (primary first, then secondary)
        val sortedProteins = (analysis.primaryProteins + analysis.secondaryProteins).filter { it.weight > 0.0 }
        currentProteinSources = sortedProteins.map { it.proteinSource.name }
        currentProteinPer100g = proteinContent

        // Check if this product is already a favorite
        checkIfFavorite(productInfo.barcode)

        // Save to history
        saveToHistory()

        // Display analysis results
        displayAnalysis(analysis, proteinContent)

        // Display detected proteins
        displayDetectedProteins(analysis.detectedProteins)

        // Show debug info
        showDebugInfo()

        // Setup protein intake calculator (premium feature)
        setupIntakeCalculator()
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

        // Calculate effective protein - use dedicated layout with info button
        val effectiveProtein = analysis.effectiveProteinPer100g
        if (effectiveProtein != null && proteinContent != null) {
            binding.tvEffectiveProtein.text = "Effective protein (quality-adjusted): ${String.format("%.1f", effectiveProtein)}g per 100g"
            binding.effectiveProteinLayout.visibility = View.VISIBLE
        } else {
            binding.effectiveProteinLayout.visibility = View.GONE
        }
    }

    private fun displayDetectedProteins(detectedProteins: List<DetectedProtein>) {
        // This overload maintains backward compatibility - calls the new version
        // by wrapping in a simple analysis check
        val primary = detectedProteins.filter { it.isPrimary && it.weight > 0.0 }.sortedByDescending { it.weight }
        val secondary = detectedProteins.filter { !it.isPrimary && it.weight > 0.0 }.sortedByDescending { it.weight }
        displayDetectedProteinsSplit(primary, secondary)
    }
    
    private fun displayDetectedProteinsSplit(primaryProteins: List<DetectedProtein>, secondaryProteins: List<DetectedProtein>) {
        val sections = mutableListOf<String>()
        
        // Format a single protein's details
        fun formatProtein(protein: DetectedProtein): String {
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
            
            return details
        }
        
        // Primary Protein Sources (sorted by weight, highest first)
        if (primaryProteins.isNotEmpty()) {
            val primaryDetails = primaryProteins.map { formatProtein(it) }
            sections.add("🥇 Primary Protein Sources:\n\n${primaryDetails.joinToString("\n\n")}")
        }
        
        // Secondary Protein Sources (sorted by weight, highest first)
        if (secondaryProteins.isNotEmpty()) {
            val secondaryDetails = secondaryProteins.map { formatProtein(it) }
            sections.add("🥈 Secondary Protein Sources:\n\n${secondaryDetails.joinToString("\n\n")}")
        }
        
        if (sections.isNotEmpty()) {
            binding.tvFoundProteins.text = sections.joinToString("\n\n")
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
                debugLog("   PDCAAS Score: ${String.format("%.2f", analysis.weightedPdcaas)}")
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
                debugLog("   PDCAAS Score: ${String.format("%.2f", analysis.weightedPdcaas)}")
                debugLog("   Quality: ${analysis.qualityLabel}")
                debugLog("   Detected proteins: ${analysis.detectedProteins.size}")

                for (protein in analysis.detectedProteins) {
                    debugLog("   • #${protein.position} ${protein.proteinSource.name} (PDCAAS: ${String.format("%.2f", protein.proteinSource.pdcaas)}) - matched '${protein.matchedKeyword}'")
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
     * @return true if user is premium, false otherwise
     */
    private fun isPremiumUser(): Boolean {
        return PremiumManager.checkPremium()
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