package com.maxilu.taptap;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7001;
    private GameView game;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        SaveMigrator.migrate(this);
        NotificationHelper.createChannels(this);
        requestNotificationPermissionIfNeeded();

        game = new GameView(this);
        setContentView(game);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    @Override protected void onPause() {
        if (game != null) game.pauseGame();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (game != null) game.resumeGame();
    }
}
