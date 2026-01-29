package com.proteinscannerandroid

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.ads.MobileAds
import com.proteinscannerandroid.data.AppDatabase
import com.proteinscannerandroid.premium.PremiumManager

class ProteinScannerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        // Force dark theme always - no light mode support
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Initialize PremiumManager
        PremiumManager.init(this)

        // Initialize AdMob SDK
        MobileAds.initialize(this) { initializationStatus ->
            // SDK initialization complete
        }
    }
}
