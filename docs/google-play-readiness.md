# Google Play Readiness Checklist — Gunnies Ballistics

## 1. Release Signing & Versioning (`app/build.gradle.kts`)
- Release signing is already wired via environment variables (`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) in [`app/build.gradle.kts`](../app/build.gradle.kts) — no secrets committed to Git.
- Before uploading to Google Play Console:
  1. Increment `versionCode` (integer, strictly increasing) and `versionName` (e.g., `"1.1.0"`) in `app/build.gradle.kts`.
  2. Enable Play App Signing in Google Play Console and upload a signed Android App Bundle:
     ```bash
     ./gradlew testDebugUnitTest bundleRelease
     ```
     Output: `app/build/outputs/bundle/release/app-release.aab`.

## 2. Privacy Policy URL
- Host [`docs/PRIVACY_POLICY.md`](PRIVACY_POLICY.md) via GitHub Pages (`https://gmascagni.github.io/gunnies/PRIVACY_POLICY.html` or raw link) and enter that URL in Play Console → **App content → Privacy policy**.

## 3. Play Console Data Safety Form Answers
- **Does your app collect or share any of the required user data types?** → **Yes** (Approximate/Precise Location is sent ephemerally to Open-Meteo *only* when the user taps Sync GPS).
- **Is all user data encrypted in transit?** → **Yes** (HTTPS to `api.open-meteo.com`).
- **Location:**
  - *Collected?* Yes (ephemeral, user-initiated only, optional).
  - *Shared with third parties?* Sent to Open-Meteo weather API to fetch local atmosphere; not sold or used for ads/tracking.
  - *Purposes:* App functionality (local weather, density altitude, Coriolis latitude).
- **Personal info, Financial, Contacts, Photos, Analytics, Device IDs:** **None collected or shared.**

## 4. Closed Testing Requirements (Personal Play Developer Accounts)
- Google Play requires **12+ opted-in testers** running a Closed Testing track for **14 consecutive days** before granting Production release access for new personal developer accounts.
- Create a **Closed testing** track, upload the signed `.aab`, add testers by email / Google Group, collect feedback on live-fire DOPE + BLE sync, then apply for Production access.
