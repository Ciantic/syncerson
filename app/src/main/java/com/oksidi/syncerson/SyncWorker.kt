package com.oksidi.syncerson

import android.content.Context
import android.content.pm.PackageManager
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
        val lanIpSuffix = prefs.getString(Constants.KEY_LAN_IP_SUFFIX, null).orEmpty()
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null).orEmpty()

        if (serverUrl.isEmpty()) {
            AppLog.append(TAG, "W", "No server URL configured, skipping sync")
            return@withContext Result.failure()
        }

        // 1. Check if we're on home WiFi (SSID match OR LAN IP-suffix match)
        if (!isConnectedToKnownNetwork(homeSsid, lanIpSuffix)) {
            AppLog.append(TAG, "I", "Not on home WiFi, skipping sync")
            return@withContext Result.failure()
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
            // TODO: Consider retrying? And change also enqueueSyncWorker to try only once more.
            Result.failure()
        }
    }

    private fun isConnectedToKnownNetwork(homeSsid: String, lanIpSuffix: String): Boolean {
        // No restrictions configured — allow all networks
        if (homeSsid.isEmpty() && lanIpSuffix.isEmpty()) return true

        // Check SSID match
        if (homeSsid.isNotEmpty()) {
            val currentSsid = getCurrentSsid(TAG, applicationContext)
            if (currentSsid == homeSsid) {
                AppLog.append(TAG, "D", "SSID match: $currentSsid")
                return true
            }
        }

        // Check LAN IP-suffix match
        if (lanIpSuffix.isNotEmpty()) {
            val ip = getDeviceIpAddress()
            AppLog.append(TAG, "D", "Device IP: $ip, checking prefix: $lanIpSuffix")
            if (ip != null && ip.startsWith(lanIpSuffix)) {
                AppLog.append(TAG, "D", "LAN IP-suffix match: $ip starts with $lanIpSuffix")
                return true
            }
        }

        AppLog.append(TAG, "D", "Neither SSID nor LAN IP-suffix matched")
        return false
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
