package com.example.dailyfocus.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, orderIndex ASC, id DESC")
    List<Task> getAllTasks();

    // Aducem task-urile fix în ordinea lor pentru editare
    @Query("SELECT * FROM tasks ORDER BY orderIndex ASC")
    List<Task> getAllTasksForReordering();

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    Task getTaskById(int id);

    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT COUNT(*) FROM tasks")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    int getCompletedCount();

    // --- ISTORIC ---
    // IGNORE + index unic (taskId, dateTimestamp): întoarce -1 dacă ziua era deja înregistrată.
    // Streak-ul se incrementează doar când insertul chiar a avut loc.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertHistory(TaskHistory history);

    // Întoarce numărul de rânduri șterse: 0 = nu exista completare pentru ziua aceea,
    // deci nici streak-ul nu trebuie decrementat.
    @Query("DELETE FROM task_history WHERE taskId = :taskId AND dateTimestamp = :dateTimestamp")
    int deleteHistory(int taskId, long dateTimestamp);

    @Query("SELECT * FROM task_history WHERE taskId = :taskId ORDER BY dateTimestamp ASC")
    List<TaskHistory> getHistoryForTask(int taskId);

    @Query("SELECT * FROM task_history ORDER BY dateTimestamp ASC")
    List<TaskHistory> getAllHistory();

    @Query("SELECT * FROM task_history WHERE dateTimestamp >= :since ORDER BY dateTimestamp ASC")
    List<TaskHistory> getHistorySince(long since);

    // --- ÎNGHEȚĂRI DE SERIE ---
    // IGNORE + index unic (taskId, dayKey): o zi înghețată se înregistrează o singură dată.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertFreeze(StreakFreeze freeze);

    @Query("SELECT * FROM streak_freezes WHERE taskId = :taskId ORDER BY dayKey ASC")
    List<StreakFreeze> getFreezesForTask(int taskId);

    @Query("SELECT * FROM streak_freezes ORDER BY dayKey ASC")
    List<StreakFreeze> getAllFreezes();

    @Query("DELETE FROM streak_freezes WHERE taskId = :taskId AND dayKey < :cutoff")
    int deleteFreezesBefore(int taskId, long cutoff);
}
