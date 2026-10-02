# Comico for Android

An independent Kotlin Android client for [comico.moe](https://comico.moe), built with Jetpack Compose and Material Design 3. Requires Android 8.0 or later.

## Install

1. Open [GitHub Releases](https://github.com/Wolfkid200444/comico-android/releases).
2. Download the `comico-android-<version>.apk` asset for the version you want.
3. Open the APK on your Android device. If prompted, allow your browser or file manager to install apps from this source.
4. Tap **Install**, then open Comico.

If no release is available yet, check the [APK build workflow](https://github.com/Wolfkid200444/comico-android/actions/workflows/build-apk.yml). A successful run publishes the APK to Releases and also provides a downloadable artifact for 30 days.

To install from a computer with Android platform tools and USB debugging enabled:

```sh
adb install -r comico-android-0.1.0.apk
```

Replace the filename with the APK you downloaded. Future builds use the same signing key and application ID, so installing a newer version keeps your local library. If an earlier build used a different signing key, Android requires uninstalling it first, which removes its local data.

Automated builds are debug APKs signed with the public test key in `signing/debug.keystore`. Use them for testing. Production distribution requires a private release signing key; never use this public key for a production release.

## Features

- Discover manga and webtoons, search titles, and load more results.
- Browse covers in an adaptive grid with tablet navigation.
- View title details and chapters, with translation language selection.
- Save titles and keep track of the last chapter opened on this device.
- Choose light, dark, or system appearance, with optional Android wallpaper colors.
- Read supported chapters using Comico's web reader inside the app. Publisher-hosted chapters link to the original reader.

The catalog currently uses Comico's `safe` content rating. Account login, cloud sync, offline downloads, native image paging, and native page-level progress tracking are not implemented.

## Build the app

### Requirements

- JDK 17.
- Android SDK Platform 35 and Build Tools 35.0.0.
- An internet connection for downloading Gradle and dependencies.

The project includes the Gradle wrapper, so a separate Gradle installation is unnecessary.

### Android Studio

1. Clone the repository:

   ```sh
   git clone https://github.com/Wolfkid200444/comico-android.git
   ```

2. Open the `comico-android` directory in Android Studio.
3. Set the Gradle JDK to JDK 17 in the Gradle settings.
4. Use SDK Manager to install Android SDK Platform 35 and Build Tools 35.0.0.
5. Wait for Gradle sync. Select an Android 8.0+ device or emulator and run the `app` configuration.

### Command line

```sh
git clone https://github.com/Wolfkid200444/comico-android.git
cd comico-android
```

Set `JAVA_HOME` to your JDK 17 installation. Set `ANDROID_HOME` to your Android SDK directory, or create an untracked `local.properties` file in the project root:

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

Install the required SDK packages with Android's command-line tools if they are not already present:

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

Build and validate on Linux or macOS:

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. With a connected device, install it using:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Publish a version

The app and the GitHub workflow both read `version.properties`:

```properties
VERSION_NAME=0.1.0
VERSION_CODE=1
```

To publish the next version:

1. Finish the code changes you want included in the APK.
2. Update **both** values. For example, use `VERSION_NAME=0.2.0` and `VERSION_CODE=2`. The version name must use `major.minor.patch`, and both values must increase.
3. Commit the version change and push it to `main`:

   ```sh
   git add version.properties
   git commit -m "Release version 0.2.0"
   git push origin main
   ```

The [workflow](.github/workflows/build-apk.yml) then:

- Validates the version change.
- Runs unit tests and Android lint, then builds the debug APK.
- Uploads `comico-android-0.2.0.apk` and its SHA-256 checksum as workflow artifacts.
- Creates the `v0.2.0` tag and GitHub release at the exact commit that triggered the build.

The initial push also publishes the starting version. Other source changes do not trigger this release workflow until you update the version file. Editing comments or whitespace without changing the version values skips the build. Existing releases remain unchanged.

You can also use **Actions → Build versioned APK → Run workflow** on `main` to build the current version manually, including retrying a failed build. Enable GitHub Actions in the repository if your account or organization has disabled it. Releases use the workflow's built-in `GITHUB_TOKEN`; no personal token or signing secret is needed for these test builds.

## API and storage

The app reads Comico's catalog, search, title details, and chapter endpoints. See the [public API specification](https://comico.moe/api/openapi.json) or the [saved OpenAPI document](docs/openapi.json).

Public API requests include a descriptive User-Agent. Search waits 400ms after typing and cancels superseded requests. The app shows loading, empty, retry, and network error states.

Bookmarks and the last opened chapter are stored in private on-device preferences. The embedded website manages reader access tokens, image protection, and its own page position. The app does not decode protected images.
