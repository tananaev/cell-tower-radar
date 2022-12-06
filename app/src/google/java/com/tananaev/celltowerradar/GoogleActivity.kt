package com.tananaev.celltowerradar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class GoogleActivity : AppCompatActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics = Firebase.analytics
        MobileAds.initialize(this) {}
        setContentView(R.layout.activity_google);
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.map, MainFragment())
                .commit()
        }
        findViewById<AdView>(R.id.ad_view).loadAd(AdRequest.Builder().build())
    }
}
