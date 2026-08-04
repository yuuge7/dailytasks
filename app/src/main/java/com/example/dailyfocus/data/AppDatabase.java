package com.example.dailyfocus.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Task.class, TaskHistory.class, StreakFreeze.class}, version = 8, exportSchema = false)
@TypeConverters({Converters.class}) // SPUNEM BAZEI DE DATE SĂ FOLOSEASCĂ CONVERTORUL
public abstract class AppDatabase extends RoomDatabase {

    public abstract TaskDao taskDao();
    private static volatile AppDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN cooldownHours INTEGER NOT NULL DEFAULT 24");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0");
        }
    };

    // MIGRAREA 3 -> 4 (Pentru Subtask-uri)
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT '[]'");
        }
    };

    // MIGRAREA 4 -> 5 (Pentru Istoric Persistent)
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // SQLite nu suportă ștergerea Foreign Key ușor, așa că recreăm tabelul
            database.execSQL("CREATE TABLE task_history_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, taskName TEXT, dateTimestamp INTEGER NOT NULL)");
            database.execSQL("INSERT INTO task_history_new (id, taskId, dateTimestamp) SELECT id, taskId, dateTimestamp FROM task_history");
            database.execSQL("DROP TABLE task_history");
            database.execSQL("ALTER TABLE task_history_new RENAME TO task_history");
            database.execSQL("CREATE INDEX index_task_history_taskId ON task_history(taskId)");
        }
    };

    // MIGRAREA 5 -> 6 (Pentru repeatDays)
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 1");
        }
    };

    // MIGRAREA 6 -> 7 (bestStreak, zile active, periodStart + index unic pe istoric)
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN bestStreak INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN daysOfWeekMask INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN periodStart INTEGER NOT NULL DEFAULT 0");
            // Pornim bestStreak de la streak-ul curent
            database.execSQL("UPDATE tasks SET bestStreak = currentStreak WHERE currentStreak > bestStreak");
            // Curățăm duplicatele din istoric înainte de a crea indexul unic
            database.execSQL("DELETE FROM task_history WHERE id NOT IN (SELECT MIN(id) FROM task_history GROUP BY taskId, dateTimestamp)");
            database.execSQL("CREATE UNIQUE INDEX index_task_history_taskId_dateTimestamp ON task_history(taskId, dateTimestamp)");
        }
    };

    // MIGRAREA 7 -> 8 (înghețare serie + resetare manuală serie)
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN isFrozen INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN frozenSince INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN streakResetAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS streak_freezes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, dayKey INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_streak_freezes_taskId ON streak_freezes(taskId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_streak_freezes_taskId_dayKey ON streak_freezes(taskId, dayKey)");
        }
    };

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "daily_focus_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build();
        }
        return instance;
    }
}
