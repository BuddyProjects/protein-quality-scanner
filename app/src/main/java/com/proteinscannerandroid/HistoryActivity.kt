package com.proteinscannerandroid

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.data.ScanHistoryEntity
import com.proteinscannerandroid.databinding.ActivityHistoryBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        observeHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { item -> openResults(item) },
            onItemLongClick = { item -> enterSelectionMode(item) },
            onSelectionChanged = { selectedIds -> updateFabVisibility(selectedIds) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        // Swipe to delete (only in non-selection mode)
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (!adapter.isSelectionMode()) {
                    val position = viewHolder.adapterPosition
                    val item = adapter.currentList[position]
                    lifecycleScope.launch {
                        database.scanHistoryDao().deleteById(item.id)
                    }
                } else {
                    adapter.notifyDataSetChanged()
                }
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return !adapter.isSelectionMode()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerHistory)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (adapter.isSelectionMode()) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        binding.btnUpgrade.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.fabCompare.setOnClickListener {
            launchCompare()
        }

        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to delete all scan history? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllHistory() {
        lifecycleScope.launch {
            database.scanHistoryDao().deleteAll()
            android.widget.Toast.makeText(
                this@HistoryActivity,
                "History cleared",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            val isPremium = PremiumManager.checkPremium()
            val historyFlow = if (isPremium) {
                database.scanHistoryDao().getAllHistory()
            } else {
                database.scanHistoryDao().getRecentHistory(PremiumManager.FREE_HISTORY_LIMIT)
            }

            // Check total count for upgrade banner
            val totalCount = database.scanHistoryDao().getHistoryCount()
            if (!isPremium && totalCount > PremiumManager.FREE_HISTORY_LIMIT) {
                binding.upgradeBanner.visibility = View.VISIBLE
            }

            historyFlow.collectLatest { historyList ->
                if (historyList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerHistory.visibility = View.GONE
                    binding.tvHint.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerHistory.visibility = View.VISIBLE
                    binding.tvHint.visibility = View.VISIBLE
                    adapter.submitList(historyList)
                }
            }
        }
    }

    private fun enterSelectionMode(initialItem: ScanHistoryEntity) {
        adapter.setSelectionMode(true)
        adapter.toggleSelection(initialItem.id)
        binding.tvHint.text = "Select 2 products to compare"
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        binding.tvHint.text = "💡 Long-press any product to compare with others"
        binding.fabCompare.visibility = View.GONE
    }

    private fun updateFabVisibility(selectedIds: Set<Long>) {
        binding.fabCompare.visibility = if (selectedIds.size == 2) View.VISIBLE else View.GONE
    }

    private fun launchCompare() {
        if (!PremiumManager.canAccessCompare()) {
            showUpgradeDialog()
            return
        }

        val selectedIds = adapter.getSelectedIds().toList()
        if (selectedIds.size == 2) {
            lifecycleScope.launch {
                val items = database.scanHistoryDao().getByIds(selectedIds)
                if (items.size == 2) {
                    val intent = Intent(this@HistoryActivity, CompareActivity::class.java).apply {
                        putExtra("FAVORITE_1_ID", items[0].id)
                        putExtra("FAVORITE_1_NAME", items[0].productName)
                        putExtra("FAVORITE_1_PDCAAS", items[0].pdcaasScore)
                        putExtra("FAVORITE_1_PROTEINS", items[0].proteinSourcesJson)
                        putExtra("FAVORITE_2_ID", items[1].id)
                        putExtra("FAVORITE_2_NAME", items[1].productName)
                        putExtra("FAVORITE_2_PDCAAS", items[1].pdcaasScore)
                        putExtra("FAVORITE_2_PROTEINS", items[1].proteinSourcesJson)
                    }
                    startActivity(intent)
                    exitSelectionMode()
                }
            }
        }
    }

    private fun showUpgradeDialog() {
        AlertDialog.Builder(this)
            .setTitle("⭐ Premium Feature")
            .setMessage("Compare products is a premium feature.\n\nUpgrade to unlock:\n• Compare products side-by-side\n• Full scan history\n• Save favorites\n• Ad-free experience")
            .setPositiveButton("Upgrade Now") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun openResults(item: ScanHistoryEntity) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("BARCODE", item.barcode)
            // Pass cached data so we don't need to re-fetch from API
            putExtra("IS_CACHED_RESULT", true)
            putExtra("CACHED_PRODUCT_NAME", item.productName)
            putExtra("CACHED_PDCAAS_SCORE", item.pdcaasScore)
            putExtra("CACHED_PROTEIN_SOURCES_JSON", item.proteinSourcesJson)
            putExtra("CACHED_PROTEIN_PER_100G", item.proteinPer100g ?: -1.0)
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (adapter.isSelectionMode()) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}
