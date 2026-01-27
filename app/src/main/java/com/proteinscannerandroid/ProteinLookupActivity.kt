package com.proteinscannerandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.proteinscannerandroid.databinding.ActivityProteinLookupBinding

class ProteinLookupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProteinLookupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityProteinLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSearch.setOnClickListener {
            val proteinName = binding.etProteinName.text.toString().trim()
            
            if (proteinName.isEmpty()) {
                Toast.makeText(this, "Please enter a protein source name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Launch results activity with protein lookup
            val intent = Intent(this, ResultsActivity::class.java)
            intent.putExtra("PROTEIN_SOURCE", proteinName)
            startActivity(intent)
        }

        binding.btnClear.setOnClickListener {
            binding.etProteinName.text?.clear()
        }
    }

    private fun applyTheme() {
        val sharedPrefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val theme = sharedPrefs.getString(SettingsActivity.KEY_THEME_MODE, SettingsActivity.THEME_DARK)
            ?: SettingsActivity.THEME_DARK

        val mode = when (theme) {
            SettingsActivity.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            SettingsActivity.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsActivity.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }

        AppCompatDelegate.setDefaultNightMode(mode)
    }
}