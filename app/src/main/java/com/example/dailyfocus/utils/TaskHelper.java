package com.example.dailyfocus.utils;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.example.dailyfocus.R;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.widget.TaskWidgetProvider;
import java.util.Calendar;
import java.util.List;

public class TaskHelper {

    public static void checkAndResetTasks(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<Task> tasks = db.taskDao().getAllTasks();
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();

        for (Task task : tasks) {
            boolean needsUpdate = false;

            if (task.isDaily) {
                cal.setTimeInMillis(now);
                cal.set(Calendar.HOUR_OF_DAY, task.resetHour);
                cal.set(Calendar.MINUTE, task.resetMinute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                long lastResetTime = cal.getTimeInMillis();
                if (lastResetTime > now) {
                    lastResetTime -= (24 * 60 * 60 * 1000L);
                }

                if (task.lastCompletionTimestamp < lastResetTime) {
                    if (task.isCompleted) {
                        task.isCompleted = false;
                        long previousResetTime = lastResetTime - (24 * 60 * 60 * 1000L);
                        if (task.lastCompletionTimestamp < previousResetTime) {
                            task.currentStreak = 0;
                        }
                    } else {
                        task.currentStreak = 0;
                    }

                    if (task.subtasks != null) {
                        for (Subtask s : task.subtasks) {
                            s.isCompleted = false;
                        }
                    }

                    task.lastCompletionTimestamp = now;
                    needsUpdate = true;
                }

            } else if (task.isCooldown24h) {
                long unlockTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                if (task.isCompleted && now >= unlockTime) {
                    task.isCompleted = false;
                    if (task.subtasks != null) {
                        for (Subtask s : task.subtasks) {
                            s.isCompleted = false;
                        }
                    }
                    task.lastCompletionTimestamp = now;
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                db.taskDao().update(task);
            }
        }
    }

    // --- METODA MODIFICATĂ: REPARĂ CORECT SIZCRONIZAREA LISTEI DIN WIDGET ---
    public static void updateWidget(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, TaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            // LINIA CHEIE: Această comandă invalidează cache-ul listei și o obligă să reîncărce elementele din DB!
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView);

            // Trimitem și update-ul vizual clasic pe ecran pentru restul componentelor
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int calculateStreak(List<TaskHistory> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        return history.size();
    }
}