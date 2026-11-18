# MicroRC

Top-down local Wi-Fi, Hotspot, and 2–4 player Online multiplayer RC racing game built with Kotlin and Jetpack Compose.

## Installing the APK from GitHub

Every commit pushed to this repository triggers the **Build Android APK** workflow (`.github/workflows/build-apk.yml`), which tests, builds, and publishes `MicroRC.apk` directly to **GitHub Releases**:

1. Open the **Releases** section of this GitHub repository (or configure **Obtainium** to track this repository).
2. Download `MicroRC.apk` from the **Latest** release (`v1.0.<build>`).
3. Open `MicroRC.apk` on your Android devices (Android 7.0+ / API 24+) to install, then sign in with your Google account for online play, friends, and leaderboards.

## Maintainer CI & Google Play Console Deployment

- **Online Google Sign-In & Cloud Lobbies (`GOOGLE_SERVICES_JSON`):** Set `GOOGLE_SERVICES_JSON` in GitHub **Settings → Secrets and variables → Actions** (supported under either **Secrets** or **Variables**). In that same cloud project:
  1. Register the APK/Play App Signing `SHA-1` & `SHA-256` certificates for `com.aistudio.microrc.k7m9xp`.
  2. Create a **Firestore Database** (`(default)` database) and publish the repository's [`firestore.rules`](./firestore.rules) under **Firestore Database → Rules** so online lobbies, friends, and leaderboards work immediately.
- **Google Play Developer Console (`.aab` Release Bundle):**
  1. Generate an upload keystore with alias `upload`:
     ```bash
     keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
     ```
  2. Base64-encode `my-upload-key.jks` (`base64 -w 0 my-upload-key.jks`) and add three GitHub Actions **Secrets**: `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD`.
  3. Each workflow run will automatically produce a signed **`MicroRC-play-store-aab`** (`MicroRC-play-store.aab`) artifact ready to upload to Google Play Console. After enabling **Play App Signing** in Google Play Console, copy Google Play's **App signing key certificate (`SHA-1` & `SHA-256`)** from **Setup → App signing** into your cloud project's Android app settings so Google Sign-In works seamlessly for Play Store installs.

## Local Wi-Fi / Hotspot Multiplayer Setup

No internet connection or sign-in is required for local play:
- **Option A (Shared Wi-Fi):** Connect 2–4 phones to the same Wi-Fi network.
- **Option B (Mobile Hotspot):** Enable a Wi-Fi Hotspot on one phone and connect the other phones to it.
- One player taps **Host Race**; the other players tap **Join Race** (or connect via the Host IP shown in the lobby).
