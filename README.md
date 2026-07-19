# Syncerson

It syncs your photos? I don't know, it really doesn't sync anything as of yet.

Goal of this project is not to use any foreground service, only WorkManager and triggers. They don't require user to run the app in background as service to do the sync.

Project is already useful for someone *building their own sync*. This implements UI and WorkManager that is required for *all* sync apps. 

Restrictions user can choose from:

- Sync only when connected to WiFi (no location permission required)
- Sync only when connected to a specific WiFi SSID (requires location permission)
- Sync only when connected to a WiFi with a specific LAN IP prefix (no location permission required)
- Sync without any restrictions

WorkManager triggers user can toggle from UI:

- Boot
- Power connected
- Periodic (15min, 30min etc)

UI has log view to debug issues, not all Android phones trigger WorkManager events the same way, so this is useful to see if the sync is actually triggered.

Project just doesn't have any sync logic yet, if one wanted to implement their own sync, it would be enough to edit SyncWorker.kt.

## Notes

### NEARBY_WIFI_DEVICES permission (tried, not sufficient)

We attempted to replace `ACCESS_FINE_LOCATION` with `NEARBY_WIFI_DEVICES` +
`usesPermissionFlags="neverForLocation"` (as recommended by
[Android docs](https://developer.android.com/develop/connectivity/wifi/wifi-permissions))
to avoid the location permission dialog. However, neither of the two methods we use to read
the SSID is covered by `NEARBY_WIFI_DEVICES`:

- `ConnectivityManager.getNetworkCapabilities().transportInfo` (Android 13+) — not listed
- `WifiManager.connectionInfo.ssid` (deprecated) — not listed
