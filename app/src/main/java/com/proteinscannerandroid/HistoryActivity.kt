package com.proteinscannerandroid

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
        adapter = HistoryAdapter { item ->
            openResults(item)
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                lifecycleScope.launch {
                    database.scanHistoryDao().deleteById(item.id)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerHistory)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnUpgrade.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerHistory.visibility = View.VISIBLE
                    adapter.submitList(historyList)
                }
            }
        }
    }

    private fun openResults(item: ScanHistoryEntity) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("BARCODE", item.barcode)
        }
        startActivity(intent)
    }
}
