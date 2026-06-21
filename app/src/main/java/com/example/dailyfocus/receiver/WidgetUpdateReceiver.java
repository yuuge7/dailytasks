package com.example.dailyfocus.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.dailyfocus.utils.TaskHelper;

public class WidgetUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Executăm verificarea task-urilor (resetări zilnice/cooldown)
        TaskHelper.checkAndResetTasks(context);

        // 2. Forțăm actualizarea widget-ului (chiar dacă nu s-au resetat task-uri, poate s-a schimbat ziua)
        TaskHelper.updateWidget(context);

        // 3. Reprogramăm următoarea alarmă
        scheduleNextAlarm(context);
    }

    public static void scheduleNextAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WidgetUpdateReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Verificăm la fiecare 15 minute (mai eficient pentru baterie, dar destul de des pentru widget)
        long interval = 15 * 60 * 1000; 
        long triggerAtMillis = System.currentTimeMillis() + interval;

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }
}
