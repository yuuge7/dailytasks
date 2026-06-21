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

public class TaskDetailsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) { finish(); return; }

        AppDatabase db = AppDatabase.getInstance(this);
        Task task = db.taskDao().getTaskById(taskId);

        TextView title = findViewById(R.id.detailTitle);
        title.setText(task.title);

        RecyclerView recyclerView = findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        List<HistoryItem> historyList = new ArrayList<>();
        List<TaskHistory> dbHistory = db.taskDao().getHistoryForTask(taskId);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 7; i++) {
            long currentDayMillis = cal.getTimeInMillis();
            boolean isDone = false;
            for (TaskHistory h : dbHistory) {
                if (h.dateTimestamp == currentDayMillis) { isDone = true; break; }
            }
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM", Locale.getDefault());
            historyList.add(new HistoryItem(sdf.format(cal.getTime()), isDone));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        recyclerView.setAdapter(new HistoryAdapter(historyList));
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