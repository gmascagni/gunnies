# Kotlin Multiplatform (KMP) & iOS Feasibility Assessment

*Status: Architecture Feasibility Study only — no iOS port started.*

## Summary
Sharing the core ballistics engine and data codecs across Android and iOS via **Kotlin Multiplatform (KMP)** is **highly feasible** with minimal refactoring, because the solver and models are already pure Kotlin with near-zero Android framework dependencies.

## Module Portability Audit

| Layer | Files | Android Dependencies | KMP Effort |
| :--- | :--- | :--- | :--- |
| **Ballistics Engine** | `BallisticEngine.kt`, `DragTables.kt` | `java.lang.Math.toRadians` / `toDegrees` (trivial swap to `kotlin.math.PI`), `System.arraycopy` (swap to `copyInto`). | **Low (1–2 days)** |
| **Models & Codecs** | `BallisticsModels.kt`, `BallisticsCatalog.kt`, `CustomDragCurveCodec.kt`, `ProfileImportCodec.kt`, `ArsenalBackupCodec.kt`, `ReticleDefinitionCodec.kt`, `Lbc1800Protocol.kt`, `KestrelBleProtocol.kt` | `java.util.UUID`, `java.nio.ByteBuffer` (swap to `kotlin.uuid.Uuid` and bit-shifts), `kotlinx.serialization` is already KMP-native. | **Low (2–3 days)** |
| **Persistence** | `BallisticsRepository.kt` | Jetpack DataStore (`androidx.datastore` now has KMP support for Android & iOS!). | **Low–Medium (2 days)** |
| **Platform Hardware** | `RangefinderBleManager.kt`, `BarometerSensorService.kt`, `WeatherService.kt`, `InclinometerDialog.kt` | Android BLE (`BluetoothGatt`), `SensorManager`, `FusedLocation`. Requires `expect/actual` wrappers using Apple `CoreBluetooth`, `CMAltimeter`, `CoreLocation`, and `CMMotionManager`. | **Medium–High (2–3 weeks)** |
| **UI Layer** | Compose Screens & Canvas (`SolverScreen`, `ReticleViewCard`, etc.) | Compose Multiplatform (`CMP`) supports Material3 + Compose `Canvas` on iOS, except Android `nativeCanvas.drawText` (needs `TextMeasurer` in Compose). | **Medium (1–2 weeks)** |

## Recommendation
1. **First** prove adoption and live-fire trust on Android (Play Store release + user feedback).
2. **When iOS demand justifies it**, extract `:shared-ballistics` as a KMP module (engine + models + codecs + unit tests) first, then choose between Compose Multiplatform UI or native SwiftUI consuming the shared XCFramework.
