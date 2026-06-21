package com.example.dailyfocus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.utils.TaskHelper;
import java.util.Calendar;

public class WidgetSubtaskActivity extends AppCompatActivity {

    private AppDatabase db;
    private Task task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int taskId = getIntent().getIntExtra("TASK_ID", -1);
        if (taskId == -1) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
        task = db.taskDao().getTaskById(taskId);

        if (task == null || task.isCompleted || task.subtasks == null || task.subtasks.isEmpty()) {
            finish();
            return;
        }

        // Construim layout-ul ferestrei direct din cod
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(50, 40, 50, 40);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout checkboxContainer = new LinearLayout(this);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);

        // Generăm CheckBox-uri pentru fiecare subtask existent
        for (Subtask sub : task.subtasks) {
            CheckBox cb = new CheckBox(this);
            cb.setText(sub.title);
            cb.setChecked(sub.isCompleted);
            cb.setTextSize(16);
            cb.setPadding(10, 16, 10, 16);

            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                sub.isCompleted = isChecked;
                task.lastCompletionTimestamp = System.currentTimeMillis();
                db.taskDao().update(task);

                // Verificăm dacă utilizatorul le-a terminat pe toate manual
                boolean allDone = true;
                for (Subtask s : task.subtasks) {
                    if (!s.isCompleted) allDone = false;
                }

                // Dacă toate subtask-urile sunt gata, completăm automat și task-ul mare
                if (allDone) {
                    executeTaskCompletion();
                    finish();
                }
            });
            checkboxContainer.addView(cb);
        }

        scrollView.addView(checkboxContainer);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Butonul de scurtătură pentru a bifa tot task-ul principal deodată
        Button btnCheckAll = new Button(this);
        btnCheckAll.setText("Bifează tot Task-ul");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 24, 0, 0);
        btnCheckAll.setLayoutParams(btnParams);

        btnCheckAll.setOnClickListener(v -> {
            // POP-UP-ul de avertizare cerut
            new AlertDialog.Builder(this)
                    .setTitle("Bifezi tot?")
                    .setMessage("Ești sigur că vrei să bifezi task-ul principal \"" + task.title + "\"? Toate subtask-urile din interior vor fi marcate ca fiind gata.")
                    .setPositiveButton("Da, bifează tot", (dialog, which) -> {
                        executeTaskCompletion();
                        finish();
                    })
                    .setNegativeButton("Nu", null)
                    .show();
        });

        rootLayout.addView(btnCheckAll);

        // Afișăm tot conținutul sub formă de dialog alert
        new AlertDialog.Builder(this)
                .setTitle(task.title)
                .setView(rootLayout)
                .setPositiveButton("Închide", (dialog, which) -> {
                    TaskHelper.updateWidget(this); // Ne asigurăm că widget-ul se reîmprospătează la închidere
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    TaskHelper.updateWidget(this);
                    finish();
                })
                .show();
    }

    private void executeTaskCompletion() {
        task.isCompleted = true;
        task.lastCompletionTimestamp = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();

        if (task.isDaily || task.isCooldown24h) {
            task.currentStreak++;
            db.taskDao().insertHistory(new TaskHistory(task.id, task.title, todayMidnight));
        }

        if (task.subtasks != null) {
            for (Subtask s : task.subtasks) s.isCompleted = true;
        }

        db.taskDao().update(task);
        TaskHelper.updateWidget(this);
    }
}