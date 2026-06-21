package com.example.dailyfocus;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.BackupData;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.receiver.WidgetUpdateReceiver;
import com.example.dailyfocus.utils.TaskHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

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
                    importData(uri);
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

        // --- LOGICA NOUĂ PENTRU BUTONUL DE REORDONARE ---
        Button btnOpenReorder = findViewById(R.id.btnOpenReorder);
        if(btnOpenReorder != null) {
            btnOpenReorder.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, ReorderActivity.class));
            });
        }

        // --- IMPORT / EXPORT ---
        findViewById(R.id.btnExport).setOnClickListener(v -> exportLauncher.launch("daily_focus_backup.json"));
        findViewById(R.id.btnImport).setOnClickListener(v -> importLauncher.launch(new String[]{"application/json", "application/octet-stream"}));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        scheduleUpdates();
        loadTasks();

        fab.setOnClickListener(v -> showAddTaskDialog());

        LinearLayout layoutHeader = findViewById(R.id.layoutHeader);
        layoutHeader.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StatsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        TaskHelper.checkAndResetTasks(this);
        loadTasks();
    }

    private void exportData(android.net.Uri uri) {
        try {
            List<Task> tasks = db.taskDao().getAllTasks();
            List<TaskHistory> history = db.taskDao().getAllHistory();
            BackupData backup = new BackupData(tasks, history);
            String json = new Gson().toJson(backup);

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.getBytes());
                os.close();
                Toast.makeText(this, "Date exportate cu succes!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Eroare la export: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importData(android.net.Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                is.close();

                BackupData backup = new Gson().fromJson(sb.toString(), BackupData.class);
                if (backup != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Import Date")
                            .setMessage("Ești sigur că vrei să imporți datele? Acest lucru va șterge sarcinile curente și istoricul actual.")
                            .setPositiveButton("Da, Importă", (dialog, which) -> {
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
                                loadTasks();
                                TaskHelper.updateWidget(this);
                                Toast.makeText(this, "Import realizat cu succes!", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Anulează", null)
                            .show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Eroare la import: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleUpdates() {
        WidgetUpdateReceiver.scheduleNextAlarm(this);
    }

    private boolean checkDailyStreaks(List<Task> tasks) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        boolean needsUpdate = false;

        for (Task task : tasks) {
            if (task.isDaily) {
                cal.setTimeInMillis(now);
                cal.set(Calendar.HOUR_OF_DAY, task.resetHour);
                cal.set(Calendar.MINUTE, task.resetMinute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long lastResetTime = cal.getTimeInMillis();
                if (lastResetTime > now) lastResetTime -= (24 * 60 * 60 * 1000L);

                if (task.lastCompletionTimestamp < lastResetTime) {
                    if (task.isCompleted) {
                        task.isCompleted = false;
                        long previousResetTime = lastResetTime - (24 * 60 * 60 * 1000L);
                        if (task.lastCompletionTimestamp < previousResetTime) task.currentStreak = 0;
                    } else {
                        task.currentStreak = 0;
                    }

                    if (task.subtasks != null) {
                        for (Subtask s : task.subtasks) {
                            s.isCompleted = false;
                        }
                    }

                    task.lastCompletionTimestamp = now;
                    db.taskDao().update(task);
                    needsUpdate = true;
                }
            } else if (task.isCooldown24h) {
                long unlockTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                if (task.isCompleted && now >= unlockTime) {
                    task.isCompleted = false;
                    if (task.subtasks != null) {
                        for (Subtask s : task.subtasks) s.isCompleted = false;
                    }
                    task.lastCompletionTimestamp = now;
                    db.taskDao().update(task);
                    needsUpdate = true;
                }
            }
        }
        return needsUpdate;
    }

    private void loadTasks() {
        List<Task> tasks = db.taskDao().getAllTasks();
        if (checkDailyStreaks(tasks)) {
            tasks = db.taskDao().getAllTasks();
        }

        for (Task t : tasks) {
            if (t.hasReminder && !t.isCompleted) scheduleTaskNotification(t);
        }

        if (adapter == null) {
            adapter = new TaskAdapter(tasks, new TaskAdapter.OnItemClickListener() {

                @Override
                public void onCheckClick(Task task) {
                    if (!task.isCompleted && task.subtasks != null && !task.subtasks.isEmpty()) {
                        boolean allSubDone = true;
                        for (Subtask s : task.subtasks) {
                            if (!s.isCompleted) allSubDone = false;
                        }

                        if (!allSubDone) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Bifezi tot?")
                                    .setMessage("Acest task are subtask-uri neterminate. Ești sigur că vrei să-l bifezi și să le marchezi pe toate ca fiind gata?")
                                    .setPositiveButton("Da, bifează tot", (dialog, which) -> {
                                        executeTaskCompletion(task);
                                    })
                                    .setNegativeButton("Nu", (dialog, which) -> {
                                        loadTasks();
                                    })
                                    .show();
                            return;
                        }
                    }
                    executeTaskCompletion(task);
                }

                @Override
                public void onDeleteClick(Task task) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Șterge Task")
                            .setMessage("Ești sigur că vrei să ștergi \"" + task.title + "\"?")
                            .setPositiveButton("Da, șterge", (dialog, which) -> {
                                task.hasReminder = false;
                                scheduleTaskNotification(task);
                                db.taskDao().delete(task);
                                loadTasks();
                                TaskHelper.updateWidget(MainActivity.this);
                            })
                            .setNegativeButton("Nu", null).show();
                }

                @Override
                public void onInfoClick(Task task) {
                    showEditTaskDialog(task);
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
        updateChart();
    }

    private void executeTaskCompletion(Task task) {
        task.isCompleted = !task.isCompleted;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();

        task.lastCompletionTimestamp = System.currentTimeMillis();

        if (task.isCompleted) {
            if (task.isDaily || task.isCooldown24h) {
                task.currentStreak++;
                db.taskDao().insertHistory(new TaskHistory(task.id, task.title, todayMidnight));
            }
            if (task.subtasks != null) {
                for (Subtask s : task.subtasks) s.isCompleted = true;
            }
        } else {
            if (task.isDaily || task.isCooldown24h) {
                if (task.currentStreak > 0) task.currentStreak--;
                db.taskDao().deleteHistory(task.id, todayMidnight);
            }
            if (task.subtasks != null) {
                for (Subtask s : task.subtasks) s.isCompleted = false;
            }
        }

        db.taskDao().update(task);
        scheduleTaskNotification(task);
        loadTasks();
        TaskHelper.updateWidget(MainActivity.this);
    }

    private void updateChart() {
        int total = db.taskDao().getTotalCount();
        int completed = db.taskDao().getCompletedCount();
        progressBar.setProgress(total > 0 ? (completed * 100) / total : 0);
        textStats.setText(completed + "/" + total + " terminate");
    }

    private void renderSubtasksInDialog(Context context, LinearLayout container, List<Subtask> list) {
        container.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            Subtask subtask = list.get(i);
            int index = i;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView txt = new TextView(context);
            txt.setText("• " + subtask.title);
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
                task.lastCompletionTimestamp = System.currentTimeMillis();
                db.taskDao().update(task);

                boolean allDone = true;
                for (Subtask s : task.subtasks) {
                    if (!s.isCompleted) allDone = false;
                }

                if (allDone && !task.isCompleted) {
                    executeTaskCompletion(task);
                } else if (!allDone && task.isCompleted) {
                    executeTaskCompletion(task);
                }
            });
            container.addView(cb);
        }

        builder.setView(container);
        builder.setPositiveButton("Gata", (dialog, which) -> {
            loadTasks();
            TaskHelper.updateWidget(MainActivity.this);
        });
        builder.show();
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

        final int[] resetHour = {0}; final int[] resetMinute = {0};
        final int[] reminderHour = {20}; final int[] reminderMinute = {0};

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            layoutResetTime.setVisibility(checkedId == R.id.radioDaily ? View.VISIBLE : View.GONE);
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
            resetHour[0] = h; resetMinute[0] = m;
            btnPickResetTime.setText(String.format("%02d:%02d", h, m));
        }, 0, 0, true).show());

        btnPickReminderTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            reminderHour[0] = h; reminderMinute[0] = m;
            btnPickReminderTime.setText(String.format("%02d:%02d", h, m));
        }, 20, 0, true).show());

        builder.setView(view);
        builder.setPositiveButton("Adaugă", (dialog, which) -> {
            String title = inputTitle.getText().toString().trim();
            if (!title.isEmpty()) {
                boolean isDaily = radioDaily.isChecked();
                boolean isCooldown = radioCooldown.isChecked();

                Task newTask = new Task(title, isDaily, isDaily ? resetHour[0] : 0, isDaily ? resetMinute[0] : 0, isCooldown);
                newTask.orderIndex = adapter != null ? adapter.getItemCount() : 0;
                newTask.subtasks = tempSubtasks;
                newTask.lastCompletionTimestamp = System.currentTimeMillis();

                if (isCooldown) {
                    try { newTask.cooldownHours = Integer.parseInt(editCooldownHours.getText().toString()); }
                    catch (NumberFormatException e) { newTask.cooldownHours = 24; }
                }

                if (switchReminder.isChecked()) {
                    newTask.hasReminder = true;
                    if (!isCooldown) { newTask.reminderHour = reminderHour[0]; newTask.reminderMinute = reminderMinute[0]; }
                }

                db.taskDao().insert(newTask);
                loadTasks();
                TaskHelper.updateWidget(MainActivity.this);
            }
        });
        builder.setNegativeButton("Anulează", null).show();
    }

    private void showEditTaskDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editează Task");
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
            btnPickResetTime.setText(String.format("%02d:%02d", task.resetHour, task.resetMinute));
        } else if (task.isCooldown24h) {
            radioCooldown.setChecked(true);
            layoutCooldownTime.setVisibility(View.VISIBLE);
            editCooldownHours.setText(String.valueOf(task.cooldownHours));
        } else { radioOneTime.setChecked(true); }

        switchReminder.setChecked(task.hasReminder);
        if (task.hasReminder) {
            if (task.isCooldown24h) { txtReminderInfo.setVisibility(View.VISIBLE); }
            else { layoutReminderTime.setVisibility(View.VISIBLE); btnPickReminderTime.setText(String.format("%02d:%02d", task.reminderHour, task.reminderMinute)); }
        }

        final int[] newResetHour = {task.resetHour}; final int[] newResetMinute = {task.resetMinute};
        final int[] newReminderHour = {task.reminderHour}; final int[] newReminderMinute = {task.reminderMinute};

        radioGroup.setOnCheckedChangeListener((g, checkedId) -> {
            layoutResetTime.setVisibility(checkedId == R.id.radioDaily ? View.VISIBLE : View.GONE);
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
            newResetHour[0] = h; newResetMinute[0] = m;
            btnPickResetTime.setText(String.format("%02d:%02d", h, m));
        }, task.resetHour, task.resetMinute, true).show());

        btnPickReminderTime.setOnClickListener(v -> new TimePickerDialog(this, (tv, h, m) -> {
            newReminderHour[0] = h; newReminderMinute[0] = m;
            btnPickReminderTime.setText(String.format("%02d:%02d", h, m));
        }, task.reminderHour, task.reminderMinute, true).show());

        builder.setView(view);
        builder.setPositiveButton("Salvează", (dialog, which) -> {
            String newTitle = inputTitle.getText().toString().trim();
            if (!newTitle.isEmpty()) {
                task.title = newTitle;
                task.isDaily = radioDaily.isChecked();
                task.isCooldown24h = radioCooldown.isChecked();
                task.subtasks = tempSubtasks;
                task.lastCompletionTimestamp = System.currentTimeMillis();

                if (task.isDaily) { task.resetHour = newResetHour[0]; task.resetMinute = newResetMinute[0]; }
                if (task.isCooldown24h) {
                    try { task.cooldownHours = Integer.parseInt(editCooldownHours.getText().toString()); }
                    catch (NumberFormatException e) { task.cooldownHours = 24; }
                }

                task.hasReminder = switchReminder.isChecked();
                if (task.hasReminder && !task.isCooldown24h) { task.reminderHour = newReminderHour[0]; task.reminderMinute = newReminderMinute[0]; }

                db.taskDao().update(task);
                scheduleTaskNotification(task);
                loadTasks();
                TaskHelper.updateWidget(this);
            }
        });
        builder.setNegativeButton("Anulează", null).show();
    }

    private void scheduleTaskNotification(Task task) {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, com.example.dailyfocus.receiver.NotificationReceiver.class);
        intent.putExtra(com.example.dailyfocus.receiver.NotificationReceiver.EXTRA_TASK_ID, task.id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, task.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (!task.hasReminder) { alarmManager.cancel(pendingIntent); return; }

        long triggerTime = 0;
        if (task.isCooldown24h) {
            if (task.isCompleted) {
                triggerTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                if (triggerTime < System.currentTimeMillis()) return;
            } else { alarmManager.cancel(pendingIntent); return; }
        } else {
            if (!task.isCompleted) {
                Calendar now = Calendar.getInstance();
                Calendar alarmTime = Calendar.getInstance();
                alarmTime.set(Calendar.HOUR_OF_DAY, task.reminderHour);
                alarmTime.set(Calendar.MINUTE, task.reminderMinute);
                alarmTime.set(Calendar.SECOND, 0);
                if (alarmTime.before(now)) alarmTime.add(Calendar.DAY_OF_YEAR, 1);
                triggerTime = alarmTime.getTimeInMillis();
            } else { alarmManager.cancel(pendingIntent); return; }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                else alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException e) { e.printStackTrace(); }
    }
}