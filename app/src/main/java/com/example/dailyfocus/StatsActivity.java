package com.example.dailyfocus;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        db = AppDatabase.getInstance(this);

        TextView txtTotalCompletions = findViewById(R.id.txtTotalCompletions);
        TextView txtActiveTasks = findViewById(R.id.txtActiveTasks);
        LinearLayout layoutStreaks = findViewById(R.id.layoutStreaks);
        TextView txtToday = findViewById(R.id.txtToday);
        TextView txtYesterday = findViewById(R.id.txtYesterday);

        // Elementul nou adăugat în design
        TextView txtThisMonth = findViewById(R.id.txtThisMonth);

        // 1. Preluăm datele din baza de date
        List<Task> allTasks = db.taskDao().getAllTasks();
        List<TaskHistory> allHistory = db.taskDao().getAllHistory();

        // 2. Imagine de Ansamblu (Cifre totale)
        int totalCompletions = allHistory.size();
        int activeTasks = allTasks.size();

        txtTotalCompletions.setText("Total task-uri finalizate vreodată: " + totalCompletions);
        txtActiveTasks.setText("Task-uri urmărite în prezent: " + activeTasks);

        // 3. Top Serii de Foc (Streaks)
        List<Task> streakTasks = new ArrayList<>();
        for (Task t : allTasks) {
            if ((t.isDaily || t.isCooldown24h) && t.currentStreak > 0) {
                streakTasks.add(t);
            }
        }

        Collections.sort(streakTasks, (t1, t2) -> Integer.compare(t2.currentStreak, t1.currentStreak));

        layoutStreaks.removeAllViews();
        if (streakTasks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nu ai nicio serie activă momentan. Menține-te constant pentru a construi serii!");
            empty.setTextSize(14);
            empty.setTextColor(0xFF757575);
            layoutStreaks.addView(empty);
        } else {
            int limit = Math.min(streakTasks.size(), 5);
            for (int i = 0; i < limit; i++) {
                Task t = streakTasks.get(i);
                TextView tv = new TextView(this);
                tv.setText("🔥 " + t.currentStreak + " zile - " + t.title);
                tv.setTextSize(16);
                tv.setPadding(0, 8, 0, 8);
                layoutStreaks.addView(tv);
            }
        }

        // 4. Activitate Recentă (Astăzi vs Ieri vs Luna aceasta)
        Calendar cal = Calendar.getInstance();

        // Salvăm luna și anul curent pentru comparația lunară
        int currentMonth = cal.get(Calendar.MONTH);
        int currentYear = cal.get(Calendar.YEAR);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterdayMidnight = todayMidnight - (24 * 60 * 60 * 1000L);

        int todayCount = 0;
        int yesterdayCount = 0;
        int thisMonthCount = 0; // Contorul nou

        for (TaskHistory h : allHistory) {
            // Verificare pentru Azi / Ieri
            if (h.dateTimestamp == todayMidnight) {
                todayCount++;
            } else if (h.dateTimestamp == yesterdayMidnight) {
                yesterdayCount++;
            }

            // Verificare pentru Luna Aceasta
            Calendar historyCal = Calendar.getInstance();
            historyCal.setTimeInMillis(h.dateTimestamp);
            if (historyCal.get(Calendar.MONTH) == currentMonth && historyCal.get(Calendar.YEAR) == currentYear) {
                thisMonthCount++;
            }
        }

        txtToday.setText("Azi: " + todayCount + " task-uri finalizate");
        txtYesterday.setText("Ieri: " + yesterdayCount + " task-uri finalizate");
        txtThisMonth.setText("Luna aceasta: " + thisMonthCount + " task-uri finalizate");
    }
}