package com.example.dailyfocus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.StreakFreeze;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.data.TaskRepository;
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
        findViewById(R.id.btnFreeze).setOnClickListener(v -> toggleFreeze());
        findViewById(R.id.btnResetStreak).setOnClickListener(v -> confirmResetStreak());

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
        streaks.setText(getString(task.isFrozen ? R.string.detail_streaks_frozen : R.string.detail_streaks,
                task.currentStreak, task.bestStreak));

        Button btnFreeze = findViewById(R.id.btnFreeze);
        btnFreeze.setText(task.isFrozen ? R.string.unfreeze_streak : R.string.freeze_streak);
        // Îngheţul are sens doar pentru serii care se pot rupe (task-uri zilnice)
        btnFreeze.setVisibility(task.isDaily ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnResetStreak).setVisibility(
                (task.isDaily || task.isCooldown24h) ? View.VISIBLE : View.GONE);
    }

    private void loadHistory() {
        TaskRepository.query(() -> {
            List<TaskHistory> history = db.taskDao().getHistoryForTask(task.id);
            List<StreakFreeze> freezes = db.taskDao().getFreezesForTask(task.id);
            // Ziua restaurabilă se calculează aici, pe thread-ul de background, ca să
            // avem deja răspunsul când decidem dacă butonul are ce să repare.
            long restorable = TaskHelper.restorableDayKey(
                    task, history, freezes, System.currentTimeMillis());
            return new HistorySnapshot(history, freezes, restorable);
        }, snapshot -> {
            historyList.clear();

            Set<Long> doneDays = new HashSet<>();
            for (TaskHistory h : snapshot.history) doneDays.add(h.dateTimestamp);

            Set<Long> frozenDays = new HashSet<>();
            for (StreakFreeze f : snapshot.freezes) frozenDays.add(f.dayKey);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM", Locale.getDefault());
            for (int i = 0; i < 7; i++) {
                long currentDayMillis = cal.getTimeInMillis();
                historyList.add(new HistoryItem(sdf.format(cal.getTime()),
                        doneDays.contains(currentDayMillis),
                        frozenDays.contains(currentDayMillis)));
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
            adapter.notifyDataSetChanged();

            // Butonul apare doar când chiar există o zi de reparat: o zi îngheţată nu
            // a rupt nimic, deci nu mai oferim o restaurare care s-ar irosi pe ea.
            boolean isStreakTask = task.isDaily || task.isCooldown24h;
            findViewById(R.id.btnRestore).setVisibility(
                    (isStreakTask && snapshot.restorableDay != 0) ? View.VISIBLE : View.GONE);
        });
    }

    /** Ce are nevoie ecranul de istoric, citit dintr-o singură trecere prin DB. */
    private static class HistorySnapshot {
        final List<TaskHistory> history;
        final List<StreakFreeze> freezes;
        final long restorableDay;

        HistorySnapshot(List<TaskHistory> history, List<StreakFreeze> freezes, long restorableDay) {
            this.history = history;
            this.freezes = freezes;
            this.restorableDay = restorableDay;
        }
    }

    /**
     * Restaurează streak-ul: marchează ziua care a rupt lanțul ca fiind completată,
     * apoi RECALCULEAZĂ streak-ul din istoric. Ziua lipsă reconectează lanțul, deci
     * revine seria întreagă (nu doar +1, cum era înainte).
     *
     * Ziua ținta sare peste zilele îngheţate: ele leagă deja lanțul, așa că nu ele
     * l-au rupt (vezi {@link TaskHelper#restorableDayKey}).
     */
    private void restoreStreak() {
        if (task == null) return;
        TaskRepository.executeThen(() -> {
            long now = System.currentTimeMillis();

            List<TaskHistory> history = db.taskDao().getHistoryForTask(task.id);
            List<StreakFreeze> freezes = db.taskDao().getFreezesForTask(task.id);

            long missingDay = TaskHelper.restorableDayKey(task, history, freezes, now);
            if (missingDay == 0) return; // lanțul e deja întreg
            db.taskDao().insertHistory(new TaskHistory(task.id, task.title, missingDay));

            history = db.taskDao().getHistoryForTask(task.id);
            int[] streaks = TaskHelper.recomputeStreaks(task, history, freezes, now);
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

    /** Îngheață seria (sau o dezgheață dacă era deja înghețată). */
    private void toggleFreeze() {
        if (task == null) return;
        if (task.isFrozen) {
            applyFreeze(false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.freeze_title)
                .setMessage(getString(R.string.freeze_message, task.title))
                .setPositiveButton(R.string.freeze_confirm, (d, w) -> applyFreeze(true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyFreeze(boolean frozen) {
        TaskRepository.executeThen(() -> TaskHelper.setFrozen(this, task, frozen), () -> {
            Toast.makeText(this, frozen ? R.string.freeze_success : R.string.unfreeze_success,
                    Toast.LENGTH_SHORT).show();
            updateStreakLabel();
            loadHistory();
            TaskHelper.updateWidget(this);
        });
    }

    /** Resetare serie: neutru = resetează și recordul all-time. */
    private void confirmResetStreak() {
        if (task == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(getString(R.string.reset_message, task.title))
                .setPositiveButton(R.string.reset_confirm, (d, w) -> applyResetStreak(false))
                .setNeutralButton(R.string.reset_with_best, (d, w) -> applyResetStreak(true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyResetStreak(boolean alsoResetBest) {
        TaskRepository.executeThen(() -> TaskHelper.resetStreak(this, task, alsoResetBest), () -> {
            Toast.makeText(this, getString(R.string.reset_success, task.currentStreak),
                    Toast.LENGTH_SHORT).show();
            updateStreakLabel();
            loadHistory();
            TaskHelper.updateWidget(this);
        });
    }

    static class HistoryItem {
        String dateStr;
        boolean isCompleted;
        // Zi sărită cu seria înghețată: nu e o completare, dar nici o zi pierdută.
        boolean isFrozen;

        HistoryItem(String d, boolean c, boolean f) {
            dateStr = d;
            isCompleted = c;
            isFrozen = f;
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
            } else if (item.isFrozen) {
                // Ziua a fost acoperită de îngheţ — lanțul a trecut peste ea intact
                holder.status.setText(R.string.history_frozen);
                holder.status.setTextColor(0xFF29B6F6);
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
