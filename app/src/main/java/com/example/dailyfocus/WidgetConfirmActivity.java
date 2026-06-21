package com.example.dailyfocus;

import android.app.AlertDialog;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.utils.TaskHelper;
import java.util.Calendar;

public class WidgetConfirmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Preluăm ID-ul task-ului pe care s-a dat click în widget
        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) {
            finish();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(this);
        Task task = db.taskDao().getTaskById(taskId);

        if (task == null || !task.isCompleted) {
            finish();
            return;
        }

        // Creăm Dialogul de Confirmare
        new AlertDialog.Builder(this)
                .setTitle("Anulare Task")
                .setMessage("Ești sigur că vrei să debifezi task-ul \"" + task.title + "\"?\n(Vei pierde progresul înregistrat astăzi).")
                .setPositiveButton("Da, debifează", (dialog, which) -> {

                    // --- LOGICA DE DEBIFARE (Ca în MainActivity) ---
                    task.isCompleted = false;

                    if (task.isDaily || task.isCooldown24h) {
                        if (task.currentStreak > 0) task.currentStreak--;

                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

                        db.taskDao().deleteHistory(task.id, cal.getTimeInMillis());
                    }

                    db.taskDao().update(task);

                    // Actualizăm Widget-ul
                    TaskHelper.updateWidget(this);

                    finish(); // Închidem fereastra
                })
                .setNegativeButton("Nu", (dialog, which) -> {
                    finish(); // Închidem fără să facem nimic
                })
                .setOnCancelListener(dialog -> finish()) // Dacă apasă pe lângă dialog
                .show();
    }
}