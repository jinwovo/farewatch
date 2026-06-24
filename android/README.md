# farewatch — Android (Compose)

Kotlin + Jetpack Compose client for the farewatch API. Lists watches and shows a watch
detail (lowest price, alert history with delivery status, destination weather) with a
"지금 폴" action — consuming the same `/api/watches` endpoints the web app uses.

## Stack
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01, Material 3)
- Retrofit 2.11 + Gson, Kotlin coroutines
- AGP 8.7.3, Gradle 8.11.1, compileSdk 35, minSdk 26

## Build
```bash
# from this directory
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
```

Requirements:
- **JDK 17** — not 21. D8 (dexing) spins/hangs at `mergeExtDexDebug` on JDK 21 with this
  AGP; JDK 17 is the supported Android-toolchain JDK. (The backend uses JDK 21; only the
  Android build needs 17.) Point `JAVA_HOME` at a JDK 17.
- **Android SDK** — set `ANDROID_HOME`, or create `local.properties` with
  `sdk.dir=/path/to/android-sdk` (gitignored). Needs `platforms;android-35` +
  `build-tools;35.0.0` + `platform-tools`.

## Run
The base URL is `http://10.0.2.2:8101/` (`API_BASE` in `app/build.gradle.kts`), which maps
to the host machine's `localhost` from the Android emulator. For a physical device, change
it to the host's LAN IP. Start the backend (`../gradlew bootRun` or the jar) first.

## Layout
```
app/src/main/java/com/portfolio/farewatch/
├── MainActivity.kt          # Compose entry point
├── api/                     # Retrofit interface + models + client
└── ui/FarewatchApp.kt       # WatchListScreen + WatchDetailScreen
```

Note: FCM push receipt is intentionally out of scope here (needs a Firebase project +
`google-services.json`); the backend's notification engine already models PUSH/EMAIL
delivery, and this client surfaces that delivery status in the detail screen.
