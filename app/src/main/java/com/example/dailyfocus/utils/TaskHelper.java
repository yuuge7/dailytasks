package com.example.dailyfocus.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.dailyfocus.R;
import com.example.dailyfocus.data.AppDatabase;
import com.example.dailyfocus.data.StreakFreeze;
import com.example.dailyfocus.data.Subtask;
import com.example.dailyfocus.data.Task;
import com.example.dailyfocus.data.TaskHistory;
import com.example.dailyfocus.receiver.NotificationReceiver;
import com.example.dailyfocus.widget.TaskWidgetProvider;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Singura sursă de adevăr pentru resetări, completări și streak-uri.
 * Toate metodele care ating baza de date trebuie apelate de pe un thread de background
 * (vezi TaskRepository).
 */
public class TaskHelper {

    /**
     * Verifică și resetează task-urile expirate. Întoarce true dacă s-a schimbat ceva.
     * Reprogramează notificările task-urilor resetate.
     */
    public static boolean checkAndResetTasks(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<Task> tasks = db.taskDao().getAllTasks();
        long now = System.currentTimeMillis();
        boolean anyChanged = false;

        for (Task task : tasks) {
            boolean changed = false;

            if (task.isDaily) {
                changed = rollDailyPeriod(db, task, now);
            } else if (task.isCooldown24h) {
                long unlockTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                if (task.isCompleted && now >= unlockTime) {
                    task.isCompleted = false;
                    resetSubtasks(task);
                    changed = true;
                }
            }

            if (changed) {
                db.taskDao().update(task);
                scheduleTaskNotification(context, task);
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    private static void resetSubtasks(Task task) {
        if (task.subtasks != null) {
            for (Subtask s : task.subtasks) s.isCompleted = false;
        }
    }

    /**
     * Avansează perioada task-ului zilnic până la cea curentă, aplicând regulile de
     * streak. Folosit atât de verificarea periodică cât și de completare, ca o bifare
     * imediat după miezul perioadei (înainte să ruleze alarma de reset) să nu fie
     * înregistrată pe perioada veche.
     *
     * Dacă task-ul e îngheţat, perioadele ratate se salvează ca StreakFreeze în loc să
     * rupă seria — astfel recalculul din istoric ajunge la același rezultat.
     */
    private static boolean rollDailyPeriod(AppDatabase db, Task task, long now) {
        boolean hadPeriodStart = task.periodStart != 0;
        Periods.ensurePeriodStart(task, now);
        boolean changed = !hadPeriodStart;

        long next = Periods.nextPeriodStart(task, task.periodStart);
        while (now >= next) {
            boolean completedInClosingPeriod = task.isCompleted;
            if (task.isCompleted) {
                task.isCompleted = false;
                resetSubtasks(task);
            }
            if (!completedInClosingPeriod) {
                long closingDay = Periods.dayKey(task.periodStart);
                // Îngheţul acoperă doar perioade încă deschise când a fost activat,
                // ca să nu salveze retroactiv zile deja pierdute.
                if (task.isFrozen && next > task.frozenSince) {
                    db.taskDao().insertFreeze(new StreakFreeze(task.id, closingDay));
                } else if (db.taskDao().countFreeze(task.id, closingDay) == 0) {
                    // Perioadă închisă fără completare și fără îngheţ -> streak pierdut.
                    // Verificăm în DB, nu doar flagul: ziua poate fi deja acoperită de un
                    // îngheţ salvat la dezgheţare sau adus dintr-un backup.
                    task.currentStreak = 0;
                }
            }
            task.periodStart = next;
            next = Periods.nextPeriodStart(task, task.periodStart);
            changed = true;
        }
        return changed;
    }

    /**
     * Îngheață / dezgheață seria unui task zilnic. Cât timp e îngheţat, zilele ratate
     * nu rup lanțul și reminderele sunt oprite; bifările cresc seria ca de obicei.
     */
    public static void setFrozen(Context context, Task task, boolean frozen) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();

        // Închidem perioadele expirate cu starea de ÎNAINTE de schimbare: înghețarea nu
        // salvează retroactiv zile deja pierdute, iar dezghețarea nu pierde zilele
        // acoperite cât timp îngheţul era activ.
        if (task.isDaily) rollDailyPeriod(db, task, now);

        // Dezghețarea nu retrage protecția perioadei în care s-a activat îngheţul:
        // altfel un îngheţ pornit și oprit în aceeași zi n-ar salva nimic, iar ziua
        // s-ar pierde la următoarea închidere de perioadă.
        // Condiția periodStart <= frozenSince distinge cele două cazuri:
        //  - îngheţ pornit în perioada curentă  -> ziua a fost protejată intenționat;
        //  - perioadă începută deja sub îngheţ  -> dezghețarea înseamnă "reiau azi",
        //    deci ziua se bifează normal, fără zi gratis.
        if (!frozen && task.isDaily && task.isFrozen && !task.isCompleted
                && task.frozenSince > 0 && task.periodStart <= task.frozenSince) {
            db.taskDao().insertFreeze(new StreakFreeze(task.id, Periods.dayKey(task.periodStart)));
        }

        task.isFrozen = frozen;
        task.frozenSince = frozen ? now : 0;

        db.taskDao().update(task);
        scheduleTaskNotification(context, task);
    }

    /**
     * Resetează seria: completările dinaintea perioadei curente nu mai intră în streak.
     * Istoricul și heatmap-ul rămân neatinse — se mută doar punctul de start al lanțului.
     * Dacă perioada curentă e deja bifată, seria repornește de la 1 (nu de la 0), ca
     * valoarea afișată să fie aceeași și după un recalcul din istoric.
     *
     * @param alsoResetBest true = recordul all-time repornește și el de la seria nouă.
     */
    public static void resetStreak(Context context, Task task, boolean alsoResetBest) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();

        if (task.isDaily) {
            rollDailyPeriod(db, task, now);
            Periods.ensurePeriodStart(task, now);
            task.streakResetAt = Periods.dayKey(task.periodStart);
        } else {
            task.streakResetAt = Periods.dayKey(now);
        }

        // Înghețurile de dinainte de reset nu mai pot lega nimic
        db.taskDao().deleteFreezesBefore(task.id, task.streakResetAt);

        int[] streaks = recomputeStreaks(task,
                db.taskDao().getHistoryForTask(task.id),
                db.taskDao().getFreezesForTask(task.id), now);
        task.currentStreak = streaks[0];
        task.bestStreak = alsoResetBest ? streaks[1] : Math.max(task.bestStreak, streaks[1]);

        db.taskDao().update(task);
        scheduleTaskNotification(context, task);
    }

    /**
     * Marchează task-ul ca terminat. Incrementează streak-ul o singură dată per perioadă
     * (indexul unic pe istoric garantează asta chiar și la bifare-debifare repetată).
     */
    public static void completeTask(Context context, Task task) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();

        // Aliniem perioada înainte de a înregistra completarea
        if (task.isDaily) rollDailyPeriod(db, task, now);

        task.isCompleted = true;
        task.lastCompletionTimestamp = now;
        if (task.subtasks != null) {
            for (Subtask s : task.subtasks) s.isCompleted = true;
        }

        if (task.isDaily || task.isCooldown24h) {
            long key = Periods.historyKey(task, now);
            long rowId = db.taskDao().insertHistory(new TaskHistory(task.id, task.title, key));
            if (rowId != -1) {
                // Perioada nu era încă înregistrată -> streak crește
                task.currentStreak++;
                if (task.currentStreak > task.bestStreak) task.bestStreak = task.currentStreak;
            }
        }

        db.taskDao().update(task);
        scheduleTaskNotification(context, task);
    }

    /**
     * Debifează task-ul. Decrementează streak-ul doar dacă exista o completare
     * înregistrată pentru perioada curentă (altfel debifarea după un reset ar fura un streak).
     */
    public static void uncompleteTask(Context context, Task task) {
        AppDatabase db = AppDatabase.getInstance(context);

        if (task.isDaily || task.isCooldown24h) {
            // Cheia perioadei în care s-a făcut completarea — calculată ÎNAINTE de a
            // modifica timestamp-ul, altfel ștergem ziua greșită din istoric.
            long key = task.isDaily
                    ? Periods.historyKey(task, task.lastCompletionTimestamp)
                    : Periods.dayKey(task.lastCompletionTimestamp);
            int deleted = db.taskDao().deleteHistory(task.id, key);
            if (deleted > 0 && task.currentStreak > 0) {
                task.currentStreak--;
            }
        }

        task.isCompleted = false;
        resetSubtasks(task);
        db.taskDao().update(task);
        scheduleTaskNotification(context, task);
    }

    public static void toggleTask(Context context, Task task) {
        if (task.isCompleted) uncompleteTask(context, task);
        else completeTask(context, task);
    }

    /**
     * Ziua pe care butonul "Restaurează Streak-ul" trebuie să o completeze: prima zi
     * programată neacoperită, mergând înapoi din perioada curentă.
     *
     * Zilele îngheţate leagă deja lanțul, deci nu ele l-au rupt — le sărim. Altfel
     * restaurarea s-ar consuma pe o zi deja acoperită, gaura reală ar rămâne deschisă
     * și seria nu s-ar mai reconecta niciodată.
     *
     * Peste zilele completate NU trecem: dacă prima zi neîngheţată din urmă e bifată,
     * lanțul e întreg și nu există nimic de restaurat (asta ține restaurarea la o
     * singură zi de grație, nu la reconstruirea întregului istoric).
     *
     * @return cheia zilei de completat, sau 0 dacă nu e nimic de restaurat.
     */
    public static long restorableDayKey(Task task, List<TaskHistory> history,
                                        List<StreakFreeze> freezes, long now) {
        Set<Long> doneDays = new HashSet<>();
        if (history != null) {
            for (TaskHistory h : history) doneDays.add(h.dateTimestamp);
        }

        if (!task.isDaily) {
            // Cooldown: seria e numărul total de completări, deci ziua lipsă e pur și simplu ieri.
            // Scădem o zi calendaristică, nu 24h fixe — la trecerea la ora de vară/iarnă
            // ziua are 23 sau 25 de ore și cheia nu ar mai pica pe miezul nopții.
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(Periods.dayKey(now));
            cal.add(Calendar.DAY_OF_YEAR, -1);
            long yesterday = cal.getTimeInMillis();
            return doneDays.contains(yesterday) ? 0 : yesterday;
        }

        Set<Long> frozenDays = new HashSet<>();
        if (freezes != null) {
            for (StreakFreeze f : freezes) frozenDays.add(f.dayKey);
        }

        Periods.ensurePeriodStart(task, now);
        long cutoff = task.streakResetAt > 0 ? Periods.dayKey(task.streakResetAt) : Long.MIN_VALUE;

        // Perioada curentă e încă deschisă — se bifează normal, nu se restaurează.
        long cursor = Periods.previousScheduledDayKey(task, Periods.dayKey(task.periodStart));

        // Fiecare pas consumă o zi îngheţată distinctă, deci bucla se oprește garantat.
        for (int i = 0; i <= frozenDays.size() && cursor >= cutoff; i++) {
            if (!frozenDays.contains(cursor)) {
                return doneDays.contains(cursor) ? 0 : cursor;
            }
            cursor = Periods.previousScheduledDayKey(task, cursor);
        }
        return 0;
    }

    /**
     * Recalculează streak-ul curent și cel mai bun streak din istoric.
     * Folosit la restaurarea din backup, la butonul "Restore Streak" și la resetare.
     * Întoarce {streakCurent, streakMaxim}.
     *
     * Zilele îngheţate leagă lanțul dar NU se numără — o serie înghețată își păstrează
     * valoarea, nu crește. Completările dinaintea lui {@code task.streakResetAt} sunt
     * ignorate (resetare manuală), dar rămân în istoric și în heatmap.
     *
     * Pentru task-uri cooldown păstrăm semantica existentă: streak-ul este numărul
     * total de completări (nu se pierde la pauze).
     */
    public static int[] recomputeStreaks(Task task, List<TaskHistory> historyAsc,
                                         List<StreakFreeze> freezes, long now) {
        // Reset manual: tot ce e mai vechi decât ziua resetării nu mai intră în lanț
        long cutoff = task.streakResetAt > 0 ? Periods.dayKey(task.streakResetAt) : Long.MIN_VALUE;

        Set<Long> keys = new HashSet<>();
        if (historyAsc != null) {
            for (TaskHistory h : historyAsc) {
                if (h.dateTimestamp >= cutoff) keys.add(h.dateTimestamp);
            }
        }

        if (task.isCooldown24h && !task.isDaily) {
            int n = keys.size();
            return new int[]{n, n};
        }

        if (keys.isEmpty()) {
            return new int[]{0, 0};
        }

        Set<Long> frozen = new HashSet<>();
        if (freezes != null) {
            for (StreakFreeze f : freezes) {
                if (f.dayKey >= cutoff) frozen.add(f.dayKey);
            }
        }
        // Lanțul = zile completate + zile acoperite de îngheţ
        Set<Long> chain = new HashSet<>(keys);
        chain.addAll(frozen);

        Periods.ensurePeriodStart(task, now);
        long currentKey = Periods.dayKey(task.periodStart);

        // Streak curent: pornim de la perioada curentă; dacă azi nu e încă bifat,
        // streak-ul rămâne viu dacă perioada anterioară e completată sau înghețată.
        int current = 0;
        long cursor = currentKey;
        if (!chain.contains(cursor)) {
            cursor = Periods.previousScheduledDayKey(task, cursor);
        }
        while (cursor >= cutoff && chain.contains(cursor)) {
            if (keys.contains(cursor)) current++;
            cursor = Periods.previousScheduledDayKey(task, cursor);
        }

        // Cel mai bun streak: cea mai lungă secvență de zile programate consecutive
        int best = 0;
        Set<Long> visited = new HashSet<>();
        for (long key : chain) {
            if (visited.contains(key)) continue;
            // Ne întoarcem la începutul secvenței din care face parte ziua
            long start = key;
            while (chain.contains(Periods.previousScheduledDayKey(task, start))) {
                start = Periods.previousScheduledDayKey(task, start);
            }
            int run = 0;
            long c = start;
            while (chain.contains(c)) {
                visited.add(c);
                if (keys.contains(c)) run++;
                c = nextScheduledDayKey(task, c);
            }
            if (run > best) best = run;
        }

        return new int[]{current, Math.max(best, current)};
    }

    private static long nextScheduledDayKey(Task task, long dayKey) {
        return Periods.nextPeriodStart(task, dayKey);
    }

    /**
     * Recalculează și salvează seria din istoric pentru un singur task.
     *
     * De apelat după o schimbare de program (ritm, zile active, oră de reset):
     * cheile din istoric au fost scrise pe vechiul ritm, deci lanțul se poate rupe.
     * Fără asta numărul afișat rămâne cel vechi și se prăbușește abia mai târziu,
     * la prima restaurare / resetare / import, fără nicio explicație pentru
     * utilizator.
     */
    public static void refreshStreakFromHistory(Context context, Task task) {
        if (!(task.isDaily || task.isCooldown24h)) return;
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();
        int[] streaks = recomputeStreaks(task,
                db.taskDao().getHistoryForTask(task.id),
                db.taskDao().getFreezesForTask(task.id), now);
        task.currentStreak = streaks[0];
        task.bestStreak = Math.max(task.bestStreak, streaks[1]);
        db.taskDao().update(task);
    }

    /**
     * Rulat după importul unui backup: reconstruiește periodStart și streak-urile
     * din istoric, ca seria să revină la valoarea reală, nu la 1.
     */
    public static void recomputeAllFromHistory(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();
        for (Task task : db.taskDao().getAllTasks()) {
            if (!(task.isDaily || task.isCooldown24h)) continue;
            task.periodStart = 0;
            Periods.ensurePeriodStart(task, now);
            List<TaskHistory> history = db.taskDao().getHistoryForTask(task.id);
            List<StreakFreeze> freezes = db.taskDao().getFreezesForTask(task.id);
            int[] streaks = recomputeStreaks(task, history, freezes, now);
            task.currentStreak = streaks[0];
            task.bestStreak = Math.max(task.bestStreak, streaks[1]);
            db.taskDao().update(task);
        }
    }

    // --- NOTIFICĂRI ---

    /** Programează (sau anulează) alarma de reminder pentru un task. */
    public static void scheduleTaskNotification(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra(NotificationReceiver.EXTRA_TASK_ID, task.id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, task.id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Serie înghețată = pauză: nu mai insistăm cu remindere
        if (!task.hasReminder || task.isFrozen) {
            alarmManager.cancel(pendingIntent);
            return;
        }

        long triggerTime;
        if (task.isCooldown24h) {
            if (task.isCompleted) {
                triggerTime = task.lastCompletionTimestamp + ((long) task.cooldownHours * 60 * 60 * 1000L);
                if (triggerTime < System.currentTimeMillis()) return;
            } else {
                alarmManager.cancel(pendingIntent);
                return;
            }
        } else {
            if (task.isCompleted) {
                alarmManager.cancel(pendingIntent);
                return;
            }
            Calendar now = Calendar.getInstance();
            Calendar alarmTime = Calendar.getInstance();
            alarmTime.set(Calendar.HOUR_OF_DAY, task.reminderHour);
            alarmTime.set(Calendar.MINUTE, task.reminderMinute);
            alarmTime.set(Calendar.SECOND, 0);
            alarmTime.set(Calendar.MILLISECOND, 0);
            if (!alarmTime.after(now)) alarmTime.add(Calendar.DAY_OF_YEAR, 1);
            // Cu mască de zile, sărim peste zilele în care task-ul nu e programat
            if (task.isDaily && task.daysOfWeekMask != 0) {
                for (int i = 0; i < 7 && !Periods.maskHasDay(task.daysOfWeekMask, alarmTime); i++) {
                    alarmTime.add(Calendar.DAY_OF_YEAR, 1);
                }
            } else if (task.isDaily && task.repeatDays > 1) {
                // La "o dată la N zile" reminderul sună doar în ziua în care începe o
                // perioadă nouă — altfel un task la 3 zile ar suna în fiecare zi.
                alarmTime = periodStartReminder(task, now, alarmTime);
            }
            triggerTime = alarmTime.getTimeInMillis();
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Primul moment de reminder care cade în ziua de început a unei perioade, cel
     * puțin la {@code earliest} și strict în viitor. Folosit pentru task-urile
     * "o dată la N zile", unde zilele din interiorul perioadei nu merită notificare.
     */
    private static Calendar periodStartReminder(Task task, Calendar now, Calendar earliest) {
        Periods.ensurePeriodStart(task, now.getTimeInMillis());

        long periodStart = task.periodStart;
        long earliestDay = Periods.dayKey(earliest.getTimeInMillis());
        while (Periods.dayKey(periodStart) < earliestDay) {
            periodStart = Periods.nextPeriodStart(task, periodStart);
        }

        Calendar candidate = reminderTimeOn(task, periodStart);
        if (!candidate.after(now)) {
            candidate = reminderTimeOn(task, Periods.nextPeriodStart(task, periodStart));
        }
        return candidate;
    }

    /** Ora de reminder în ziua în care începe perioada dată. */
    private static Calendar reminderTimeOn(Task task, long periodStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Periods.dayKey(periodStart));
        cal.set(Calendar.HOUR_OF_DAY, task.reminderHour);
        cal.set(Calendar.MINUTE, task.reminderMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /** Reprogramează toate reminderele (după boot sau import). */
    public static void rescheduleAllReminders(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        for (Task task : db.taskDao().getAllTasks()) {
            if (task.hasReminder) scheduleTaskNotification(context, task);
        }
    }

    // --- WIDGET ---

    public static void updateWidget(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, TaskWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            // Invalidează cache-ul listei și o obligă să reîncarce elementele din DB
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView);

            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
