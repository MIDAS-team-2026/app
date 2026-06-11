package com.midas26.mobileapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showReminder(context)

        val prefs = com.midas26.mobileapp.util.PrefsManager.from(context)
        if (prefs.getNotificationEnabled()) {
            AlarmScheduler.schedule(context, prefs.getNotificationHour(), prefs.getNotificationMinute())
        }
    }
}
