<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tracediary_logo_transparent.png" width="128" alt="TraceDiary logo">
</p>
<h1 align="center">TraceDiary Android</h1>
<p align="center">
  A private place to write, keep, and revisit your daily notes.
  <br>
  <a href="README.zh-CN.md">简体中文</a>
</p>

TraceDiary is a native Android diary app for quick daily writing and thoughtful reflection. Your diary is stored locally, with optional encrypted cloud sync and evidence-based AI search when you choose to configure those services.

## Features

- **Write:** Edit a plain-text diary for today or another date. Choose when a new diary day begins, browse a weekly or monthly calendar, and rely on automatic saving and optional reminders.
- **Look back:** Search by words, year, or dates with entries. Review past entries from the same day of the year and write an annual summary.
- **AI search:** Search diary entries with an on-device semantic index and ask a read-only, user-configured AI service. Answers include dates and matching source text so you can verify them.
- **Statistics:** Review writing streaks, monthly word counts, and a yearly activity heatmap.
- **Protect and move your data:** Encrypt the local database with your app password, sync encrypted diary content to a configured object-storage service, and import or export JSON, TXT ZIP, or Markdown ZIP backups.
- **Make it yours:** Set a weekly calendar start day, choose an accent color, and configure diary reminders.

## Privacy

Diary entries are stored in an SQLCipher-encrypted local database. The app password is used to derive the database and cloud-content encryption keys. When cloud sync is enabled, diary content is encrypted before it is uploaded to the object-storage service you configure.

AI search is optional. Semantic search runs on your device. If you ask a question, the app sends the relevant diary excerpts and other data needed for that request to the cloud AI service you configured. Review that provider's privacy policy before connecting it. Keyword search remains available without AI.

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
