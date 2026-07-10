package com.example.dailyfocus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.Periods;
import com.example.dailyfocus.utils.TaskHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TaskDetailsActivity extends AppCompatActivity {
    private Task task;
    private AppDatabase db;
    private HistoryAdapter adapter;
    private final List<HistoryItem> historyList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);

        RecyclerView recyclerView = findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnRestore).setOnClickListener(v -> restoreStreak());

        TaskRepository.query(() -> db.taskDao().getTaskById(taskId), loaded -> {
            if (loaded == null) {
                finish();
                return;
            }
            task = loaded;
            TextView title = findViewById(R.id.detailTitle);
            title.setText(task.title);
            updateStreakLabel();
            loadHistory();
        });
    }

    private void updateStreakLabel() {
        TextView streaks = findViewById(R.id.detailStreaks);
        streaks.setText(getString(R.string.detail_streaks, task.currentStreak, task.bestStreak));
    }

    private void loadHistory() {
        TaskRepository.query(() -> db.taskDao().getHistoryForTask(task.id), dbHistory -> {
            historyList.clear();

            Set<Long> doneDays = new HashSet<>();
            for (TaskHistory h : dbHistory) doneDays.add(h.dateTimestamp);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long yesterdayMidnight = cal.getTimeInMillis() - Periods.DAY_MS;
            boolean yesterdayCompleted = false;

            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM", Locale.getDefault());
            for (int i = 0; i < 7; i++) {
                long currentDayMillis = cal.getTimeInMillis();
                boolean isDone = doneDays.contains(currentDayMillis);
                if (currentDayMillis == yesterdayMidnight && isDone) yesterdayCompleted = true;
                historyList.add(new HistoryItem(sdf.format(cal.getTime()), isDone));
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
            adapter.notifyDataSetChanged();

            boolean isStreakTask = task.isDaily || task.isCooldown24h;
            findViewById(R.id.btnRestore).setVisibility(
                    (isStreakTask && !yesterdayCompleted) ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * Restaurează streak-ul: marchează ziua programată anterioară ca fiind completată,
     * apoi RECALCULEAZĂ streak-ul din istoric. Ziua lipsă reconectează lanțul, deci
     * revine seria întreagă (nu doar +1, cum era înainte).
     */
    private void restoreStreak() {
        if (task == null) return;
        TaskRepository.executeThen(() -> {
            long now = System.currentTimeMillis();
            Periods.ensurePeriodStart(task, now);

            long missingDay;
            if (task.isDaily) {
                missingDay = Periods.previousScheduledDayKey(task, Periods.dayKey(task.periodStart));
            } else {
                missingDay = Periods.dayKey(now) - Periods.DAY_MS;
            }
            db.taskDao().insertHistory(new TaskHistory(task.id, task.title, missingDay));

            List<TaskHistory> history = db.taskDao().getHistoryForTask(task.id);
            int[] streaks = TaskHelper.recomputeStreaks(task, history, now);
            task.currentStreak = streaks[0];
            task.bestStreak = Math.max(task.bestStreak, streaks[1]);
            db.taskDao().update(task);
        }, () -> {
            Toast.makeText(this,
                    getString(R.string.restore_success, task.currentStreak), Toast.LENGTH_SHORT).show();
            updateStreakLabel();
            loadHistory();
            TaskHelper.updateWidget(this);
        });
    }

    static class HistoryItem {
        String dateStr;
        boolean isCompleted;

        HistoryItem(String d, boolean c) {
            dateStr = d;
            isCompleted = c;
        }
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        List<HistoryItem> list;

        HistoryAdapter(List<HistoryItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = list.get(position);
            holder.date.setText(item.dateStr);
            if (item.isCompleted) {
                holder.status.setText(R.string.history_done);
                holder.status.setTextColor(0xFF4CAF50);
            } else {
                holder.status.setText(R.string.history_missed);
                holder.status.setTextColor(0xFFF44336);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView date, status;

            ViewHolder(View v) {
                super(v);
                date = v.findViewById(R.id.textDate);
                status = v.findViewById(R.id.textStatus);
            }
        }
    }
}
