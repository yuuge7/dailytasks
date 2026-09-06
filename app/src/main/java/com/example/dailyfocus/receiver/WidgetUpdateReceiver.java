package com.example.dailyfocus.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.TaskHelper;

public class WidgetUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        TaskRepository.execute(() -> {
            try {
                // 1. Verificăm resetările zilnice/cooldown
                TaskHelper.checkAndResetTasks(context);
                // 2. Forțăm actualizarea widget-ului (poate s-a schimbat ziua)
                TaskHelper.updateWidget(context);
                // 3. Reprogramăm următoarea alarmă
                scheduleNextAlarm(context);
            } finally {
                pendingResult.finish();
            }
        });
    }

    /**
     * Programează următoarea verificare periodică.
     *
     * Alarmă INEXACTĂ intenționat: un refresh de widget nu are nevoie de precizie la
     * secundă, iar alarmele exacte cer SCHEDULE_EXACT_ALARM — permisiune care NU mai
     * este acordată automat începând cu Android 14. Varianta exactă arunca
     * SecurityException și omora aplicația la prima pornire după instalare.
     * setAndAllowWhileIdle nu cere nicio permisiune și trece și prin Doze.
     */
    public static void scheduleNextAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, WidgetUpdateReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Verificăm la fiecare 15 minute (eficient pentru baterie, destul de des pentru widget)
        long interval = 15 * 60 * 1000;
        long triggerAtMillis = System.currentTimeMillis() + interval;

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } catch (Exception e) {
            // Nicio alarmă nu justifică oprirea aplicației — widget-ul se va actualiza
            // oricum la următoarea deschidere.
            e.printStackTrace();
        }
    }
}
