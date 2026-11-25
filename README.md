# BC1800 Ballistics

Native Android precision ballistics solver and **Bushnell Bone Collector 1800 (`LBC1800` / `BC-xxxxxx`)** Bluetooth Low Energy (BLE) companion app built with Kotlin and Jetpack Compose.

Designed for both standard smartphones and foldables (such as the Samsung Galaxy Z Fold cover and unfolded dual-column layouts), with independent **Firearms**, **Scopes / Optics**, and **Ammo** profiles that sync directly to your rangefinder.

---

## Features

- **Precision 3-DOF+ RK4 Ballistics Engine** (`BallisticEngine.kt`, `DragTables.kt`)
  - Standard BRL/JBM **G1 and G7** drag curves with verified Mach/Cd knot alignment.
  - Humid ICAO air density, station pressure conversion, speed of sound, and density altitude.
  - **MV Truing** bisection solver from observed downrange impact drop.
  - **Wind Gust Bracket**, 3D inclined-fire wind decomposition, and moving target lead (**Mover**).
  - **Spin Drift**, **Aerodynamic Jump**, and **Coriolis** (horizontal + vertical Eötvös) using GPS latitude and direction of fire.
  - **Powder Temperature Sensitivity** (`fps/°F` & chrono temp), vertical/horizontal zero POI offsets, and optic **Elevation/Windage Scale Factor (SSF)**.
  - Single-pass $O(N)$ **DOPE Range Card** with interactive **Trajectory Drop Chart** and **MPBR** calculation.
- **Tactical HUD, Tape-Measure Range Dial & True Reticles**
  - Interactive tape-measure yardage/meter dial with tappable magnifier loupe for direct numeric range entry.
  - Scope-matched FFP/SFP reticles (`Arken VPR MIL`, `Primary Arms ARC-2 MOA`, `ACSS R-Grid`, `Simple Duplex`, `Red Dot 65 MOA`, `Handgun 3-Dot`) with pinch-to-zoom, **Hold All vs. Dial Elev • Hold Wind** toggle, and true-scale target plate overlay (`Off / 6" / 10" / 18"`).
- **Live GPS Weather & Wind Clock**
  - One-tap GPS weather & elevation sync via Open-Meteo (no API key required) plus relative wind clock and manual atmospheric override.
- **Modular Firearms, Scopes & Ammo Arsenal**
  - Separate **Guns**, **Scopes**, and **Ammo** libraries with Favorites (`★`), built-in starter catalog (`BallisticsCatalog.kt`), per-firearm Scope/Ammo memory, and optional Auto-Match Scope Unit (`MOA`/`MRAD`).
- **Bushnell Bone Collector 1800 BLE Integration**
  - Serializes `:DU`, `:DD`, `:BP` (34-field ballistic profile + atmosphere + mover), `:AV` (6-pair powder-temp velocity table), and parses incoming laser range/angle pings cleanly.
- **Field Themes & Text Scaling**
  - 1-tap switching between **Field Dark**, **Night Red** (OLED night-vision preserving), and **Daylight**, plus independent text scaling.

---

## Requirements

### Android Device
- **Android 8.0 (Oreo, API 26)** or higher (`minSdk = 26`, `targetSdk = 35`).
- Bluetooth Low Energy (for LBC1800 rangefinder pairing) and GPS/Internet (for live local weather sync).

### Build Environment
- **JDK 17**
- **Android SDK Platform 35** & Build Tools (`compileSdk = 35`)
- Android Studio **Meerkat / Narwhal** or newer (or Gradle CLI via included `./gradlew` / `gradlew.bat`)

---

## How to Load the App onto Your Phone

Choose any of the three methods below.

### Option 1 — Android Studio (Recommended)

1. **Clone the repository**:
   ```bash
   git clone https://github.com/gmascagni/gunnies.git
   cd gunnies
   ```
2. Open the folder in **Android Studio** and let Gradle sync complete.
3. On your Android phone, enable **Developer Options** and **USB Debugging**:
   - Open **Settings → About phone → Software information** and tap **Build number** 7 times.
   - Open **Settings → Developer options** and enable **USB debugging**.
4. Connect your phone to your computer with a USB data cable and tap **Allow USB debugging** on the phone prompt.
5. Select your phone in the Android Studio device dropdown and click **Run ▶**.

---

### Option 2 — Build & Install via Command Line (`gradlew` + `adb`)

Make sure **JDK 17** and **Android SDK Platform 35** (plus `platform-tools` for `adb`) are installed.

#### Windows PowerShell
```powershell
git clone https://github.com/gmascagni/gunnies.git
cd gunnies

# Point to your JDK 17 and Android SDK directories
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# Run unit tests and build the debug APK
.\gradlew.bat testDebugUnitTest assembleDebug

# Verify your phone is connected via USB Debugging
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices

# Install (or update) and launch the app
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell monkey -p com.bc1800.ballistics -c android.intent.category.LAUNCHER 1
```

#### macOS / Linux
```bash
git clone https://github.com/gmascagni/gunnies.git
cd gunnies

export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "$JAVA_HOME")
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

# Run unit tests and build the debug APK
./gradlew testDebugUnitTest assembleDebug

# Install onto USB-connected phone
"$ANDROID_HOME/platform-tools/adb" devices
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell monkey -p com.bc1800.ballistics -c android.intent.category.LAUNCHER 1
```

> **Note:** On Windows, Gradle automatically locates the Android SDK if `ANDROID_HOME` is set or if you create a `local.properties` file containing `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.

---

### Option 3 — Direct APK Sideload (No Computer ADB Connection Needed During Install)

1. Build the APK on a computer (or copy `app/build/outputs/apk/debug/app-debug.apk` after running `./gradlew assembleDebug`).
2. Transfer `app-debug.apk` to your phone (USB file transfer, Google Drive, Quick Share, etc.).
3. Open your phone's **Files** / **My Files** app and tap `app-debug.apk`.
4. If prompted, allow **Install unknown apps** for your file manager/browser and confirm **Install**.
5. *Samsung One UI note:* If **Settings → Security and privacy → Auto Blocker** is enabled, Android may block sideloaded APKs—temporarily disable Auto Blocker or use **Option 1 / Option 2 (ADB)** instead.

---

## First-Time Setup & Bushnell LBC1800 Pairing

1. **Grant Runtime Permissions**:
   - **Location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`)**: Required for **Weather → Sync GPS** (live temperature, station pressure, wind, altitude, Coriolis latitude) and BLE scanning on older Android builds.
   - **Nearby Devices (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`)**: Required on Android 12+ to scan and pair with the Bone Collector 1800.
2. **Configure Your Rig (`Setup` tab)**:
   - Select or add your **Gun**, **Scope**, and **Ammo** separately (or browse the built-in catalog).
   - Verify barrel twist, zero distance, sight height, and click value (`1/4 MOA` vs `0.1 MRAD`).
3. **Pair the Bushnell Bone Collector 1800 (`LBC1800`)**:
   - Press the **Fire** button on your Bone Collector 1800 to wake its Bluetooth radio.
   - Tap the **BLE status pill** in the top bar (or open **LBC1800** on unfolded tablets/foldables) → **Scan BLE**.
   - Select your `BC-xxxxxx` / `LBC1800` unit and tap **Connect**.
   - Tap **Sync** to push your active Firearm + Scope + Ammo + live Atmosphere to the rangefinder. Laser range pings automatically update the Solver HUD.

---

## Project Structure & Testing

```text
app/src/main/java/com/bc1800/ballistics/
├── MainActivity.kt                  # Adaptive Z Fold NavigationRail / BottomBar + permissions + GPS trigger
├── ble/
│   ├── Lbc1800Protocol.kt           # Checksum (XOR 0x8A), HDLC deframer, :DU/:DD/:AV/:BP command builders
│   └── RangefinderBleManager.kt     # Serialized GATT queue + paced writes + telemetry parser
├── data/
│   ├── BallisticsCatalog.kt         # Built-in firearms, optics, and factory ammo database
│   ├── BallisticsRepository.kt      # Jetpack DataStore persistence & non-destructive arsenal merge
│   ├── UnitFormatters.kt            # MOA/MRAD/Clicks/Velocity/Distance/Pressure formatting
│   └── WeatherService.kt            # GPS location lookup + Open-Meteo atmospheric & wind fetch
├── engine/
│   ├── BallisticEngine.kt           # Zero-allocation RK4 solver, single-pass DOPE card, MV truing
│   └── DragTables.kt                # Verified BRL/JBM G1 & G7 Cd(Mach) drag curves
├── model/
│   └── BallisticsModels.kt          # FirearmSetup, ScopeSetup, AmmoLoad, RifleProfile, BallisticSolution
└── ui/
    ├── BallisticsViewModel.kt       # Off-main-thread Dispatchers.Default solver pipeline & BLE coordination
    ├── components/
    │   └── ReticleViewCard.kt       # Interactive scope-matched reticle canvas & hold/dial overlay
    ├── screens/
    │   ├── SolverScreen.kt          # HUD, Rig Picker, Tape-Measure Dial, MV Truing, Mover Lead
    │   ├── RangeCardScreen.kt       # DOPE Range Card table + Trajectory Drop Chart
    │   ├── WeatherScreen.kt         # Live GPS station, Wind Clock, Manual Atmosphere
    │   ├── ProfilesScreen.kt        # Guns / Scopes / Ammo manager & Catalog browser
    │   └── RangefinderAndSettingsScreen.kt # LBC1800 BLE Console + Precision Preferences
    └── theme/
        └── Theme.kt                 # Field Dark, OLED Night Red, Daylight palettes & tabular typography
```

### Run Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```
Includes **17 unit tests** across `BallisticEngineTest` (G1/G7 table alignment, 3D inclined wind, Coriolis, aero jump, MV truing, MPBR, single-pass DOPE consistency) and `Lbc1800ProtocolTest` (XOR checksum verification, HDLC chunked deframing, `:BP` / `:AV` frame encoding).
