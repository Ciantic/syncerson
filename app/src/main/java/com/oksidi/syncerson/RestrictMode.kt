package com.oksidi.syncerson

import android.content.Context
import com.oksidi.syncerson.R

enum class RestrictMode(val prefKey: String) {
    NONE("none"),
    WIFI("wifi"),
    SSID("ssid"),
    IP_SUFFIX("ip_suffix");

    companion object {
        fun fromPrefKey(key: String?): RestrictMode =
            entries.firstOrNull { it.prefKey == key } ?: NONE
    }
}

fun RestrictMode.displayName(context: Context): String = when (this) {
    RestrictMode.NONE -> context.getString(R.string.restrict_mode_none)
    RestrictMode.WIFI -> context.getString(R.string.restrict_mode_wifi)
    RestrictMode.SSID -> context.getString(R.string.restrict_mode_ssid)
    RestrictMode.IP_SUFFIX -> context.getString(R.string.restrict_mode_ip_suffix)
}
