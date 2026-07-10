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

    @ColumnInfo(defaultValue = "1")
    public int repeatDays = 1;

    // Câmp pentru reordonare (Drag & Drop)
    @ColumnInfo(defaultValue = "0")
    public int orderIndex = 0;

    @ColumnInfo(defaultValue = "[]")
    public List<Subtask> subtasks;

    // Cea mai lungă serie atinsă vreodată
    @ColumnInfo(defaultValue = "0")
    public int bestStreak = 0;

    // Zile active pentru task-uri zilnice: bit 0 = Luni ... bit 6 = Duminică.
    // 0 = dezactivat (se folosește repeatDays).
    @ColumnInfo(defaultValue = "0")
    public int daysOfWeekMask = 0;

    // Începutul perioadei curente (momentul ultimului reset) pentru task-uri zilnice.
    // 0 = neinițializat; se calculează la prima verificare.
    @ColumnInfo(defaultValue = "0")
    public long periodStart = 0;

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
        this.bestStreak = 0;
        this.daysOfWeekMask = 0;
        this.periodStart = 0;

        // Inițializăm lista goală ca să nu luăm eroare de NullPointerException
        this.subtasks = new ArrayList<>();
    }
}
