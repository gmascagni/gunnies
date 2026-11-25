# Changelog

## [Unreleased]

### Added
- **Phase 1 — Validation & CI:** Live-captured external reference fixtures (Berger Bryan Litz G1/G7 & ShootersCalculator across `.22 LR`, `.223 Rem`, `.308 Win`, `6.5 CM`, `.300 WM`, and `1,200 yd` high-altitude inclined fire), `BallisticValidationTest`, `VALIDATION.md`, and PR CI running `testDebugUnitTest` + `assembleDebug`.
- **Phase 2 — Custom Drag Curves & Import:** `DragModel.CUSTOM` (`Cd(Mach)` up to 128 knots), JSON/CSV import & export (`CustomDragCurveCodec.kt`, `docs/schemas/custom-drag-curve.md`), Solver HUD active drag-model badge, 10 attributed manufacturer rimfire/subsonic/precision loads (389 catalog profiles), and Strelok-style profile import (`ProfileImportCodec.kt`).
- **Phase 3 — Better Calibration:** Multi-point Levenberg-Marquardt MV + BC fitting with before/after RMS error and per-point residuals (`fitMvAndBc`), Cold-Bore/Session Logbook shot import into truing, and Tall-Target optic Elevation/Windage SSF calibration workflow.
- **Phase 4 — Uncertainty & Wind:** First-order Gaussian error propagation + exact 2D polar circular-plate Hit Probability estimator (`P(Hit)` modal on Solver HUD) and 3-Zone Wind flight-path corridor visual on the Weather tab.
- **Phase 5 — Hardware & Integrations:** `RangefinderClient` interface, LBC1800 **Auto-Reconnect on Wake** + toggleable **18s `:DU` Keepalive Heartbeat**, 1-tap **Prep Field** (`GPS + Auto-Sync`) on Solver HUD, Kestrel / Bluetooth SIG Environmental Sensing (`0x181A`) live atmosphere decoding, optional **Phone Barometer** (`TYPE_PRESSURE`), and `docs/hardware-protocols.md`.
- **Phase 6 — Data-Driven Reticles:** JSON reticle schema (`ReticleDefinitionCodec.kt`, `docs/schemas/reticle-definition.md`), bundled asset JSON reticles (`app/src/main/assets/reticles/`), and `CONTRIBUTING.md`.
- **Phase 7 — Distribution & Polish:** Apache 2.0 `LICENSE`, `docs/PRIVACY_POLICY.md`, `docs/google-play-readiness.md`, `docs/kmp-ios-feasibility.md`, GitHub bug report issue template, and real-world `LBC1800` viewfinder guide in `README.md` and `docs/index.html`.
