package com.example.dailyfocus.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Punct unic de acces asincron la baza de date + backup automat.
 * Toate operațiile DB rulează pe un singur thread de background;
 * rezultatele sunt livrate pe main thread.
 */
public class TaskRepository {

    public interface Callback<T> {
        void onResult(T result);
    }

    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    // Păstrăm ultimele 7 backupuri automate, cel mult unul la 20 de ore
    private static final int AUTO_BACKUP_KEEP = 7;
    private static final long AUTO_BACKUP_MIN_INTERVAL_MS = 20 * 60 * 60 * 1000L;

    private TaskRepository() {}

    /** Rulează pe background, fără rezultat. */
    public static void execute(Runnable work) {
        io.execute(work);
    }

    /** Rulează pe background, apoi livrează rezultatul pe main thread. */
    public static <T> void query(Callable<T> work, Callback<T> callback) {
        io.execute(() -> {
            try {
                T result = work.call();
                main.post(() -> callback.onResult(result));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /** Rulează pe background, apoi notifică main thread-ul (fără rezultat). */
    public static void executeThen(Runnable work, Runnable onMain) {
        io.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            main.post(onMain);
        });
    }

    // --- BACKUP AUTOMAT ---

    public static File backupDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Backupurile automate existente, cel mai recent primul. */
    public static File[] listAutoBackups(Context context) {
        File[] files = backupDir(context).listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    /** Serializează toate datele în JSON (de apelat pe background). */
    public static String exportJson(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<Task> tasks = db.taskDao().getAllTasks();
        List<TaskHistory> history = db.taskDao().getAllHistory();
        List<StreakFreeze> freezes = db.taskDao().getAllFreezes();
        return new Gson().toJson(new BackupData(tasks, history, freezes));
    }

    /**
     * Scrie un backup automat dacă cel mai recent e mai vechi de ~o zi.
     * De apelat pe background (ex: la părăsirea aplicației).
     */
    public static void maybeAutoBackup(Context context) {
        try {
            File[] existing = listAutoBackups(context);
            long now = System.currentTimeMillis();
            if (existing.length > 0 && now - existing[0].lastModified() < AUTO_BACKUP_MIN_INTERVAL_MS) {
                return;
            }

            String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(new Date(now));
            File out = new File(backupDir(context), "auto_backup_" + stamp + ".json");
            try (FileWriter writer = new FileWriter(out)) {
                writer.write(exportJson(context));
            }

            // Curățăm backupurile vechi
            File[] all = listAutoBackups(context);
            for (int i = AUTO_BACKUP_KEEP; i < all.length; i++) {
                all[i].delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Înlocuiește toate datele cu cele din backup (de apelat pe background).
     * Streak-urile sunt recalculate din istoric de apelant (TaskHelper.recomputeAllFromHistory).
     */
    public static void importBackup(Context context, BackupData backup) {
        AppDatabase db = AppDatabase.getInstance(context);
        db.clearAllTables();
        if (backup.tasks != null) {
            for (Task t : backup.tasks) {
                db.taskDao().insert(t);
            }
        }
        if (backup.history != null) {
            for (TaskHistory h : backup.history) {
                db.taskDao().insertHistory(h);
            }
        }
        if (backup.freezes != null) {
            for (StreakFreeze f : backup.freezes) {
                db.taskDao().insertFreeze(f);
            }
        }
    }
}
