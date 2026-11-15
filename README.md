# Mantel

An Android TV wall display for your home lab: live machine stats from Glances and GitHub Actions deploy status on one screen.

## Status

The main dashboard is built in Jetpack Compose for TV and runs on mock data. The Glances and GitHub clients are not written yet, so the app makes no network calls.

The design language ("Hive") is in [`docs/design/DESIGN.md`](docs/design/DESIGN.md), with its values in [`docs/design/tokens.json`](docs/design/tokens.json).

## Build

You need JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).

1. Point the build at your SDK in `local.properties` (not checked in):

   ```
   sdk.dir=/path/to/android/sdk
   ```

2. `gradle.properties` pins `org.gradle.java.home` to a Homebrew JDK 17 on macOS. On another machine, change that line or override it:

   ```bash
   ./gradlew -Dorg.gradle.java.home=/path/to/jdk17 :app:assembleDebug
   ```

3. Build, test and install with the wrapper:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:installDebug
   ```

The app targets a 1920x1080 Android TV (minSdk 26). To try it without a TV, create an Android TV emulator with the `tv_1080p` device profile and install onto it.

## Fonts

Fraunces and DM Sans are bundled under the SIL Open Font License; the license texts are in [`licenses/`](licenses/).
