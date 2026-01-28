package com.proteinscannerandroid

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.ads.MobileAds

class ProteinScannerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Force dark theme always - no light mode support
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        // Initialize AdMob SDK
        MobileAds.initialize(this) { initializationStatus ->
            // SDK initialization complete
        }
    }
}
