package com.bc1800.ballistics.data

import com.bc1800.ballistics.model.AmmoCategory
import com.bc1800.ballistics.model.AmmoLoad
import com.bc1800.ballistics.model.ClickUnit
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.FirearmCategory
import com.bc1800.ballistics.model.FirearmSetup
import com.bc1800.ballistics.model.ReticleType
import com.bc1800.ballistics.model.ScopeCategory
import com.bc1800.ballistics.model.ScopeSetup
import com.bc1800.ballistics.model.SuppressorCategory
import com.bc1800.ballistics.model.SuppressorSetup
import com.bc1800.ballistics.model.TwistDirection
import com.bc1800.ballistics.model.WeaponMode

/**
 * Lightweight, instant-search offline reference catalog of popular rifles, handguns, scopes,
 * red dots, and factory ammunition loads (~25 KB in-memory, zero startup overhead).
 * Users can browse & add any item to their personal Setup list in one tap without cluttering Favorites.
 */
object BallisticsCatalog {

    val firearms: List<FirearmSetup> = listOf(
        // Rimfire Rifles
        FirearmSetup("cat-gun-cz457", "CZ 457 (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 20.5, 16.0, TwistDirection.RIGHT, false, "Bolt-action rimfire • 1:16\" RH"),
        FirearmSetup("cat-gun-ruger1022", "Ruger 10/22 Carbine (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 18.5, 16.0, TwistDirection.RIGHT, false, "Semi-auto rimfire • 1:16\" RH"),
        FirearmSetup("cat-gun-tikka-t1x", "Tikka T1x MTR (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 20.0, 16.5, TwistDirection.RIGHT, false, "Precision rimfire bolt-action • 1:16.5\" RH"),
        FirearmSetup("cat-gun-bergara-b14r", "Bergara B-14R (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 18.0, 16.0, TwistDirection.RIGHT, false, "Rem 700 footprint precision .22 LR trainer"),
        FirearmSetup("cat-gun-savage-b22", "Savage B22 Precision (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 18.0, 16.0, TwistDirection.RIGHT, false, "MDT chassis rimfire bolt-action"),
        FirearmSetup("cat-gun-ruger-rar", "Ruger American Rimfire (.22 LR)", FirearmCategory.RIFLE, ".22 LR", 18.0, 16.0, TwistDirection.RIGHT, false, "10/22 magazine compatible bolt rifle"),

        // AR / Modern Sporting Rifles
        FirearmSetup("cat-gun-anderson-ar15", "Anderson AR-15 16\" (.223 / 5.56)", FirearmCategory.RIFLE, ".223 Rem / 5.56", 16.0, 8.0, TwistDirection.RIGHT, false, "16\" carbine/mid-length • 1:8\" RH"),
        FirearmSetup("cat-gun-ar15-16-17", "AR-15 16\" 1:7 Twist (5.56 NATO)", FirearmCategory.RIFLE, "5.56x45mm NATO", 16.0, 7.0, TwistDirection.RIGHT, false, "Stabilizes 55–77gr match bullets • 1:7\" RH"),
        FirearmSetup("cat-gun-ar15-18-spr", "AR-15 18\" SPR (.223 Wylde)", FirearmCategory.RIFLE, ".223 Wylde / 5.56", 18.0, 8.0, TwistDirection.RIGHT, false, "18\" precision rifle-gas barrel • 1:8\" RH"),
        FirearmSetup("cat-gun-ar15-20", "AR-15 20\" Rifle (5.56 NATO)", FirearmCategory.RIFLE, "5.56x45mm NATO", 20.0, 7.0, TwistDirection.RIGHT, false, "Full-velocity 20\" barrel • 1:7\" RH"),
        FirearmSetup("cat-gun-ar10-308", "AR-10 / LR-308 18\" (.308 Win)", FirearmCategory.RIFLE, ".308 Winchester", 18.0, 10.0, TwistDirection.RIGHT, false, "Large-frame gas gun • 1:10\" RH"),

        // Bolt-Action Centerfire Rifles
        FirearmSetup("cat-gun-tikka-t3x-65", "Tikka T3x Lite / CTR (6.5 Creedmoor)", FirearmCategory.RIFLE, "6.5 Creedmoor", 24.0, 8.0, TwistDirection.RIGHT, false, "24\" barrel • 1:8\" RH"),
        FirearmSetup("cat-gun-tikka-t3x-308", "Tikka T3x Lite (.308 Win)", FirearmCategory.RIFLE, ".308 Winchester", 22.4, 11.0, TwistDirection.RIGHT, false, "Lightweight hunting rifle • 1:11\" RH"),
        FirearmSetup("cat-gun-bergara-hmr-65", "Bergara B-14 HMR (6.5 Creedmoor)", FirearmCategory.RIFLE, "6.5 Creedmoor", 22.0, 8.0, TwistDirection.RIGHT, false, "Hunting & Match Rifle • 1:8\" RH"),
        FirearmSetup("cat-gun-bergara-hmr-308", "Bergara B-14 HMR (.308 Win)", FirearmCategory.RIFLE, ".308 Winchester", 20.0, 10.0, TwistDirection.RIGHT, false, "Hunting & Match Rifle • 1:10\" RH"),
        FirearmSetup("cat-gun-ruger-am-pred-65", "Ruger American Predator (6.5 CM)", FirearmCategory.RIFLE, "6.5 Creedmoor", 22.0, 8.0, TwistDirection.RIGHT, false, "Threaded hunting barrel • 1:8\" RH"),
        FirearmSetup("cat-gun-ruger-am-ranch-223", "Ruger American Ranch (.223 / 5.56)", FirearmCategory.RIFLE, ".223 Rem / 5.56", 16.1, 8.0, TwistDirection.RIGHT, false, "Compact AR-mag bolt rifle • 1:8\" RH"),
        FirearmSetup("cat-gun-rem700-308", "Remington 700 SPS / Tactical (.308)", FirearmCategory.RIFLE, ".308 Winchester", 20.0, 10.0, TwistDirection.RIGHT, false, "Classic short-action bolt rifle • 1:10\" RH"),
        FirearmSetup("cat-gun-savage110-270", "Savage 110 Hunter (.270 Win)", FirearmCategory.RIFLE, ".270 Winchester", 22.0, 10.0, TwistDirection.RIGHT, false, "AccuTrigger hunting rifle • 1:10\" RH"),
        FirearmSetup("cat-gun-win70-3006", "Winchester Model 70 (.30-06 Sprg)", FirearmCategory.RIFLE, ".30-06 Springfield", 24.0, 10.0, TwistDirection.RIGHT, false, "Controlled-round-feed big-game rifle • 1:10\" RH"),
        FirearmSetup("cat-gun-xbolt-300wm", "Browning X-Bolt (.300 Win Mag)", FirearmCategory.RIFLE, ".300 Win Mag", 26.0, 10.0, TwistDirection.RIGHT, false, "Magnum mountain hunting rifle • 1:10\" RH"),
        FirearmSetup("cat-gun-marlin-336", "Marlin 336 Lever-Action (.30-30 Win)", FirearmCategory.RIFLE, ".30-30 Winchester", 20.0, 10.0, TwistDirection.RIGHT, false, "Classic woods lever rifle • 1:10\" RH"),

        // Handguns
        FirearmSetup("cat-gun-glock17", "Glock 17 Gen 4/5 (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 4.49, 9.84, TwistDirection.RIGHT, false, "Full-size duty pistol • 1:9.84\" RH"),
        FirearmSetup("cat-gun-glock19", "Glock 19 Gen 4/5 (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 4.02, 9.84, TwistDirection.RIGHT, false, "Compact carry pistol • 1:9.84\" RH"),
        FirearmSetup("cat-gun-glock43x", "Glock 43X (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 3.41, 9.84, TwistDirection.RIGHT, false, "Slimline concealed carry • 1:9.84\" RH"),
        FirearmSetup("cat-gun-sig-p365", "Sig Sauer P365 / XL (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 3.7, 10.0, TwistDirection.RIGHT, false, "Micro-compact 9mm • 1:10\" RH"),
        FirearmSetup("cat-gun-sig-p320", "Sig Sauer P320 (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 4.7, 10.0, TwistDirection.RIGHT, false, "Modular striker-fired pistol • 1:10\" RH"),
        FirearmSetup("cat-gun-mp9", "S&W M&P9 M2.0 (9mm)", FirearmCategory.HANDGUN, "9mm Luger", 4.25, 10.0, TwistDirection.RIGHT, false, "Duty / range 9mm pistol • 1:10\" RH"),
        FirearmSetup("cat-gun-1911-45", "1911 Government 5\" (.45 ACP)", FirearmCategory.HANDGUN, ".45 ACP", 5.0, 16.0, TwistDirection.LEFT, false, "Classic single-action .45 ACP • 1:16\" LH"),
        FirearmSetup("cat-gun-ruger-mk4", "Ruger Mark IV Target (.22 LR)", FirearmCategory.HANDGUN, ".22 LR", 5.5, 16.0, TwistDirection.RIGHT, false, "Bull-barrel rimfire target pistol • 1:16\" RH")
    )

    val scopes: List<ScopeSetup> = listOf(
        // Arken
        ScopeSetup("cat-sc-arken-sh4-416", "Arken SH-4 Gen II 4-16x50", "34mm FFP • VPR MIL (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 1.85, 50.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Zero Stop • 0.1 MRAD • VPR MIL Christmas Tree"),
        ScopeSetup("cat-sc-arken-sh4-624", "Arken SH-4 Gen II 6-24x50", "34mm FFP • VPR MIL (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 1.90, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Long-range 6-24x50 FFP • VPR MIL"),
        ScopeSetup("cat-sc-arken-ep5", "Arken EP-5 5-25x56 FFP", "34mm ED Glass • VPR MIL (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 2.00, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Japanese ED Glass • 34mm tube • 0.1 MRAD"),

        // Primary Arms
        ScopeSetup("cat-sc-pa-414-moa", "Primary Arms SLx 4-14x44 FFP (MOA)", "30mm FFP • ARC-2 MOA (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ARC2_MOA, WeaponMode.RIFLE, 2.50, 50.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "1/4 MOA turrets • ARC-2 MOA FFP"),
        ScopeSetup("cat-sc-pa-414-acss", "Primary Arms SLx 4-14x44 FFP (ACSS)", "30mm FFP • ACSS / R-Grid (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ACSS_RGRID, WeaponMode.RIFLE, 2.50, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "ACSS Chevron + Horseshoe + R-Grid tree"),
        ScopeSetup("cat-sc-pa-16-nova", "Primary Arms SLx 1-6x24 LPVO", "30mm • ACSS Nova / Aurora (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ACSS_RGRID, WeaponMode.RIFLE, 2.60, 100.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "AR-15 LPVO • 1.54\" cantilever mount (~2.60\" SH)"),
        ScopeSetup("cat-sc-pa-318", "Primary Arms SLx 3-18x50 FFP", "30mm FFP • ACSS Athena / Apollo", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ACSS_RGRID, WeaponMode.RIFLE, 1.90, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Mid-to-long-range FFP 0.1 MRAD"),

        // Vortex
        ScopeSetup("cat-sc-vortex-pst2-525", "Vortex Viper PST Gen II 5-25x50", "30mm FFP • EBR-7C MRAD", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 1.90, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "0.1 MRAD Christmas-tree FFP"),
        ScopeSetup("cat-sc-vortex-dbt-416", "Vortex Diamondback Tactical 4-16x44", "30mm FFP • EBR-2C (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 1.85, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Popular rimfire & PRS FFP optic"),
        ScopeSetup("cat-sc-vortex-dbt-624", "Vortex Diamondback Tactical 6-24x50", "30mm FFP • EBR-2C (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ARC2_MOA, WeaponMode.RIFLE, 1.90, 100.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "MOA FFP Christmas-tree reticle"),
        ScopeSetup("cat-sc-vortex-crossfire-39", "Vortex Crossfire II 3-9x40", "1\" SFP • Dead-Hold BDC (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.SIMPLE_DUPLEX, WeaponMode.RIFLE, 1.65, 100.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "Classic hunting scope"),
        ScopeSetup("cat-sc-vortex-se-18", "Vortex Strike Eagle 1-8x24", "30mm • AR-BDC3 (1/2 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ACSS_RGRID, WeaponMode.RIFLE, 2.60, 100.0, clickUnit = ClickUnit.HALF_MOA, notes = "AR-15 LPVO • 1/2 MOA clicks"),

        // Leupold, Bushnell, Athlon, Nightforce
        ScopeSetup("cat-sc-leupold-vxf-39", "Leupold VX-Freedom 3-9x40", "1\" SFP • Hunt-Plex (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.SIMPLE_DUPLEX, WeaponMode.RIFLE, 1.60, 100.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "Lightweight classic deer rifle scope"),
        ScopeSetup("cat-sc-leupold-vx5-315", "Leupold VX-5HD 3-15x44 CDS-ZL2", "30mm • Wind-Plex (1/4 MOA)", ScopeCategory.RIFLE_SCOPE, ReticleType.PA_ARC2_MOA, WeaponMode.RIFLE, 1.75, 100.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "CDS-ZL2 ZeroLock hunting dial"),
        ScopeSetup("cat-sc-bushnell-mped", "Bushnell Match Pro ED 5-30x56", "34mm FFP • Deploy MIL 2 (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 2.00, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Flagship PRS / rimfire match scope"),
        ScopeSetup("cat-sc-athlon-ares-etr", "Athlon Ares ETR 4.5-30x56", "34mm FFP • APRS1 MIL (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 2.00, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "34mm precision long-range MIL tree"),
        ScopeSetup("cat-sc-nf-nx8-432", "Nightforce NX8 4-32x50 F1", "30mm FFP • MIL-XT (0.1 MRAD)", ScopeCategory.RIFLE_SCOPE, ReticleType.ARKEN_VPR_MIL, WeaponMode.RIFLE, 1.90, 100.0, clickUnit = ClickUnit.TENTH_MRAD, notes = "Compact heavy-duty FFP MIL-XT"),

        // Red Dots & Handgun Optics
        ScopeSetup("cat-sc-holosun-510c", "Holosun HS510C / EOTech EXPS", "1x Reflex • 2 MOA Dot + 65 MOA Ring", ScopeCategory.RED_DOT_IRON, ReticleType.RED_DOT_CIRCLE_65, WeaponMode.RIFLE, 2.60, 50.0, clickUnit = ClickUnit.HALF_MOA, notes = "AR-15 co-witness reflex • 50/200 yd zero"),
        ScopeSetup("cat-sc-sig-romeo5", "Sig Romeo5 / Aimpoint Micro (2 MOA)", "1x Red Dot • 2 MOA Dot (1/2 MOA)", ScopeCategory.RED_DOT_IRON, ReticleType.RED_DOT_CIRCLE_65, WeaponMode.RIFLE, 2.60, 50.0, clickUnit = ClickUnit.HALF_MOA, notes = "Standard AR-height red dot • 1/2 MOA clicks"),
        ScopeSetup("cat-sc-holosun-507c", "Holosun 507C / Trijicon RMR (Pistol)", "Slide-Mounted Pistol Red Dot", ScopeCategory.HANDGUN_OPTIC, ReticleType.RED_DOT_CIRCLE_65, WeaponMode.RIFLE, 1.00, 25.0, clickUnit = ClickUnit.HALF_MOA, notes = "Slide-mounted handgun optic • 25 yd zero"),
        ScopeSetup("cat-sc-glock-irons", "Glock Factory 3-Dot / U-Notch Irons", "Standard Pistol Iron Sights", ScopeCategory.HANDGUN_OPTIC, ReticleType.HANDGUN_THREE_DOT, WeaponMode.RIFLE, 0.65, 25.0, clickUnit = ClickUnit.QUARTER_MOA, notes = "Standard handgun iron sights • ~0.65\" SH")
    )

    val ammo: List<AmmoLoad> = listOf(
        // .22 LR Rimfire
        AmmoLoad("cat-am-cci-quiet22", "CCI Quiet-22 — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.120, 710.0, 40.0, 0.223, notes = "Subsonic 710 fps Lead Round Nose"),
        AmmoLoad("cat-am-cci-sv", "CCI Standard Velocity — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.120, 1070.0, 40.0, 0.223, notes = "Gold-standard subsonic target .22 LR"),
        AmmoLoad("cat-am-cci-blazer22", "CCI Blazer .22 LR — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.133, 1235.0, 40.0, 0.223, notes = "High Velocity 40gr LRN"),
        AmmoLoad("cat-am-cci-minimag36", "CCI Mini-Mag HP — 36gr CPHP", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.125, 1260.0, 36.0, 0.223, notes = "Copper-Plated Hollow Point"),
        AmmoLoad("cat-am-cci-minimag40", "CCI Mini-Mag Target — 40gr CPRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.125, 1235.0, 40.0, 0.223, notes = "Copper-Plated Round Nose"),
        AmmoLoad("cat-am-win-supersupp45", "Winchester Super Suppressed — 45gr", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.130, 1090.0, 45.0, 0.223, notes = "45gr Black Copper-Plated Subsonic"),
        AmmoLoad("cat-am-sk-stdplus", "SK Standard Plus — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.138, 1073.0, 40.0, 0.223, notes = "Match-grade German rimfire (CZ 457 favorite)"),
        AmmoLoad("cat-am-sk-rm", "SK Rifle Match — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.138, 1073.0, 40.0, 0.223, notes = "High-consistency competition .22 LR"),
        AmmoLoad("cat-am-eley-club", "ELEY Club / Match — 40gr Flat Nose", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.125, 1085.0, 40.0, 0.223, notes = "Precision ELEY flat-nose rimfire"),
        AmmoLoad("cat-am-lapua-cx", "Lapua Center-X — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.140, 1073.0, 40.0, 0.223, notes = "Top-tier NRL22 / PRS Rimfire match ammo"),
        AmmoLoad("cat-am-fed-automatch", "Federal AutoMatch — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.128, 1200.0, 40.0, 0.223, notes = "Bulk semi-auto 10/22 plinking load"),

        // .223 Rem / 5.56 NATO
        AmmoLoad("cat-am-wolf-gold55", "Wolf Gold .223 Rem — 55gr FMJ", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.255, 3150.0, 55.0, 0.224, notes = "Brass-case copper FMJ (3250 fps / 24\")"),
        AmmoLoad("cat-am-ae-556-55", "Federal American Eagle 5.56 — 55gr FMJ", "5.56x45mm NATO", AmmoCategory.RIFLE, DragModel.G1, 0.246, 3165.0, 55.0, 0.224, notes = "XM193-spec 55gr FMJ"),
        AmmoLoad("cat-am-fed-le-62", "Federal LE Tactical .223 — 62gr SP", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.224, 3050.0, 62.0, 0.224, notes = "Tactical Bonded 62gr Soft Point (LE223T3)"),
        AmmoLoad("cat-am-scorpio-55", "STV Scorpio .223 Rem — 55gr FMJ", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.250, 3150.0, 55.0, 0.224, notes = "55gr FMJ range load"),
        AmmoLoad("cat-am-m855-62", "Winchester / Lake City M855 — 62gr Green Tip", "5.56x45mm NATO", AmmoCategory.RIFLE, DragModel.G1, 0.304, 3020.0, 62.0, 0.224, notes = "62gr FMJ steel penetrator"),
        AmmoLoad("cat-am-fgmm-69", "Federal Gold Medal .223 — 69gr SMK", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.301, 2950.0, 69.0, 0.224, notes = "Sierra MatchKing 69gr BTHP"),
        AmmoLoad("cat-am-imi-77", "IMI 5.56 Razor Core — 77gr SMK (Mk 262)", "5.56x45mm NATO", AmmoCategory.RIFLE, DragModel.G7, 0.190, 2740.0, 77.0, 0.224, notes = "Long-range AR-15 77gr Sierra MatchKing"),
        AmmoLoad("cat-am-horn-75-bthp", "Hornady Frontier 5.56 — 75gr BTHP Match", "5.56x45mm NATO", AmmoCategory.RIFLE, DragModel.G1, 0.395, 2790.0, 75.0, 0.224, notes = "Heavy match 5.56 for 1:7 / 1:8 barrels"),

        // Centerfire Hunting / Long-Range
        AmmoLoad("cat-am-65cm-143eldx", "Hornady Precision Hunter 6.5 CM — 143gr ELD-X", "6.5 Creedmoor", AmmoCategory.RIFLE, DragModel.G7, 0.315, 2700.0, 143.0, 0.264, notes = "Premier deer/elk hunting load"),
        AmmoLoad("cat-am-65cm-140eldm", "Hornady Match 6.5 CM — 140gr ELD-M", "6.5 Creedmoor", AmmoCategory.RIFLE, DragModel.G7, 0.326, 2710.0, 140.0, 0.264, notes = "Long-range match 6.5 Creedmoor"),
        AmmoLoad("cat-am-308-168fgmm", "Federal Gold Medal .308 — 168gr SMK", ".308 Winchester", AmmoCategory.RIFLE, DragModel.G1, 0.462, 2650.0, 168.0, 0.308, notes = "Classic .308 match benchmark"),
        AmmoLoad("cat-am-308-175fgmm", "Federal Gold Medal .308 — 175gr SMK", ".308 Winchester", AmmoCategory.RIFLE, DragModel.G7, 0.243, 2600.0, 175.0, 0.308, notes = "M118LR 1,000-yard .308 load"),
        AmmoLoad("cat-am-308-168eldm", "Hornady Match .308 — 168gr ELD-M", ".308 Winchester", AmmoCategory.RIFLE, DragModel.G1, 0.523, 2700.0, 168.0, 0.308, notes = "High-BC .308 match/hunting"),
        AmmoLoad("cat-am-270-130sst", "Hornady Superformance .270 — 130gr SST", ".270 Winchester", AmmoCategory.RIFLE, DragModel.G1, 0.460, 3060.0, 130.0, 0.277, notes = "Flat-shooting whitetail/mule deer load"),
        AmmoLoad("cat-am-3006-180part", "Federal Premium .30-06 — 180gr Partition", ".30-06 Springfield", AmmoCategory.RIFLE, DragModel.G1, 0.474, 2700.0, 180.0, 0.308, notes = "Nosler Partition big-game load"),
        AmmoLoad("cat-am-300wm-200eldx", "Hornady Precision Hunter .300 WM — 200gr ELD-X", ".300 Win Mag", AmmoCategory.RIFLE, DragModel.G7, 0.300, 2850.0, 200.0, 0.308, notes = "Long-range magnum hunting load"),
        AmmoLoad("cat-am-3030-160ftx", "Hornady LEVERevolution .30-30 — 160gr FTX", ".30-30 Winchester", AmmoCategory.RIFLE, DragModel.G1, 0.330, 2400.0, 160.0, 0.308, notes = "Flex-tip modern lever-action load"),

        // Handgun Ammo
        AmmoLoad("cat-am-9mm-syntech124", "Federal Syntech Range 9mm — 124gr TSJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.150, 1110.0, 124.0, 0.355, notes = "Clean polymer-jacketed range load"),
        AmmoLoad("cat-am-9mm-blazer115", "CCI Blazer Brass 9mm — 115gr FMJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.125, 1145.0, 115.0, 0.355, notes = "Standard 115gr brass-case practice ammo"),
        AmmoLoad("cat-am-9mm-blazer124", "CCI Blazer Brass 9mm — 124gr FMJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.145, 1090.0, 124.0, 0.355, notes = "Standard 124gr NATO-weight practice ammo"),
        AmmoLoad("cat-am-9mm-hst124", "Federal HST 9mm — 124gr JHP", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.147, 1150.0, 124.0, 0.355, notes = "Premier defensive hollow point"),
        AmmoLoad("cat-am-9mm-hst147", "Federal HST 9mm — 147gr JHP", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.180, 1000.0, 147.0, 0.355, notes = "Subsonic heavy defensive hollow point"),
        AmmoLoad("cat-am-9mm-golddot124p", "Speer Gold Dot 9mm +P — 124gr GDHP", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.145, 1220.0, 124.0, 0.355, notes = "Bonded LE duty +P hollow point"),
        AmmoLoad("cat-am-45acp-230fmj", "CCI Blazer Brass .45 ACP — 230gr FMJ", ".45 ACP", AmmoCategory.HANDGUN, DragModel.G1, 0.195, 830.0, 230.0, 0.451, notes = "Classic subsonic 230gr hardball"),
        AmmoLoad("cat-am-10mm-180jhp", "Sig Sauer Elite 10mm — 180gr V-Crown", "10mm Auto", AmmoCategory.HANDGUN, DragModel.G1, 0.165, 1250.0, 180.0, 0.400, notes = "Full-power woods defense 10mm")
    )

    val suppressors: List<SuppressorSetup> = listOf(
        // Rimfire (.22)
        SuppressorSetup("cat-supp-gemtech-gm22", "Gemtech GM-22", "Gemtech", SuppressorCategory.RIMFIRE, ".22 LR", "1/2x28 Direct Thread", 5.0, 2.5, 1.0, notes = "G-Core 7075 aluminum monocore • Ultra-light 2.5 oz"),
        SuppressorSetup("cat-supp-deadair-mask", "Dead Air Mask HD", "Dead Air", SuppressorCategory.RIMFIRE, ".22 LR / 5.7x28", "1/2x28 Direct Thread", 5.1, 6.6, 1.07, notes = "Titanium tube & stainless baffles • Zero first-round pop"),
        SuppressorSetup("cat-supp-sico-sparrow", "SilencerCo Sparrow 22", "SilencerCo", SuppressorCategory.RIMFIRE, ".22 LR / .17 HMR", "1/2x28 Direct Thread", 5.08, 6.5, 1.06, notes = "Multi-part clam-shell monocore for easy cleaning"),
        SuppressorSetup("cat-supp-sico-switchback", "SilencerCo Switchback 22", "SilencerCo", SuppressorCategory.RIMFIRE, ".22 LR / 5.7x28", "1/2x28 Direct Thread", 5.75, 6.5, 1.07, notes = "Modular 4-configuration rimfire suppressor"),
        SuppressorSetup("cat-supp-rugged-oculus", "Rugged Oculus 22", "Rugged", SuppressorCategory.RIMFIRE, ".22 LR / .22 WMR", "1/2x28 Direct Thread", 5.25, 6.9, 1.06, notes = "17-4 stainless modular belt-fed rated rimfire can"),
        SuppressorSetup("cat-supp-q-elcamino", "Q El Camino", "Q", SuppressorCategory.RIMFIRE, ".22 LR / .17 HMR", "1/2x28 Direct Thread", 5.95, 4.3, 1.0, notes = "Titanium tube + stainless baffles • 4.3 oz"),
        SuppressorSetup("cat-supp-tbac-22td", "Thunder Beast 22 Take Down", "TBAC", SuppressorCategory.RIMFIRE, ".22 LR / .22 WMR", "1/2x28 Direct Thread", 5.6, 5.9, 1.0, notes = "Precision bolt-action rimfire repeatable POI"),

        // Rifle
        SuppressorSetup("cat-supp-sf-rc2", "SureFire SOCOM556-RC2", "SureFire", SuppressorCategory.RIFLE, "5.56x45mm NATO", "SureFire Fast-Attach QD", 6.4, 17.0, 1.5, notes = "Duty-grade 5.56 QD suppressor • Minimal repeatable POI shift"),
        SuppressorSetup("cat-supp-ocl-polonium", "Otter Creek Labs Polonium", "Otter Creek Labs", SuppressorCategory.RIFLE, "5.56 / 6mm", "1.375x24 HUB / 1/2x28", 5.8, 13.5, 1.625, notes = "High-suppression 17-4 PH stainless 5.56/6mm can"),
        SuppressorSetup("cat-supp-ocl-polonium-k", "Otter Creek Labs Polonium-K", "Otter Creek Labs", SuppressorCategory.RIFLE, "5.56 / 6mm", "1.375x24 HUB / 1/2x28", 4.8, 11.5, 1.625, notes = "Compact K-length low-backpressure 5.56/6mm suppressor"),
        SuppressorSetup("cat-supp-da-sandman-s", "Dead Air Sandman-S", "Dead Air", SuppressorCategory.RIFLE, ".308 / 6.5 CM / 5.56", "KeyMo QD", 6.8, 17.7, 1.5, notes = "Full-auto rated Stellite baffle .30 cal QD"),
        SuppressorSetup("cat-supp-da-nomad30", "Dead Air Nomad-30", "Dead Air", SuppressorCategory.RIFLE, ".300 WM / .308 / 6.5 CM", "1.375x24 HUB", 6.5, 14.0, 1.735, notes = "Tubeless 17-4 stainless hunting & precision .30 cal"),
        SuppressorSetup("cat-supp-sico-omega300", "SilencerCo Omega 300", "SilencerCo", SuppressorCategory.RIFLE, ".300 WM / .308 / 5.56", "ASR QD / HUB", 7.08, 14.8, 1.56, notes = "Classic precision .30 cal w/ Anchor Brake"),
        SuppressorSetup("cat-supp-hux-flow556k", "HUXWRX FLOW 556k", "HUXWRX", SuppressorCategory.RIFLE, "5.56x45mm NATO", "HX-QD LH", 5.5, 11.8, 1.6, notes = "3D-printed Flow-Through low-blowback AR-15 suppressor"),

        // Pistol / Subgun
        SuppressorSetup("cat-supp-rugged-obsidian9", "Rugged Obsidian 9", "Rugged", SuppressorCategory.PISTOL, "9mm / .300 BLK Sub", "Nielsen Piston / 1/2x28", 7.8, 12.7, 1.37, notes = "Modular 7.8\" / 4.85\" handgun & PCC 9mm suppressor"),
        SuppressorSetup("cat-supp-sico-omega9k", "SilencerCo Omega 9K", "SilencerCo", SuppressorCategory.PISTOL, "9mm / .300 BLK", "Alpha Piston / 3-Lug", 4.7, 7.2, 1.48, notes = "Compact welded subgun & pistol 9mm can"),
        SuppressorSetup("cat-supp-da-wolfman", "Dead Air Wolfman", "Dead Air", SuppressorCategory.PISTOL, "9mm / .300 BLK / 5.56", "KeyMicro / 1/2x28", 7.5, 14.4, 1.618, notes = "Modular PCC / handgun / subsonic rifle suppressor"),

        // Multi-Caliber
        SuppressorSetup("cat-supp-sico-hybrid46m", "SilencerCo Hybrid 46M", "SilencerCo", SuppressorCategory.MULTI_CAL, "9mm–.45-70 / .338 LM", "Charlie ASR / Piston", 7.72, 14.9, 1.57, notes = "Modular universal multi-caliber rifle & pistol can")
    )
}
