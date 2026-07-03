package com.example.dailyfocus;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.widget.Toast;
import com.example.dailyfocus.utils.TaskHelper;

public class TaskDetailsActivity extends AppCompatActivity {
    private Task task;
    private AppDatabase db;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) { finish(); return; }

        db = AppDatabase.getInstance(this);
        task = db.taskDao().getTaskById(taskId);

        TextView title = findViewById(R.id.detailTitle);
        title.setText(task.title);

        RecyclerView recyclerView = findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        Button btnRestore = findViewById(R.id.btnRestore);
        btnRestore.setOnClickListener(v -> restoreStreak());

        loadHistory();
    }

    private void loadHistory() {
        historyList.clear();
        List<TaskHistory> dbHistory = db.taskDao().getHistoryForTask(task.id);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

        long todayMidnight = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterdayMidnight = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 1); // Reset to today for the loop

        boolean yesterdayCompleted = false;

        for (int i = 0; i < 7; i++) {
            long currentDayMillis = cal.getTimeInMillis();
            boolean isDone = false;
            for (TaskHistory h : dbHistory) {
                if (h.dateTimestamp == currentDayMillis) { isDone = true; break; }
            }
            if (currentDayMillis == yesterdayMidnight && isDone) yesterdayCompleted = true;

            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM", Locale.getDefault());
            historyList.add(new HistoryItem(sdf.format(cal.getTime()), isDone));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        adapter.notifyDataSetChanged();

        // Arătăm butonul de restore doar dacă ieri nu a fost completat ȘI este un task zilnic
        boolean isStreakTask = task.isDaily || task.isCooldown24h;
        findViewById(R.id.btnRestore).setVisibility((isStreakTask && !yesterdayCompleted) ? View.VISIBLE : View.GONE);
    }

    private void restoreStreak() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long yesterdayMidnight = cal.getTimeInMillis() - (24 * 60 * 60 * 1000L);

        db.taskDao().insertHistory(new TaskHistory(task.id, task.title, yesterdayMidnight));
        task.currentStreak++;
        db.taskDao().update(task);

        Toast.makeText(this, "Streak restored for yesterday!", Toast.LENGTH_SHORT).show();
        loadHistory();
        TaskHelper.updateWidget(this);
    }

    static class HistoryItem {
        String dateStr; boolean isCompleted;
        HistoryItem(String d, boolean c) { dateStr = d; isCompleted = c; }
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        List<HistoryItem> list;
        HistoryAdapter(List<HistoryItem> list) { this.list = list; }
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = list.get(position);
            holder.date.setText(item.dateStr);
            if (item.isCompleted) {
                holder.status.setText("COMPLETAT"); holder.status.setTextColor(0xFF4CAF50);
            } else {
                holder.status.setText("NEFĂCUT"); holder.status.setTextColor(0xFFF44336);
            }
        }
        @Override
        public int getItemCount() { return list.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView date, status;
            ViewHolder(View v) { super(v); date = v.findViewById(R.id.textDate); status = v.findViewById(R.id.textStatus); }
        }
    }
}