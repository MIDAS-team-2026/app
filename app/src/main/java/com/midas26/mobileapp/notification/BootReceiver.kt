package com.midas26.mobileapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.midas26.mobileapp.util.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PrefsManager.from(context)
        if (prefs.getNotificationEnabled()) {
            AlarmScheduler.schedule(context, prefs.getNotificationHour(), prefs.getNotificationMinute())
        }
    }
}
