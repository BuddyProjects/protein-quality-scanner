package com.proteinscannerandroid

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.proteinscannerandroid.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "ProteinScannerPrefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
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

        // Theme selection
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioDark -> THEME_DARK
                R.id.radioLight -> THEME_LIGHT
                R.id.radioSystem -> THEME_SYSTEM
                else -> THEME_DARK
            }
            
            saveThemePreference(theme)
            applyTheme(theme)
        }
    }

    private fun loadCurrentSettings() {
        val currentTheme = sharedPreferences.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        
        when (currentTheme) {
            THEME_DARK -> binding.radioDark.isChecked = true
            THEME_LIGHT -> binding.radioLight.isChecked = true
            THEME_SYSTEM -> binding.radioSystem.isChecked = true
        }
    }

    private fun saveThemePreference(theme: String) {
        sharedPreferences.edit()
            .putString(KEY_THEME_MODE, theme)
            .apply()
    }

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}