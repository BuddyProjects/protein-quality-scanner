package com.proteinscannerandroid

import android.app.Application
import com.google.android.gms.ads.MobileAds

class ProteinScannerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob SDK
        MobileAds.initialize(this) { initializationStatus ->
            // SDK initialization complete
        }
    }
}
