# MicroRC

Top-down offline local Wi-Fi and Hotspot multiplayer RC racing game for 2–4 players built with Kotlin and Jetpack Compose.

## Installing the APK from GitHub

Every commit pushed to this repository triggers the **Build Android APK** workflow (`.github/workflows/build-apk.yml`), which tests, builds, and publishes `MicroRC.apk` directly to **GitHub Releases**:

1. Open the **Releases** section of this GitHub repository (or tap **Releases** in the GitHub mobile app).
2. Download `MicroRC.apk` from the **Latest** release (`v1.0.<build>`).
3. Open `MicroRC.apk` on your Android devices (Android 7.0+ / API 24+) to install.

*(The raw `MicroRC.apk` is also available under the workflow run's **Artifacts** tab.)*

## Local Wi-Fi / Hotspot Multiplayer Setup

No internet connection is required:
- **Option A (Shared Wi-Fi):** Connect 2–4 phones to the same Wi-Fi network.
- **Option B (Mobile Hotspot):** Enable a Wi-Fi Hotspot on one phone and connect the other phones to it.
- One player taps **Host Race**; the other players tap **Join Race** (or connect via the Host IP shown in the lobby).
