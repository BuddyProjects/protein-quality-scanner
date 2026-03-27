package com.proteinscannerandroid

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.snackbar.Snackbar
import com.proteinscannerandroid.premium.PremiumManager
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.data.DailyIntakeEntity
import com.proteinscannerandroid.databinding.ActivityDailyIntakeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DailyIntakeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDailyIntakeBinding
    private val database by lazy { AppDatabase.getInstance(this) }
    private lateinit var adapter: DailyIntakeAdapter
    private var currentTotal = 0.0
    private var currentGoal = 150.0
    private var nativeAd: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyIntakeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentGoal = DailyIntakeManager.getGoal(this)

        setupRecyclerView()
        setupClickListeners()
        observeData()
        updateMotivationalCard()
        updateGoalDisplay()
        loadNativeAd()

        // Check if launched with "add" intent from ResultsActivity
        handleAddIntent()
    }

    private fun setupRecyclerView() {
        adapter = DailyIntakeAdapter { entry ->
            lifecycleScope.launch {
                database.dailyIntakeDao().deleteById(entry.id)
            }
        }
        binding.rvTodayLog.layoutManager = LinearLayoutManager(this)
        binding.rvTodayLog.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnQuickAdd.setOnClickListener {
            val proteinText = binding.etQuickProtein.text.toString()
            val protein = proteinText.toDoubleOrNull()
            if (protein == null || protein <= 0) {
                Toast.makeText(this, "Enter a valid protein amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val description = binding.etQuickDescription.text.toString().ifBlank { "Manual entry" }

            lifecycleScope.launch {
                database.dailyIntakeDao().insert(
                    DailyIntakeEntity(
                        date = DailyIntakeManager.todayDateString(),
                        productName = description,
                        proteinGrams = protein,
                        pdcaasScore = null,
                        effectiveProteinGrams = null,
                        barcode = null
                    )
                )
            }

            binding.etQuickProtein.text?.clear()
            binding.etQuickDescription.text?.clear()

            Snackbar.make(binding.root, "Added ${String.format("%.1f", protein)}g protein", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Today's Log")
                .setMessage("Remove all entries for today?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        database.dailyIntakeDao().deleteAllForDate(DailyIntakeManager.todayDateString())
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnChangeGoal.setOnClickListener {
            showGoalDialog()
        }

        binding.btnGoalInfo.setOnClickListener {
            showGoalInfoDialog()
        }
    }

    private fun showGoalInfoDialog() {
        val message = "How much protein do you need?\n\n" +
            "General guidelines (per day):\n\n" +
            "• Sedentary adults: 0.8g per kg body weight\n" +
            "• Active / recreational exercise: 1.2–1.6g per kg\n" +
            "• Strength training / muscle building: 1.6–2.2g per kg\n" +
            "• Endurance athletes: 1.2–1.8g per kg\n\n" +
            "Example: A 75kg person doing strength training should aim for 120–165g of protein per day.\n\n" +
            "Important: This app tracks effective (quality-adjusted) protein. A product with 30g of low-quality protein may only count as 15g effective protein. Set your goal based on effective protein, not label claims.\n\n" +
            "These are general recommendations. Consult a healthcare professional for personalized advice."

        AlertDialog.Builder(this)
            .setTitle("Protein Goal Guidance")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun observeData() {
        val today = DailyIntakeManager.todayDateString()

        lifecycleScope.launch {
            // Combine entries and total into a single observation
            database.dailyIntakeDao().getEntriesForDate(today)
                .combine(database.dailyIntakeDao().getTotalForDate(today)) { entries, total ->
                    Pair(entries, total ?: 0.0)
                }
                .collectLatest { (entries, total) ->
                    currentTotal = total
                    adapter.submitList(entries)

                    // Update empty state
                    binding.tvEmptyLog.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvTodayLog.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE

                    // Update progress ring
                    updateProgress(total)

                    // Update motivational message (may change when entries added/goal hit)
                    updateMotivationalCard()
                }
        }
    }

    private fun updateProgress(total: Double) {
        val percent = DailyIntakeManager.progressPercent(total, currentGoal)
        val progress = (total / currentGoal).coerceIn(0.0, 1.0).toFloat()

        binding.progressRing.setProgress(progress, animate = true)
        binding.progressRing.setCenterText(String.format("%.0fg / %.0fg", total, currentGoal))
        binding.tvProgressDetail.text = String.format("%.1fg / %.0fg", total, currentGoal)
        binding.tvProgressSubtitle.text = "$percent% of daily goal"

        // Celebrate if goal reached for the first time today
        if (total >= currentGoal && !DailyIntakeManager.hasAlreadyCelebratedToday(this)) {
            DailyIntakeManager.markCelebratedToday(this)
            celebrateGoalReached()
        }
    }

    private fun celebrateGoalReached() {
        binding.progressRing.pulseAnimation()
        binding.progressRing.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        Snackbar.make(
            binding.root,
            "\uD83C\uDF89 Goal reached! You hit your ${String.format("%.0f", currentGoal)}g protein target!",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun updateMotivationalCard() {
        lifecycleScope.launch {
            val goalDates = database.dailyIntakeDao().getDatesWhereGoalMet(currentGoal)
            val streak = DailyIntakeManager.calculateStreak(goalDates)
            val hasEntriesToday = database.dailyIntakeDao().getAllDatesWithEntries()
                .contains(DailyIntakeManager.todayDateString())

            val message = DailyIntakeManager.getMotivationalMessage(
                streak = streak,
                todayTotal = currentTotal,
                goal = currentGoal,
                hasEntriesToday = hasEntriesToday
            )

            binding.streakCard.visibility = View.VISIBLE
            binding.tvStreakEmoji.text = message.emoji
            binding.tvStreakCount.text = message.title
            binding.tvStreakSubtitle.text = message.subtitle
        }
    }

    private fun showGoalDialog() {
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.0f", currentGoal))
            setSelection(text.length)
            setPadding(60, 40, 60, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Daily Protein Goal")
            .setMessage("Enter your target protein intake in grams:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newGoal = editText.text.toString().toDoubleOrNull()
                if (newGoal != null && newGoal > 0) {
                    currentGoal = newGoal
                    DailyIntakeManager.setGoal(this, newGoal)
                    updateGoalDisplay()
                    updateProgress(currentTotal)
                } else {
                    Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        binding.tvCurrentGoal.text = "${String.format("%.0f", currentGoal)}g protein"
    }

    private fun handleAddIntent() {
        val productName = intent.getStringExtra("INTAKE_PRODUCT_NAME") ?: return
        val proteinGrams = intent.getDoubleExtra("INTAKE_PROTEIN_GRAMS", -1.0)
        val pdcaasScore = intent.getDoubleExtra("INTAKE_PDCAAS_SCORE", -1.0).takeIf { it >= 0 }
        val barcode = intent.getStringExtra("INTAKE_BARCODE")

        if (proteinGrams > 0) {
            val effectiveGrams = if (pdcaasScore != null) proteinGrams * pdcaasScore else null

            lifecycleScope.launch {
                database.dailyIntakeDao().insert(
                    DailyIntakeEntity(
                        date = DailyIntakeManager.todayDateString(),
                        productName = productName,
                        proteinGrams = proteinGrams,
                        pdcaasScore = pdcaasScore,
                        effectiveProteinGrams = effectiveGrams,
                        barcode = barcode
                    )
                )
            }

            Snackbar.make(binding.root, "Added ${String.format("%.1f", proteinGrams)}g from $productName", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun loadNativeAd() {
        if (PremiumManager.checkPremium()) {
            binding.nativeAdCard.visibility = View.GONE
            return
        }

        val adUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID

        val adLoader = AdLoader.Builder(this, adUnitId)
            .forNativeAd { ad: NativeAd ->
                nativeAd?.destroy()
                nativeAd = ad

                if (isDestroyed || isFinishing) {
                    ad.destroy()
                    return@forNativeAd
                }

                val adView = layoutInflater.inflate(R.layout.native_ad_layout, null) as NativeAdView
                populateNativeAdView(ad, adView)

                binding.nativeAdContainer.removeAllViews()
                binding.nativeAdContainer.addView(adView)
                binding.nativeAdCard.visibility = View.VISIBLE
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    binding.nativeAdCard.visibility = View.GONE
                    Log.d("DailyIntakeActivity", "Native ad failed to load: ${loadAdError.message}")
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

        (adView.headlineView as TextView).text = nativeAd.headline

        if (nativeAd.body != null) {
            (adView.bodyView as TextView).text = nativeAd.body
            adView.bodyView?.visibility = View.VISIBLE
        } else {
            adView.bodyView?.visibility = View.GONE
        }

        if (nativeAd.callToAction != null) {
            (adView.callToActionView as Button).text = nativeAd.callToAction
            adView.callToActionView?.visibility = View.VISIBLE
        } else {
            adView.callToActionView?.visibility = View.GONE
        }

        if (nativeAd.icon != null) {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = View.GONE
        }

        if (nativeAd.advertiser != null) {
            (adView.advertiserView as TextView).text = nativeAd.advertiser
            adView.advertiserView?.visibility = View.VISIBLE
        } else {
            adView.advertiserView?.visibility = View.GONE
        }

        adView.setNativeAd(nativeAd)
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }
}
