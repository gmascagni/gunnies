# Privacy Policy — Gunnies Ballistics

**Effective Date:** November 25, 2026  
**Package ID:** `com.gunnies.ballistics`

Gunnies Ballistics is an offline-first precision ballistics calculator for Android. We do **not** run user accounts, analytics trackers, advertising SDKs, or cloud profile sync servers.

## 1. Data Stored Locally on Your Device
All firearm, scope, ammo, suppressor, custom drag curve, stage card, and cold-bore/session logbook data is stored exclusively on your device using Android Jetpack DataStore. You can export or delete this data at any time via **Settings → Arsenal Backup & Restore** or by uninstalling the app.

## 2. Optional Location Permission (`ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`)
- **When used:** Only when you explicitly tap **Sync GPS** / **Prep Field** (or scan Bluetooth on Android 8–11 where Android requires location permission for BLE scanning).
- **What is transmitted:** Your latitude and longitude are sent directly from your device to the public **[Open-Meteo](https://open-meteo.com/) API** solely to retrieve current local temperature, humidity, station pressure, wind speed/direction, and elevation. Your latitude is also stored locally in your active atmosphere to compute optional Coriolis corrections.
- **No background tracking:** Gunnies Ballistics never collects location in the background and never sends your location to any developer-operated server.

## 3. Optional Bluetooth Permission (`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`)
- Used solely to discover and communicate locally with optional Bluetooth Low Energy (BLE) rangefinders (e.g., Bushnell Bone Collector 1800) and environmental sensors (e.g., Kestrel / Bluetooth SIG ESS weather meters). No Bluetooth data leaves your device.

## 4. Onboard Sensors (Inclinometer & Barometer)
- Phone orientation sensors (`TYPE_ROTATION_VECTOR` / accelerometer) and optional barometric pressure sensor (`TYPE_PRESSURE`) are read locally on-device for shot inclination angles and station pressure. No sensor data is transmitted off-device.

## 5. Contact
For privacy questions or support, open an issue at https://github.com/gmascagni/gunnies/issues.
