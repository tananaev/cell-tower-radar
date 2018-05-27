package com.tananaev.celltowerradar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;

public class RatingDialogFragment extends DialogFragment {

    private static final String KEY_FIRST_START = "firstStart";
    private static final String KEY_LAUNCH_COUNT = "launchCount";
    private static final String KEY_DISABLED = "disabled";

    private static final int DAYS_UNTIL_PROMPT = 5;
    private static final int LAUNCHES_UNTIL_PROMPT = 5;

    public static void showRating(Context context, FragmentManager fragmentManager) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (!preferences.contains(KEY_FIRST_START)) {
            preferences.edit().putLong(KEY_FIRST_START, System.currentTimeMillis()).apply();
        }

        boolean disabled = preferences.getBoolean(KEY_DISABLED, false);
        long firstStart = preferences.getLong(KEY_FIRST_START, 0);
        int launchCount = preferences.getInt(KEY_LAUNCH_COUNT, 0) + 1;
        int daysCount = (int) ((System.currentTimeMillis() - firstStart) / 24 / 3600 / 1000);

        preferences.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply();

        if (!disabled && launchCount >= LAUNCHES_UNTIL_PROMPT && daysCount >= DAYS_UNTIL_PROMPT) {
            new RatingDialogFragment().show(fragmentManager, null);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.rate_title)
                .setMessage(R.string.rate_message)
                .setNeutralButton(R.string.rate_later, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        preferences.edit().putInt(KEY_LAUNCH_COUNT, 0).apply();
                    }
                })
                .setNegativeButton(R.string.rate_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        preferences.edit().putBoolean(KEY_DISABLED, true).apply();
                    }
                })
                .setPositiveButton(R.string.rate_rate, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        preferences.edit().putBoolean(KEY_DISABLED, true).apply();
                        Uri uri = Uri.parse("market://details?id=" + getActivity().getPackageName());
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                })
                .create();
    }

}
