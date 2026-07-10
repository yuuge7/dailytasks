package com.example.dailyfocus.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.dailyfocus.data.TaskRepository;
import com.example.dailyfocus.utils.TaskHelper;

/**
 * Alarmele nu supraviețuiesc restartului telefonului — le reprogramăm la boot,
 * altfel reminderele mor până la prima deschidere a aplicației.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();
        TaskRepository.execute(() -> {
            try {
                TaskHelper.checkAndResetTasks(context);
                TaskHelper.rescheduleAllReminders(context);
                WidgetUpdateReceiver.scheduleNextAlarm(context);
                TaskHelper.updateWidget(context);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
