package com.proteinscannerandroid

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.proteinscannerandroid.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "ProteinScannerPrefs"
        const val KEY_DEBUG_ENABLED = "debug_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        setupUI()
        loadCurrentSettings()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Debug toggle
        binding.switchDebug.setOnCheckedChangeListener { _, isChecked ->
            saveDebugPreference(isChecked)
        }
    }

    private fun loadCurrentSettings() {
        // Load debug setting (default: OFF)
        val debugEnabled = sharedPreferences.getBoolean(KEY_DEBUG_ENABLED, false)
        binding.switchDebug.isChecked = debugEnabled
    }

    private fun saveDebugPreference(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DEBUG_ENABLED, enabled)
            .apply()
    }
}
