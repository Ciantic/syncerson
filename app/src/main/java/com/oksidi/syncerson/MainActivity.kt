package com.oksidi.syncerson

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val SYNC_WORK_NAME = "home_wifi_sync"
        const val PREFS_NAME = "sync_prefs"
        const val KEY_SSID = "home_wifi_ssid"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_PERMISSION_REQUESTED = "perm_requested"
        private const val PERMISSION_REQUEST_LOCATION = 1
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var ssidInput: EditText
    private lateinit var detectButton: Button
    private lateinit var permissionStatus: TextView
    private lateinit var grantPermissionButton: Button
    private lateinit var logOutput: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var syncStatusText: TextView
    private var syncScheduled = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshUiRunnable = object : Runnable {
        override fun run() {
            logOutput.text = AppLog.getText()
            logScrollView.post { logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            updateSyncStatusDisplay()
            updatePermissionStatus()
            refreshHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        ssidInput = findViewById(R.id.ssidInput)
        permissionStatus = findViewById(R.id.permissionStatus)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        logOutput = findViewById(R.id.logOutput)
        logScrollView = findViewById(R.id.logScrollView)
        syncStatusText = findViewById(R.id.syncStatus)
        val serverUrlInput = findViewById<EditText>(R.id.serverUrlInput)
        detectButton = findViewById(R.id.detectButton)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val syncNowButton = findViewById<Button>(R.id.syncNowButton)
        val copyLogButton = findViewById<TextView>(R.id.copyLogButton)
        val intervalSpinner = findViewById<Spinner>(R.id.intervalSpinner)

        // Interval spinner
        val intervalOptions = resources.getStringArray(R.array.repeat_intervals)
        val intervalValues = resources.getStringArray(R.array.repeat_interval_values)
        intervalSpinner.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, intervalOptions)

        // Restore saved interval selection
        val savedInterval = prefs.getString(KEY_INTERVAL, "15") ?: "15"
        val savedIndex = intervalValues.indexOf(savedInterval)
        if (savedIndex >= 0) intervalSpinner.setSelection(savedIndex)

        // Load saved values
        ssidInput.setText(prefs.getString(KEY_SSID, ""))
        serverUrlInput.setText(
            prefs.getString(KEY_SERVER_URL, null)?.ifEmpty { null }
                ?: "http://192.168.8.200:8080/sync"
        )

        detectButton.setOnClickListener {
            onDetectClicked()
        }

        grantPermissionButton.setOnClickListener {
            requestLocationPermission()
        }

        saveButton.setOnClickListener {
            val ssid = ssidInput.text.toString().trim()
            val serverUrl = serverUrlInput.text.toString().trim()
            val selectedIntervalIdx = intervalSpinner.selectedItemPosition
            val interval = intervalValues[selectedIntervalIdx]

            prefs.edit()
                .putString(KEY_SSID, ssid)
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_INTERVAL, interval)
                .apply()

            if (interval == "0") {
                WorkManager.getInstance(this).cancelAllWork()
                syncScheduled = false
                AppLog.append(TAG, "I", "Sync disabled (Disabled selected)")
            } else {
                scheduleSyncWork(interval.toLong())
            }

            Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
            AppLog.append(TAG, "I", "Settings saved: SSID=$ssid, URL=$serverUrl")
        }

        syncNowButton.setOnClickListener {
            AppLog.append(TAG, "I", "Manual sync triggered by user")
            runNow()
        }

        copyLogButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Syncerson log", AppLog.getText())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Only schedule if interval is not "0"
        val intervalStr = prefs.getString(KEY_INTERVAL, "15") ?: "15"
        if (intervalStr != "0") {
            scheduleSyncWork(intervalStr.toLong())
        } else {
            syncScheduled = false
        }
    }

    private fun onDetectClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            detectAndFillSsid()
        } else {
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_LOCATION
            )
        } else {
            val alreadyRequested = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
            if (!alreadyRequested) {
                prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_LOCATION
                )
            } else {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("Location permission was permanently denied. Open app settings to grant it.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            detectAndFillSsid()
        } else {
            Toast.makeText(this, R.string.ssid_unknown_toast, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        logOutput.text = AppLog.getText()
        logScrollView.post { logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
        refreshHandler.postDelayed(refreshUiRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshUiRunnable)
    }

    private fun updateSyncStatusDisplay() {
        syncStatusText.text = getString(
            if (syncScheduled) R.string.sync_status_scheduled else R.string.sync_status_not_scheduled
        )
        syncStatusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (syncScheduled) android.R.color.holo_green_dark else android.R.color.holo_orange_dark
            )
        )
    }

    private fun updatePermissionStatus() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            permissionStatus.text = getString(R.string.permission_granted)
            permissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark))
            grantPermissionButton.visibility = android.view.View.GONE
            detectButton.isEnabled = true
            detectButton.alpha = 1.0f
        } else {
            detectButton.isEnabled = false
            detectButton.alpha = 0.5f
            val permanentlyDenied = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)

            if (permanentlyDenied) {
                permissionStatus.text = getString(R.string.permission_permanently_denied)
                permissionStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark))
                grantPermissionButton.visibility = android.view.View.VISIBLE
            } else {
                permissionStatus.text = getString(R.string.permission_needed)
                permissionStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                grantPermissionButton.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun detectAndFillSsid() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val ssid = wifiManager?.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""

        if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
            ssidInput.setText(ssid)
            Toast.makeText(this, getString(R.string.ssid_detected_toast, ssid), Toast.LENGTH_SHORT).show()
            AppLog.append(TAG, "I", "Detected SSID: $ssid")
        } else {
            Toast.makeText(this, R.string.ssid_unknown_toast, Toast.LENGTH_LONG).show()
            AppLog.append(TAG, "W", "Could not detect SSID")
        }
    }

    private fun scheduleSyncWork(intervalMinutes: Long) {
        // Cancel all old work first (clears stale OneTimeWorkRequests from previous runs)
        WorkManager.getInstance(this).cancelAllWork()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )

        AppLog.append(TAG, "I", "Sync scheduled every ${intervalMinutes}min")
        syncScheduled = true
    }

    private fun runNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()

        WorkManager.getInstance(this).enqueue(request)
        AppLog.append(TAG, "I", "One-shot sync work enqueued")
    }
}
