# Contributing to Gunnies Ballistics

Thanks for helping improve Gunnies Ballistics! We welcome contributions across physics validation, data-driven reticles, manufacturer-attributed ammo/bullet specs, and Android UX.

## 1. Contributing Reticles (No Kotlin Required)
1. Review the JSON schema in [`docs/schemas/reticle-definition.md`](docs/schemas/reticle-definition.md).
2. Add a new `.json` file under `app/src/main/assets/reticles/` (use [`generic_mil_tree_02.json`](app/src/main/assets/reticles/generic_mil_tree_02.json) as a template).
3. Verify subtensions against the optic manufacturer's published reticle manual (do not copy trademarked logos or proprietary SVG artwork without permission).
4. Run `./gradlew testDebugUnitTest` to confirm `ReticleDefinitionTest` validates cleanly.

## 2. Contributing Bullet & Ammo Catalog Data
- **Attribution Required:** Only submit manufacturer-published BCs, weights, diameters, and muzzle velocities (e.g., Berger, Sierra, Hornady, Lapua, Federal, CCI, SK, Eley) or your own measured chronograph/truing data.
- **No Unlicensed Third-Party Datasets:** Do **not** submit proprietary Applied Ballistics CDMs, Hornady Doppler tables, or scraped commercial solver databases without written permission.
- Include the source in the `notes` field (e.g., `"Manufacturer published G7 BC"`).

## 3. Contributing Custom `Cd(Mach)` Drag Curves
- Follow [`docs/schemas/custom-drag-curve.md`](docs/schemas/custom-drag-curve.md).
- Include clear provenance (`source` field) and strictly increasing Mach knots (`0.0–5.0`).

## 4. Solver & Code Changes
- Keep `BallisticEngine.kt` off the main thread (`Dispatchers.Default`) and zero-allocation inside RK4 loops.
- Existing saved profiles must remain compatible (non-destructive DataStore/JSON migrations only).
- Do not change solver output for existing profiles without updating [`VALIDATION.md`](VALIDATION.md) and adding unit tests.
- Every PR must pass `./gradlew testDebugUnitTest assembleDebug`.
