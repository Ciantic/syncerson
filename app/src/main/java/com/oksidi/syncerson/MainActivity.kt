package com.oksidi.syncerson

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
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
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var ssidInput: EditText
    private lateinit var detectButton: Button
    private lateinit var locationPermissionStatus: TextView
    private lateinit var grantLocationPermissionButton: Button
    private lateinit var bgLocationPermissionStatus: TextView
    private lateinit var grantBgLocationPermissionButton: Button
    private lateinit var bootReceiverStatus: TextView
    private lateinit var toggleBootReceiverButton: Button
    private lateinit var powerReceiverStatus: TextView
    private lateinit var togglePowerReceiverButton: Button
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
            updateReceiverToggles()
            refreshHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppLog.init(this)
        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        ssidInput = findViewById(R.id.ssidInput)
        locationPermissionStatus = findViewById(R.id.locationPermissionStatus)
        grantLocationPermissionButton = findViewById(R.id.grantLocationPermissionButton)
        bgLocationPermissionStatus = findViewById(R.id.bgLocationPermissionStatus)
        grantBgLocationPermissionButton = findViewById(R.id.grantBgLocationPermissionButton)
        bootReceiverStatus = findViewById(R.id.bootReceiverStatus)
        toggleBootReceiverButton = findViewById(R.id.toggleBootReceiverButton)
        powerReceiverStatus = findViewById(R.id.powerReceiverStatus)
        togglePowerReceiverButton = findViewById(R.id.togglePowerReceiverButton)
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
        val savedInterval = prefs.getString(Constants.KEY_INTERVAL, "15") ?: "15"
        val savedIndex = intervalValues.indexOf(savedInterval)
        if (savedIndex >= 0) intervalSpinner.setSelection(savedIndex)

        // Load saved values
        ssidInput.setText(prefs.getString(Constants.KEY_SSID, ""))
        serverUrlInput.setText(
            prefs.getString(Constants.KEY_SERVER_URL, null)?.ifEmpty { null }
                ?: "http://192.168.8.200:8080/sync"
        )

        detectButton.setOnClickListener {
            detectAndFillSsid()
        }

        grantLocationPermissionButton.setOnClickListener {
            requestLocationPermission()
        }

        grantBgLocationPermissionButton.setOnClickListener {
            requestBackgroundLocationPermission()
        }

        toggleBootReceiverButton.setOnClickListener {
            toggleReceiver(ComponentName(this, BootReceiver::class.java))
        }

        togglePowerReceiverButton.setOnClickListener {
            toggleReceiver(ComponentName(this, PowerConnectedReceiver::class.java))
        }

        saveButton.setOnClickListener {
            val ssid = ssidInput.text.toString().trim()
            val serverUrl = serverUrlInput.text.toString().trim()
            val selectedIntervalIdx = intervalSpinner.selectedItemPosition
            val interval = intervalValues[selectedIntervalIdx]

            prefs.edit()
                .putString(Constants.KEY_SSID, ssid)
                .putString(Constants.KEY_SERVER_URL, serverUrl)
                .putString(Constants.KEY_INTERVAL, interval)
                .apply()

            if (interval == "0") {
                WorkManager.getInstance(this).cancelAllWork()
                syncScheduled = false
                AppLog.append(TAG, "I", "Sync disabled (Disabled selected)")
            } else {
                scheduleSyncWork(interval.toLong())
            }

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
            AppLog.append(TAG, "I", "Log copied to clipboard")
        }

        // Only schedule if interval is not "0"
        val intervalStr = prefs.getString(Constants.KEY_INTERVAL, "15") ?: "15"
        if (intervalStr != "0") {
            scheduleSyncWork(intervalStr.toLong())
        } else {
            syncScheduled = false
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                Constants.PERMISSION_REQUEST_LOCATION
            )
        } else {
            val alreadyRequested = prefs.getBoolean(Constants.KEY_PERMISSION_REQUESTED, false)
            if (!alreadyRequested) {
                prefs.edit().putBoolean(Constants.KEY_PERMISSION_REQUESTED, true).apply()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    Constants.PERMISSION_REQUEST_LOCATION
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
        when (requestCode) {
            Constants.PERMISSION_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    AppLog.append(TAG, "I", "Location permission granted")
                } else {
                    AppLog.append(TAG, "W", "Location permission denied")
                }
            }
            Constants.PERMISSION_REQUEST_BACKGROUND_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    AppLog.append(TAG, "I", "Background location granted — SSID readable at all times")
                } else {
                    AppLog.append(TAG, "W", "Background location denied — SSID only readable when app is open")
                }
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        val alreadyRequested = prefs.getBoolean(Constants.KEY_BG_PERMISSION_REQUESTED, false)
        if (alreadyRequested) {
            // User already chose; don't nag
            AppLog.append(TAG, "W", "Background location already denied — enable 'Allow all the time' in app settings")
            return
        }
        prefs.edit().putBoolean(Constants.KEY_BG_PERMISSION_REQUESTED, true).apply()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            Constants.PERMISSION_REQUEST_BACKGROUND_LOCATION
        )
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
        val fgGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val bgGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Location permission row
        if (fgGranted) {
            locationPermissionStatus.text = "✓ Granted"
            locationPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark))
            grantLocationPermissionButton.visibility = android.view.View.GONE
            detectButton.isEnabled = true
            detectButton.alpha = 1.0f
        } else {
            locationPermissionStatus.text = "✗ Not granted"
            locationPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark))
            grantLocationPermissionButton.visibility = android.view.View.VISIBLE
            detectButton.isEnabled = false
            detectButton.alpha = 0.5f
        }

        // Background location permission row
        if (!fgGranted) {
            bgLocationPermissionStatus.visibility = android.view.View.GONE
            grantBgLocationPermissionButton.visibility = android.view.View.GONE
        } else if (bgGranted) {
            bgLocationPermissionStatus.visibility = android.view.View.VISIBLE
            bgLocationPermissionStatus.text = "✓ Granted"
            bgLocationPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark))
            grantBgLocationPermissionButton.visibility = android.view.View.GONE
        } else {
            bgLocationPermissionStatus.visibility = android.view.View.VISIBLE
            bgLocationPermissionStatus.text = "✗ Not granted (needed for background SSID)"
            bgLocationPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark))
            grantBgLocationPermissionButton.visibility = android.view.View.VISIBLE
        }
    }

    private fun detectAndFillSsid() {
        val ssid = getCurrentSsid(TAG, this)

        if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
            ssidInput.setText(ssid)
            AppLog.append(TAG, "I", "Detected SSID: $ssid")
        } else {
            AppLog.append(TAG, "W", "Could not detect SSID")
        }
    }

    private fun toggleReceiver(component: ComponentName) {
        val pm = packageManager
        val currentState = pm.getComponentEnabledSetting(component)
        val currentlyEnabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        pm.setComponentEnabledSetting(
            component,
            if (currentlyEnabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        AppLog.append(TAG, "I", "${component.shortClassName} ${if (currentlyEnabled) "disabled" else "enabled"}")
    }

    private fun updateReceiverToggles() {
        updateReceiverToggle(
            ComponentName(this, BootReceiver::class.java),
            bootReceiverStatus, toggleBootReceiverButton,
            "On boot"
        )
        updateReceiverToggle(
            ComponentName(this, PowerConnectedReceiver::class.java),
            powerReceiverStatus, togglePowerReceiverButton,
            "On power"
        )
    }

    private fun updateReceiverToggle(
        component: ComponentName,
        statusText: TextView, button: Button, label: String
    ) {
        // Read from PackageManager — the source of truth
        val currentState = packageManager.getComponentEnabledSetting(component)
        val enabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        statusText.text = "$label: ${if (enabled) "enabled ✓" else "disabled ✗"}"
        statusText.setTextColor(
            ContextCompat.getColor(this,
                if (enabled) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            )
        )
        button.text = if (enabled) "Disable" else "Enable"
    }

    private fun scheduleSyncWork(intervalMinutes: Long) {
        WorkManager.getInstance(this).cancelAllWork()
        schedulePeriodicSync(this, intervalMinutes)
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
