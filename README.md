<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tracediary_logo_transparent.png" width="128" alt="TraceDiary logo">
</p>
<h1 align="center">TraceDiary Android</h1>
<p align="center">
  A private place to write, keep, and revisit your daily notes.
  <br>
  <a href="README.zh-CN.md">简体中文</a>
</p>
<p align="center">
  <a href="https://github.com/Lane0218/TraceDiary-Android/releases"><img src="https://img.shields.io/badge/Download-GitHub%20Releases-181717?style=flat-square&logo=github&logoColor=white" alt="Download from GitHub Releases"></a>
  <a href="#requirements"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0 or later"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-356859?style=flat-square" alt="MIT License"></a>
</p>

TraceDiary is a native Android diary app for quick daily writing and thoughtful reflection. The name joins “Trace,” the marks time leaves behind, with “Diary.” Its defining feature, **On This Day**, brings entries from the same date in past years back into view.

Your diary is stored locally, with optional encrypted cloud sync and evidence-based AI search when you choose to configure those services.

## Features

- **Write:** Edit a plain-text diary for today or another date. Choose when a new diary day begins, browse a weekly or monthly calendar, and rely on automatic saving and optional reminders. The configurable day boundary is useful if you write after midnight: for example, with a 4 a.m. boundary, a 1 a.m. entry still belongs to the previous diary day.
- **On This Day:** Revisit entries written on this calendar date in previous years. This is the heart of TraceDiary's look-back experience. You can also search by words, year, or dates with entries, and write an annual summary.
- **AI search:** Download the on-device embedding model and build an encrypted semantic index, then connect a compatible LLM such as DeepSeek. Ask questions like “How have I been doing lately?” or “What work have I focused on recently?” Answers cite dates and diary excerpts, helping you spot patterns and gather material for a personal review.
- **Statistics:** Review writing streaks, monthly word counts, and a yearly activity heatmap.
- **Protect and move your data:** Encrypt the local database with your app password, sync encrypted diary content to a configured object-storage service, and import or export JSON, TXT ZIP, or Markdown ZIP backups.
- **Make it yours:** Set a weekly calendar start day, choose an accent color, and configure diary reminders.

## Quick Start

TraceDiary supports Android phones running Android 8.0 (API 26) or later. Install the APK from GitHub Releases:

1. Open [GitHub Releases](https://github.com/Lane0218/TraceDiary-Android/releases) and download the latest TraceDiary APK.
2. Open the downloaded APK on your phone. If Android asks, allow your browser or file manager to install apps, then tap **Install**.
3. Open TraceDiary and set your app password.

## Privacy

Diary entries are stored in an SQLCipher-encrypted local database. The app password is used to derive the database and cloud-content encryption keys. When cloud sync is enabled, diary content is encrypted before it is uploaded to the object-storage service you configure.

AI search is optional. After you download the embedding model and build the index, semantic search runs on your device and the index is encrypted locally. To generate an answer, the app sends relevant diary excerpts and other data needed for that request to the cloud AI service you configure, such as DeepSeek. Review that provider's privacy policy before connecting it. Keyword search remains available without AI.

Exported backup files are plaintext JSON, TXT ZIP, or Markdown ZIP files. Keep exported files somewhere you trust.

## Tech Stack

- Kotlin 2.2.10 and Jetpack Compose with Material 3
- Room and SQLCipher for local storage
- DataStore for preferences
- WorkManager and OkHttp for background work and networking
- ONNX Runtime Android for the on-device `BAAI/bge-small-zh-v1.5` embedding model
- A single Android app module organized into UI, ViewModel, Domain, and Data layers

## Build and Test

### Requirements

- Android Studio with its bundled Java runtime
- Android SDK for API 36
- Android 8.0 (API 26) or later to install the app

Open the project in Android Studio and run the `app` configuration, or build from a terminal with the Gradle Wrapper.

```shell
# Windows
gradlew.bat test assembleDebug --no-daemon

# macOS / Linux
./gradlew test assembleDebug --no-daemon
```

Run Compose instrumentation tests on a running emulator or connected Android device:

```shell
gradlew.bat connectedDebugAndroidTest --no-daemon
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## License

TraceDiary is licensed under the [MIT License](LICENSE).
