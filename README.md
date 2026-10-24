# FotoSort

Tinder-style photo cleaner for Android. Pick a folder, then:

- **swipe left**: keep
- **swipe right**: delete (marked, then moved to the system trash in one batch)
- **swipe up / down**: next / previous photo without deciding

Deleted photos go to the Android trash (restorable for 30 days). Kept photos are remembered
and skipped next time; turn that off in Settings.

Requires Android 11+ (API 30).

## Build

Open the folder in Android Studio, or:

```bash
./gradlew assembleDebug
```

The build needs JDK 17+ (`JAVA_HOME`). The APK lands in `app/build/outputs/apk/debug/`.
