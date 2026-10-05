# Comico for Android

An independent Android app for [comico.moe](https://comico.moe). Requires Android 8.0 or later.

[Download the latest APK](https://github.com/Wolfkid200444/comico-android/releases/latest) · [Changelog](https://github.com/Wolfkid200444/comico-android/releases) · [Report an issue](https://github.com/Wolfkid200444/comico-android/issues)

## Features

- Discover and search manga with filters.
- Organize your library with collections, sorting, and display options.
- Read full-screen with chapter navigation and customizable controls.
- Download chapters or automatically save opened chapters for offline reading.
- Sync your library and history with your Comico account.
- View reading statistics and check for app updates.

Chapters are available offline once all their images finish saving. Manage downloads and storage in Settings. Online features require an internet connection.

## Install

Download the APK from Releases and open it on your phone. Allow installation from your browser or file manager if Android asks.

GitHub builds use a public test signing key. Installing a newer build with the same key keeps your local data.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35, or run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`.

To publish an update, increase both values in `version.properties`, add its changelog under `release-notes/`, and push to `main`. GitHub Actions builds and publishes the release.
