package com.maxilu.taptap;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.drawable.Icon;

public final class NotificationHelper {
    public static final String CHANNEL_PROGRESS = "tap_tap_progress";
    public static final String CHANNEL_REWARDS = "tap_tap_rewards";
    public static final String CHANNEL_UPDATES = "tap_tap_updates";

    private NotificationHelper() {}

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel progress = new NotificationChannel(
                CHANNEL_PROGRESS,
                "Tap Tap — Progress",
                NotificationManager.IMPORTANCE_DEFAULT);
        progress.setDescription("Egg hatching and monster evolution notifications.");

        NotificationChannel rewards = new NotificationChannel(
                CHANNEL_REWARDS,
                "Tap Tap — Rewards",
                NotificationManager.IMPORTANCE_DEFAULT);
        rewards.setDescription("Offline coins and collection rewards.");

        NotificationChannel updates = new NotificationChannel(
                CHANNEL_UPDATES,
                "Tap Tap — Updates",
                NotificationManager.IMPORTANCE_HIGH);
        updates.setDescription("New Tap Tap versions and important update information.");

        nm.createNotificationChannel(progress);
        nm.createNotificationChannel(rewards);
        nm.createNotificationChannel(updates);
    }

    private static PendingIntent openGameIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 100, intent, flags);
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static void show(Context context, int id, String channel, String title, String text) {
        if (!canNotify(context)) return;

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.ic_taptap_notification)
                .setLargeIcon(Icon.createWithResource(context, R.drawable.tap_tap_icon))
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Tap Tap")
                .setContentIntent(openGameIntent(context))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setColor(0xFF6D3DFF);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, builder.build());
    }

    public static void showEggReady(Context context, String monsterName) {
        show(context, 2001, CHANNEL_PROGRESS,
                "TAP TAP · Egg hatched",
                monsterName + " is ready in your collection.");
    }

    public static void showOfflineReward(Context context, long coins) {
        if (coins <= 0) return;
        show(context, 2002, CHANNEL_REWARDS,
                "TAP TAP · Offline rewards",
                "Your monsters collected " + coins + " coins while you were away.");
    }

    public static void showUpdateAvailable(Context context, String versionName) {
        show(context, 2003, CHANNEL_UPDATES,
                "TAP TAP · Update available",
                "Version " + versionName + " is ready to install.");
    }
}
