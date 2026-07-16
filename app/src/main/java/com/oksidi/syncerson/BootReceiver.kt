package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.init(context.applicationContext)
        AppLog.append("BootReceiver", "I", "Device booted — running one-shot sync")

        enqueueSyncWorker(context)
        AppLog.append("BootReceiver", "I", "One-shot sync enqueued")
    }
}
