package com.proteinscannerandroid

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.proteinscannerandroid.databinding.ActivityMainBinding
import com.proteinscannerandroid.premium.PremiumManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private var pendingCameraAction: (() -> Unit)? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        
        setupClickListeners()
        // 3D helix now handles its own animations via OpenGL
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

        binding.btnSettings.setOnClickListener {
            openSettings()
        }

        binding.btnInfo.setOnClickListener {
            openInfo()
        }

        binding.logoButton.setOnClickListener {
            // Could add easter egg or info screen here
            Toast.makeText(this, "Protein Quality Scanner v1.0", Toast.LENGTH_SHORT).show()
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
            .setMessage("For less than a coffee ☕, get:\n\n✓ Save favorite products\n✓ Unlimited scan history\n✓ Compare products side-by-side\n✓ Ad-free experience\n\n🙏 Support an indie developer and help keep this app growing!")
            .setPositiveButton("Upgrade - \$1.99") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }
}