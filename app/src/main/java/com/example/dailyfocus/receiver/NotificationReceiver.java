package com.example.dailyfocus.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.dailyfocus.MainActivity;
import com.example.dailyfocus.R;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;

public class NotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "daily_focus_reminders";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
        if (taskId == -1) return;

        AppDatabase db = AppDatabase.getInstance(context);
        Task task = db.taskDao().getTaskById(taskId);
        if (task == null || !task.hasReminder) return;

        // --- LOGICA DE TRIMITERE ---
        boolean shouldNotify = false;
        String notifTitle = task.title;
        String notifBody = "";

        if (task.isCooldown24h) {
            // La Cooldown, notificăm când se resetează.
            // Chiar dacă în DB apare încă "completed", alarma a sunat fix la 24h.
            // Deci e momentul să anunțăm resetarea.
            shouldNotify = true;
            notifTitle = "✅ " + task.title + " este disponibil!";
            notifBody = "Au trecut 24h. Poți realiza task-ul din nou.";

            // Opțional: Putem chiar reseta task-ul aici în DB, dar TaskHelper o va face oricum la deschidere.
        } else {
            // La task-uri normale, notificăm doar dacă NU sunt făcute
            if (!task.isCompleted) {
                shouldNotify = true;
                notifTitle = "🔔 Reminder: " + task.title;
                notifBody = "Nu uita să îți termini task-ul pe azi!";
            }
        }

        if (shouldNotify) {
            showNotification(context, task, notifTitle, notifBody);
        }
    }

    private void showNotification(Context context, Task task, String title, String body) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent appIntent = new Intent(context, MainActivity.class);
        // Când dăm click, deschidem aplicația
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, task.id + 1000, appIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(task.id, builder.build());
    }
}