# Gunnies Ballistics — Hardware & BLE Protocol Reference

This document records the protocol sources, implementation status, and licensing/proprietary-key boundaries for every external sensor and rangefinder evaluated for Gunnies Ballistics.

---

## 1. Implemented Hardware Integrations

### Bushnell Bone Collector 1800 (`LBC1800`)
- **Transport:** Bluetooth Low Energy (BLE) ISSC/Microchip Transparent UART service (`49535343-fe7d-4ae5-8fa9-9fafd205e455`).
- **Commands Implemented:** `:DU` (Device Unlock / Keepalive), `:DD` (Query Device Info), `:BP` (Ballistic Profile + Atmosphere + Mover), `:AV` (6-pair powder-temp velocity table), HDLC/ASCII telemetry RX.
- **Connection Reliability Features (Phase 5):**
  - **Remembered Device + Auto-Reconnect:** Stores the last paired `BC-xxxxxx` MAC address and leverages Android BLE `autoConnect = true` so pressing **Fire** on the rangefinder silently reconnects when it wakes from CR2 sleep.
  - **Optional Keepalive Heartbeat:** Sends a lightweight `:DU` heartbeat every 18 seconds while connected (configurable toggle in **Settings → Rangefinder**) to help keep the hardware radio awake longer.
- **Known Hardware Limits:** Accepts single BC (Gunnies sends primary/highest-velocity BC), single wind vector (Gunnies sends the `5/9`, `3/9`, `1/9` range-weighted average in 3-Zone mode), and caps onboard display solutions at `800 yd`.

### Kestrel & Bluetooth SIG Environmental Sensing (`0x181A`)
- **Protocol Source:** Public **Bluetooth SIG Environmental Sensing Service (ESS, `0x181A`)** specification (open standard, no proprietary keys).
- **Characteristics Decoded (`KestrelBleProtocol.kt`):**
  - `0x2A6E` Temperature (`sint16`, `0.01 °C` → `°F`)
  - `0x2A6F` Relative Humidity (`uint16`, `0.01 %`)
  - `0x2A6D` Station Pressure (`uint32`, `0.1 Pa` → `inHg`)
  - `0x2A70` True Wind Speed / `0x2A72` Apparent Wind Speed (`uint16`, `0.01 m/s` → `mph`)
- **Note on Kestrel LiNK Proprietary Profile Sync:** Kestrel's proprietary LiNK gun-profile sync service (writing gun tables *to* a Kestrel 5700 Elite) is governed by Nielsen-Kellerman's LiNK SDK/NDA and is **not** reverse-engineered or shipped without authorization. Standard ESS live-atmosphere reading requires no proprietary keys.

### Onboard Phone Barometer (`Sensor.TYPE_PRESSURE`)
- **Source:** Standard Android `SensorManager` (`Sensor.TYPE_PRESSURE`), reporting raw station pressure in hPa (`inHg = hPa / 33.8638866667`).
- **Permissions:** None required; works 100% offline. Automatically disabled on devices without a hardware barometric pressure sensor.

---

## 2. Evaluated Rangefinders & Chronographs (Not Yet Shipped)

| Device | Transport / Protocol Status | Blocker / Decision |
| :--- | :--- | :--- |
| **Garmin Xero C1 Pro (Chronograph)** | Uses Garmin's proprietary BLE / ANT+ protocol synced via the Garmin ShotView app. | **Flagged — Not Shipped:** No public/open GATT specification is published by Garmin for third-party Android apps; reverse-engineering Garmin's proprietary framing/auth is intentionally excluded unless an official Garmin SDK or open CSV/JSON export import is used. (Note: ShotView CSV/shot-string import for MV SD / average MV is a clean, sanctioned future option.) |
| **Garmin Xero A1i / X1i** | Proprietary Garmin BLE/ANT+ | **Flagged — Not Shipped:** Same proprietary protocol restriction as Xero C1 Pro. |
| **SIG Sauer KILO BDX** | Proprietary Applied Ballistics / BDX BLE GATT protocol. | **Flagged — Not Shipped:** Requires vendor-authorized BDX/AB protocol documentation; not reverse-engineered. |
| **Revic / Vortex Impact 4000 / Leica** | Vendor-specific BLE GATT services. | **Pending Vendor Docs:** Behind `RangefinderClient`, new drivers can be added cleanly once official GATT specs or user-verified open NMEA/UART telemetry frames are available. |
