# MicroRC

Top-down offline local Wi-Fi and Hotspot multiplayer RC racing game for 2–4 players built with Kotlin and Jetpack Compose.

## Installing the APK from GitHub Actions

Every commit pushed to this repository triggers the **Build Android APK** workflow (`.github/workflows/build-apk.yml`), which runs the test suite and builds an installable `MicroRC-debug.apk`.

1. Open the **Actions** tab on GitHub and select the latest **Build Android APK** run.
2. Under **Artifacts**, download `MicroRC-debug-apk-<run_number>`.
3. Extract the `.zip` file and install `MicroRC-debug.apk` on your Android devices (Android 7.0+ / API 24+).

## Local Wi-Fi / Hotspot Multiplayer Setup

No internet connection is required:
- **Option A (Shared Wi-Fi):** Connect 2–4 phones to the same Wi-Fi network.
- **Option B (Mobile Hotspot):** Enable a Wi-Fi Hotspot on one phone and connect the other phones to it.
- One player taps **Host Race**; the other players tap **Join Race** (or connect via the Host IP shown in the lobby).
