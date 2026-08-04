package com.example.dailyfocus.data;

import java.util.List;

public class BackupData {
    public List<Task> tasks;
    public List<TaskHistory> history;
    // Zilele acoperite de îngheţ; lipsesc în backupurile vechi (rămân null).
    public List<StreakFreeze> freezes;

    public BackupData() {}

    public BackupData(List<Task> tasks, List<TaskHistory> history, List<StreakFreeze> freezes) {
        this.tasks = tasks;
        this.history = history;
        this.freezes = freezes;
    }
}
