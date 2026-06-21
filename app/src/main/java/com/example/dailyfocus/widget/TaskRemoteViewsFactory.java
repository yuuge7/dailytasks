package com.example.dailyfocus.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import com.example.dailyfocus.R;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private List<Task> tasks = new ArrayList<>();

    public TaskRemoteViewsFactory(Context context) { this.context = context; }

    @Override
    public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        // Reîncărcăm lista de task-uri din baza de date
        tasks = AppDatabase.getInstance(context).taskDao().getAllTasks();
    }

    @Override
    public void onDestroy() {}

    @Override
    public int getCount() { return tasks.size(); }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= tasks.size() || tasks == null) return null;

        Task task = tasks.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item);

        // 1. Setăm Titlul
        views.setTextViewText(R.id.widgetItemTitle, task.title);

        // 2. Setăm Subtitlul dinamic (Timp + Subtask-uri + Streak)
        StringBuilder details = new StringBuilder();

        if (task.isCooldown24h) {
            if (task.isCompleted) {
                // Calculăm cât timp mai este până la resetare pe baza cooldownHours setat de utilizator
                long now = System.currentTimeMillis();
                long resetTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                long diff = resetTime - now;

                if (diff > 0) {
                    long hours = diff / (60 * 60 * 1000);
                    long minutes = (diff / (60 * 1000)) % 60;
                    details.append(String.format(Locale.getDefault(), "Reset în: %dh %02dm", hours, minutes));
                } else {
                    details.append("Se resetează curând...");
                }
            } else {
                details.append("Disponibil (Cooldown ").append(task.cooldownHours).append("h)");
            }
        } else if (task.isDaily) {
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d", task.resetHour, task.resetMinute);
            details.append("Zilnic (Reset la ").append(timeStr).append(")");
        } else {
            details.append("O singură dată");
        }

        // --- Adăugăm Subtask-urile (dacă există) ---
        if (task.subtasks != null && !task.subtasks.isEmpty()) {
            int doneCount = 0;
            for (Subtask s : task.subtasks) {
                if (s.isCompleted) doneCount++;
            }
            details.append(" • ").append(doneCount).append("/").append(task.subtasks.size()).append(" Subtask-uri");
        }

        // --- Adăugăm Seria / Streak-ul ---
        if (task.currentStreak > 0 && (task.isDaily || task.isCooldown24h)) {
            details.append(" • 🔥 ").append(task.currentStreak);
        }

        views.setTextViewText(R.id.widgetItemInfo, details.toString());

        // 3. Logica Vizuală (Design Modern - Checkbox și Opacitate)
        if (task.isCompleted) {
            views.setImageViewResource(R.id.widgetItemCheck, android.R.drawable.checkbox_on_background);
            views.setTextColor(R.id.widgetItemTitle, 0xFF808080); // Gri
            views.setInt(R.id.widgetItemContainer, "setBackgroundResource", R.drawable.widget_item_bg);
        } else {
            views.setImageViewResource(R.id.widgetItemCheck, android.R.drawable.checkbox_off_background);
            views.setTextColor(R.id.widgetItemTitle, 0xFFFFFFFF); // Alb
        }

        // 4. Intent pentru click pe întreg rândul
        Intent fillInIntent = new Intent();
        fillInIntent.putExtra(TaskWidgetProvider.EXTRA_TASK_ID, task.id);
        views.setOnClickFillInIntent(R.id.widgetItemContainer, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() { return null; }
    @Override
    public int getViewTypeCount() { return 1; }
    @Override
    public long getItemId(int position) { return position; }
    @Override
    public boolean hasStableIds() { return true; }
}