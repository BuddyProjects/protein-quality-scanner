package com.proteinscannerandroid

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.data.PendingScan
import com.proteinscannerandroid.databinding.ActivityPendingScansBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PendingScansActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPendingScansBinding
    private lateinit var adapter: PendingScanAdapter
    private val database by lazy { AppDatabase.getInstance(this) }
    private var isRetrying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingScansBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        observePendingScans()
    }

    private fun setupRecyclerView() {
        adapter = PendingScanAdapter(
            onRetryClick = { item -> retrySingleScan(item) },
            onItemClick = { item -> retrySingleScan(item) }
        )
        binding.recyclerPending.layoutManager = LinearLayoutManager(this)
        binding.recyclerPending.adapter = adapter

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
                    database.pendingScanDao().deleteById(item.id)
                    Snackbar.make(binding.root, "Removed from queue", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerPending)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllDialog()
        }

        binding.fabRetryAll.setOnClickListener {
            retryAllScans()
        }
    }

    private fun observePendingScans() {
        lifecycleScope.launch {
            database.pendingScanDao().getAll().collectLatest { pendingList ->
                if (pendingList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerPending.visibility = View.GONE
                    binding.infoBanner.visibility = View.GONE
                    binding.fabRetryAll.visibility = View.GONE
                    binding.btnClearAll.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerPending.visibility = View.VISIBLE
                    binding.infoBanner.visibility = View.VISIBLE
                    binding.fabRetryAll.visibility = View.VISIBLE
                    binding.btnClearAll.visibility = View.VISIBLE
                    adapter.submitList(pendingList)
                }
            }
        }
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Pending")
            .setMessage("Are you sure you want to remove all pending scans? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllPending()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllPending() {
        lifecycleScope.launch {
            database.pendingScanDao().deleteAll()
            Toast.makeText(this@PendingScansActivity, "Cleared all pending scans", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retrySingleScan(pendingScan: PendingScan) {
        if (isRetrying) {
            Toast.makeText(this, "Please wait, retry in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            isRetrying = true
            try {
                val result = OpenFoodFactsService.fetchProductWithStatus(pendingScan.barcode)

                when (result) {
                    is FetchResult.Success -> {
                        // Success! Remove from queue and open results with prefetched data
                        // (avoid second API call which could fail)
                        val product = result.product
                        android.util.Log.d("PendingScans", "SUCCESS for ${pendingScan.barcode}")
                        android.util.Log.d("PendingScans", "  Name: ${product.name}")
                        android.util.Log.d("PendingScans", "  Brand: ${product.brand}")
                        android.util.Log.d("PendingScans", "  Ingredients: ${product.ingredientsText?.take(100)}...")
                        android.util.Log.d("PendingScans", "  Protein: ${product.proteinPer100g}")
                        
                        database.pendingScanDao().deleteById(pendingScan.id)
                        val intent = Intent(this@PendingScansActivity, ResultsActivity::class.java)
                        intent.putExtra("BARCODE", pendingScan.barcode)
                        intent.putExtra("PREFETCHED_PRODUCT", true)
                        intent.putExtra("PRODUCT_NAME", product.name)
                        intent.putExtra("PRODUCT_BRAND", product.brand)
                        intent.putExtra("PRODUCT_INGREDIENTS", product.ingredientsText)
                        intent.putExtra("PRODUCT_PROTEIN_100G", product.proteinPer100g ?: -1.0)
                        startActivity(intent)
                    }
                    is FetchResult.ProductNotFound -> {
                        // Product not in database - remove from queue with message
                        android.util.Log.d("PendingScans", "NOT FOUND for ${pendingScan.barcode}")
                        database.pendingScanDao().deleteById(pendingScan.id)
                        Snackbar.make(
                            binding.root,
                            "Product not found in database",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    is FetchResult.ApiUnavailable -> {
                        android.util.Log.d("PendingScans", "API UNAVAILABLE for ${pendingScan.barcode}: ${result.reason}")
                        Snackbar.make(
                            binding.root,
                            "Server unavailable, try again later",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    is FetchResult.NetworkError -> {
                        android.util.Log.d("PendingScans", "NETWORK ERROR for ${pendingScan.barcode}: ${result.reason}")
                        Snackbar.make(
                            binding.root,
                            "Still offline - check your connection",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Retry failed: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                isRetrying = false
            }
        }
    }

    private fun retryAllScans() {
        if (isRetrying) {
            Toast.makeText(this, "Please wait, retry in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            isRetrying = true
            val pendingList = database.pendingScanDao().getAllForRetry()
            
            if (pendingList.isEmpty()) {
                Toast.makeText(this@PendingScansActivity, "No pending scans to retry", Toast.LENGTH_SHORT).show()
                isRetrying = false
                return@launch
            }

            var successCount = 0
            var failCount = 0
            var notFoundCount = 0

            binding.fabRetryAll.isEnabled = false
            binding.fabRetryAll.text = "Retrying..."

            for (pending in pendingList) {
                try {
                    val result = OpenFoodFactsService.fetchProductWithStatus(pending.barcode)

                    when (result) {
                        is FetchResult.Success -> {
                            // Success - remove from queue
                            database.pendingScanDao().deleteById(pending.id)
                            successCount++
                        }
                        is FetchResult.ProductNotFound -> {
                            // Not found - remove from queue
                            database.pendingScanDao().deleteById(pending.id)
                            notFoundCount++
                        }
                        is FetchResult.ApiUnavailable, is FetchResult.NetworkError -> {
                            // Still can't reach - keep in queue
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }

            binding.fabRetryAll.isEnabled = true
            binding.fabRetryAll.text = "Retry All"
            isRetrying = false

            // Show summary
            val message = buildString {
                if (successCount > 0) append("✓ $successCount successful")
                if (notFoundCount > 0) {
                    if (isNotEmpty()) append("\n")
                    append("⚠ $notFoundCount not in database")
                }
                if (failCount > 0) {
                    if (isNotEmpty()) append("\n")
                    append("✗ $failCount still pending")
                }
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

            // If some succeeded, open results for the most recent success
            if (successCount > 0 && failCount == 0 && notFoundCount == 0) {
                // All succeeded, go back to main
                Toast.makeText(this@PendingScansActivity, "All scans completed!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
