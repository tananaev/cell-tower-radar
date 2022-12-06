package com.tananaev.celltowerradar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RegularActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, MainFragment())
                .commit()
        }
    }
}
