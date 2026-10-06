package com.maxilu.taptap;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        SaveMigrator.migrate(this);
        game = new GameView(this);
        setContentView(game);
    }
    @Override protected void onPause() { super.onPause(); if (game != null) game.saveAndLeave(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.applyOffline(); }
}
