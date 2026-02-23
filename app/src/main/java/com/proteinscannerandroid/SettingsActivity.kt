package com.proteinscannerandroid

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.proteinscannerandroid.billing.BillingManager
import com.proteinscannerandroid.billing.PurchaseState
import com.proteinscannerandroid.databinding.ActivitySettingsBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var billingManager: BillingManager
    
    // Hidden debug mode toggle (tap version 7 times)
    private var tapCount = 0
    private var lastTapTime = 0L

    companion object {
        const val PREFS_NAME = "ProteinScannerPrefs"
        const val KEY_DEBUG_ENABLED = "debug_enabled"
        const val KEY_DEBUG_PREMIUM_ENABLED = "debug_premium_enabled"
        private const val TAP_TIMEOUT_MS = 2000L
        private const val TAPS_REQUIRED = 7
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        billingManager = BillingManager(this)

        setupUI()
        loadCurrentSettings()
        observePremiumState()
        observePurchaseState()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Hidden debug mode: tap version 7 times to toggle
        binding.tvVersion.setOnClickListener {
            val now = System.currentTimeMillis()
            
            // Reset counter if too much time has passed
            if (now - lastTapTime > TAP_TIMEOUT_MS) {
                tapCount = 0
            }
            lastTapTime = now
            tapCount++
            
            // Show progress hints at tap 4, 5, 6
            when (tapCount) {
                4 -> Toast.makeText(this, "3 more taps...", Toast.LENGTH_SHORT).show()
                5 -> Toast.makeText(this, "2 more taps...", Toast.LENGTH_SHORT).show()
                6 -> Toast.makeText(this, "1 more tap...", Toast.LENGTH_SHORT).show()
                TAPS_REQUIRED -> {
                    tapCount = 0
                    toggleDebugMode()
                }
            }
        }

        // Upgrade button
        binding.btnUpgrade.setOnClickListener {
            billingManager.launchPurchaseFlow(this)
        }

        // Restore purchases
        binding.tvRestorePurchases.setOnClickListener {
            billingManager.queryExistingPurchases()
            Toast.makeText(this, "Checking for existing purchases...", Toast.LENGTH_SHORT).show()
        }

        // Rate app button
        binding.btnRateApp.setOnClickListener {
            RateAppManager.openPlayStore(this)
        }

        // Send feedback button
        binding.btnSendFeedback.setOnClickListener {
            RateAppManager.openFeedbackEmail(this)
        }
    }
    
    private fun toggleDebugMode() {
        val currentDebug = sharedPreferences.getBoolean(KEY_DEBUG_ENABLED, false)
        val currentPremiumDebug = sharedPreferences.getBoolean(KEY_DEBUG_PREMIUM_ENABLED, false)
        
        // Toggle both debug flags together
        val newState = !currentDebug
        
        sharedPreferences.edit()
            .putBoolean(KEY_DEBUG_ENABLED, newState)
            .putBoolean(KEY_DEBUG_PREMIUM_ENABLED, newState)
            .apply()
        
        // Update PremiumManager for premium debug
        PremiumManager.setPremium(newState)
        
        val status = if (newState) "enabled 🔧" else "disabled"
        Toast.makeText(this, "Debug mode $status", Toast.LENGTH_LONG).show()
    }

    private fun loadCurrentSettings() {
        // Load saved debug states (applies premium debug if it was enabled)
        val premiumDebugEnabled = sharedPreferences.getBoolean(KEY_DEBUG_PREMIUM_ENABLED, false)
        if (premiumDebugEnabled) {
            PremiumManager.setPremium(true)
        }
    }

    private fun observePremiumState() {
        lifecycleScope.launch {
            PremiumManager.isPremium.collectLatest { isPremium ->
                updatePremiumUI(isPremium)
            }
        }
    }

    private fun updatePremiumUI(isPremium: Boolean) {
        if (isPremium) {
            binding.premiumActiveLayout.visibility = View.VISIBLE
            binding.upgradeLayout.visibility = View.GONE
        } else {
            binding.premiumActiveLayout.visibility = View.GONE
            binding.upgradeLayout.visibility = View.VISIBLE
        }
    }

    private fun observePurchaseState() {
        lifecycleScope.launch {
            billingManager.purchaseState.collectLatest { state ->
                when (state) {
                    is PurchaseState.Loading -> {
                        binding.btnUpgrade.isEnabled = false
                        binding.btnUpgrade.text = "Processing..."
                    }
                    is PurchaseState.Success -> {
                        Toast.makeText(this@SettingsActivity, "Thank you for upgrading!", Toast.LENGTH_LONG).show()
                        billingManager.resetState()
                    }
                    is PurchaseState.Error -> {
                        Toast.makeText(this@SettingsActivity, state.message, Toast.LENGTH_LONG).show()
                        billingManager.resetState()
                        binding.btnUpgrade.isEnabled = true
                        binding.btnUpgrade.text = "Upgrade to Premium - \$1.99"
                    }
                    is PurchaseState.Idle -> {
                        binding.btnUpgrade.isEnabled = true
                        binding.btnUpgrade.text = "Upgrade to Premium - \$1.99"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}
