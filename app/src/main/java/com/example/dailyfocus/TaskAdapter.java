package com.example.dailyfocus;

import android.content.Context;
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
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onCheckClick(Task task);
        void onDeleteClick(Task task);
        void onInfoClick(Task task);
        void onTaskClick(Task task);
    }

    public TaskAdapter(List<Task> tasks, OnItemClickListener listener) {
        this.tasks = tasks;
        this.listener = listener;
    }

    public void updateList(List<Task> newTasks) {
        this.tasks = newTasks;
        notifyDataSetChanged();
    }

    public List<Task> getTasks() {
        return tasks;
    }

    /** Etichetă scurtă pentru zilele active, ex: "Lu Mi Vi". */
    public static String weekdayLabel(Context context, int mask) {
        String[] abbrev = context.getResources().getStringArray(R.array.day_abbreviations);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(abbrev[i]);
            }
        }
        return sb.toString();
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
        Context context = holder.itemView.getContext();

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
        String resetTime = String.format(Locale.US, "%02d:%02d", task.resetHour, task.resetMinute);

        if (task.isDaily) {
            if (task.daysOfWeekMask != 0) {
                details.append(context.getString(R.string.detail_weekdays,
                        weekdayLabel(context, task.daysOfWeekMask), resetTime));
            } else if (task.repeatDays > 1) {
                details.append(context.getString(R.string.detail_every_n_days, task.repeatDays, resetTime));
            } else {
                details.append(context.getString(R.string.detail_daily, resetTime));
            }
        } else if (task.isCooldown24h) {
            if (task.isCompleted) {
                long resetTimeMillis = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(resetTimeMillis);
                details.append(context.getString(R.string.detail_cooldown_back,
                        String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))));
            } else {
                details.append(context.getString(R.string.detail_cooldown_available, task.cooldownHours));
            }
        } else {
            details.append(context.getString(R.string.detail_once));
        }

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
