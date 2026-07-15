package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class PowerConnectedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.init(context.applicationContext)
        AppLog.append("PowerConnectedReceiver", "I", "Power connected — triggering sync")

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
        AppLog.append("PowerConnectedReceiver", "I", "One-shot sync enqueued")
    }
}
