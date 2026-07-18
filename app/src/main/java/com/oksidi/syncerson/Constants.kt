package com.oksidi.syncerson

object Constants {
    const val SYNC_WORK_NAME = "home_wifi_sync"
    const val PERIODIC_WORK_NAME = "home_wifi_sync_periodic"
    const val PREFS_NAME = "sync_prefs"
    const val KEY_RESTRICT_MODE = "restrict_mode"
    const val KEY_SSID = "home_wifi_ssid"
    const val KEY_LAN_IP_SUFFIX = "lan_ip_suffix"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_INTERVAL = "interval_minutes"
    const val KEY_PERMISSION_REQUESTED = "perm_requested"
    const val PERMISSION_REQUEST_LOCATION = 1
    const val PERMISSION_REQUEST_BACKGROUND_LOCATION = 2
    const val PERMISSION_REQUEST_MEDIA = 3

    // Restrict mode values (stored as string in prefs)
    const val RESTRICT_MODE_NONE = "none"
    const val RESTRICT_MODE_WIFI = "wifi"
    const val RESTRICT_MODE_SSID = "ssid"
    const val RESTRICT_MODE_IP_SUFFIX = "ip_suffix"
}
