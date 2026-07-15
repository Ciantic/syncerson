package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("TestReceiver", "Triggering SyncWorker via broadcast")
        enqueueSyncWorker(context)
    }
}
