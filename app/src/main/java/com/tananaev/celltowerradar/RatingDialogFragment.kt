@file:Suppress("DEPRECATION")
package com.tananaev.celltowerradar

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

class RatingDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return AlertDialog.Builder(requireContext())
                .setTitle(R.string.rate_title)
                .setMessage(R.string.rate_message)
                .setNeutralButton(R.string.rate_later) { _, _ -> preferences.edit().putInt(KEY_LAUNCH_COUNT, 0).apply() }
                .setNegativeButton(R.string.rate_no) { _, _ -> preferences.edit().putBoolean(KEY_DISABLED, true).apply() }
                .setPositiveButton(R.string.rate_rate) { _, _ ->
                    preferences.edit().putBoolean(KEY_DISABLED, true).apply()
                    val uri = Uri.parse("market://details?id=" + requireContext().packageName)
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                .create()
    }

    companion object {
        private const val KEY_FIRST_START = "firstStart"
        private const val KEY_LAUNCH_COUNT = "launchCount"
        private const val KEY_DISABLED = "disabled"
        private const val DAYS_UNTIL_PROMPT = 5
        private const val LAUNCHES_UNTIL_PROMPT = 5

        fun showRating(context: Context?, fragmentManager: FragmentManager?) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (!preferences.contains(KEY_FIRST_START)) {
                preferences.edit().putLong(KEY_FIRST_START, System.currentTimeMillis()).apply()
            }
            val disabled = preferences.getBoolean(KEY_DISABLED, false)
            val firstStart = preferences.getLong(KEY_FIRST_START, 0)
            val launchCount = preferences.getInt(KEY_LAUNCH_COUNT, 0) + 1
            val daysCount = ((System.currentTimeMillis() - firstStart) / 24 / 3600 / 1000).toInt()
            preferences.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()
            if (!disabled && launchCount >= LAUNCHES_UNTIL_PROMPT && daysCount >= DAYS_UNTIL_PROMPT) {
                RatingDialogFragment().show(fragmentManager!!, null)
            }
        }
    }
}
