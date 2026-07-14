package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("TestReceiver", "Triggering SyncWorker via broadcast")
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
