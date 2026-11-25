# Gunnies Ballistics — Data-Driven Reticle Schema (`schemaVersion = 1`)

Gunnies Ballistics defines reticles as declarative JSON documents validated by [`ReticleDefinitionCodec.kt`](../../app/src/main/java/com/gunnies/ballistics/data/ReticleDefinitionCodec.kt). New reticles can be added to `app/src/main/assets/reticles/*.json` without modifying Kotlin rendering code.

## JSON Fields

| Field | Type | Required | Constraints / Notes |
| :--- | :--- | :---: | :--- |
| `schemaVersion` | integer | Yes | Must be `1`. |
| `id` | string | Yes | Unique kebab-case identifier (e.g., `"generic-mil-tree-02"`). |
| `name` | string | Yes | Human-readable reticle display name. |
| `focalPlane` | string | Yes | `"FFP"`, `"SFP"`, or `"NON_MAGNIFYING"`. |
| `unit` | string | Yes | `"MRAD"` or `"MOA"`. |
| `stadiaStyle` | string | Yes | `"CHRISTMAS_TREE"`, `"DUPLEX"`, `"RED_DOT"`, `"HOLOGRAPHIC_RING"`, or `"PISTOL_IRONS"`. |
| `defaultZoom` | number | Yes | Must fall within `[minZoom, maxZoom]`. |
| `minZoom` / `maxZoom` | number | Yes | `1.0 <= minZoom <= maxZoom <= 60.0`. |
| `maxSubtension` | number | Yes | Total half-span subtension in `unit` (`1.0..150.0`). |
| `majorTickStep` | number | Yes | Major hash spacing (`> 0`). |
| `minorTickStep` | number | Yes | Minor hash spacing (`> 0` and `<= majorTickStep`). |
| `treeStartHold` / `treeMaxHold` | number | No | Vertical hold start/end for Christmas-tree windage dots (`0` if none). |
| `centerDotSize` | number | No | Center dot diameter in `unit`. |
| `outerRingDiameter` | number | No | Outer ring diameter in `unit` (`65` or `68` MOA for red dot / holographic). |
| `illuminatedByDefault` | boolean | No | Defaults to `false`. |
| `author` / `notes` | string | No | Attribution and subtension notes. |
