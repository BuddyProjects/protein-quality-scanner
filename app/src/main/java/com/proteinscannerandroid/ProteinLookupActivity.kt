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
}