# MicroRC

Top-down local Wi-Fi, Hotspot, and 2–4 player Online Web multiplayer RC racing game built with Kotlin and Jetpack Compose.

## Installing the APK from GitHub

Every commit pushed to this repository triggers the **Build Android APK** workflow (`.github/workflows/build-apk.yml`), which tests, builds, and publishes `MicroRC.apk` directly to **GitHub Releases**:

1. Open the **Releases** section of this GitHub repository (or configure **Obtainium** to track this repository).
2. Download `MicroRC.apk` from the **Latest** release (`v1.0.<build>`).
3. Open `MicroRC.apk` on your Android devices (Android 7.0+ / API 24+) to install.

## Online Web Play, Friends List, WhatsApp Invites & Google Sign-In on GitHub Builds

When pushing from Google AI Studio to GitHub, AI Studio excludes `app/google-services.json` (and `debug.keystore.base64`) from the Git export. You can enable **Firebase & Google Sign-In** on GitHub builds in **either** of two ways:

### Method 1 — Instant In-App Setup (No Rebuild Needed)
1. Install `MicroRC.apk` and open **Online Web Play**.
2. Tap **Paste google-services.json** on the Cloud Sign-In card and paste the JSON from AI Studio's `app/google-services.json`. MicroRC initializes Firebase immediately on your device!
3. Tap **APK Signing Info** on the same screen, copy the `SHA-1` & `SHA-256` fingerprints of your GitHub/Obtainium APK, and add them once in [Firebase Console](https://console.firebase.google.com/) → **Project Settings → Your apps (`com.aistudio.microrc.k7m9xp`) → Add fingerprint**.

### Method 2 — Permanent GitHub Actions Secret (Baked into Every Release APK)
1. In your GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**:
   - Name: `GOOGLE_SERVICES_JSON` — Value: paste the contents of `app/google-services.json` from AI Studio.
2. Match the APK signing certificate (pick A or B):
   - **Keep your Obtainium `ci-keystore`:** Add the GitHub APK's `SHA-1` and `SHA-256` (shown in the GitHub Actions workflow summary or in-app **APK Signing Info**) to Firebase Console → **Project Settings**, then update `GOOGLE_SERVICES_JSON`.
   - **Or use AI Studio's key:** Add secret `SIGNING_KEYSTORE_BASE64` with the contents of `debug.keystore.base64`.

## Local Wi-Fi / Hotspot Multiplayer Setup

No internet connection is required:
- **Option A (Shared Wi-Fi):** Connect 2–4 phones to the same Wi-Fi network.
- **Option B (Mobile Hotspot):** Enable a Wi-Fi Hotspot on one phone and connect the other phones to it.
- One player taps **Host Race**; the other players tap **Join Race** (or connect via the Host IP shown in the lobby).
