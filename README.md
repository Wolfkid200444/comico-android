# comico.moe for Android

An independent Kotlin Android client for [comico.moe](https://comico.moe), built with Jetpack Compose and Material Design 3. Requires Android 8.0 or later.

## Install

1. Open [GitHub Releases](https://github.com/Wolfkid200444/comico-android/releases).
2. Download the `comico-android-<version>.apk` asset for the version you want.
3. Open the APK on your Android device. If prompted, allow your browser or file manager to install apps from this source.
4. Tap **Install**, then open comico.moe.

If no release is available yet, check the [APK build workflow](https://github.com/Wolfkid200444/comico-android/actions/workflows/build-apk.yml). A successful run publishes the APK to Releases and also provides a downloadable artifact for 30 days.

To install from a computer with Android platform tools and USB debugging enabled:

```sh
adb install -r comico-android-0.6.0.apk
```

Replace the filename with the APK you downloaded. Future builds use the same signing key and application ID, so installing a newer version keeps your local library. If an earlier build used a different signing key, Android requires uninstalling it first, which removes its local data.

Automated builds are debug APKs signed with the public test key in `signing/debug.keystore`. Use them for testing. Production distribution requires a private release signing key; never use this public key for a production release.

## Features

- Discover recently updated, popular, newly released, and most followed titles.
- Search manga and webtoons with filters and load more results.
- Sync account libraries and reading history, browse your comments, and edit your profile.
- View the public experience leaderboard.
- Browse covers in an adaptive grid with tablet navigation.
- View title details and chapters, with translation language selection.
- Save titles and resume at your last read page on this device.
- Use the website’s dark colors by default, or choose light, dark, or system appearance, with optional Android wallpaper colors.
- Read chapters as native images with long strip, single page, or double page layouts, pinch zoom, and chapter navigation. Publisher-only chapters link to the original reader.

The catalog defaults to Comico's `safe` content rating; use the content filter to change it. Account sign-in and registration are available in Settings. Library and history sync are available. Offline chapter downloads are not implemented.

## Search filters

Tap **Filters** in Discover or Search to choose:

- Sort by relevance, recently updated, most followed, or title.
- Content rating threshold, from safe only through all ratings.
- Type: manga, manhwa, manhua, or webtoons.
- Demographic: shounen, shoujo, seinen, or josei.
- Release status: ongoing, completed, hiatus, or cancelled.
- Up to 20 Comick tags from Comico's live tag list.

Search the tag list by name, select tags, then tap **Apply filters**. **Reset** restores the defaults in the dialog; tap Apply to use them. Filters persist on this device and reset pagination when changed. You can search with a title, tags, or both. Tag and title searches use Comico's Comick-backed search, matching its website.

## Reader settings

Open **Settings → Global reader defaults** to choose settings for all manga. Open a title's **Reader settings for this manga**, or tap the reader's settings icon, to customize that manga. Each field can use its global value or an explicit override. **Use all global defaults** removes all overrides for that manga.

| Setting | Choices |
| --- | --- |
| Reading mode | Auto by format, long strip, single page, double page |
| Page width | Narrow, comfort, wide, full |
| Proxy method | Auto, Method 1, Method 2, Method 3 |
| Source | Auto or a source reported by Comico for that manga/chapter |
| Page navigation | Horizontal swipe, vertical scroll |
| Page direction | Left to right, right to left |

Auto mode chooses long strip for webtoons, manhwa, and manhua, and single pages for manga. Auto proxy tries direct source images first, then Method 1 and Method 2. The manual methods match Comico's `all`, `api`, and `direct` values respectively.

Source choices come from the live API. They can include MangaDex, ComickLive, Atsu, and other sources, depending on the manga and chapter. Global source preferences are populated as you open titles. An unavailable preferred source falls back to Auto, with a notice in the reader. The bottom reader controls show the source and method actually used.

Choose horizontal swipe or vertical scroll for single pages and double-page spreads. Long-strip mode always scrolls. Pinch or double-tap to zoom, tap a page to hide controls, and use the page slider to jump. Reading position, global settings, and individual manga overrides persist on this device.

The native reader resolves the same public access-token and read-session endpoints as Comico's website. Method 3 includes direct resolvers for MangaDex, ComickLive, Atsu, Manganato, MangaBall, and XComic. Other providers use server-resolved images through Method 1 or Method 2. Source availability and server failures can affect reading. Protected scrambled image formats are not supported; the reader prompts you to choose another source instead of displaying scrambled pages.

Tap **Start reading** at the bottom right of manga details to open the earliest chapter for the selected language and source at page 1. This resets that chapter’s saved reading position.

The app icon uses the supplied comico.moe logo.

## Account

Open **Settings → Account** to sign in with your email or username, create an account, or sign out. Registration may require email verification before signing in. Passwords are not saved. Session cookies are encrypted with Android Keystore and sent only to comico.moe over HTTPS.

Signing in loads your account library and server-side reading history. Saving or removing a title updates your account library. Native chapter reading sends chapter, page, page count, and completion progress to the same endpoint used by the website. Offline changes are queued for that account and retried when you open Library or History, resume reading, or pull down to refresh Library, History, or Settings → Data. Sync errors remain visible.

Each account has its own cache and pending changes. Signing out restores the guest library and history. To add guest data to an account, use **Settings → Data → Add guest library and history to this account**. This imports guest data without deleting the guest copy. Clearing account history clears it on both the website and this device. Reader preferences remain local.

Bottom navigation is **Discover → Search → Library → History → Profile**. The three-dot menu on Profile opens Settings and the website. Profile shows your avatar, banner, badges, reading stats, and experience progress. Pull down to refresh.

Settings uses a list of Account, Identity and social links, Comments, Reading, Appearance, Data, Leaderboard, Help, and About pages. Identity supports avatar and banner URLs, Gravatar, and up to five validated social links. Appearance offers multiple palettes, Android dynamic colors, pure black dark mode, date formats, relative dates, and navigation labels. Data includes guest import, JSON export, history clearing, and pull-to-sync.

Library’s header search filters saved titles, including synchronized account titles. History entries open manga details; use Resume to continue reading. Settings → Leaderboard shows the website’s top 50 accounts by experience.

Manga details has a Comments action beside Save. Chapter rows and the reader also have comment buttons, opening separate chapter discussions. Signed-in users can post Markdown and upload images, with automatic inline previews. The composer includes a formatting guide and comment policy. Pull down to refresh discussions.

**Discover** displays Recently updated, Recent popular, New releases, and Most followed as separate horizontal Material 3 carousels on one page. Each feed loads independently and has its own retry state. Signing in adds New chapters from followed comics and Reading history carousels above the public feeds. Tap a history cover to open manga details. Feed arrows open full grids that load more titles as you scroll; pull down to refresh Discover and feed pages. Carousel and page scroll positions are retained when switching screens.

The interface uses native Material 3 carousels, a search bar, tonal card surfaces, selected navigation indicators, and a scrolling app bar. The default website colors and supplied logo remain in use.

See [account sync contracts](docs/account-sync.md) for endpoints and implementation details.

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
VERSION_NAME=0.6.0
VERSION_CODE=6
```

To publish the next version:

1. Finish the code changes you want included in the APK.
2. Update **both** values. For example, use `VERSION_NAME=0.7.0` and `VERSION_CODE=7`. The version name must use `major.minor.patch`, and both values must increase.
3. Commit the version change and push it to `main`:

   ```sh
   git add version.properties
   git commit -m "Release version 0.7.0"
   git push origin main
   ```

The [workflow](.github/workflows/build-apk.yml) then:

- Validates the version change.
- Runs unit tests and Android lint, then builds the debug APK.
- Uploads `comico-android-<version>.apk` and its SHA-256 checksum as workflow artifacts.
- Creates the version tag and GitHub release at the exact commit that triggered the build.

The initial push also publishes the starting version. Other source changes do not trigger this release workflow until you update the version file. Editing comments or whitespace without changing the version values skips the build. Existing releases remain unchanged.

You can also use **Actions → Build versioned APK → Run workflow** on `main` to build the current version manually, including retrying a failed build. Enable GitHub Actions in the repository if your account or organization has disabled it. Releases use the workflow's built-in `GITHUB_TOKEN`; no personal token or signing secret is needed for these test builds.

## API and storage

The app reads Comico's catalog, search, title details, and chapter endpoints. See the [public API specification](https://comico.moe/api/openapi.json) or the [saved OpenAPI document](docs/openapi.json).

Public API requests include a descriptive User-Agent. Search waits 400ms after typing and cancels superseded requests. The app shows loading, empty, retry, and network error states.

Account libraries and reading history sync with comico.moe and are cached in private on-device preferences. Guest bookmarks and history remain local. Reader settings stay on this device. The Kotlin reader obtains normal access tokens and session image URLs from Comico; no HTML reader or WebView is embedded. Provider metadata may be parsed from its source page when that provider exposes image information there.
