package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.net.Inet4Address
import java.net.NetworkInterface
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

/**
 * Returns the device's Wi-Fi IPv4 address as a string (e.g. "192.168.8.100"),
 * or null if not connected to Wi-Fi.
 */
fun getDeviceIpAddress(): String? {
    try {
        // Prefer WifiManager.connectionInfo (simpler, only returns Wi-Fi IP)
        // Iterate all interfaces as reliable fallback
        val candidates = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        for (intf in candidates) {
            if (intf.isLoopback || !intf.isUp) continue
            for (addr in intf.inetAddresses.toList()) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    // Prioritize wlan interfaces
                    val name = intf.name.lowercase()
                    if (name.startsWith("wlan")) return addr.hostAddress
                }
            }
        }
        // Fallback: return first non-loopback IPv4 from any interface
        for (intf in candidates) {
            if (intf.isLoopback || !intf.isUp) continue
            for (addr in intf.inetAddresses.toList()) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
    } catch (_: Exception) { }
    return null
}

fun enqueueSyncWorker(context: Context) {
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val restrictMode = RestrictMode.fromPrefKey(prefs.getString(Constants.KEY_RESTRICT_MODE, null))

    val requestBuilder = OneTimeWorkRequestBuilder<SyncWorker>()

    when (restrictMode) {
        RestrictMode.NONE -> { /* no constraint */ }
        RestrictMode.WIFI, RestrictMode.SSID, RestrictMode.IP_SUFFIX -> 
        {
            requestBuilder.setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
        }
    }

    WorkManager.getInstance(context).enqueueUniqueWork(
        Constants.SYNC_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        requestBuilder.build()
    )
}

fun schedulePeriodicSync(context: Context, intervalMinutes: Long) {
    if (intervalMinutes <= 0) {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.PERIODIC_WORK_NAME)
        AppLog.append("Sync", "I", "Periodic sync off")
        return
    } else {
        AppLog.append("Sync", "I", "Sync scheduled every ${intervalMinutes}min")
    }

    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val restrictMode = RestrictMode.fromPrefKey(prefs.getString(Constants.KEY_RESTRICT_MODE, null))

    val builder = PeriodicWorkRequestBuilder<PeriodicWorker>(intervalMinutes, TimeUnit.MINUTES)

    when (restrictMode) {
        RestrictMode.NONE -> { /* no constraint */ }
        RestrictMode.WIFI, RestrictMode.SSID, RestrictMode.IP_SUFFIX ->
        {
            builder.setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
        }
    }

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        Constants.PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.REPLACE,
        builder.build()
    )
}