package com.example.dailyfocus.data;

import java.util.List;

public class BackupData {
    public List<Task> tasks;
    public List<TaskHistory> history;

    public BackupData() {}

    public BackupData(List<Task> tasks, List<TaskHistory> history) {
        this.tasks = tasks;
        this.history = history;
    }
}