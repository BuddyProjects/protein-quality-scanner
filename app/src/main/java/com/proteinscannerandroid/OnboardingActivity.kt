package com.proteinscannerandroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.proteinscannerandroid.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter
    
    companion object {
        private const val PREFS_NAME = "OnboardingPrefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        
        fun shouldShowOnboarding(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
        
        fun markOnboardingComplete(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOnboardingPages()
        setupIndicators()
        setupButtons()
    }

    private fun setupOnboardingPages() {
        val pages = listOf(
            OnboardingPage(
                iconRes = R.drawable.onboarding_1,
                title = "Scan Products",
                description = "Point your camera at a product barcode to scan it. You can also enter barcodes manually if scanning doesn't work."
            ),
            OnboardingPage(
                iconRes = R.drawable.onboarding_2,
                title = "Understand Your Score",
                description = "Each product gets a PDCAAS score from 0–100. Higher means better protein quality. We also show which protein sources were detected."
            ),
            OnboardingPage(
                iconRes = R.drawable.onboarding_3,
                title = "Quality Insights",
                description = "Products with collagen or other low-quality proteins are flagged. The score reflects real nutritional value, not just label claims."
            )
        )

        adapter = OnboardingAdapter(pages)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    private fun setupIndicators() {
        val indicators = arrayOfNulls<ImageView>(adapter.itemCount)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        for (i in indicators.indices) {
            indicators[i] = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.indicator_inactive))
                this.layoutParams = layoutParams
            }
            binding.indicatorLayout.addView(indicators[i])
        }

        updateIndicators(0)
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorLayout.childCount) {
            val indicator = binding.indicatorLayout.getChildAt(i) as ImageView
            indicator.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (i == position) R.drawable.indicator_active else R.drawable.indicator_inactive
                )
            )
        }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        updateButtons(0)
    }

    private fun updateButtons(position: Int) {
        if (position == adapter.itemCount - 1) {
            binding.btnNext.text = "Got it"
            binding.btnSkip.visibility = View.INVISIBLE
        } else {
            binding.btnNext.text = "Next"
            binding.btnSkip.visibility = View.VISIBLE
        }
    }

    private fun finishOnboarding() {
        markOnboardingComplete(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewPager.currentItem > 0) {
            binding.viewPager.currentItem -= 1
        } else {
            super.onBackPressed()
        }
    }
}

data class OnboardingPage(
    val iconRes: Int,
    val title: String,
    val description: String
)
