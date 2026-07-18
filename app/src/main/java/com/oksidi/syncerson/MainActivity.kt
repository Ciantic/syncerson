package com.oksidi.syncerson

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var ssidInput: EditText
    private lateinit var lanIpSuffixInput: EditText
    private lateinit var detectButton: Button
    private lateinit var locationPermissionStatus: TextView
    private lateinit var grantLocationPermissionButton: Button
    private lateinit var bgLocationPermissionStatus: TextView
    private lateinit var grantBgLocationPermissionButton: Button
    private lateinit var bootReceiverStatus: TextView
    private lateinit var toggleBootReceiverButton: Button
    private lateinit var powerReceiverStatus: TextView
    private lateinit var togglePowerReceiverButton: Button
    private lateinit var mediaPermissionStatus: TextView
    private lateinit var grantMediaPermissionButton: Button
    private lateinit var permissionSettingsButton: Button
    private lateinit var logOutput: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var followLogButton: TextView
    private lateinit var periodicSyncStatusText: TextView
    private var followLog = true
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshUiRunnable = object : Runnable {
        override fun run() {
            logOutput.text = AppLog.getText()
            if (followLog) {
                logScrollView.post { logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
            updatePeriodicSyncStatusDisplay()
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

        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            AppLog.append(TAG, "D", "Pref changed: $key")
        }

        ssidInput = findViewById(R.id.ssidInput)
        lanIpSuffixInput = findViewById(R.id.lanIpSuffixInput)
        locationPermissionStatus = findViewById(R.id.locationPermissionStatus)
        grantLocationPermissionButton = findViewById(R.id.grantLocationPermissionButton)
        bgLocationPermissionStatus = findViewById(R.id.bgLocationPermissionStatus)
        grantBgLocationPermissionButton = findViewById(R.id.grantBgLocationPermissionButton)
        bootReceiverStatus = findViewById(R.id.bootReceiverStatus)
        toggleBootReceiverButton = findViewById(R.id.toggleBootReceiverButton)
        powerReceiverStatus = findViewById(R.id.powerReceiverStatus)
        togglePowerReceiverButton = findViewById(R.id.togglePowerReceiverButton)
        mediaPermissionStatus = findViewById(R.id.mediaPermissionStatus)
        grantMediaPermissionButton = findViewById(R.id.grantMediaPermissionButton)
        permissionSettingsButton = findViewById(R.id.permissionSettingsButton)
        logOutput = findViewById(R.id.logOutput)
        logScrollView = findViewById(R.id.logScrollView)
        followLogButton = findViewById(R.id.followLogButton)
        periodicSyncStatusText = findViewById(R.id.periodicSyncStatus)
        val serverUrlInput = findViewById<EditText>(R.id.serverUrlInput)
        detectButton = findViewById(R.id.detectButton)
        val syncNowButton = findViewById<Button>(R.id.syncNowButton)
        val copyLogButton = findViewById<TextView>(R.id.copyLogButton)
        val intervalSpinner = findViewById<MaterialAutoCompleteTextView>(R.id.intervalSpinner)

        // Interval dropdown
        val intervalOptions = resources.getStringArray(R.array.repeat_intervals)
        val intervalValues = resources.getStringArray(R.array.repeat_interval_values)
        intervalSpinner.setAdapter(android.widget.ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line, intervalOptions))

        // Restore saved interval selection
        val savedInterval = prefs.getString(Constants.KEY_INTERVAL, "0") ?: "0"
        val savedIndex = intervalValues.indexOf(savedInterval)
        if (savedIndex >= 0) intervalSpinner.setText(intervalOptions[savedIndex], false)

        // Load saved values
        ssidInput.setText(prefs.getString(Constants.KEY_SSID, ""))
        lanIpSuffixInput.setText(prefs.getString(Constants.KEY_LAN_IP_SUFFIX, ""))
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

        grantMediaPermissionButton.setOnClickListener {
            requestMediaPermission()
        }

        permissionSettingsButton.setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }

        toggleBootReceiverButton.setOnClickListener {
            toggleReceiver(ComponentName(this, BootReceiver::class.java))
        }

        togglePowerReceiverButton.setOnClickListener {
            toggleReceiver(ComponentName(this, PowerConnectedReceiver::class.java))
        }

        // Auto-save each text field with 300ms debounce (separate per field)
        val ssidDebounce = Debouncer(300L)
        val lanIpDebounce = Debouncer(300L)
        val urlDebounce = Debouncer(300L)
        ssidInput.doAfterTextChanged {
            ssidDebounce.submit { prefs.edit().putString(Constants.KEY_SSID, it?.toString()?.trim() ?: "").apply() }
        }
        lanIpSuffixInput.doAfterTextChanged {
            lanIpDebounce.submit { prefs.edit().putString(Constants.KEY_LAN_IP_SUFFIX, it?.toString()?.trim() ?: "").apply() }
        }
        serverUrlInput.doAfterTextChanged {
            urlDebounce.submit { prefs.edit().putString(Constants.KEY_SERVER_URL, it?.toString()?.trim() ?: "").apply() }
        }

        // Auto-save interval on blur or item select
        val saveInterval = {
            val selectedText = intervalSpinner.text.toString()
            val idx = intervalOptions.indexOf(selectedText)
            val interval = if (idx >= 0) intervalValues[idx] else "0"
            prefs.edit().putString(Constants.KEY_INTERVAL, interval).apply()
            schedulePeriodicSync(this, interval.toLong())
        }
        intervalSpinner.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveInterval()
        }
        intervalSpinner.setOnItemClickListener { _, _, _, _ -> saveInterval() }

        syncNowButton.setOnClickListener {
            AppLog.append(TAG, "I", "Manual sync triggered by user")
            runNow()
        }

        followLogButton.setOnClickListener {
            followLog = !followLog
            followLogButton.text = getString(if (followLog) R.string.follow_log_on else R.string.follow_log_off)
            if (followLog) {
                logScrollView.post { logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }

        copyLogButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Syncerson log", AppLog.getText())
            clipboard.setPrimaryClip(clip)
            AppLog.append(TAG, "I", "Log copied to clipboard")
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
            Constants.PERMISSION_REQUEST_MEDIA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val count = getMediaCount()
                    AppLog.append(TAG, "I",
                        if (count < 0) "Media access: limited" else "Media access granted ($count photos)")
                } else {
                    AppLog.append(TAG, "W", "Media access denied")
                }
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            Constants.PERMISSION_REQUEST_BACKGROUND_LOCATION
        )
    }

    private fun requestMediaPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
            Constants.PERMISSION_REQUEST_MEDIA
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

    private fun updatePeriodicSyncStatusDisplay() {
        val workInfos = WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWork(Constants.PERIODIC_WORK_NAME)
            .get()
        val scheduled = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
        periodicSyncStatusText.text = getString(
            if (scheduled) R.string.sync_status_scheduled else R.string.sync_status_not_scheduled
        )
        periodicSyncStatusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (scheduled) android.R.color.holo_green_dark else android.R.color.holo_orange_dark
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
            bgLocationPermissionStatus.visibility = android.view.View.VISIBLE
            bgLocationPermissionStatus.text = "✗ Not granted (needs location first)"
            bgLocationPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark))
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

        // Media permission row
        val mediaGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        if (mediaGranted) {
            val count = getMediaCount()
            if (count < 0) {
                mediaPermissionStatus.text = "⚠ Limited access"
                mediaPermissionStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                grantMediaPermissionButton.visibility = android.view.View.VISIBLE
            } else {
                mediaPermissionStatus.text = "✓ Granted ($count photos)"
                mediaPermissionStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark))
                grantMediaPermissionButton.visibility = android.view.View.GONE
            }
        } else {
            mediaPermissionStatus.text = "✗ Not granted"
            mediaPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark))
            grantMediaPermissionButton.visibility = android.view.View.VISIBLE
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

    /** Returns photo count, or -1 if access is limited (Android 14+). */
    private fun getMediaCount(): Int {
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null, null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        } catch (e: Exception) {
            // Permission says granted but query failed = limited access (Android 14+)
            -1
        }
    }

    private fun runNow() {
        enqueueSyncWorker(this)
        AppLog.append(TAG, "I", "One-shot sync work enqueued")
    }
}

/** Debounces rapid calls — only the last one within [delayMs] runs, but always runs within [maxWaitMs]. */
class Debouncer(
    private val delayMs: Long,
    private val maxWaitMs: Long = 1000L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var maxWaitRunnable: Runnable? = null
    private var action: (() -> Unit)? = null

    fun submit(action: () -> Unit) {
        this.action = action
        runnable?.let { handler.removeCallbacks(it) }
        runnable = Runnable { flush() }.also { handler.postDelayed(it, delayMs) }
        if (maxWaitRunnable == null) {
            maxWaitRunnable = Runnable { flush() }.also { handler.postDelayed(it, maxWaitMs) }
        }
    }

    private fun flush() {
        runnable?.let { handler.removeCallbacks(it) }
        maxWaitRunnable?.let { handler.removeCallbacks(it) }
        runnable = null
        maxWaitRunnable = null
        action?.invoke()
    }
}
