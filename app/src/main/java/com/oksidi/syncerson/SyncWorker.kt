package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"

        // Change these to your home server
        private const val HOME_WIFI_SSID = "YourHomeWiFiSSID"
        private const val SERVER_URL = "http://192.168.1.100:8080/sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "SyncWorker started")

        // 1. Check if we're on home WiFi
        if (!isConnectedToHomeWifi()) {
            Log.i(TAG, "Not on home WiFi, skipping sync")
            return@withContext Result.retry()
        }

        // 2. Send data to server
        return@withContext try {
            val payload = "hello from Syncerson".toByteArray()
            sendBytes(payload)
            Log.i(TAG, "Sync successful")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private fun isConnectedToHomeWifi(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: run {
            Log.d(TAG, "No active network")
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: run {
            Log.d(TAG, "No network capabilities")
            return false
        }

        val transports = capabilities.transportInfo
        Log.d(TAG, "Network transports: $transports")
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        // Accept WiFi or Ethernet (emulator uses Ethernet)
        if (!hasWifi && !hasEthernet) {
            Log.d(TAG, "Not on WiFi or Ethernet, skipping")
            return false
        }

        // Optional: check specific SSID (requires ACCESS_FINE_LOCATION on Android 8+)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentSsid = wifiManager?.connectionInfo?.ssid?.removeSurrounding("\"")
        Log.d(TAG, "Current SSID: $currentSsid")

        // Uncomment to require a specific home SSID:
        // return currentSsid == HOME_WIFI_SSID

        return true
    }

    private fun sendBytes(data: ByteArray) {
        val url = URL(SERVER_URL)
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
        Log.d(TAG, "Server responded with: $responseCode")

        connection.disconnect()

        if (responseCode !in 200..299) {
            throw RuntimeException("Server returned HTTP $responseCode")
        }
    }
}
