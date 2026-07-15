package com.oksidi.syncerson

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.provider.MediaStore
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
            val photoCount = getPhotoCount()
            val latestTimestamp = getLatestPhotoTimestamp()
            var url = appendQueryParam(serverUrl, "photos", photoCount.toString())
            url = appendQueryParam(url, "latest", latestTimestamp.toString())
            val payload = "hello from Syncerson".toByteArray()
            sendBytes(url, payload)
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

    private fun appendQueryParam(url: String, key: String, value: String): String {
        val separator = if (url.contains('?')) "&" else "?"
        return "$url$separator$key=$value"
    }

    private fun getPhotoCount(): Int {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) return 0

        return try {
            applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, null
            )?.use { cursor -> cursor.count } ?: 0
        } catch (e: Exception) {
            AppLog.append(TAG, "W", "Cannot count photos: ${e.message}")
            0
        }
    }

    private fun getLatestPhotoTimestamp(): Long {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) return 0L

        return try {
            val bundle = android.os.Bundle().apply {
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                putInt(
                    android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putStringArray(
                    android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                )
            }
            applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATE_MODIFIED),
                bundle, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            AppLog.append(TAG, "W", "Cannot get latest timestamp: ${e.message}")
            0L
        }
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
