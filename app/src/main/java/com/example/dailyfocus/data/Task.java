package com.example.dailyfocus.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public boolean isCompleted;
    public boolean isDaily;
    public int resetHour;
    public int resetMinute;
    public boolean isCooldown24h;
    public long lastCompletionTimestamp;
    public int currentStreak;
    public boolean hasReminder;
    public int reminderHour;
    public int reminderMinute;

    @ColumnInfo(defaultValue = "24")
    public int cooldownHours = 24;

    // Câmp pentru reordonare (Drag & Drop)
    @ColumnInfo(defaultValue = "0")
    public int orderIndex = 0;

    // --- CÂMP NOU PENTRU SUBTASK-URI ---
    @ColumnInfo(defaultValue = "[]")
    public List<Subtask> subtasks;

    public Task(String title, boolean isDaily, int resetHour, int resetMinute, boolean isCooldown24h) {
        this.title = title;
        this.isDaily = isDaily;
        this.resetHour = resetHour;
        this.resetMinute = resetMinute;
        this.isCooldown24h = isCooldown24h;
        this.isCompleted = false;
        this.lastCompletionTimestamp = 0;
        this.currentStreak = 0;
        this.hasReminder = false;
        this.reminderHour = 20;
        this.reminderMinute = 0;
        this.cooldownHours = 24;
        this.orderIndex = 0;

        // Inițializăm lista goală ca să nu luăm eroare de NullPointerException
        this.subtasks = new ArrayList<>();
    }
}