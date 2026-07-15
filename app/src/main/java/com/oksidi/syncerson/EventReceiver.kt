package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class EventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.init(context.applicationContext)
        AppLog.append("EventReceiver", "I", "Received: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> onBoot(context)
            Intent.ACTION_POWER_CONNECTED -> onPowerConnected(context)
        }
    }

    private fun onBoot(context: Context) {
        AppLog.append("EventReceiver", "I", "Device booted — rescheduling sync")

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val intervalStr = prefs.getString(Constants.KEY_INTERVAL, "15") ?: "15"

        if (intervalStr == "0") {
            AppLog.append("EventReceiver", "I", "Sync disabled — skipping")
            return
        }

        val intervalMinutes = intervalStr.toLongOrNull() ?: 15L
        schedulePeriodicSync(context, intervalMinutes)
        AppLog.append("EventReceiver", "I", "Sync scheduled every ${intervalMinutes}min")
    }

    private fun onPowerConnected(context: Context) {
        AppLog.append("EventReceiver", "I", "Power connected — triggering sync")

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val intervalStr = prefs.getString(Constants.KEY_INTERVAL, "15") ?: "15"
        if (intervalStr == "0") {
            AppLog.append("EventReceiver", "I", "Sync disabled — skipping")
            return
        }

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
        AppLog.append("EventReceiver", "I", "One-shot sync enqueued")
    }
}
