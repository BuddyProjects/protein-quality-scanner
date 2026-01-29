package com.proteinscannerandroid

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.proteinscannerandroid.databinding.ActivityCompareBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlin.math.abs

class CompareActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCompareBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Premium gate
        if (!PremiumManager.canAccessCompare()) {
            showUpgradeDialogAndFinish()
            return
        }

        binding = ActivityCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        loadComparisonData()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadComparisonData() {
        val name1 = intent.getStringExtra("FAVORITE_1_NAME") ?: "Product 1"
        val pdcaas1 = intent.getDoubleExtra("FAVORITE_1_PDCAAS", 0.0)
        val proteins1Json = intent.getStringExtra("FAVORITE_1_PROTEINS") ?: "[]"

        val name2 = intent.getStringExtra("FAVORITE_2_NAME") ?: "Product 2"
        val pdcaas2 = intent.getDoubleExtra("FAVORITE_2_PDCAAS", 0.0)
        val proteins2Json = intent.getStringExtra("FAVORITE_2_PROTEINS") ?: "[]"

        // Parse protein sources
        val listType = object : TypeToken<List<String>>() {}.type
        val proteins1: List<String> = try {
            gson.fromJson(proteins1Json, listType) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val proteins2: List<String> = try {
            gson.fromJson(proteins2Json, listType) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        // Display product 1
        binding.tvProduct1Name.text = name1
        binding.tvPdcaas1.text = String.format("%.2f", pdcaas1)
        binding.tvProteins1.text = if (proteins1.isNotEmpty()) {
            proteins1.joinToString("\n")
        } else {
            "No protein sources"
        }
        colorPdcaasBadge(binding.tvPdcaas1, pdcaas1)

        // Display product 2
        binding.tvProduct2Name.text = name2
        binding.tvPdcaas2.text = String.format("%.2f", pdcaas2)
        binding.tvProteins2.text = if (proteins2.isNotEmpty()) {
            proteins2.joinToString("\n")
        } else {
            "No protein sources"
        }
        colorPdcaasBadge(binding.tvPdcaas2, pdcaas2)

        // Determine winner and show difference
        val diff = abs(pdcaas1 - pdcaas2)
        val percentDiff = if (pdcaas2 > 0) {
            ((pdcaas1 - pdcaas2) / pdcaas2 * 100).toInt()
        } else 0

        when {
            pdcaas1 > pdcaas2 -> {
                binding.tvWinner1.visibility = View.VISIBLE
                binding.tvDifference.text = "$name1 has ${abs(percentDiff)}% higher protein quality"
            }
            pdcaas2 > pdcaas1 -> {
                binding.tvWinner2.visibility = View.VISIBLE
                binding.tvDifference.text = "$name2 has ${abs(percentDiff)}% higher protein quality"
            }
            else -> {
                binding.tvDifference.text = "Both products have equal protein quality"
            }
        }
    }

    private fun colorPdcaasBadge(view: View, score: Double) {
        val color = when {
            score >= 0.9 -> ContextCompat.getColor(this, R.color.quality_excellent)
            score >= 0.75 -> ContextCompat.getColor(this, R.color.quality_good)
            score >= 0.5 -> ContextCompat.getColor(this, R.color.quality_medium)
            else -> ContextCompat.getColor(this, R.color.quality_low)
        }
        val background = view.background as? GradientDrawable
        background?.setColor(color)
    }

    private fun showUpgradeDialogAndFinish() {
        AlertDialog.Builder(this)
            .setTitle("⭐ Unlock Compare")
            .setMessage("For less than the price of a coffee ☕, get:\n\n✓ Compare products side-by-side\n✓ Unlimited scan history\n✓ Save favorite products\n✓ Ad-free experience\n\n🙏 Support an indie developer and help keep this app growing!")
            .setPositiveButton("Upgrade - \$1.99") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
            .setNegativeButton("Maybe Later") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
