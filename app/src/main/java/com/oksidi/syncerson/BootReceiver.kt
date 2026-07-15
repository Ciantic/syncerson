package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.init(context.applicationContext)
        AppLog.append("BootReceiver", "I", "Device booted — rescheduling sync")

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val intervalMinutes = prefs.getString(Constants.KEY_INTERVAL, "15")?.toLongOrNull()
            ?.takeIf { it > 0 } ?: 15L

        schedulePeriodicSync(context, intervalMinutes)
        AppLog.append("BootReceiver", "I", "Sync scheduled every ${intervalMinutes}min")
    }
}
