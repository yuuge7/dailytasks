package com.example.dailyfocus;

import android.os.Bundle;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.views.HeatmapView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsActivity extends AppCompatActivity {

    private static class StatsData {
        List<Task> tasks;
        List<TaskHistory> history;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        AppDatabase db = AppDatabase.getInstance(this);
        TaskRepository.query(() -> {
            StatsData data = new StatsData();
            data.tasks = db.taskDao().getAllTasks();
            data.history = db.taskDao().getAllHistory();
            return data;
        }, this::bind);
    }

    private void bind(StatsData data) {
        List<Task> allTasks = data.tasks;
        List<TaskHistory> allHistory = data.history;

        TextView txtTotalCompletions = findViewById(R.id.txtTotalCompletions);
        TextView txtActiveTasks = findViewById(R.id.txtActiveTasks);
        LinearLayout layoutStreaks = findViewById(R.id.layoutStreaks);
        TextView txtToday = findViewById(R.id.txtToday);
        TextView txtYesterday = findViewById(R.id.txtYesterday);
        TextView txtThisMonth = findViewById(R.id.txtThisMonth);

        // 1. Imagine de ansamblu
        txtTotalCompletions.setText(getResources().getQuantityString(
                R.plurals.stats_total_completions, allHistory.size(), allHistory.size()));
        txtActiveTasks.setText(getResources().getQuantityString(
                R.plurals.stats_active_tasks, allTasks.size(), allTasks.size()));

        // 2. Heatmap de activitate
        Map<Long, Integer> dayCounts = new HashMap<>();
        for (TaskHistory h : allHistory) {
            Integer prev = dayCounts.get(h.dateTimestamp);
            dayCounts.put(h.dateTimestamp, prev == null ? 1 : prev + 1);
        }
        HeatmapView heatmap = findViewById(R.id.heatmapView);
        heatmap.setData(dayCounts);
        HorizontalScrollView heatmapScroll = findViewById(R.id.heatmapScroll);
        heatmapScroll.post(() -> heatmapScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT));

        // 3. Top serii (curente + record)
        List<Task> streakTasks = new ArrayList<>();
        for (Task t : allTasks) {
            if ((t.isDaily || t.isCooldown24h) && (t.currentStreak > 0 || t.bestStreak > 0)) {
                streakTasks.add(t);
            }
        }
        Collections.sort(streakTasks, (t1, t2) -> Integer.compare(t2.currentStreak, t1.currentStreak));

        layoutStreaks.removeAllViews();
        if (streakTasks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_no_streaks);
            empty.setTextSize(14);
            empty.setTextColor(0xFF757575);
            layoutStreaks.addView(empty);
        } else {
            int limit = Math.min(streakTasks.size(), 5);
            for (int i = 0; i < limit; i++) {
                Task t = streakTasks.get(i);
                TextView tv = new TextView(this);
                tv.setText(getResources().getQuantityString(R.plurals.stats_streak_row,
                        t.currentStreak, t.currentStreak, t.title, t.bestStreak));
                tv.setTextSize(16);
                tv.setPadding(0, 8, 0, 8);
                layoutStreaks.addView(tv);
            }
        }

        // 4. Activitate recentă
        Calendar cal = Calendar.getInstance();
        int currentMonth = cal.get(Calendar.MONTH);
        int currentYear = cal.get(Calendar.YEAR);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterdayMidnight = cal.getTimeInMillis();

        int todayCount = 0;
        int yesterdayCount = 0;
        int thisMonthCount = 0;

        Calendar historyCal = Calendar.getInstance();
        for (TaskHistory h : allHistory) {
            if (h.dateTimestamp == todayMidnight) {
                todayCount++;
            } else if (h.dateTimestamp == yesterdayMidnight) {
                yesterdayCount++;
            }
            historyCal.setTimeInMillis(h.dateTimestamp);
            if (historyCal.get(Calendar.MONTH) == currentMonth && historyCal.get(Calendar.YEAR) == currentYear) {
                thisMonthCount++;
            }
        }

        txtToday.setText(getResources().getQuantityString(R.plurals.stats_today, todayCount, todayCount));
        txtYesterday.setText(getResources().getQuantityString(
                R.plurals.stats_yesterday, yesterdayCount, yesterdayCount));
        txtThisMonth.setText(getResources().getQuantityString(
                R.plurals.stats_this_month, thisMonthCount, thisMonthCount));

        // 5. Statistici totale pe task (all-time)
        LinearLayout layoutAllTimeStats = findViewById(R.id.layoutAllTimeStats);
        layoutAllTimeStats.removeAllViews();

        Map<String, Integer> taskCounts = new HashMap<>();
        for (TaskHistory h : allHistory) {
            String name = h.taskName != null ? h.taskName : getString(R.string.stats_unknown_task);
            Integer prev = taskCounts.get(name);
            taskCounts.put(name, prev == null ? 1 : prev + 1);
        }

        List<Map.Entry<String, Integer>> sortedTaskCounts = new ArrayList<>(taskCounts.entrySet());
        Collections.sort(sortedTaskCounts, (e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

        if (sortedTaskCounts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_no_history);
            empty.setTextSize(14);
            empty.setTextColor(0xFF757575);
            layoutAllTimeStats.addView(empty);
        } else {
            for (Map.Entry<String, Integer> entry : sortedTaskCounts) {
                TextView tv = new TextView(this);
                tv.setText(getResources().getQuantityString(R.plurals.stats_alltime_row,
                        entry.getValue(), entry.getKey(), entry.getValue()));
                tv.setTextSize(16);
                tv.setPadding(0, 8, 0, 8);
                layoutAllTimeStats.addView(tv);
            }
        }
    }
}
