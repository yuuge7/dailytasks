package com.example.dailyfocus.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * O zi programată sărită fără pierderea streak-ului, pentru că task-ul era îngheţat.
 *
 * NU este o completare: nu intră în istoric, nu apare în heatmap și nu incrementează
 * seria — doar leagă lanțul peste ziua respectivă atunci când streak-ul e recalculat.
 */
@Entity(tableName = "streak_freezes",
        indices = {
                @Index("taskId"),
                // O singură înregistrare per task per zi
                @Index(value = {"taskId", "dayKey"}, unique = true)
        })
public class StreakFreeze {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int taskId;
    public long dayKey;

    public StreakFreeze(int taskId, long dayKey) {
        this.taskId = taskId;
        this.dayKey = dayKey;
    }
}
