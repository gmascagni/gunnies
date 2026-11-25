# Custom Drag Curve & Profile Import Schemas

Gunnies Ballistics supports user-supplied **Custom Cd-vs-Mach drag tables** per ammo load (in addition to standard BRL/JBM G1, G7, and up to 5 velocity-banded Multi-BC pairs) as well as **Strelok-style profile imports**.

---

## 1. Custom Drag Curve Physics Convention

A custom drag curve specifies the projectile's actual aerodynamic drag coefficient $C_d(M)$ across Mach numbers. During RK4 integration, `BallisticEngine` evaluates $C_d(M)$ via binary-search linear interpolation (zero allocations per step) and applies the projectile's **sectional density**:

$$\text{SD} = \frac{m_{\text{grains}} / 7000}{d_{\text{in}}^2}$$

with form factor $i = 1.0$. For a standard G1 or G7 reference projectile where $\text{BC} = \text{SD}$, supplying the G1 or G7 $C_d(M)$ table as a custom curve reproduces the exact G1 or G7 trajectory.

> **Hardware BLE fallback:** Because the Bushnell Bone Collector 1800 (`LBC1800`) `:BP` wire format only accepts G1 or G7 with a single BC, syncing a Custom Cd load to the rangefinder falls back to the load's primary BC (G7 for BC ≥ 0.20, G1 otherwise).

---

## 2. Custom Drag Curve JSON Schema (`schemaVersion = 1`)

```json
{
  "schemaVersion": 1,
  "name": "Example 140 gr Match Custom Cd",
  "source": "User-measured / public reference",
  "points": [
    { "mach": 0.00, "cd": 0.1198 },
    { "mach": 0.50, "cd": 0.1194 },
    { "mach": 0.85, "cd": 0.1306 },
    { "mach": 0.95, "cd": 0.2054 },
    { "mach": 1.05, "cd": 0.4043 },
    { "mach": 1.20, "cd": 0.3884 },
    { "mach": 1.50, "cd": 0.3440 },
    { "mach": 2.00, "cd": 0.2980 },
    { "mach": 2.50, "cd": 0.2697 },
    { "mach": 3.00, "cd": 0.2442 }
  ]
}
```

### Validation Rules
- `schemaVersion`: Must be `1`.
- `points`: Between **2** and **128** points (automatically sorted ascending by `mach` on import).
- `mach`: Finite number in **`0.0` to `5.0`**; duplicate Mach values are rejected.
- `cd`: Finite number in **`0.01` to `3.0`**.

---

## 3. Custom Drag Curve CSV Format

```csv
# name: Example 140 gr Match Custom Cd
# source: User-measured / public reference
mach,cd
0.0000,0.11980
0.5000,0.11940
0.9500,0.20540
1.0500,0.40430
1.5000,0.34400
2.5000,0.26970
```

- Optional `# name:` and `# source:` metadata comment lines.
- Optional `mach,cd` header row.
- Comma, semicolon, or tab delimiters accepted; UTF-8 BOM and CRLF/LF line endings handled automatically.

---

## 4. Strelok-Style Profile Import (`ProfileImportCodec`)

From **Arsenal Setup → Import**, Gunnies can import single-profile exports in **Strelok XML**, **JSON**, **key=value (`.srl` / `.txt`)**, or **2-row CSV** formats.

Recognized fields (case-insensitive, punctuation-insensitive):
- **Rifle:** `rifle_name` / `rifle`, `caliber`, `barrel_length`, `twist`, `twist_direction` (`right`/`left`/`none`)
- **Scope:** `scope_name` / `scope`, `sight_height`, `zero_distance` / `zero`, `click_unit` (`1/4 MOA`, `0.1 MRAD`, etc.)
- **Ammo:** `cartridge_name` / `ammo`, `drag_function` (`G1`/`G7`), `bc` (or multi-BC `bc1..bc5` paired with `v1..v5`), `mv` / `muzzle_velocity` (or `mv_ms`), `bullet_weight`, `bullet_diameter`, `bullet_length`, `temp_sensitivity`, `chrono_temp`
