# farewatch Android — run on an emulator & record

The app **builds** headlessly (`./gradlew assembleDebug`, CI green). To **see/record it running**
you need an Android runtime. The command-line SDK's `emulator` package wouldn't resolve in this
environment, so use **Android Studio** (its bundled SDK/AVD manager installs the emulator fine).

## Already set up on this machine
- **Android SDK** — `C:\workspace\tools\android-sdk` (`ANDROID_HOME`); emulator binary already downloaded.
- **JDK 17** — `C:\workspace\tools\jdk-17` (Temurin). The Android build needs 17, **not 21**.
- **Backend** — running at `localhost:8101` with seeded watches. The app calls `http://10.0.2.2:8101`
  (the emulator's alias for the host), so data shows up with no extra config.

## Steps
1. **Install Android Studio** — `winget install Google.AndroidStudio`, or https://developer.android.com/studio
2. **Open the project** — Android Studio → *Open* → `C:\workspace\17_projectD\android`.
   If it asks for the SDK, point to `C:\workspace\tools\android-sdk`.
3. **Set the Gradle JDK to 17** (avoids the D8 dexing hang seen on JDK 21):
   *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK →* add
   `C:\workspace\tools\jdk-17`. Let Gradle sync.
4. **Create an emulator** — *Device Manager → + Create Virtual Device →* Pixel 7 → *System Image*
   API 35 (or 34) → *Download* (Studio's SDK Manager works) → Finish.
5. **Make sure the backend is up** (it is, on `:8101`). If not:
   `& 'C:\workspace\tools\jdk-21\bin\java.exe' -Xmx512m -jar 'C:\workspace\17_projectD\build\libs\farewatch-0.0.1-SNAPSHOT.jar'`
6. **Run** — select the AVD in the toolbar, click **Run ▶**. The emulator boots, installs the APK,
   and launches farewatch: the watch list (seeded routes) → tap one → detail (lowest price, alert
   history, destination weather, *지금 폴*).
7. **Record** — in the emulator's side toolbar → **⋮ (Extended controls) → Record and Playback →
   Start Recording**; interact; Stop; Save (WebM/GIF). CLI alternative:
   `C:\workspace\tools\android-sdk\platform-tools\adb.exe shell screenrecord /sdcard/demo.mp4`
   (Ctrl+C to stop) then `adb pull /sdcard/demo.mp4`.

## Real device instead (no emulator needed)
Enable *Developer options → USB debugging*, plug in over USB, then:
```
adb install app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.portfolio.farewatch/.MainActivity
adb shell screenrecord /sdcard/demo.mp4     # Ctrl+C to stop, then: adb pull /sdcard/demo.mp4
```
(For a physical device, change `API_BASE` in `app/build.gradle.kts` from `10.0.2.2` to the host's LAN IP.)
