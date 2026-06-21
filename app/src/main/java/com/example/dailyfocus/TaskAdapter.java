package com.example.dailyfocus;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import java.util.Calendar;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onCheckClick(Task task);
        void onDeleteClick(Task task);
        void onInfoClick(Task task);
        void onTaskClick(Task task); // NOU: Pentru a deschide lista de subtask-uri
    }

    public TaskAdapter(List<Task> tasks, OnItemClickListener listener) {
        this.tasks = tasks;
        this.listener = listener;
    }

    public void updateList(List<Task> newTasks) {
        this.tasks = newTasks;
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        Task item = tasks.remove(fromPosition);
        tasks.add(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<Task> getTasks() {
        return tasks;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);

        holder.title.setText(task.title);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.isCompleted);

        if (task.isCompleted) {
            holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(0.6f);
        } else {
            holder.title.setPaintFlags(holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.title.setAlpha(1.0f);
        }

        StringBuilder details = new StringBuilder();

        if (task.isDaily) {
            details.append("Zilnic (Reset ").append(String.format("%02d:%02d", task.resetHour, task.resetMinute)).append(")");
        } else if (task.isCooldown24h) {
            if (task.isCompleted) {
                long resetTimeMillis = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(resetTimeMillis);
                details.append("Revine la ").append(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
            } else {
                details.append("Disponibil (Cooldown ").append(task.cooldownHours).append("h)");
            }
        } else {
            details.append("O singură dată");
        }

        // --- NOU: Afișăm statusul Subtask-urilor ---
        if (task.subtasks != null && !task.subtasks.isEmpty()) {
            int doneCount = 0;
            for (Subtask s : task.subtasks) {
                if (s.isCompleted) doneCount++;
            }
            details.append(" • ").append(doneCount).append("/").append(task.subtasks.size()).append(" Subtask-uri");
        }

        if (task.currentStreak > 0 && (task.isDaily || task.isCooldown24h)) {
            details.append(" • 🔥 ").append(task.currentStreak);
        }

        holder.type.setText(details.toString());

        if (task.isDaily || task.isCooldown24h) {
            holder.btnInfo.setVisibility(View.VISIBLE);
        } else {
            holder.btnInfo.setVisibility(View.GONE);
        }

        holder.checkBox.setOnClickListener(v -> {
            if (listener != null) listener.onCheckClick(task);
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, type;
        CheckBox checkBox;
        ImageView btnDelete, btnInfo;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txtTitle);
            type = itemView.findViewById(R.id.txtDetails);
            checkBox = itemView.findViewById(R.id.chkTask);
            btnInfo = itemView.findViewById(R.id.imgInfo);
            btnDelete = itemView.findViewById(R.id.imgDelete);

            // NOU: Click pe întregul rând deschide meniul de Subtask-uri
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onTaskClick(tasks.get(position));
                }
            });

            btnDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onDeleteClick(tasks.get(position));
                }
            });

            btnInfo.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onInfoClick(tasks.get(position));
                }
            });
        }
    }
}