package com.example.dailyfocus;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.BackupData;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.receiver.WidgetUpdateReceiver;
import com.example.dailyfocus.utils.Periods;
import com.example.dailyfocus.utils.TaskHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int[] WEEKDAY_CHECKBOX_IDS = {
            R.id.chkDay0, R.id.chkDay1, R.id.chkDay2, R.id.chkDay3,
            R.id.chkDay4, R.id.chkDay5, R.id.chkDay6
    };

    private AppDatabase db;
    private TaskAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView textStats;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    exportData(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importFromUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        textStats = findViewById(R.id.textStats);
        FloatingActionButton fab = findViewById(R.id.fabAdd);

        Button btnOpenReorder = findViewById(R.id.btnOpenReorder);
        if (btnOpenReorder != null) {
            btnOpenReorder.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ReorderActivity.class)));
        }

        findViewById(R.id.btnExport).setOnClickListener(v -> exportLauncher.launch("daily_focus_backup.json"));
        findViewById(R.id.btnImport).setOnClickListener(v -> showImportSourceDialog());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        attachSwipeGestures();
        WidgetUpdateReceiver.scheduleNextAlarm(this);
        requestNotificationPermissionIfNeeded();

        fab.setOnClickListener(v -> showAddTaskDialog());

        LinearLayout layoutHeader = findViewById(R.id.layoutHeader);
        layoutHeader.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, StatsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Singura logică de resetare este în TaskHelper — rulată async, apoi reîncărcăm lista
        TaskRepository.executeThen(() -> TaskHelper.checkAndResetTasks(this), this::loadTasks);
    }

    @Override
    protected void onStop() {
        super.onStop();
        TaskRepository.execute(() -> TaskRepository.maybeAutoBackup(getApplicationContext()));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    // --- EXPORT / IMPORT ---

    private void exportData(android.net.Uri uri) {
        TaskRepository.query(() -> {
            String json = TaskRepository.exportJson(this);
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) return false;
                os.write(json.getBytes());
            }
            return true;
        }, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.export_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showImportSourceDialog() {
        String[] options = {
                getString(R.string.import_source_file),
                getString(R.string.import_source_auto)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        importLauncher.launch(new String[]{"application/json", "application/octet-stream"});
                    } else {
                        showAutoBackupPicker();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAutoBackupPicker() {
        TaskRepository.query(() -> TaskRepository.listAutoBackups(this), files -> {
            if (files == null || files.length == 0) {
                Toast.makeText(this, R.string.no_auto_backups, Toast.LENGTH_SHORT).show();
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            String[] labels = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                labels[i] = sdf.format(new Date(files[i].lastModified()));
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.import_source_auto)
                    .setItems(labels, (dialog, which) -> importFromFile(files[which]))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void importFromUri(android.net.Uri uri) {
        TaskRepository.query(() -> {
            StringBuilder sb = new StringBuilder();
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return new Gson().fromJson(sb.toString(), BackupData.class);
        }, this::confirmAndImport);
    }

    private void importFromFile(File file) {
        TaskRepository.query(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return new Gson().fromJson(sb.toString(), BackupData.class);
        }, this::confirmAndImport);
    }

    private void confirmAndImport(BackupData backup) {
        if (backup == null) {
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_title)
                .setMessage(R.string.import_message)
                .setPositiveButton(R.string.import_confirm, (dialog, which) ->
                        TaskRepository.executeThen(() -> {
                            TaskRepository.importBackup(this, backup);
                            // Reconstruim streak-urile din istoric — repară bug-ul
                            // "streak de 1 zi după restore"
                            TaskHelper.recomputeAllFromHistory(this);
                            TaskHelper.checkAndResetTasks(this);
                            TaskHelper.rescheduleAllReminders(this);
                        }, () -> {
                            loadTasks();
                            TaskHelper.updateWidget(this);
                            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --- LISTA DE TASK-URI ---

    private void loadTasks() {
        TaskRepository.query(() -> db.taskDao().getAllTasks(), tasks -> {
            if (adapter == null) {
                adapter = new TaskAdapter(tasks, new TaskAdapter.OnItemClickListener() {
                    @Override
                    public void onCheckClick(Task task) {
                        handleCheckClick(task);
                    }

                    @Override
                    public void onDeleteClick(Task task) {
                        confirmDelete(task);
                    }

                    @Override
                    public void onInfoClick(Task task) {
                        List<String> options = new ArrayList<>();
                        options.add(getString(R.string.edit_task));
                        options.add(getString(R.string.view_history));
                        // Îngheţul are sens doar pentru serii care se pot rupe
                        if (task.isDaily) {
                            options.add(getString(task.isFrozen
                                    ? R.string.unfreeze_streak : R.string.freeze_streak));
                        }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(task.title)
                                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                                    if (which == 0) {
                                        showEditTaskDialog(task);
                                    } else if (which == 1) {
                                        Intent intent = new Intent(MainActivity.this, TaskDetailsActivity.class);
                                        intent.putExtra("TASK_ID", task.id);
                                        startActivity(intent);
                                    } else {
                                        toggleFreeze(task);
                                    }
                                }).show();
                    }

                    @Override
                    public void onTaskClick(Task task) {
                        if (task.subtasks != null && !task.subtasks.isEmpty()) {
                            showSubtasksCheckDialog(task);
                        } else {
                            showEditTaskDialog(task);
                        }
                    }
                });
                recyclerView.setAdapter(adapter);
            } else {
                adapter.updateList(tasks);
            }

            int total = tasks.size();
            int completed = 0;
            for (Task t : tasks) {
                if (t.isCompleted) completed++;
            }
            progressBar.setProgress(total > 0 ? (completed * 100) / total : 0);
            textStats.setText(getString(R.string.progress_stats, completed, total));
        });
    }

    private void handleCheckClick(Task task) {
        if (!task.isCompleted && task.subtasks != null && !task.subtasks.isEmpty()) {
            boolean allSubDone = true;
            for (Subtask s : task.subtasks) {
                if (!s.isCompleted) allSubDone = false;
            }
            if (!allSubDone) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.check_all_title)
                        .setMessage(R.string.check_all_message)
                        .setPositiveButton(R.string.check_all_confirm, (dialog, which) -> toggleAndReload(task))
                        .setNegativeButton(R.string.no, (dialog, which) -> loadTasks())
                        .show();
                return;
            }
        }
        toggleAndReload(task);
    }

    private void toggleAndReload(Task task) {
        TaskRepository.executeThen(() -> TaskHelper.toggleTask(this, task), () -> {
            loadTasks();
            TaskHelper.updateWidget(this);
        });
    }

    /** Îngheață seria din meniul rapid (dezghețarea nu are nevoie de confirmare). */
    private void toggleFreeze(Task task) {
        if (task.isFrozen) {
            applyFreeze(task, false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.freeze_title)
                .setMessage(getString(R.string.freeze_message, task.title))
                .setPositiveButton(R.string.freeze_confirm, (dialog, which) -> applyFreeze(task, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyFreeze(Task task, boolean frozen) {
        TaskRepository.executeThen(() -> TaskHelper.setFrozen(this, task, frozen), () -> {
            Toast.makeText(this, frozen ? R.string.freeze_success : R.string.unfreeze_success,
                    Toast.LENGTH_SHORT).show();
            loadTasks();
            TaskHelper.updateWidget(this);
        });
    }

    private void confirmDelete(Task task) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, task.title))
                .setPositiveButton(R.string.delete_confirm, (dialog, which) ->
                        TaskRepository.executeThen(() -> {
                            task.hasReminder = false;
                            TaskHelper.scheduleTaskNotification(this, task); // anulează alarma
                            db.taskDao().delete(task);
                        }, () -> {
                            loadTasks();
                            TaskHelper.updateWidget(this);
                        }))
                .setNegativeButton(R.string.no, (dialog, which) -> loadTasks())
                .setOnCancelListener(dialog -> loadTasks())
                .show();
    }

    private void attachSwipeGestures() {
        // Swipe dreapta = bifează/debifează; swipe stânga = șterge (cu confirmare)
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (adapter == null || position == RecyclerView.NO_POSITION) return;
                Task task = adapter.getTasks().get(position);
                if (direction == ItemTouchHelper.RIGHT) {
                    handleCheckClick(task);
                } else {
                    confirmDelete(task);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                // Atenuăm cardul pe măsură ce e tras, ca feedback vizual
                vh.itemView.setAlpha(1f - Math.min(0.6f, Math.abs(dX) / vh.itemView.getWidth()));
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                vh.itemView.setAlpha(1f);
                super.clearView(rv, vh);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    // --- DIALOGURI SUBTASK ---

    private void renderSubtasksInDialog(Context context, LinearLayout container, List<Subtask> list) {
        container.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            Subtask subtask = list.get(i);
            int index = i;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView txt = new TextView(context);
            txt.setText(getString(R.string.subtask_bullet, subtask.title));
            txt.setTextSize(16);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView btnDelete = new TextView(context);
            btnDelete.setText("❌");
            btnDelete.setPadding(16, 0, 16, 0);
            btnDelete.setOnClickListener(v -> {
                list.remove(index);
                renderSubtasksInDialog(context, container, list);
            });

            row.addView(txt);
            row.addView(btnDelete);
            container.addView(row);
        }
    }

    private void showSubtasksCheckDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(task.title);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 24);

        for (Subtask sub : task.subtasks) {
            CheckBox cb = new CheckBox(this);
            cb.setText(sub.title);
            cb.setChecked(sub.isCompleted);
            cb.setTextSize(16);
            cb.setPadding(0, 16, 0, 16);

            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                sub.isCompleted = isChecked;

                boolean allDone = true;
                for (Subtask s : task.subtasks) {
                    if (!s.isCompleted) allDone = false;
                }
                boolean shouldToggle = (allDone && !task.isCompleted) || (!allDone && task.isCompleted);

                TaskRepository.executeThen(() -> {
                    if (shouldToggle) {
                        TaskHelper.toggleTask(this, task);
                    } else {
                        db.taskDao().update(task);
                    }
                }, () -> {
                    if (shouldToggle) {
                        loadTasks();
                        TaskHelper.updateWidget(this);
                    }
                });
            });
            container.addView(cb);
        }

        builder.setView(container);
        builder.setPositiveButton(R.string.done, (dialog, which) -> {
            loadTasks();
            TaskHelper.updateWidget(this);
        });
        builder.show();
    }

    // --- ADĂUGARE / EDITARE ---

    private int readWeekdayMask(View view) {
        int mask = 0;
        for (int i = 0; i < 7; i++) {
            CheckBox cb = view.findViewById(WEEKDAY_CHECKBOX_IDS[i]);
            if (cb != null && cb.isChecked()) mask |= (1 << i);
        }
        return mask;
    }

    private void writeWeekdayMask(View view, int mask) {
        for (int i = 0; i < 7; i++) {
            CheckBox cb = view.findViewById(WEEKDAY_CHECKBOX_IDS[i]);
            if (cb != null) cb.setChecked((mask & (1 << i)) != 0);
        }
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);

        EditText inputTitle = view.findViewById(R.id.editTitle);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupType);
        RadioButton radioDaily = view.findViewById(R.id.radioDaily);
        RadioButton radioCooldown = view.findViewById(R.id.radioCooldown);
        LinearLayout layoutResetTime = view.findViewById(R.id.layoutResetTime);
        Button btnPickResetTime = view.findViewById(R.id.btnPickResetTime);
        LinearLayout layoutCooldownTime = view.findViewById(R.id.layoutCooldownTime);
        EditText editCooldownHours = view.findViewById(R.id.editCooldownHours);
        Switch switchReminder = view.findViewById(R.id.switchReminder);
        LinearLayout layoutReminderTime = view.findViewById(R.id.layoutReminderTime);
        Button btnPickReminderTime = view.findViewById(R.id.btnPickReminderTime);
        TextView txtReminderInfo = view.findViewById(R.id.txtReminderInfo);
        EditText editRepeatDays = view.findViewById(R.id.editRepeatDays);
        LinearLayout layoutWeekdays = view.findViewById(R.id.layoutWeekdays);

        List<Subtask> tempSubtasks = new ArrayList<>();
        LinearLayout layoutSubtasksContainer = view.findViewById(R.id.layoutSubtasksContainer);
        EditText editSubtaskName = view.findViewById(R.id.editSubtaskName);
        Button btnAddSubtask = view.findViewById(R.id.btnAddSubtask);

        btnAddSubtask.setOnClickListener(v -> {
            String subName = editSubtaskName.getText().toString().trim();
            if (!subName.isEmpty()) {
                tempSubtasks.add(new Subtask(subName));
                renderSubtasksInDialog(this, layoutSubtasksContainer, tempSubtasks);
                editSubtaskName.setText("");
            }
        });

        final int[] resetHour = {0};
        final int[] resetMinute = {0};
        final int[] reminderHour = {20};
        final int[] reminderMinute = {0};

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean daily = checkedId == R.id.radioDaily;
            layoutResetTime.setVisibility(daily ? View.VISIBLE : View.GONE);
            layoutWeekdays.setVisibility(daily ? View.VISIBLE : View.GONE);
            layoutCooldownTime.setVisibility(checkedId == R.id.radioCooldown ? View.VISIBLE : View.GONE);
            if (switchReminder.isChecked()) {
                if (checkedId == R.id.radioCooldown) {
                    layoutReminderTime.setVisibility(View.GONE);
                    txtReminderInfo.setVisibility(View.VISIBLE);
                } else {
                    layoutReminderTime.setVisibility(View.VISIBLE);
                    txtReminderInfo.setVisibility(View.GONE);
                }
            }
        });

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (radioCooldown.isChecked()) {
                    layoutReminderTime.setVisibility(View.GONE);
                    txtReminderInfo.setVisibility(View.VISIBLE);
                } else {
                    layoutReminderTime.setVisibility(View.VISIBLE);
                    txtReminderInfo.setVisibility(View.GONE);
                }
            } else {
                layoutReminderTime.setVisibility(View.GONE);
                txtReminderInfo.setVisibility(View.GONE);
            }
        });

        btnPickResetTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            resetHour[0] = h;
            resetMinute[0] = m;
            btnPickResetTime.setText(String.format(Locale.US, "%02d:%02d", h, m));
        }, 0, 0, true).show());

        btnPickReminderTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            reminderHour[0] = h;
            reminderMinute[0] = m;
            btnPickReminderTime.setText(String.format(Locale.US, "%02d:%02d", h, m));
        }, 20, 0, true).show());

        builder.setView(view);
        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String title = inputTitle.getText().toString().trim();
            if (title.isEmpty()) return;

            boolean isDaily = radioDaily.isChecked();
            boolean isCooldown = radioCooldown.isChecked();

            Task newTask = new Task(title, isDaily, isDaily ? resetHour[0] : 0, isDaily ? resetMinute[0] : 0, isCooldown);
            if (isDaily) {
                newTask.daysOfWeekMask = readWeekdayMask(view);
                try {
                    newTask.repeatDays = Integer.parseInt(editRepeatDays.getText().toString());
                } catch (NumberFormatException e) {
                    newTask.repeatDays = 1;
                }
                if (newTask.repeatDays < 1) newTask.repeatDays = 1;
                // Zilele specifice au prioritate față de "la N zile"
                if (newTask.daysOfWeekMask != 0) newTask.repeatDays = 1;
            }
            newTask.orderIndex = adapter != null ? adapter.getItemCount() : 0;
            newTask.subtasks = tempSubtasks;

            if (isCooldown) {
                try {
                    newTask.cooldownHours = Integer.parseInt(editCooldownHours.getText().toString());
                } catch (NumberFormatException e) {
                    newTask.cooldownHours = 24;
                }
            }

            if (switchReminder.isChecked()) {
                newTask.hasReminder = true;
                if (!isCooldown) {
                    newTask.reminderHour = reminderHour[0];
                    newTask.reminderMinute = reminderMinute[0];
                }
            }

            TaskRepository.executeThen(() -> {
                Periods.ensurePeriodStart(newTask, System.currentTimeMillis());
                newTask.id = (int) db.taskDao().insert(newTask);
                TaskHelper.scheduleTaskNotification(this, newTask);
            }, () -> {
                loadTasks();
                TaskHelper.updateWidget(this);
            });
        });
        builder.setNegativeButton(R.string.cancel, null).show();
    }

    private void showEditTaskDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_task);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);

        EditText inputTitle = view.findViewById(R.id.editTitle);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupType);
        RadioButton radioDaily = view.findViewById(R.id.radioDaily);
        RadioButton radioCooldown = view.findViewById(R.id.radioCooldown);
        RadioButton radioOneTime = view.findViewById(R.id.radioOneTime);
        LinearLayout layoutResetTime = view.findViewById(R.id.layoutResetTime);
        Button btnPickResetTime = view.findViewById(R.id.btnPickResetTime);
        LinearLayout layoutCooldownTime = view.findViewById(R.id.layoutCooldownTime);
        EditText editCooldownHours = view.findViewById(R.id.editCooldownHours);
        Switch switchReminder = view.findViewById(R.id.switchReminder);
        LinearLayout layoutReminderTime = view.findViewById(R.id.layoutReminderTime);
        Button btnPickReminderTime = view.findViewById(R.id.btnPickReminderTime);
        TextView txtReminderInfo = view.findViewById(R.id.txtReminderInfo);
        EditText editRepeatDays = view.findViewById(R.id.editRepeatDays);
        LinearLayout layoutWeekdays = view.findViewById(R.id.layoutWeekdays);

        List<Subtask> tempSubtasks = new ArrayList<>();
        if (task.subtasks != null) {
            for (Subtask s : task.subtasks) {
                Subtask clone = new Subtask(s.title);
                clone.isCompleted = s.isCompleted;
                tempSubtasks.add(clone);
            }
        }

        LinearLayout layoutSubtasksContainer = view.findViewById(R.id.layoutSubtasksContainer);
        EditText editSubtaskName = view.findViewById(R.id.editSubtaskName);
        Button btnAddSubtask = view.findViewById(R.id.btnAddSubtask);
        renderSubtasksInDialog(this, layoutSubtasksContainer, tempSubtasks);

        btnAddSubtask.setOnClickListener(v -> {
            String subName = editSubtaskName.getText().toString().trim();
            if (!subName.isEmpty()) {
                tempSubtasks.add(new Subtask(subName));
                renderSubtasksInDialog(this, layoutSubtasksContainer, tempSubtasks);
                editSubtaskName.setText("");
            }
        });

        inputTitle.setText(task.title);

        if (task.isDaily) {
            radioDaily.setChecked(true);
            layoutResetTime.setVisibility(View.VISIBLE);
            layoutWeekdays.setVisibility(View.VISIBLE);
            btnPickResetTime.setText(String.format(Locale.US, "%02d:%02d", task.resetHour, task.resetMinute));
            editRepeatDays.setText(String.valueOf(task.repeatDays));
            writeWeekdayMask(view, task.daysOfWeekMask);
        } else if (task.isCooldown24h) {
            radioCooldown.setChecked(true);
            layoutCooldownTime.setVisibility(View.VISIBLE);
            editCooldownHours.setText(String.valueOf(task.cooldownHours));
        } else {
            radioOneTime.setChecked(true);
        }

        switchReminder.setChecked(task.hasReminder);
        if (task.hasReminder) {
            if (task.isCooldown24h) {
                txtReminderInfo.setVisibility(View.VISIBLE);
            } else {
                layoutReminderTime.setVisibility(View.VISIBLE);
                btnPickReminderTime.setText(String.format(Locale.US, "%02d:%02d", task.reminderHour, task.reminderMinute));
            }
        }

        final int[] newResetHour = {task.resetHour};
        final int[] newResetMinute = {task.resetMinute};
        final int[] newReminderHour = {task.reminderHour};
        final int[] newReminderMinute = {task.reminderMinute};

        radioGroup.setOnCheckedChangeListener((g, checkedId) -> {
            boolean daily = checkedId == R.id.radioDaily;
            layoutResetTime.setVisibility(daily ? View.VISIBLE : View.GONE);
            layoutWeekdays.setVisibility(daily ? View.VISIBLE : View.GONE);
            layoutCooldownTime.setVisibility(checkedId == R.id.radioCooldown ? View.VISIBLE : View.GONE);
            if (switchReminder.isChecked()) {
                layoutReminderTime.setVisibility(checkedId == R.id.radioCooldown ? View.GONE : View.VISIBLE);
                txtReminderInfo.setVisibility(checkedId == R.id.radioCooldown ? View.VISIBLE : View.GONE);
            }
        });

        switchReminder.setOnCheckedChangeListener((bv, isChecked) -> {
            layoutReminderTime.setVisibility(isChecked && !radioCooldown.isChecked() ? View.VISIBLE : View.GONE);
            txtReminderInfo.setVisibility(isChecked && radioCooldown.isChecked() ? View.VISIBLE : View.GONE);
        });

        btnPickResetTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            newResetHour[0] = h;
            newResetMinute[0] = m;
            btnPickResetTime.setText(String.format(Locale.US, "%02d:%02d", h, m));
        }, task.resetHour, task.resetMinute, true).show());

        btnPickReminderTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            newReminderHour[0] = h;
            newReminderMinute[0] = m;
            btnPickReminderTime.setText(String.format(Locale.US, "%02d:%02d", h, m));
        }, task.reminderHour, task.reminderMinute, true).show());

        builder.setView(view);
        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String newTitle = inputTitle.getText().toString().trim();
            if (newTitle.isEmpty()) return;

            boolean wasDaily = task.isDaily;
            boolean wasCooldown = task.isCooldown24h;
            boolean scheduleChanged;

            task.title = newTitle;
            task.isDaily = radioDaily.isChecked();
            task.isCooldown24h = radioCooldown.isChecked();
            task.subtasks = tempSubtasks;

            int oldResetHour = task.resetHour;
            int oldResetMinute = task.resetMinute;
            int oldRepeatDays = task.repeatDays;
            int oldMask = task.daysOfWeekMask;

            if (task.isDaily) {
                task.daysOfWeekMask = readWeekdayMask(view);
                try {
                    task.repeatDays = Integer.parseInt(editRepeatDays.getText().toString());
                } catch (NumberFormatException e) {
                    task.repeatDays = 1;
                }
                if (task.repeatDays < 1) task.repeatDays = 1;
                if (task.daysOfWeekMask != 0) task.repeatDays = 1;
                task.resetHour = newResetHour[0];
                task.resetMinute = newResetMinute[0];
            }
            if (task.isCooldown24h) {
                try {
                    task.cooldownHours = Integer.parseInt(editCooldownHours.getText().toString());
                } catch (NumberFormatException e) {
                    task.cooldownHours = 24;
                }
            }

            scheduleChanged = wasDaily != task.isDaily || wasCooldown != task.isCooldown24h
                    || oldResetHour != task.resetHour || oldResetMinute != task.resetMinute
                    || oldRepeatDays != task.repeatDays || oldMask != task.daysOfWeekMask;

            task.hasReminder = switchReminder.isChecked();
            if (task.hasReminder && !task.isCooldown24h) {
                task.reminderHour = newReminderHour[0];
                task.reminderMinute = newReminderMinute[0];
            }

            boolean reanchor = scheduleChanged;
            TaskRepository.executeThen(() -> {
                if (reanchor && task.isDaily) {
                    // Programul s-a schimbat — reancorăm perioada la momentul curent
                    task.periodStart = Periods.currentPeriodStart(task, System.currentTimeMillis());
                }
                db.taskDao().update(task);
                if (reanchor) {
                    // Istoricul a fost scris pe vechiul ritm: recalculăm seria acum, ca
                    // valoarea afișată să corespundă noului program de la bun început.
                    TaskHelper.refreshStreakFromHistory(this, task);
                }
                TaskHelper.scheduleTaskNotification(this, task);
            }, () -> {
                loadTasks();
                TaskHelper.updateWidget(this);
            });
        });
        builder.setNegativeButton(R.string.cancel, null).show();
    }
}
