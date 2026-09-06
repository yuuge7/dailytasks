package com.example.dailyfocus.utils;

import com.example.dailyfocus.data.Task;
import java.util.Calendar;

/**
 * Toată matematica de perioade într-un singur loc.
 *
 * O "perioadă" este intervalul în care un task zilnic poate fi completat o singură dată:
 *  - repeatDays == 1, fără mască: de la ora de reset până a doua zi la ora de reset;
 *  - repeatDays > 1: N zile de la ultimul punct de reset;
 *  - daysOfWeekMask != 0: de la ora de reset a unei zile active până la ora de reset
 *    a următoarei zile active (zilele inactive aparțin perioadei precedente).
 *
 * Istoricul este cheiat pe miezul nopții al zilei în care a ÎNCEPUT perioada,
 * astfel încât o completare la 01:00 pentru un task cu reset la 04:00 se
 * înregistrează pe ziua calendaristică corectă.
 */
public final class Periods {

    public static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private Periods() {}

    /** Miezul nopții al zilei din care face parte timestamp-ul. */
    public static long dayKey(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** bit 0 = Luni ... bit 6 = Duminică. */
    public static boolean maskHasDay(int mask, Calendar cal) {
        int dow = cal.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 ... SATURDAY=7
        int bit = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY; // Luni=0 ... Duminică=6
        return (mask & (1 << bit)) != 0;
    }

    /** Punctul de reset (resetHour:resetMinute) al zilei în care cade timestamp-ul. */
    private static Calendar resetPointOfDay(Task task, long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, task.resetHour);
        cal.set(Calendar.MINUTE, task.resetMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /**
     * Începutul perioadei curente calculat direct din "acum".
     * Pentru repeatDays > 1 nu poate reconstitui ancora — folosit doar la
     * inițializare sau după editarea programului task-ului.
     */
    public static long currentPeriodStart(Task task, long now) {
        Calendar cal = resetPointOfDay(task, now);
        if (cal.getTimeInMillis() > now) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        if (task.daysOfWeekMask != 0) {
            // Mergem înapoi până la cea mai recentă zi activă (max o săptămână)
            for (int i = 0; i < 7 && !maskHasDay(task.daysOfWeekMask, cal); i++) {
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
        }
        return cal.getTimeInMillis();
    }

    /**
     * Avansează o zi în direcția dată până când cade pe o zi activă din mască.
     * Se oprește după o săptămână întreagă: o mască validă are cel puțin o zi din 7,
     * iar una coruptă (ex. dintr-un backup editat manual) ar bloca altfel aplicația.
     */
    private static void stepToMaskDay(Task task, Calendar cal, int step) {
        for (int i = 0; i < 7; i++) {
            cal.add(Calendar.DAY_OF_YEAR, step);
            if (maskHasDay(task.daysOfWeekMask, cal)) return;
        }
    }

    /** Următorul punct de reset după periodStart. Avansează întotdeauna cel puțin o zi. */
    public static long nextPeriodStart(Task task, long periodStart) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(periodStart);
        if (task.daysOfWeekMask != 0) {
            stepToMaskDay(task, cal, 1);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, Math.max(1, task.repeatDays));
        }
        return cal.getTimeInMillis();
    }

    /** Cheia (miezul nopții) zilei programate anterioare — pentru mersul înapoi prin streak. */
    public static long previousScheduledDayKey(Task task, long dayKey) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayKey);
        if (task.daysOfWeekMask != 0) {
            stepToMaskDay(task, cal, -1);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -Math.max(1, task.repeatDays));
        }
        return cal.getTimeInMillis();
    }

    /** Se asigură că periodStart este inițializat pentru task-uri zilnice. */
    public static void ensurePeriodStart(Task task, long now) {
        if (task.isDaily && task.periodStart == 0) {
            // Pentru task-uri existente completate, ancorăm pe ziua completării ca să nu
            // pierdem o perioadă la migrare; altfel pe perioada curentă.
            long anchor = (task.isCompleted && task.lastCompletionTimestamp > 0)
                    ? task.lastCompletionTimestamp : now;
            task.periodStart = currentPeriodStart(task, anchor);
        }
    }

    /**
     * Cheia de istoric pentru starea curentă a task-ului:
     * ziua în care a început perioada curentă (task zilnic) sau ziua momentului dat.
     */
    public static long historyKey(Task task, long timestamp) {
        if (task.isDaily) {
            ensurePeriodStart(task, timestamp);
            return dayKey(task.periodStart);
        }
        return dayKey(timestamp);
    }

    /** Task-ul e programat azi? (relevant doar pentru mască de zile) */
    public static boolean isScheduledOn(Task task, long timestamp) {
        if (!task.isDaily || task.daysOfWeekMask == 0) return true;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return maskHasDay(task.daysOfWeekMask, cal);
    }
}
