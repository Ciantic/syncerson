package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Device booted — rescheduling sync")

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val intervalStr = prefs.getString(MainActivity.KEY_INTERVAL, "15") ?: "15"

        if (intervalStr == "0") {
            Log.i("BootReceiver", "Sync disabled — skipping")
            return
        }

        val intervalMinutes = intervalStr.toLongOrNull() ?: 15L

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MainActivity.SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )

        Log.i("BootReceiver", "Sync scheduled every ${intervalMinutes}min")
    }
}
