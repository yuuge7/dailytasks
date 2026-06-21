package com.example.dailyfocus.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_history",
        indices = {@Index("taskId")})
public class TaskHistory {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int taskId;
    public String taskName; // Numele task-ului salvat pentru a rămâne în statistici după ștergere
    public long dateTimestamp;

    public TaskHistory(int taskId, String taskName, long dateTimestamp) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.dateTimestamp = dateTimestamp;
    }
}