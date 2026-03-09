package com.proteinscannerandroid

import android.app.AlertDialog
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyIntakeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentGoal = DailyIntakeManager.getGoal(this)

        setupRecyclerView()
        setupClickListeners()
        observeData()
        loadStreak()
        updateGoalDisplay()

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

    private fun loadStreak() {
        lifecycleScope.launch {
            val dates = database.dailyIntakeDao().getAllDatesWithEntries()
            val streak = DailyIntakeManager.calculateStreak(dates)

            if (streak > 0) {
                binding.streakCard.visibility = View.VISIBLE
                binding.tvStreakCount.text = "$streak Day Streak!"
                binding.tvStreakSubtitle.text = when {
                    streak >= 30 -> "Incredible dedication!"
                    streak >= 14 -> "You're on fire!"
                    streak >= 7 -> "One week strong!"
                    streak >= 3 -> "Keep it going!"
                    else -> "Great start!"
                }
            } else {
                binding.streakCard.visibility = View.GONE
            }
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
}
