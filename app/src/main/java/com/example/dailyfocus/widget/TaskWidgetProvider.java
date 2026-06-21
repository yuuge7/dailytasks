package com.example.dailyfocus.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.example.dailyfocus.MainActivity;
import com.example.dailyfocus.R;
import com.example.dailyfocus.WidgetConfirmActivity;
import com.example.dailyfocus.WidgetSubtaskActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.utils.TaskHelper;
import java.util.Calendar;

public class TaskWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_TASK = "com.example.dailyfocus.ACTION_TOGGLE_TASK";
    public static final String EXTRA_TASK_ID = "com.example.dailyfocus.EXTRA_TASK_ID";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault());
            String dateStr = sdf.format(new java.util.Date());
            dateStr = dateStr.substring(0, 1).toUpperCase() + dateStr.substring(1);
            views.setTextViewText(R.id.widgetDate, dateStr);

            Intent intent = new Intent(context, TaskWidgetService.class);
            views.setRemoteAdapter(R.id.widgetListView, intent);
            views.setEmptyView(R.id.widgetListView, R.id.empty_view);

            Intent appIntent = new Intent(context, MainActivity.class);
            PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetTitle, appPendingIntent);
            views.setOnClickPendingIntent(R.id.widgetBtnAdd, appPendingIntent);

            Intent toggleIntent = new Intent(context, TaskWidgetProvider.class);
            toggleIntent.setAction(ACTION_TOGGLE_TASK);
            PendingIntent togglePendingIntent = PendingIntent.getBroadcast(context, 1, toggleIntent, PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.widgetListView, togglePendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_TASK.equals(intent.getAction())) {
            int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
            if (taskId != -1) {
                new Thread(() -> {
                    AppDatabase db = AppDatabase.getInstance(context);
                    Task task = db.taskDao().getTaskById(taskId);

                    if (task != null) {
                        if (task.isCompleted) {
                            // CAZUL 1: Task completat -> deschide popup confirmare debifare
                            Intent confirmIntent = new Intent(context, WidgetConfirmActivity.class);
                            confirmIntent.putExtra("TASK_ID", task.id);
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            context.startActivity(confirmIntent);

                        } else if (task.subtasks != null && !task.subtasks.isEmpty()) {
                            // CAZUL 2: Task necompletat dar ARE subtask-uri -> deschide popup-ul de subtask-uri
                            Intent subtaskIntent = new Intent(context, WidgetSubtaskActivity.class);
                            subtaskIntent.putExtra("TASK_ID", task.id);
                            subtaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            context.startActivity(subtaskIntent);

                        } else {
                            // CAZUL 3: Task simplu, fără subtask-uri -> bifează instantaneu
                            task.isCompleted = true;
                            task.lastCompletionTimestamp = System.currentTimeMillis();

                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                            long todayMidnight = cal.getTimeInMillis();

                            if (task.isDaily || task.isCooldown24h) {
                                task.currentStreak++;
                                db.taskDao().insertHistory(new TaskHistory(task.id, task.title, todayMidnight));
                            }

                            db.taskDao().update(task);
                            TaskHelper.updateWidget(context);
                        }
                    }
                }).start();
            }
        }
    }
}