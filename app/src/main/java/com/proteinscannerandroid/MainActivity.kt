package com.proteinscannerandroid

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.databinding.ActivityMainBinding
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private var pendingCameraAction: (() -> Unit)? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we need to show onboarding
        if (OnboardingActivity.shouldShowOnboarding(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        
        setupClickListeners()
        observePendingScans()
        // 3D helix now handles its own animations via OpenGL
    }

    override fun onResume() {
        super.onResume()
        refreshDailyIntakeProgress()
    }

    private fun refreshDailyIntakeProgress() {
        lifecycleScope.launch {
            val today = DailyIntakeManager.todayDateString()
            val goal = DailyIntakeManager.getGoal(this@MainActivity)
            database.dailyIntakeDao().getTotalForDate(today).collectLatest { total ->
                val current = total ?: 0.0
                val progress = (current / goal).coerceIn(0.0, 1.0).toFloat()
                binding.mainProgressRing.setProgress(progress)
                val percent = DailyIntakeManager.progressPercent(current, goal)
                binding.tvDailyIntakeSubtitle.text = "${String.format("%.0f", current)}g / ${String.format("%.0f", goal)}g ($percent%)"
            }
        }
    }

    private fun observePendingScans() {
        lifecycleScope.launch {
            database.pendingScanDao().getCount().collectLatest { count ->
                if (count > 0) {
                    binding.pendingBadge.visibility = View.VISIBLE
                    binding.pendingBadge.text = if (count > 99) "99+" else count.toString()
                    binding.btnPendingScans.visibility = View.VISIBLE
                } else {
                    binding.pendingBadge.visibility = View.GONE
                    binding.btnPendingScans.visibility = View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnScanBarcode.setOnClickListener {
            if (checkCameraPermission()) {
                openBarcodeScanner()
            } else {
                pendingCameraAction = { openBarcodeScanner() }
                requestCameraPermission()
            }
        }

        binding.btnScanIngredients.setOnClickListener {
            if (checkCameraPermission()) {
                openIngredientScanner()
            } else {
                pendingCameraAction = { openIngredientScanner() }
                requestCameraPermission()
            }
        }

        binding.btnManualEntry.setOnClickListener {
            openManualEntry()
        }

        binding.btnProteinLookup.setOnClickListener {
            openProteinLookup()
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnFavorites.setOnClickListener {
            if (PremiumManager.checkPremium()) {
                startActivity(Intent(this, FavoritesActivity::class.java))
            } else {
                showPremiumUpsellDialog("Favorites")
            }
        }

        binding.btnDailyIntake.setOnClickListener {
            startActivity(Intent(this, DailyIntakeActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            openSettings()
        }

        binding.btnInfo.setOnClickListener {
            openInfo()
        }

        binding.btnPendingScans.setOnClickListener {
            startActivity(Intent(this, PendingScansActivity::class.java))
        }

        binding.logoButton.setOnClickListener {
            // Could add easter egg or info screen here
            Toast.makeText(this, "Protein Quality Scanner v1.0.3", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCameraAction?.invoke()
                pendingCameraAction = null
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_needed),
                    Toast.LENGTH_LONG
                ).show()
                pendingCameraAction = null
            }
        }
    }

    private fun openBarcodeScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        startActivity(intent)
    }

    private fun openIngredientScanner() {
        val intent = Intent(this, IngredientCameraActivity::class.java)
        startActivity(intent)
    }

    private fun openManualEntry() {
        val intent = Intent(this, ManualEntryActivity::class.java)
        startActivity(intent)
    }

    private fun processBarcode(barcode: String) {
        val intent = Intent(this, ResultsActivity::class.java)
        intent.putExtra("BARCODE", barcode)
        startActivity(intent)
    }

    private fun openProteinLookup() {
        val intent = Intent(this, ProteinLookupActivity::class.java)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openInfo() {
        val intent = Intent(this, InfoActivity::class.java)
        startActivity(intent)
    }

    private fun showPremiumUpsellDialog(featureName: String) {
        AlertDialog.Builder(this)
            .setTitle("⭐ Unlock $featureName")
            .setMessage("For less than the price of a coffee ☕, get:\n\n✓ Save favorite products\n✓ Unlimited scan history\n✓ Compare products side-by-side\n✓ Ad-free experience\n\n🙏 Support an indie developer and help keep this app growing!")
            .setPositiveButton("Upgrade - \$1.99") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }
}