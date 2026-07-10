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
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.TaskHelper;

public class NotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "daily_focus_reminders";
    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String ACTION_COMPLETE_TASK = "com.example.dailyfocus.ACTION_COMPLETE_FROM_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
        if (taskId == -1) return;

        final PendingResult pendingResult = goAsync();
        final boolean isCompleteAction = ACTION_COMPLETE_TASK.equals(intent.getAction());

        TaskRepository.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                Task task = db.taskDao().getTaskById(taskId);
                if (task == null) return;

                if (isCompleteAction) {
                    // Butonul "Bifează" din notificare
                    if (!task.isCompleted) {
                        TaskHelper.completeTask(context, task);
                        TaskHelper.updateWidget(context);
                    }
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancel(task.id);
                    return;
                }

                // Alarma de reminder
                if (!task.hasReminder) return;

                if (task.isCooldown24h) {
                    // La cooldown notificăm când task-ul redevine disponibil
                    TaskHelper.checkAndResetTasks(context);
                    Task fresh = db.taskDao().getTaskById(taskId);
                    if (fresh != null) task = fresh;
                    showNotification(context, task,
                            context.getString(R.string.notif_cooldown_title, task.title),
                            context.getString(R.string.notif_cooldown_body, task.cooldownHours),
                            !task.isCompleted);
                    TaskHelper.updateWidget(context);
                } else if (!task.isCompleted) {
                    showNotification(context, task,
                            context.getString(R.string.notif_reminder_title, task.title),
                            context.getString(R.string.notif_reminder_body),
                            true);
                    // Reprogramăm reminderul pentru următoarea zi programată
                    TaskHelper.scheduleTaskNotification(context, task);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, Task task, String title, String body, boolean withCompleteAction) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent appIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, task.id + 1000, appIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (withCompleteAction) {
            // Buton "Bifează" direct din notificare, fără a deschide aplicația
            Intent completeIntent = new Intent(context, NotificationReceiver.class);
            completeIntent.setAction(ACTION_COMPLETE_TASK);
            completeIntent.putExtra(EXTRA_TASK_ID, task.id);
            PendingIntent completePending = PendingIntent.getBroadcast(
                    context, task.id + 2000, completeIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.checkbox_on_background,
                    context.getString(R.string.notif_action_complete), completePending);
        }

        notificationManager.notify(task.id, builder.build());
    }
}
