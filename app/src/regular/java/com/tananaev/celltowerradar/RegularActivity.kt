package com.tananaev.celltowerradar

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

class RegularActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        findViewById<View>(android.R.id.content).applySystemBarInsets()
        if (savedInstanceState == null) {
            supportFragmentManager.commit { add(android.R.id.content, MainFragment()) }
        }
    }
}
