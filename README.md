# Syncerson

It syncs your photos? I don't know, it really doesn't sync anything as of yet.

## Notes

### NEARBY_WIFI_DEVICES permission (tried, not sufficient)

We attempted to replace `ACCESS_FINE_LOCATION` with `NEARBY_WIFI_DEVICES` +
`usesPermissionFlags="neverForLocation"` (as recommended by
[Android docs](https://developer.android.com/develop/connectivity/wifi/wifi-permissions))
to avoid the location permission dialog. However, neither of the two methods we use to read
the SSID is covered by `NEARBY_WIFI_DEVICES`:

- `ConnectivityManager.getNetworkCapabilities().transportInfo` (Android 13+) — not listed
- `WifiManager.connectionInfo.ssid` (deprecated) — not listed

## Sync triggers

The app has no foreground service. Sync is triggered by enqueuing one-shot work into
WorkManager. When a home SSID is configured, an `UNMETERED` (WiFi) constraint is applied.
WorkManager holds the work until WiFi is available, then runs it within seconds — effectively
a "sync when I get home" trigger.

| Trigger | Constraint | Behavior |
|---|---|---|
| Boot (`BootReceiver`) | UNMETERED (if SSID set) | Enqueues on boot, runs when WiFi available |
| Power connected (`PowerConnectedReceiver`) | UNMETERED (if SSID set) | Enqueues on plug-in, runs when WiFi available |
| Periodic timer (`PeriodicWorker`) | None | Fires every N minutes, re-enqueues the one-shot pipeline |
| "Sync now" button | UNMETERED (if SSID set) | Same as above, user-initiated |

The periodic worker has no constraints so it can re-prime the pipeline from anywhere.
The one-shot carries the UNMETERED constraint, so when you walk in the door and your phone
joins home WiFi, the waiting one-shot fires within seconds.
