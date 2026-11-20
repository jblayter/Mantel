# Mantel

An Android TV wall display for your home lab: live machine stats from Glances and GitHub Actions deploy status on one screen.

## Status

The main dashboard is built in Jetpack Compose for TV. Machine stats and containers are live from Glances, and the deploys card is live from GitHub Actions once the config has a token. Without one it shows sample data and the header says "sample deploys".

Mantel is read-only. It only ever sends GET requests.

The design language ("Hive") is in [`docs/design/DESIGN.md`](docs/design/DESIGN.md), with its values in [`docs/design/tokens.json`](docs/design/tokens.json).

## Set up a display

A freshly installed Mantel shows a "not set up yet" screen until you give it a config file. Nothing about your network is in the APK.

1. Write `mantel-config.json`. Keep it out of version control: it holds a token.

   ```json
   {
     "glancesUrl": "http://192.168.1.50:61208",
     "githubToken": "github_pat_...",
     "repos": ["owner/name"]
   }
   ```

   - `glancesUrl` is the machine running Glances in web server mode (`glances -w`). Use an IP address: most TVs cannot resolve `.local` names.
   - To watch several machines, replace `glancesUrl` with a list: `"glancesUrls": ["http://192.168.1.50:61208", "http://192.168.1.61:61208"]`. The display shows one at a time and takes turns, with every machine on a shelf along the bottom.
   - `githubToken` and `repos` are optional and go together. Use a fine-grained personal access token limited to those repositories, with read-only access to Actions and Metadata and nothing else.

2. Push it to the TV. adb cannot write straight into an app's storage on Android 11 and later, so this takes two commands:

   ```bash
   adb push mantel-config.json /data/local/tmp/
   ```

   ```bash
   adb shell run-as com.blayter.mantel cp /data/local/tmp/mantel-config.json files/
   ```

Mantel looks for the file every few seconds, stores its contents encrypted, deletes the file and starts. A new file pushed later replaces everything stored, so it must be complete. If the file cannot be used, the setup screen says why. If the TV cannot encrypt, the token is not stored at all and the deploys card says so.

Delete the copy in `/data/local/tmp` afterwards:

```bash
adb shell rm /data/local/tmp/mantel-config.json
```

`run-as` needs a debug build. On a release build, copy the file to `/sdcard/Android/data/com.blayter.mantel/files/` with a file manager instead.

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

To work on the screen without a Glances machine, build the all-mock app:

```bash
./gradlew :app:installDebug -Pmantel.mock=true
```

The app targets a 1920x1080 Android TV (minSdk 26). To try it without a TV, create an Android TV emulator with the `tv_1080p` device profile and install onto it.

## Fonts

Fraunces and DM Sans are bundled under the SIL Open Font License; the license texts are in [`licenses/`](licenses/).
