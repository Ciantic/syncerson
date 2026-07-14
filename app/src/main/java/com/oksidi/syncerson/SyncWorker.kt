package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        AppLog.append(TAG, "I", "SyncWorker started")

        val prefs = applicationContext.getSharedPreferences(
            MainActivity.PREFS_NAME, Context.MODE_PRIVATE
        )
        val homeSsid = prefs.getString(MainActivity.KEY_SSID, null).orEmpty()
        val serverUrl = prefs.getString(MainActivity.KEY_SERVER_URL, null).orEmpty()

        if (serverUrl.isEmpty()) {
            AppLog.append(TAG, "W", "No server URL configured, skipping sync")
            return@withContext Result.retry()
        }

        // 1. Check if we're on home WiFi
        if (!isConnectedToHomeWifi(homeSsid)) {
            AppLog.append(TAG, "I", "Not on home WiFi, skipping sync")
            return@withContext Result.retry()
        }

        // 2. Send data to server
        return@withContext try {
            val payload = "hello from Syncerson".toByteArray()
            sendBytes(serverUrl, payload)
            AppLog.append(TAG, "I", "Sync successful — HTTP 200")
            Result.success()
        } catch (e: Exception) {
            AppLog.append(TAG, "E", "Sync failed: ${e.message}")
            Result.retry()
        }
    }

    private fun isConnectedToHomeWifi(homeSsid: String): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: run {
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: run {
            return false
        }

        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        // Accept WiFi or Ethernet (emulator uses Ethernet)
        if (!hasWifi && !hasEthernet) {
            return false
        }

        // Check specific SSID if configured
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentSsid = wifiManager?.connectionInfo?.ssid?.removeSurrounding("\"")

        if (homeSsid.isNotEmpty() && currentSsid != homeSsid) {
            AppLog.append(TAG, "D", "SSID mismatch: current=$currentSsid, required=$homeSsid")
            return false
        }

        return true
    }

    private fun sendBytes(serverUrl: String, data: ByteArray) {
        val url = URL(serverUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.run {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        connection.outputStream.use { outputStream ->
            outputStream.write(data)
            outputStream.flush()
        }

        val responseCode = connection.responseCode
        AppLog.append(TAG, "D", "Server responded with: $responseCode")

        connection.disconnect()

        if (responseCode !in 200..299) {
            throw RuntimeException("Server returned HTTP $responseCode")
        }
    }
}
