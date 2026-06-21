package com.example.dailyfocus.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, orderIndex ASC, id DESC")
    List<Task> getAllTasks();

    // --- ADAUGĂ LINIA ASTA NOUĂ: Aducem task-urile fix în ordinea lor pentru editare ---
    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC")
    List<Task> getAllTasksForReordering();

    // METODA CARE LIPSEA ȘI DĂDEA EROARE:
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    Task getTaskById(int id);

    @Insert
    void insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT COUNT(*) FROM tasks")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    int getCompletedCount();

    // --- ISTORIC ---
    @Insert
    void insertHistory(TaskHistory history);

    @Query("DELETE FROM task_history WHERE taskId = :taskId AND dateTimestamp = :dateTimestamp")
    void deleteHistory(int taskId, long dateTimestamp);

    @Query("SELECT * FROM task_history WHERE taskId = :taskId ORDER BY dateTimestamp ASC")
    List<TaskHistory> getHistoryForTask(int taskId);

    @Query("SELECT * FROM task_history ORDER BY dateTimestamp ASC")
    List<TaskHistory> getAllHistory();
}