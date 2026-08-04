package com.example.dailyfocus.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import androidx.core.content.ContextCompat;
import com.example.dailyfocus.R;
import com.example.dailyfocus.TaskAdapter;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private List<Task> tasks = new ArrayList<>();

    public TaskRemoteViewsFactory(Context context) { this.context = context; }

    @Override
    public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        // Rulează pe thread de background (binder) — sigur pentru Room
        tasks = AppDatabase.getInstance(context).taskDao().getAllTasks();
    }

    @Override
    public void onDestroy() {}

    @Override
    public int getCount() { return tasks.size(); }

    @Override
    public RemoteViews getViewAt(int position) {
        if (tasks == null || position >= tasks.size()) return null;

        Task task = tasks.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item);

        views.setTextViewText(R.id.widgetItemTitle, task.title);

        StringBuilder details = new StringBuilder();

        if (task.isCooldown24h) {
            if (task.isCompleted) {
                long now = System.currentTimeMillis();
                long resetTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                long diff = resetTime - now;

                if (diff > 0) {
                    long hours = diff / (60 * 60 * 1000);
                    long minutes = (diff / (60 * 1000)) % 60;
                    details.append(context.getString(R.string.widget_reset_in, hours, minutes));
                } else {
                    details.append(context.getString(R.string.widget_reset_soon));
                }
            } else {
                details.append(context.getString(R.string.detail_cooldown_available, task.cooldownHours));
            }
        } else if (task.isDaily) {
            String timeStr = String.format(Locale.US, "%02d:%02d", task.resetHour, task.resetMinute);
            if (task.daysOfWeekMask != 0) {
                details.append(context.getString(R.string.detail_weekdays,
                        TaskAdapter.weekdayLabel(context, task.daysOfWeekMask), timeStr));
            } else if (task.repeatDays > 1) {
                details.append(context.getString(R.string.detail_every_n_days, task.repeatDays, timeStr));
            } else {
                details.append(context.getString(R.string.detail_daily, timeStr));
            }
        } else {
            details.append(context.getString(R.string.detail_once));
        }

        // Progresul subtask-urilor (ex: 2/5)
        if (task.subtasks != null && !task.subtasks.isEmpty()) {
            int doneCount = 0;
            for (Subtask s : task.subtasks) {
                if (s.isCompleted) doneCount++;
            }
            details.append(context.getString(R.string.detail_subtasks, doneCount, task.subtasks.size()));
        }

        if (task.currentStreak > 0 && (task.isDaily || task.isCooldown24h)) {
            details.append(context.getString(R.string.detail_streak, task.currentStreak));
        }

        if (task.isFrozen) {
            details.append(context.getString(R.string.detail_frozen));
        }

        views.setTextViewText(R.id.widgetItemInfo, details.toString());

        // Culori din resurse — urmează automat tema light/dark a sistemului
        int primary = ContextCompat.getColor(context, R.color.widget_text_primary);
        int secondary = ContextCompat.getColor(context, R.color.widget_text_secondary);

        if (task.isCompleted) {
            views.setImageViewResource(R.id.widgetItemCheck, android.R.drawable.checkbox_on_background);
            views.setTextColor(R.id.widgetItemTitle, secondary);
        } else {
            views.setImageViewResource(R.id.widgetItemCheck, android.R.drawable.checkbox_off_background);
            views.setTextColor(R.id.widgetItemTitle, primary);
        }
        views.setTextColor(R.id.widgetItemInfo, secondary);

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
