package com.proteinscannerandroid

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.data.FavoriteEntity
import com.proteinscannerandroid.databinding.ActivityFavoritesBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var adapter: FavoritesAdapter
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        observeFavorites()
    }

    private fun setupRecyclerView() {
        adapter = FavoritesAdapter(
            onItemClick = { item -> openResults(item) },
            onItemLongClick = { item -> enterSelectionMode(item) },
            onSelectionChanged = { selectedIds -> updateFabVisibility(selectedIds) }
        )
        binding.recyclerFavorites.layoutManager = LinearLayoutManager(this)
        binding.recyclerFavorites.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (adapter.isSelectionMode()) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        binding.btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.fabCompare.setOnClickListener {
            launchCompare()
        }
    }

    private fun observeFavorites() {
        lifecycleScope.launch {
            database.favoriteDao().getAllFavorites().collectLatest { favoritesList ->
                if (favoritesList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerFavorites.visibility = View.GONE
                    binding.tvHint.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerFavorites.visibility = View.VISIBLE
                    binding.tvHint.visibility = View.VISIBLE
                    adapter.submitList(favoritesList)
                }
            }
        }
    }

    private fun enterSelectionMode(initialItem: FavoriteEntity) {
        adapter.setSelectionMode(true)
        adapter.toggleSelection(initialItem.id)
        binding.tvTitle.text = "Select 2 to Compare"
        binding.tvHint.text = "Select 2 products to compare"
        binding.btnCancelSelection.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        binding.tvTitle.text = "Favorites"
        binding.tvHint.text = "💡 Long-press any product to compare with others"
        binding.btnCancelSelection.visibility = View.GONE
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
                val favorites = database.favoriteDao().getByIds(selectedIds)
                if (favorites.size == 2) {
                    val intent = Intent(this@FavoritesActivity, CompareActivity::class.java).apply {
                        putExtra("FAVORITE_1_ID", favorites[0].id)
                        putExtra("FAVORITE_1_NAME", favorites[0].productName)
                        putExtra("FAVORITE_1_PDCAAS", favorites[0].pdcaasScore)
                        putExtra("FAVORITE_1_PROTEINS", favorites[0].proteinSourcesJson)
                        putExtra("FAVORITE_2_ID", favorites[1].id)
                        putExtra("FAVORITE_2_NAME", favorites[1].productName)
                        putExtra("FAVORITE_2_PDCAAS", favorites[1].pdcaasScore)
                        putExtra("FAVORITE_2_PROTEINS", favorites[1].proteinSourcesJson)
                    }
                    startActivity(intent)
                    exitSelectionMode()
                }
            }
        }
    }

    private fun showUpgradeDialog() {
        AlertDialog.Builder(this)
            .setTitle("⭐ Unlock Compare")
            .setMessage("For less than a coffee ☕, get:\n\n✓ Compare products side-by-side\n✓ Unlimited scan history\n✓ Save favorite products\n✓ Ad-free experience\n\n🙏 Support an indie developer and help keep this app growing!")
            .setPositiveButton("Upgrade - \$1.99") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun openResults(item: FavoriteEntity) {
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
