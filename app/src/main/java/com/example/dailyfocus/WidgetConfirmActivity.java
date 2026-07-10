package com.example.dailyfocus;

import android.app.AlertDialog;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.TaskHelper;

public class WidgetConfirmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) {
            finish();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(this);
        TaskRepository.query(() -> db.taskDao().getTaskById(taskId), task -> {
            if (task == null || !task.isCompleted) {
                finish();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.widget_uncheck_title)
                    .setMessage(getString(R.string.widget_uncheck_message, task.title))
                    .setPositiveButton(R.string.widget_uncheck_confirm, (dialog, which) ->
                            TaskRepository.executeThen(() -> {
                                // Logica unică de debifare — decrementează streak-ul doar
                                // dacă exista completare înregistrată pentru perioada curentă
                                TaskHelper.uncompleteTask(this, task);
                            }, () -> {
                                TaskHelper.updateWidget(this);
                                finish();
                            }))
                    .setNegativeButton(R.string.no, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        });
    }
}
