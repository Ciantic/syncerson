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
