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

    companion object {
        const val PREFS_NAME = "ProteinScannerPrefs"
        const val KEY_DEBUG_ENABLED = "debug_enabled"
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

        // Premium debug toggle
        binding.switchPremiumDebug.setOnCheckedChangeListener { _, isChecked ->
            PremiumManager.setPremium(isChecked)
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Premium mode $status", Toast.LENGTH_SHORT).show()
        }

        // Debug toggle
        binding.switchDebug.setOnCheckedChangeListener { _, isChecked ->
            saveDebugPreference(isChecked)
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
    }

    private fun loadCurrentSettings() {
        // Load debug setting (default: OFF)
        val debugEnabled = sharedPreferences.getBoolean(KEY_DEBUG_ENABLED, false)
        binding.switchDebug.isChecked = debugEnabled

        // Load premium debug setting
        binding.switchPremiumDebug.isChecked = PremiumManager.checkPremium()
    }

    private fun saveDebugPreference(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DEBUG_ENABLED, enabled)
            .apply()
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
