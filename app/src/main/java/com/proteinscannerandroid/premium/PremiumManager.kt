package com.proteinscannerandroid.premium

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PremiumManager {
    private const val PREFS_NAME = "ProteinScannerPrefs"
    private const val KEY_IS_PREMIUM = "is_premium"

    const val FREE_HISTORY_LIMIT = 3

    private var sharedPreferences: SharedPreferences? = null

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPremium.value = sharedPreferences?.getBoolean(KEY_IS_PREMIUM, false) ?: false
    }

    fun checkPremium(): Boolean {
        return _isPremium.value
    }

    fun setPremium(premium: Boolean) {
        sharedPreferences?.edit()?.putBoolean(KEY_IS_PREMIUM, premium)?.apply()
        _isPremium.value = premium
    }

    fun canAccessFullHistory(): Boolean {
        return checkPremium()
    }

    fun canAccessCompare(): Boolean {
        return checkPremium()
    }

    fun shouldShowAds(): Boolean {
        return !checkPremium()
    }
}
