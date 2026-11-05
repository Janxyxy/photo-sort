# FotoSort

Tinder-style photo cleaner for Android. Pick a folder, then:

- **swipe left**: keep
- **swipe right**: delete (marked, then moved to the system trash in one batch)
- **swipe up / down**: next / previous photo without deciding

Deleted photos go to the Android trash (restorable for 30 days). Kept photos are remembered
and skipped next time; turn that off in Settings.

The **Categories** tab sorts photos by what's in them (screenshots, receipts, blurry shots, closed
eyes, dogs, beaches, ...) using [Eye](https://desertant.com/models/eye/) from Desert Ant Labs. It runs
on the phone; the first scan downloads a 66 MB model. Eye is free up to 100k monthly active devices
([license](https://license.desertant.com/)).

Requires Android 11+ (API 30) and a 64-bit phone (arm64) for the Categories tab.

## Build

Open the folder in Android Studio, or:

```bash
./gradlew assembleDebug
```

The build needs JDK 17+ (`JAVA_HOME`). It makes one APK per ABI in `app/build/outputs/apk/debug/`:
`app-arm64-v8a-debug.apk` for phones, `app-x86_64-debug.apk` for the emulator.
