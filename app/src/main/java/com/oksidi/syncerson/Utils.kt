package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: run {
        AppLog.append(tag, "W", "No active network — cannot detect SSID")
        return null
    }

    // Primary: modern transportInfo (works on real WiFi devices)
    val wifiInfo = caps.transportInfo as? WifiInfo
    if (wifiInfo != null) {
        val ssid = wifiInfo.ssid?.removeSurrounding("\"")
        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") return ssid
    }

    // Fallback: WifiManager.connectionInfo (works on emulator, which uses Ethernet transport)
    @Suppress("DEPRECATION")
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
        val wm = context.applicationContext.getSystemService(
            Context.WIFI_SERVICE) as WifiManager
        val ssid = wm.connectionInfo.ssid?.removeSurrounding("\"")
        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") return ssid
    }

    AppLog.append(tag, "W", "Not connected to WiFi — cannot detect SSID")
    return null
}

fun schedulePeriodicSync(context: Context, intervalMinutes: Long) {
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