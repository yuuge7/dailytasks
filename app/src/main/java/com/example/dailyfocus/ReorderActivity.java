package com.example.dailyfocus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.utils.TaskHelper;
import java.util.List;

public class ReorderActivity extends AppCompatActivity {

    private AppDatabase db;
    private List<Task> tasks;
    private ReorderAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reorder);
        setTitle("Schimbă Ordinea");

        db = AppDatabase.getInstance(this);
        tasks = db.taskDao().getAllTasksForReordering(); // Aducem lista STRICT după orderIndex

        RecyclerView recyclerView = findViewById(R.id.recyclerViewReorder);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ReorderAdapter();
        recyclerView.setAdapter(adapter);

        // Aici am mutat logica de Drag & Drop
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                Task item = tasks.remove(fromPosition);
                tasks.add(toPosition, item);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Când lăsăm task-ul, salvăm imediat în baza de date
                for (int i = 0; i < tasks.size(); i++) {
                    Task task = tasks.get(i);
                    task.orderIndex = i;
                    db.taskDao().update(task);
                }
            }
        };

        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView);

        findViewById(R.id.btnSaveOrder).setOnClickListener(v -> {
            TaskHelper.updateWidget(this);
            finish(); // Ne întoarcem pe ecranul principal
        });
    }

    // Mini-adaptor vizual pentru ecranul de reordonare (arată doar numele)
    private class ReorderAdapter extends RecyclerView.Adapter<ReorderAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setPadding(32, 32, 32, 32);
            textView.setTextSize(18);
            textView.setBackgroundResource(android.R.drawable.list_selector_background);
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText("☰   " + tasks.get(position).title);
        }

        @Override
        public int getItemCount() {
            return tasks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }
}