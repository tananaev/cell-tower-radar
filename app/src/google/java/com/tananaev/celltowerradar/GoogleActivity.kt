package com.tananaev.celltowerradar

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewManagerFactory
import com.tananaev.celltowerradar.databinding.ActivityGoogleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GoogleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGoogleBinding
    private var adsInitialized = false
    private var adRequested = false
    private var adLoaded = false
    private var resumed = false
    private var sessionCounted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sessionCounted = savedInstanceState?.getBoolean("sessionCounted") ?: false
        binding = ActivityGoogleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        binding.adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                adLoaded = true
                updateBanner()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                adRequested = false
                adLoaded = false
                binding.adContainer.isVisible = false
                binding.adView.pause()
            }
        }
        supportFragmentManager.setFragmentResultListener(MainFragment.CONTENT_STATE, this) { _, result ->
            if (result.getBoolean(MainFragment.CONTENT_AVAILABLE)) handleRating()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            MobileAds.initialize(this@GoogleActivity) {
                lifecycleScope.launch {
                    adsInitialized = true
                    updateBanner()
                }
            }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit { add(R.id.map, MainFragment()) }
        }
    }

    private fun updateBanner() {
        if (isDestroyed) return
        binding.adContainer.isVisible = adLoaded
        if (resumed) {
            binding.adView.resume()
            if (adsInitialized && !adRequested) {
                adRequested = true
                binding.adView.loadAd(AdRequest.Builder().build())
            }
        } else {
            binding.adView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateBanner()
    }

    override fun onPause() {
        resumed = false
        binding.adView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.adView.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sessionCounted", sessionCounted)
        super.onSaveInstanceState(outState)
    }

    private fun handleRating() {
        if (sessionCounted) return
        sessionCounted = true
        // Preserve the file previously used by android.preference.PreferenceManager.
        val preferences = getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
        if (preferences.getBoolean("ratingShown", false)) return
        val openTimes = preferences.getInt("openTimes", 0) + 1
        preferences.edit { putInt("openTimes", openTimes) }
        if (openTimes < 5) return
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnSuccessListener(this) { info ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                manager.launchReviewFlow(this, info).addOnCompleteListener(this) {
                    // Completion does not guarantee Google actually displayed a prompt.
                    preferences.edit { putBoolean("ratingShown", true) }
                }
            }
        }
    }
}
