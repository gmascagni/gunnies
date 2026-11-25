# Gunnies Ballistics — External Solver Validation

This document publishes side-by-side validation results comparing **`BallisticEngine`** (`BallisticEngine.kt` + `DragTables.kt`) against live-captured reference trajectories from external ballistics solvers.

Every fixture in [`app/src/test/resources/validation/reference_fixtures.json`](app/src/test/resources/validation/reference_fixtures.json) is executed automatically by [`BallisticValidationTest.kt`](app/src/test/java/com/gunnies/ballistics/engine/BallisticValidationTest.kt) during `./gradlew testDebugUnitTest` and on every GitHub Actions pull request. Any regression beyond the documented per-fixture tolerances fails CI.

---

## Reference Sources & Capture Notes (`2026-11-25`)

| Reference Tool | Status | Notes |
| :--- | :--- | :--- |
| **Berger Bullets Ballistics Calculator** (`https://bergerbullets.com/ballistics-calculator/`) | **Captured (6 cases)** | Powered by Bryan Litz's G1/G7 ballistic solver. Uses Temperature + Altitude (ICAO dry/standard air; no separate humidity or spin-drift toggle). |
| **ShootersCalculator** (`https://shooterscalculator.com/ballistic-trajectory-chart.php`) | **Captured (2 cross-checks)** | Independent public G1/G7 trajectory solver with explicit Temperature, Barometric Pressure, Altitude, and Relative Humidity (`50%` RH). |
| **JBM Ballistics** (`https://jbmballistics.com/`) | **Retired upstream** | JBM permanently retired its online CGI trajectory calculators (HTTP 404 notice on `jbmtraj-5.1.cgi`). |
| **Hornady 4DOF / Applied Ballistics Quantum** | **Manual contribution ready** | Hornady's web calculator (`hornady.com/4dof`) is gated behind an 18+ confirmation modal, and AB Quantum requires a licensed account. Fixtures from either tool can be appended directly to `reference_fixtures.json` using the same schema. |

> **Sign Convention:** All tables below report elevation as **come-up MOA** (`+` = dial UP / bullet below line of sight, `-` = dial DOWN / bullet above line of sight) and windage as **windward hold MOA** (`+` = hold/dial RIGHT into a 3 o'clock / `90°` crosswind).

---

## Summary of Results & CI Tolerances

| Benchmark Fixture | Source | Max \|ΔElev\| (MOA) | Elev Tol | Max \|ΔWind\| (MOA) | Wind Tol | Max \|ΔVel\| | Max \|ΔTOF\| |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.22 LR` Subsonic 40 gr G1 (25–200 yd) | Berger | `0.39` (`0.12` ≥ 50 yd) | `±0.50` | `0.26` | `±0.50` | `0.35%` | `0.00s` |
| `.22 LR` Subsonic 40 gr G1 (50% RH, 25–200 yd) | ShootersCalc | `0.05` | `±0.50` | `0.33` | `±0.50` | `0.27%` | `0.00s` |
| `.223 Rem` 77 gr SMK G7 (100–600 yd) | Berger | `0.03` | `±0.20` | `0.09` | `±0.20` | `0.04%` | `0.00s` |
| `.308 Win` 175 gr SMK G7 (100–1,000 yd) | Berger | `0.06` | `±0.20` | `0.06` | `±0.20` | `0.25%` | `0.01s` |
| `.308 Win` 175 gr SMK G7 (50% RH, 100–1,000 yd) | ShootersCalc | `0.66` | `±0.75` | `0.33` | `±0.45` | `2.38%` | `0.02s` |
| `6.5 Creedmoor` 140 gr ELD-M G7 (100–1,200 yd) | Berger | `0.12` | `±0.25` | `0.05` | `±0.20` | `0.26%` | `0.01s` |
| `.300 Win Mag` 200 gr ELD-X G7 (100–1,000 yd) | Berger | `0.05` | `±0.20` | `0.05` | `±0.20` | `0.19%` | `0.00s` |
| `6.5 CM` 1,200 yd (`5,000 ft`, `35°F`, `+15°`, `12 mph`) | Berger | `0.58` | `±0.75` | `0.26` | `±0.55` | `2.26%` | `0.03s` |

---

## Detailed Side-by-Side Comparison Tables

### 1. `.22 LR` Subsonic — 40 gr LRN (`G1 0.120`, `1070 fps`, `1.75"` SH, `50 yd` Zero, `10 mph` @ 3:00)

#### 1a. vs. Berger (Bryan Litz G1, `59°F`, `29.92 inHg`, `0%` RH)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 25 | -0.66 | -1.05 | +0.39 | 1.24 | 1.30 | -0.06 | 1017 | 1017 | +0.04% | 0.07s | 0.07s |
| 50 | 0.00 | 0.00 | 0.00 | 2.39 | 2.45 | -0.06 | 974 | 974 | +0.03% | 0.15s | 0.15s |
| 75 | 3.13 | 3.14 | -0.01 | 3.48 | 3.55 | -0.07 | 937 | 936 | +0.16% | 0.23s | 0.23s |
| 100 | 7.06 | 7.08 | -0.02 | 4.52 | 4.74 | -0.22 | 905 | 902 | +0.32% | 0.31s | 0.31s |
| 150 | 16.17 | 16.23 | -0.06 | 6.50 | 6.74 | -0.24 | 849 | 846 | +0.30% | 0.48s | 0.48s |
| 200 | 26.48 | 26.60 | -0.12 | 8.41 | 8.67 | -0.26 | 800 | 797 | +0.35% | 0.66s | 0.66s |

#### 1b. vs. ShootersCalculator (`59°F`, `29.92 inHg`, `50%` RH)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 25 | -0.66 | -0.71 | +0.05 | 1.23 | 1.56 | -0.33 | 1018 | 1018 | -0.02% | 0.07s | 0.07s |
| 50 | 0.00 | -0.02 | +0.02 | 2.38 | 2.54 | -0.16 | 975 | 975 | -0.01% | 0.15s | 0.15s |
| 75 | 3.13 | 3.12 | +0.01 | 3.46 | 3.56 | -0.10 | 938 | 939 | -0.09% | 0.23s | 0.23s |
| 100 | 7.05 | 7.04 | +0.01 | 4.49 | 4.56 | -0.07 | 906 | 907 | -0.14% | 0.31s | 0.31s |
| 150 | 16.14 | 16.12 | +0.02 | 6.46 | 6.48 | -0.02 | 849 | 851 | -0.18% | 0.48s | 0.48s |
| 200 | 26.44 | 26.39 | +0.05 | 8.36 | 8.34 | +0.02 | 801 | 803 | -0.27% | 0.66s | 0.66s |

---

### 2. `.223 Rem / 5.56` — 77 gr SMK (`G7 0.190`, `2750 fps`, `2.60"` SH, `100 yd` Zero, `10 mph` @ 3:00 — Berger)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.00 | 0.00 | 0.00 | 0.86 | 0.94 | -0.08 | 2510 | 2510 | -0.01% | 0.11s | 0.11s |
| 200 | 1.40 | 1.41 | -0.01 | 1.80 | 1.88 | -0.08 | 2281 | 2282 | -0.02% | 0.24s | 0.24s |
| 300 | 4.00 | 4.01 | -0.01 | 2.83 | 2.91 | -0.08 | 2066 | 2066 | 0.00% | 0.38s | 0.38s |
| 400 | 7.25 | 7.27 | -0.02 | 3.97 | 4.05 | -0.08 | 1862 | 1862 | 0.00% | 0.53s | 0.53s |
| 500 | 11.13 | 11.15 | -0.02 | 5.23 | 5.31 | -0.08 | 1668 | 1668 | +0.01% | 0.70s | 0.70s |
| 600 | 15.71 | 15.74 | -0.03 | 6.64 | 6.73 | -0.09 | 1483 | 1484 | -0.04% | 0.89s | 0.89s |

---

### 3. `.308 Win` — 175 gr SMK (`G7 0.243`, `2600 fps`, `1.75"` SH, `100 yd` Zero, `10 mph` @ 3:00)

#### 3a. vs. Berger (Bryan Litz G7, `59°F`, `29.92 inHg`, `0%` RH)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.00 | 0.00 | 0.00 | 0.72 | 0.78 | -0.06 | 2417 | 2417 | -0.01% | 0.12s | 0.12s |
| 300 | 4.89 | 4.89 | 0.00 | 2.33 | 2.39 | -0.06 | 2073 | 2074 | -0.03% | 0.39s | 0.39s |
| 500 | 12.10 | 12.10 | 0.00 | 4.20 | 4.26 | -0.06 | 1759 | 1759 | -0.03% | 0.70s | 0.70s |
| 700 | 21.41 | 21.40 | +0.01 | 6.43 | 6.49 | -0.06 | 1467 | 1469 | -0.14% | 1.08s | 1.07s |
| 900 | 33.54 | 33.51 | +0.03 | 9.14 | 9.19 | -0.05 | 1201 | 1204 | -0.25% | 1.53s | 1.52s |
| 1000 | 41.04 | 40.98 | +0.06 | 10.71 | 10.75 | -0.04 | 1089 | 1091 | -0.20% | 1.79s | 1.78s |

#### 3b. vs. ShootersCalculator (`59°F`, `29.92 inHg`, `50%` RH)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.00 | 0.00 | 0.00 | 0.72 | 0.74 | -0.02 | 2417 | 2421 | -0.15% | 0.12s | 0.12s |
| 300 | 4.89 | 4.86 | +0.03 | 2.32 | 2.28 | +0.04 | 2075 | 2084 | -0.44% | 0.39s | 0.39s |
| 500 | 12.09 | 12.01 | +0.08 | 4.19 | 4.10 | +0.09 | 1761 | 1774 | -0.76% | 0.70s | 0.70s |
| 700 | 21.38 | 21.17 | +0.21 | 6.41 | 6.25 | +0.16 | 1470 | 1490 | -1.37% | 1.07s | 1.07s |
| 900 | 33.47 | 33.01 | +0.46 | 9.10 | 8.84 | +0.26 | 1204 | 1228 | -1.96% | 1.53s | 1.51s |
| 1000 | 40.94 | 40.28 | +0.66 | 10.66 | 10.33 | +0.33 | 1091 | 1118 | -2.38% | 1.79s | 1.77s |

---

### 4. `6.5 Creedmoor` — 140 gr ELD-M (`G7 0.326`, `2710 fps`, `1.75"` SH, `100 yd` Zero, `10 mph` @ 3:00 — Berger)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.00 | 0.00 | 0.00 | 0.50 | 0.55 | -0.05 | 2570 | 2570 | -0.01% | 0.11s | 0.11s |
| 300 | 4.11 | 4.10 | +0.01 | 1.59 | 1.63 | -0.04 | 2301 | 2302 | -0.04% | 0.36s | 0.36s |
| 500 | 9.97 | 9.95 | +0.02 | 2.80 | 2.84 | -0.04 | 2050 | 2052 | -0.11% | 0.64s | 0.63s |
| 700 | 17.02 | 16.99 | +0.03 | 4.15 | 4.19 | -0.04 | 1814 | 1816 | -0.10% | 0.95s | 0.94s |
| 900 | 25.45 | 25.39 | +0.06 | 5.69 | 5.72 | -0.03 | 1592 | 1594 | -0.14% | 1.30s | 1.30s |
| 1000 | 30.29 | 30.22 | +0.07 | 6.54 | 6.57 | -0.03 | 1485 | 1488 | -0.19% | 1.50s | 1.49s |
| 1200 | 41.51 | 41.39 | +0.12 | 8.44 | 8.46 | -0.02 | 1283 | 1286 | -0.26% | 1.93s | 1.92s |

---

### 5. `.300 Win Mag` — 200 gr ELD-X (`G7 0.300`, `2850 fps`, `1.75"` SH, `100 yd` Zero, `10 mph` @ 3:00 — Berger)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.00 | 0.00 | 0.00 | 0.51 | 0.55 | -0.04 | 2694 | 2694 | -0.02% | 0.11s | 0.11s |
| 300 | 3.65 | 3.65 | 0.00 | 1.61 | 1.66 | -0.05 | 2395 | 2396 | -0.06% | 0.34s | 0.34s |
| 500 | 9.04 | 9.03 | +0.01 | 2.85 | 2.89 | -0.04 | 2116 | 2117 | -0.06% | 0.61s | 0.61s |
| 700 | 15.61 | 15.59 | +0.02 | 4.25 | 4.29 | -0.04 | 1856 | 1858 | -0.10% | 0.91s | 0.91s |
| 900 | 23.58 | 23.54 | +0.04 | 5.85 | 5.89 | -0.04 | 1612 | 1615 | -0.16% | 1.26s | 1.26s |
| 1000 | 28.20 | 28.15 | +0.05 | 6.74 | 6.78 | -0.04 | 1496 | 1499 | -0.19% | 1.45s | 1.45s |

---

### 6. `1,000+ yd` Extended / High-Altitude & `+15°` Incline — `6.5 Creedmoor` 140 gr ELD-M (`5,000 ft`, `35°F`, `12 mph` @ 3:00 — Berger)
| Range (yd) | Gunnies Elev | Ref Elev | ΔElev (MOA) | Gunnies Wind | Ref Wind | ΔWind (MOA) | Gunnies Vel | Ref Vel | ΔVel | Gunnies TOF | Ref TOF |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 300 | 3.74 | 3.73 | +0.01 | 1.63 | 1.64 | -0.01 | 2352 | 2360 | -0.34% | 0.36s | 0.35s |
| 600 | 12.24 | 12.16 | +0.08 | 3.50 | 3.46 | +0.04 | 2022 | 2038 | -0.78% | 0.77s | 0.76s |
| 900 | 23.01 | 22.77 | +0.24 | 5.69 | 5.56 | +0.13 | 1718 | 1742 | -1.37% | 1.25s | 1.24s |
| 1000 | 27.20 | 26.87 | +0.33 | 6.50 | 6.34 | +0.16 | 1622 | 1649 | -1.65% | 1.43s | 1.42s |
| 1200 | 36.69 | 36.11 | +0.58 | 8.28 | 8.02 | +0.26 | 1436 | 1469 | -2.26% | 1.83s | 1.80s |

---

## Known Deviations & Explanations

1. **`.22 LR` Subsonic at `25 yd` vs. Berger (`+0.39 MOA`, `0.10 in`):** At `25 yd`, `1 MOA ≈ 0.26 in`, so a `0.10 in` difference near the muzzle maps to `0.39 MOA`. Gunnies (`-0.66 MOA`) agrees closely with ShootersCalculator (`-0.71 MOA`, `Δ = +0.05 MOA`); Berger's web output (`-1.05 MOA`) reflects coarse early-range integration / zeroing discretization, and converges to within `0.01–0.12 MOA` from `50 yd` to `200 yd`.
2. **`.308 Win` 175 gr SMK at `900–1,000 yd` vs. ShootersCalculator (`+0.46 to +0.66 MOA`):** ShootersCalculator's G7 curve retains `~27 fps` higher velocity at `1,000 yd` (`1,118 fps`) than both **Berger/Bryan Litz** (`1,091 fps`, `40.98 MOA`) and **Gunnies** (`1,091 fps`, `40.94 MOA` at 50% RH; `1,089 fps`, `41.04 MOA` at 0% RH). Gunnies matches Berger to within `0.06 MOA` at `1,000 yd`; the wider `±0.75 MOA` tolerance on the ShootersCalculator `.308` cross-check accounts for ShootersCalculator's lower transonic G7 drag.
3. **High-Altitude + Cold Air (`5,000 ft`, `35°F`, `+15°` Incline) at `1,200 yd` (`+0.58 MOA`, `-2.26%` velocity):** Berger's web calculator takes Altitude + Temperature without a separate Station Pressure field and uses an altitude-lapse atmospheric density approximation, whereas Gunnies converts sea-level `29.92 inHg` at `5,000 ft` (`24.90 inHg` station) and computes exact ideal-gas density at `35°F` (slightly denser cold air → slightly more drag at `1,200 yd`).
4. **Small constant windage offset (`~0.04–0.08 MOA`) vs. Berger:** Berger's web calculator includes a small near-muzzle windage offset convention at `100 yd` (`0.04–0.08 MOA` higher than Gunnies' pure zero-wind-at-zero-range 3-DOF integration), remaining constant across `100–1,200 yd`.

---

## Reproducing Locally
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.gunnies.ballistics.engine.BallisticValidationTest"
```
