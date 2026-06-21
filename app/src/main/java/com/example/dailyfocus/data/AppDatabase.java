package com.example.dailyfocus.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Task.class, TaskHistory.class}, version = 5, exportSchema = false)
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

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "daily_focus_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}