package com.proteinscannerandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.proteinscannerandroid.databinding.ActivityManualEntryBinding

class ManualEntryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManualEntryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAnalyze.setOnClickListener {
            val barcode = binding.etBarcode.text.toString().trim()
            if (barcode.isNotEmpty()) {
                val intent = Intent(this, ResultsActivity::class.java)
                intent.putExtra("BARCODE", barcode)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please enter a barcode", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}