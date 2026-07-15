package com.oksidi.syncerson

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerConnectedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.init(context.applicationContext)
        AppLog.append("PowerConnectedReceiver", "I", "Power connected — triggering sync")

        enqueueSyncWorker(context)
        AppLog.append("PowerConnectedReceiver", "I", "One-shot sync enqueued")
    }
}
