package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun getCurrentSsid(tag: String, context: Context): String? {
    val cm = context.getSystemService(
        Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
    // Primary: ConnectivityManager.activeNetwork + transportInfo (non-deprecated, API 33+)
    cm.activeNetwork?.let { network ->
        cm.getNetworkCapabilities(network)?.let { caps ->
            val info = caps.transportInfo as? WifiInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") return ssid
        }
    }

    // Fallback: WifiManager.connectionInfo. Handles devices where activeNetwork is
    // null or transportInfo is not populated despite WiFi being connected.
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Suppress("DEPRECATION")
    val wmSsid = wm.connectionInfo.ssid?.removeSurrounding("\"")
    if (!wmSsid.isNullOrEmpty() && wmSsid != "<unknown ssid>") return wmSsid

    AppLog.append(tag, "W", "Not connected to WiFi — cannot detect SSID")
    return null
}

fun schedulePeriodicSync(context: Context, intervalMinutes: Long) {
    if (intervalMinutes <= 0) {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.SYNC_WORK_NAME)
        AppLog.append("Sync", "I", "Periodic sync off")
        return
    }

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .build()

    val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        Constants.SYNC_WORK_NAME,
        ExistingPeriodicWorkPolicy.REPLACE,
        syncRequest
    )
}