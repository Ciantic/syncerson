package com.oksidi.syncerson

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
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
        AppLog.init(applicationContext)
        AppLog.append(TAG, "I", "SyncWorker started")

        val prefs = applicationContext.getSharedPreferences(
            Constants.PREFS_NAME, Context.MODE_PRIVATE
        )
        val homeSsid = prefs.getString(Constants.KEY_SSID, null).orEmpty()
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null).orEmpty()

        if (serverUrl.isEmpty()) {
            AppLog.append(TAG, "W", "No server URL configured, skipping sync")
            return@withContext Result.retry()
        }

        // 1. Check if we're on home WiFi
        if (!isConnectedToWifi(homeSsid)) {
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

    private fun isConnectedToWifi(homeSsid: String): Boolean {
        if (homeSsid.isEmpty()) return true

        val currentSsid = getCurrentSsid(TAG, applicationContext)
        if (currentSsid != homeSsid) {
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
