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
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.TaskHelper;

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
        TaskRepository.query(() -> db.taskDao().getTaskById(taskId), loaded -> {
            task = loaded;
            if (task == null || task.isCompleted || task.subtasks == null || task.subtasks.isEmpty()) {
                finish();
                return;
            }
            buildDialog();
        });
    }

    private void buildDialog() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(50, 40, 50, 40);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout checkboxContainer = new LinearLayout(this);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);

        for (Subtask sub : task.subtasks) {
            CheckBox cb = new CheckBox(this);
            cb.setText(sub.title);
            cb.setChecked(sub.isCompleted);
            cb.setTextSize(16);
            cb.setPadding(10, 16, 10, 16);

            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                sub.isCompleted = isChecked;

                boolean allDone = true;
                for (Subtask s : task.subtasks) {
                    if (!s.isCompleted) allDone = false;
                }

                if (allDone) {
                    TaskRepository.executeThen(() -> {
                        TaskHelper.completeTask(this, task);
                    }, () -> {
                        TaskHelper.updateWidget(this);
                        finish();
                    });
                } else {
                    TaskRepository.execute(() -> db.taskDao().update(task));
                }
            });
            checkboxContainer.addView(cb);
        }

        scrollView.addView(checkboxContainer);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button btnCheckAll = new Button(this);
        btnCheckAll.setText(R.string.widget_check_all);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 24, 0, 0);
        btnCheckAll.setLayoutParams(btnParams);

        btnCheckAll.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.check_all_title)
                        .setMessage(getString(R.string.widget_check_all_message, task.title))
                        .setPositiveButton(R.string.check_all_confirm, (dialog, which) ->
                                TaskRepository.executeThen(() -> {
                                    TaskHelper.completeTask(this, task);
                                }, () -> {
                                    TaskHelper.updateWidget(this);
                                    finish();
                                }))
                        .setNegativeButton(R.string.no, null)
                        .show());

        rootLayout.addView(btnCheckAll);

        new AlertDialog.Builder(this)
                .setTitle(task.title)
                .setView(rootLayout)
                .setPositiveButton(R.string.close, (dialog, which) -> {
                    TaskHelper.updateWidget(this);
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    TaskHelper.updateWidget(this);
                    finish();
                })
                .show();
    }
}
