package com.maxilu.taptap;

import android.content.Context;
import android.content.SharedPreferences;

public final class SaveMigrator {
    public static final int SAVE_VERSION = 1;
    private static final String PREFS = "tap_tap_save";

    private SaveMigrator() {}

    public static void migrate(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int oldVersion = sp.getInt("save_version", 0);
        SharedPreferences.Editor edit = sp.edit();

        if (oldVersion < 1) {
            edit.putInt("save_version", 1);
        }

        edit.apply();
    }
}
