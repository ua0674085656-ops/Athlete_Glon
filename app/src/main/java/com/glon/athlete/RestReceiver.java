package com.glon.athlete;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/** Срабатывает по окончании отдыха, даже если приложение свёрнуто или закрыто. */
public class RestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 2, open, flags);

        Notification n = new NotificationCompat.Builder(ctx, "rest")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Отдых закончен")
                .setContentText("Следующий подход")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, n);
    }
}
