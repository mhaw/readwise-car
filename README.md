# Readwise Car (Android Auto)

Personal Android Auto audio player for Readwise Reader articles using on-device Android TextToSpeech, Jetpack Media3 (`1.11.1`), ExoPlayer, and Room offline caching.

## Configuration
- **Package ID**: `com.example.readwisecar`
- **Default Playback Speed**: `1.25x`
- **Offline HTML Pre-Cache**: `12` articles per sync
- **Auto-Advance**: `Enabled`

## Build Path 1: Cloud APK via GitHub Actions (No Android Studio)
1. Push this folder to a private GitHub repository.
2. Open the **Actions** tab and wait ~3 minutes for `Build Readwise Car Debug APK` to finish.
3. Download `readwise-car-debug-apk`, unzip, and install `app-debug.apk` on your Android phone.

## Build Path 2: Android Studio
1. Open this folder directly in Android Studio.
2. Connect your Android phone with USB Debugging enabled and click **Run**.

## Unlocking Sideloaded Apps in Android Auto
1. On your phone, open **Settings -> Android Auto**.
2. Tap **Version** 10 times to enable Developer Settings.
3. Tap the 3-dot menu -> **Developer settings** -> enable **Unknown sources**.
4. Open **Readwise Car**, paste your token from `https://readwise.io/access_token`, and tap **Sync Now**.
