package com.proteinscannerandroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Manages app rating prompts based on usage.
 * Shows a prompt after X scans, with smart routing:
 * - "Yes, I love it!" → Play Store
 * - "Not really" → Feedback email
 */
object RateAppManager {
    private const val PREFS_NAME = "RateAppPrefs"
    private const val KEY_SCAN_COUNT = "scan_count"
    private const val KEY_RATED = "has_rated"
    private const val KEY_NEVER_ASK = "never_ask"
    private const val KEY_LAST_PROMPT_COUNT = "last_prompt_count"
    
    // Show prompt after this many scans
    private const val FIRST_PROMPT_AT = 5
    private const val PROMPT_INTERVAL = 15  // Ask again every 15 scans if dismissed
    
    const val FEEDBACK_EMAIL = "proteinscanner.feedback@gmail.com"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.proteinscannerandroid"
    
    /**
     * Call this after each successful scan.
     */
    fun recordScan(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_SCAN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_SCAN_COUNT, currentCount).apply()
    }
    
    /**
     * Check if we should show the rate prompt.
     */
    fun shouldShowRatePrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Never ask again if user chose that option or already rated
        if (prefs.getBoolean(KEY_RATED, false) || prefs.getBoolean(KEY_NEVER_ASK, false)) {
            return false
        }
        
        val scanCount = prefs.getInt(KEY_SCAN_COUNT, 0)
        val lastPromptCount = prefs.getInt(KEY_LAST_PROMPT_COUNT, 0)
        
        // First prompt
        if (scanCount >= FIRST_PROMPT_AT && lastPromptCount == 0) {
            return true
        }
        
        // Subsequent prompts (every PROMPT_INTERVAL scans after dismissal)
        if (lastPromptCount > 0 && scanCount >= lastPromptCount + PROMPT_INTERVAL) {
            return true
        }
        
        return false
    }
    
    /**
     * Show the rate app dialog with smart routing.
     */
    fun showRatePrompt(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scanCount = prefs.getInt(KEY_SCAN_COUNT, 0)
        
        // Record that we showed the prompt
        prefs.edit().putInt(KEY_LAST_PROMPT_COUNT, scanCount).apply()
        
        AlertDialog.Builder(activity)
            .setTitle("Enjoying Protein Scanner? 🧬")
            .setMessage("You've scanned $scanCount products! We'd love to hear what you think.")
            .setPositiveButton("⭐ Love it!") { _, _ ->
                // Mark as rated and open Play Store
                prefs.edit().putBoolean(KEY_RATED, true).apply()
                openPlayStore(activity)
            }
            .setNegativeButton("Could be better") { _, _ ->
                // Open feedback email
                openFeedbackEmail(activity, "Feedback: Room for improvement")
            }
            .setNeutralButton("Ask later") { dialog, _ ->
                // Just dismiss, will ask again after PROMPT_INTERVAL more scans
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Open Play Store listing.
     */
    fun openPlayStore(context: Context) {
        try {
            // Try Play Store app first
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.proteinscannerandroid")))
        } catch (e: Exception) {
            // Fall back to browser
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)))
        }
    }
    
    /**
     * Open email client for feedback.
     */
    fun openFeedbackEmail(context: Context, subject: String = "Protein Scanner Feedback") {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "\n\n---\nApp Version: ${getAppVersion(context)}\nDevice: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}")
        }
        
        try {
            context.startActivity(Intent.createChooser(intent, "Send feedback via..."))
        } catch (e: Exception) {
            // No email client
            android.widget.Toast.makeText(context, "No email app found. Email us at: $FEEDBACK_EMAIL", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Mark that user never wants to be asked again.
     */
    fun neverAskAgain(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NEVER_ASK, true)
            .apply()
    }
}
