# Gunnies Ballistics

Native Android precision ballistics solver with optional **Bushnell Bone Collector 1800 (`LBC1800`)** Bluetooth Low Energy (BLE) rangefinder sync, built with Kotlin and Jetpack Compose.

Works completely standalone on standard Android smartphones, tablets, and adaptive dual-pane foldables (such as the Samsung Galaxy Z Fold). All ballistic calculations, profiles, reticles, stage cards, DOPE tables, shot logs, and backups run offline on the device.

**Website:** https://gmascagni.github.io/gunnies/

---

## Install & Update (Android Users)

The simplest way to install Gunnies Ballistics on Android (**Android 8.0 / API 26 or newer**) is downloading the APK from **GitHub Releases**:

1. On your Android phone, open **[GitHub Releases](https://github.com/gmascagni/gunnies/releases)** and download the latest `.apk` file.
2. Open your phone's **Files** / **My Files** app and tap the downloaded `.apk`.
3. When Android prompts for permission, enable **Install unknown apps** for your browser or file manager, then tap **Install**.
   - *Samsung One UI note:* If **Settings → Security and privacy → Auto Blocker** is enabled, Samsung blocks sideloaded APKs. Temporarily disable Auto Blocker during install, or install via ADB (developer section below).
4. Open **Gunnies Ballistics** and grant optional runtime permissions when prompted:
   - **Location:** Only needed if you tap **Weather → Sync GPS** (live weather, elevation, and GPS latitude for Coriolis) or use BLE scanning on older Android versions.
   - **Nearby Devices (Bluetooth):** Only needed if you pair a Bushnell Bone Collector 1800 (`LBC1800`) rangefinder.

### Signing Status, Updating & Uninstalling
- **Build signing transparency:** Only APKs named `*-release.apk` produced with repository release signing secrets (verified via `apksigner verify` in CI) are release-signed. Pre-release APKs named `*-debug.apk` are debug-signed test builds and are **not** production-signed.
- **How to update:**
  - When updating between two **release-signed APKs** signed with the same key, simply download and tap the newer `.apk`—Android updates in place and keeps your saved profiles, stage cards, and logbook.
  - When switching between a **debug build** and a **release build** (or between two CI debug builds signed by different runner debug keys), Android will reject an in-place overwrite with a signature conflict. First go to **Settings → Arsenal Backup & Restore → Export (.json)**, uninstall the existing app (**Settings → Apps → Gunnies Ballistics → Uninstall**), install the new `.apk`, and tap **Import (.json)** to restore your data.
- **How to uninstall:** Long-press the Gunnies Ballistics icon → **Uninstall** (or via **Android Settings → Apps → Gunnies Ballistics → Uninstall**). Export a JSON backup first if you want to keep custom profiles, truing data, stage cards, or shot logs.

---

## Features (Verified Against App Code)

- **3-DOF+ RK4 Ballistics Engine** (`BallisticEngine.kt`, `DragTables.kt`)
  - Standard BRL/JBM **G1 and G7** drag curves (`Mach 0.00–5.00`).
  - **Stepped Multi-BC:** Up to **5 velocity-to-BC bands** per ammo load in the UI with smooth ±20 fps blending across boundaries.
  - **Two-Stage Ballistic Calibration (Truing):** Stage 1 supersonic **Muzzle Velocity (MV)** truing and Stage 2 transonic/subsonic **Drop Scale Factor (DSF)** truing at `Mach ≤ 1.20` (up to 6 Mach calibration nodes).
  - **3-Zone Wind Banding** (Near `0–33%`, Mid `33–66%`, Far `66–100%`) plus **Single Wind**, **Wind Gust Bracket**, 3D inclined-fire wind & gravity decomposition, and **Moving Target Lead (Mover)**.
  - **Optional Corrections:** Miller `Sg` + Litz **Spin Drift**, Near-zone **Aerodynamic Jump**, and horizontal + vertical (Eötvös) **Coriolis** (activates once GPS latitude is synced).
  - **Powder Temperature Sensitivity** (`fps/°F` & chrono temp), vertical/horizontal zero POI offsets, suppressor MV & POI shifts, and optic **Elevation/Windage Sight Scale Factors (SSF)**.
  - **DOPE Range Card**, interactive **Trajectory Drop Chart**, and **MPBR** (`6"` vital zone default).
- **Solver HUD, Phone Inclinometer & 7 Scope-Matched Reticles**
  - Interactive tape-measure distance dial with tappable magnifier loupe for direct numeric entry.
  - **Shot Inclination Angle** modal supporting **Measure with Phone** (Hold-to-Lock using `TYPE_ROTATION_VECTOR` with accelerometer fallback, `±60°`) or direct manual entry (`±75°` in solver).
  - **7 Reticles** (`ReticleType`): **3 FFP** (`Arken VPR MIL`, `Primary Arms ARC-2 MOA`, `Primary Arms ACSS / R-Grid`) and **4 SFP / non-magnifying** (`Classic Duplex`, `Red Dot (2/65 MOA)`, `EOTech 68 MOA / 1 MOA`, `Handgun 3-Dot Irons`), with magnification control, **Hold All** vs. **Dial Elev / Hold Wind**, and target plate overlay (`Off / 6" / 10" / 18"`).
- **PRS / Field Multi-Target Stage Cards** (`StageCardScreen.kt`)
  - Create, reorder, save, and share custom stage cards with up to **10 targets per stage** (independent distance, inclination angle, wind override, and mover speed per target; includes 3 starter stage cards).
- **Cold-Bore & Session DOPE Logbook** (`LogbookModal.kt`)
  - One-tap shot logging from the Solver HUD recording timestamp, active rig, atmospheric snapshot, target range, predicted vs. actual elevation/wind, and a **Cold Bore** flag.
- **Modular Arsenal & 379-Item Built-In Database** (`BallisticsCatalog.kt`, `ProfilesScreen.kt`)
  - Independent **Guns**, **Scopes**, **Ammo**, and **Suppressors** tabs with Favorites (`★`), inline **My Arsenal | Database** browsing, **Unsuppressed** 1-tap toggle, and optional Auto-Match Scope Unit (`MOA`/`MRAD`).
  - Built-in database contains **379 profiles**: **115 firearms**, **86 scopes**, **114 ammo loads**, and **64 suppressors** (plus pre-populated starter arsenal defaults: 7 firearms, 6 scopes, 13 ammo loads, 2 suppressors).
- **Full Arsenal JSON Backup & Restore** (`ArsenalBackupCodec.kt`)
  - Versioned `.json` export and import via Android's Storage Access Framework with **Merge** or **Overwrite** modes (includes firearms, scopes, ammo with Multi-BC & DSF tables, suppressors, stage cards, session logs, and preferences).
- **Live GPS Weather & Offline Operation** (`WeatherService.kt`, `WeatherScreen.kt`)
  - **Sync GPS** fetches current temperature, relative humidity, surface (station) pressure, wind speed, gusts, and meteorological wind direction from **Open-Meteo** (requires GPS + internet; no API key required) and computes Density Altitude and relative wind clock from your target bearing.
  - All core ballistic calculations, manual weather/wind entry, saved weather defaults, profiles, reticles, stage cards, DOPE tables, logbook, and JSON backups work **100% offline**.
- **Optional Bushnell Bone Collector 1800 (`LBC1800`) BLE Sync** (`Lbc1800Protocol.kt`, `RangefinderBleManager.kt`)
  - Optional Bluetooth companion mode: wakes/unlocks (`:DU`), queries device info (`:DD`), pushes active rifle profile + atmosphere + mover (`:BP`) and 6-pair powder-temp velocity table (`:AV`), and ingests live laser range & incline pings.
  - Hardware notes: the `LBC1800` protocol accepts a single BC (Gunnies sends your primary/highest-velocity BC) and a single wind vector (Gunnies sends the `5/9`, `3/9`, `1/9` range-weighted vector average when 3-Zone Wind is active). Onboard rangefinder solutions cap at **800 yards**; Gunnies solves beyond 800 yards on the phone (up to 1,800 yd/m on DOPE cards).
- **Field Themes, Text Scaling & Keep Screen Awake**
  - **Field Dark**, **Night Red** (OLED night-vision friendly), and **Daylight** themes, adjustable UI text scaling, and `FLAG_KEEP_SCREEN_ON` keep-awake toggle.

---

## Ballistic Model Technical Reference ("3-DOF+")

### What "3-DOF+" Means in Gunnies Ballistics
In [`BallisticEngine.kt`](app/src/main/java/com/gunnies/ballistics/engine/BallisticEngine.kt), the core trajectory solver is a **3-DOF (three degrees of freedom) point-mass integrator** using fixed-step **4th-order Runge-Kutta (RK4)** integration at `dt = 0.001 s` (1 ms) in a line-of-sight tilted coordinate frame `(x, y, z)` and velocity `(vx, vy, vz)`, with linear sub-step interpolation at each requested target range.

The **"+"** denotes rotational, Earth-frame, and calibration terms layered onto the 3-DOF trajectory without solving full 6-DOF rigid-body pitch/yaw/roll equations of motion:

| Component | Implementation in Code | When It Applies |
| :--- | :--- | :--- |
| **Drag Curves** | BRL/JBM **G1** (79 Mach/Cd knots, `Mach 0.00–5.00`, omitting the non-existent `0.65` G1 knot) and **G7** (80 knots, `Mach 0.00–5.00`) via binary-search linear interpolation (`DragTables.kt`). | Every trajectory solve and zero-angle solve. |
| **Velocity-Banded Multi-BC** | Up to 5 `BcBand(minVelocityFps, bc)` pairs in UI (up to 8 packed in primitive arrays inside `BallisticEngine`). Evaluated from relative airspeed `vRel` at every RK4 sub-step with a smooth **±20 fps linear ramp** (`BC_BLEND_HALF_FPS = 20.0`) across band boundaries. | Whenever `AmmoLoad.bcBands` is non-empty; otherwise uses single `ballisticCoefficient`. |
| **Atmosphere & Air State** | Station pressure directly (`pressureIsStation = true`) or ICAO barometric conversion `P * (1 - 6.8755856e-6 * altFt)^5.25588`. Humid air density splits dry air (`Rd = 287.058`) and water vapor (`Rv = 461.495`, Tetens saturation vapor pressure) relative to ICAO `1.2250 kg/m³`. Speed of sound includes humidity term `1 + 0.14 * (Pv / P)`. | Every solve and zero-angle calculation (zeroing uses `zeroTempF`, `zeroPressureInHg`, `zeroHumidityPercent`). |
| **Powder Temp & Suppressor Shift** | `effectiveMV = (ammoMV + suppressor.mvShiftFps) + (tempF - chronoTempF) * mvTempSensitivityFpsPerF` (clamped `50–6000 fps`). Suppressor vertical/horizontal POI shifts add to scope zero offsets. | Always applied when non-zero. |
| **3D Inclined Fire** | Gravity decomposed as `gx = -g sin(θ)`, `gy = -g cos(θ)`; horizontal wind vectors rotated into the tilted LOS frame (`clampedAngleDeg` in `[-75°, +75°]`). | Whenever inclination angle is non-zero. |
| **Single & 3-Zone Wind + Gusts** | Single uniform wind, OR 3-Zone wind (`Near 0–33%`, `Mid 33–66%`, `Far 66–100%` of trajectory max range) sampled at every RK4 sub-step. When `windGustMph > sustainedMph + 1.0`, a second pass computes the Wind Gust Bracket (scaling all 3 zones proportionally in 3-zone mode). | Every single-target solve; Stage Card per-target wind overrides fall back to single wind for that target. |
| **Coriolis (Horizontal + Eötvös)** | Integrated directly inside RK4 acceleration: `ay += 2ω cos(lat) sin(az) vx` and `az += 2ω sin(lat) vx` (`ω = 7.2921159e-5 rad/s`). | Active **only** when `includeCoriolis == true`, `weaponMode == RIFLE`, **and** `gpsLatitude != null` (captured via **Weather → Sync GPS**). |
| **Gyroscopic Stability (`Sg`) & Spin Drift** | Density- and velocity-corrected Miller `Sg` (using explicit `bulletLengthInches` if supplied, otherwise caliber/weight heuristic). Post-integration Bryan Litz empirical drift: `±1.25 * (Sg + 1.2) * t^1.83` inches (right for RH twist, left for LH twist). | Active when `includeSpinDrift == true`, `weaponMode == RIFLE`, `twistDirection != NONE`, and `barrelTwistInches > 0`. |
| **Aerodynamic Jump** | Post-integration empirical vertical shift: `0.012 * nearCrosswindMph * twistSign * (1.5 / max(Sg, 1.0))` MOA, using **Near-zone** crosswind. | Active when `includeAeroJump == true`, `weaponMode == RIFLE`, and `twistDirection != NONE`. |
| **Drop Scale Factor (DSF)** | Post-integration vertical drop multiplier `scaledSy = sy * appliedDsf`, linearly interpolated across up to 6 ascending Mach nodes (`0.65–1.45` on truing). Smoothly blends back to `1.0` at `Mach 1.20` (`TRANSONIC_MACH_GATE`) when the highest node is below Mach 1.20. | Whenever `AmmoLoad.dsfTable` is non-empty. |
| **Two-Stage Truing (Bisection)** | **Stage 1 (MV Truing):** 22-iteration bisection over `[0.65×MV, 1.40×MV]` (`350–5000 fps`) to match observed elevation hold. **Stage 2 (DSF Truing):** Locks MV and runs 22-iteration bisection over `[0.65, 1.45]` at the target's Mach (`≤ 1.20`). | User-initiated from Solver HUD **True MV / DSF** modal (requires range > `zeroRange + 10 yd` and hold > `0.1 MOA`). |
| **Sight Scale Factor & Mover Lead** | Elevation/windage multiplied by `elevationSsf`/`windageSsf`. Moving target lead computed as `moverSpeedFps * TOF` converted to angle/inches. | Always applied (`SSF` defaults to `1.0`; Mover Lead shown when `moverSpeedMph > 0.1`). |

### Known Limitations
- **Not a 6-DOF or custom Doppler radar (CDM) solver:** Gunnies does not integrate rigid-body bullet pitch/yaw dynamics, Magnus force, or proprietary radar drag models. Real-world accuracy depends on accurate rifle/optic/ammo measurements, atmosphere inputs, and field truing.
- **Coriolis requires GPS latitude:** Because manual latitude entry is not currently exposed, Coriolis remains inactive until **Weather → Sync GPS** has captured a latitude (stored in the active atmosphere).
- **DSF scales vertical drop only:** DSF is applied as a post-integration multiplier on vertical drop (`sy`); it does not modify horizontal wind drift, remaining velocity, or time of flight, and is not applied during zero-angle solving.
- **Zero-angle assumptions:** `computeZeroAngleRad` assumes calm wind (`0 mph`) at `zeroTempF`, `zeroPressureInHg`, and `zeroHumidityPercent`.
- **3-Zone Wind on DOPE Range Cards:** On multi-row DOPE Range Cards (`generateRangeCard`), Near/Mid/Far thirds are measured relative to the card's maximum range (`maxTargetFt`) rather than re-partitioning thirds for each individual row.
- **Estimated bullet length when omitted:** If `bulletLengthInches` is blank on an ammo load, Miller `Sg` (and therefore Spin Drift and Aero Jump) uses a caliber/weight length estimate.
- **Integration bounds:** Maximum simulated flight time is `16.0 s` (`10.0 s` during zeroing) while `vx > 15 fps`; solver inclination is clamped to `±75°` (`±60°` in the phone inclinometer); DOPE cards cap at `1,800` yards/meters.
- **In-app reference:** A plain-language summary of this model and its limitations is also available inside the app under **Settings → About & Ballistic Model (3-DOF+)**.

---

## Developer Build & Release Setup

### Requirements
- **JDK 17**
- **Android SDK Platform 35** & Build Tools (`compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`)
- Android Studio **Meerkat / Narwhal** or newer, or Gradle CLI via `./gradlew` / `gradlew.bat` (Gradle 8.13 wrapper included)

### Build & Install via Command Line (`gradlew` + `adb`)

#### Windows PowerShell
```powershell
git clone https://github.com/gmascagni/gunnies.git
cd gunnies

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat testDebugUnitTest assembleDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell monkey -p com.gunnies.ballistics -c android.intent.category.LAUNCHER 1
```

#### macOS / Linux
```bash
git clone https://github.com/gmascagni/gunnies.git
cd gunnies

export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "$JAVA_HOME")
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

./gradlew testDebugUnitTest assembleDebug
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell monkey -p com.gunnies.ballistics -c android.intent.category.LAUNCHER 1
```

Or open the repository in **Android Studio**, enable **USB Debugging** on your Android device, and click **Run ▶**.

### Automated APK Builds & Signed GitHub Releases (GitHub Actions)
[`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) runs unit tests and builds APK artifacts on pushes/PRs to `main`, version tags (`v*`), and manual workflow dispatches:

- **Without signing secrets configured:** Builds a debug APK (`gunnies-ballistics-v<version>-debug.apk`), uploads it as a workflow artifact, and (on `v*` tag push or `publish_release=true`) publishes a **Pre-release** explicitly labeled as a debug test build.
- **With release signing secrets configured:** Decodes the keystore in runner temp storage, runs `./gradlew assembleRelease` using the optional `signingConfigs.release` block in [`app/build.gradle.kts`](app/build.gradle.kts), verifies the APK signature with Android SDK `apksigner verify`, generates a `.sha256` checksum, and publishes a signed **GitHub Release** (`gunnies-ballistics-v<version>-release.apk`).

#### Remaining One-Time Setup for Signed Releases (Maintainers)
Never commit keystores or passwords to the repository. To enable verified signed release APKs in GitHub Actions:
1. Generate a release keystore locally (keep it backed up securely—losing it prevents in-place updates):
   ```bash
   keytool -genkey -v -keystore gunnies-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gunnies
   ```
2. Base64-encode the keystore (single line, no line wraps):
   - **macOS/Linux:** `base64 -w 0 gunnies-release.jks` (or `base64 -i gunnies-release.jks | tr -d '\n'` on macOS)
   - **PowerShell:** `[Convert]::ToBase64String([IO.File]::ReadAllBytes("gunnies-release.jks"))`
3. In GitHub **Settings → Secrets and variables → Actions**, add these four repository secrets:
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
4. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, push to `main`, and push a matching tag (e.g., `git tag v1.0.0 && git push origin v1.0.0`).

---

## Project Structure & Testing

```text
app/src/main/java/com/gunnies/ballistics/
├── MainActivity.kt                  # Adaptive Z Fold NavigationRail / BottomBar, permissions, keep-awake
├── ble/
│   ├── Lbc1800Protocol.kt           # XOR 0x8A checksum, HDLC deframer, :DU/:DD/:AV/:BP command builders
│   └── RangefinderBleManager.kt     # Serialized GATT queue, paced writes, telemetry parser
├── data/
│   ├── ArsenalBackupCodec.kt        # Versioned JSON Arsenal export/import codec (Merge / Overwrite)
│   ├── BallisticsCatalog.kt         # 379-item built-in firearms, scopes, ammo & suppressors database
│   ├── BallisticsRepository.kt      # Jetpack DataStore persistence & non-destructive arsenal merge
│   ├── UnitFormatters.kt            # MOA/MRAD/Clicks/Velocity/Distance/Pressure/Wind formatting
│   └── WeatherService.kt            # GPS location lookup + Open-Meteo atmospheric & wind fetch
├── engine/
│   ├── BallisticEngine.kt           # 3-DOF+ RK4 solver, Multi-BC, DSF, 3-Zone Wind, DOPE, MV/DSF truing
│   └── DragTables.kt                # Verified BRL/JBM G1 & G7 Cd(Mach) drag curves
├── model/
│   └── BallisticsModels.kt          # Firearm/Scope/Ammo/Suppressor setups, StageCard, SessionLog, WindProfile
└── ui/
    ├── BallisticsViewModel.kt       # Off-main-thread Dispatchers.Default solver pipeline & BLE coordination
    ├── components/
    │   ├── GunneryComponents.kt     # Shared tactical HUD panels & hardware badges
    │   ├── InclinometerDialog.kt    # Phone rotation-vector / accelerometer angle lock & manual entry
    │   ├── LogbookModal.kt          # Cold-bore & range session DOPE logbook sheet
    │   ├── RangefinderHelpView.kt   # LBC1800 display indicator reference guide
    │   └── ReticleViewCard.kt       # Interactive scope-matched reticle canvas (7 reticles) & hold/dial overlay
    ├── screens/
    │   ├── SolverScreen.kt          # HUD, Rig Picker, Tape-Measure Dial, MV/DSF Truing, Mover Lead
    │   ├── StageCardScreen.kt       # PRS / Field multi-target stage cards (up to 10 targets)
    │   ├── RangeCardScreen.kt       # DOPE Range Card table + Trajectory Drop Chart
    │   ├── WeatherScreen.kt         # Live GPS station, Single/3-Zone Wind, Target Bearing, Manual Atmosphere
    │   ├── ProfilesScreen.kt        # Guns / Scopes / Ammo (Multi-BC) / Cans manager & inline Database
    │   └── RangefinderAndSettingsScreen.kt # LBC1800 BLE Console, Precision Physics, Backup, About & Ballistic Model
    └── theme/
        └── Theme.kt                 # Field Dark, OLED Night Red, Daylight palettes & tabular typography
```

### Run Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```
Includes **26 unit tests** across `BallisticEngineTest` (**19 tests**: G1/G7 table alignment, 3D inclined wind, 3-zone wind banding, stepped Multi-BC smooth blending, DSF interpolation & truing, Coriolis, aero jump, MV truing, MPBR, single-pass DOPE consistency, JSON backup codec, catalog counts) and `Lbc1800ProtocolTest` (**7 tests**: XOR checksum verification, HDLC chunked deframing, `:BP` / `:AV` frame encoding, primary BC & range-weighted average wind serialization).
