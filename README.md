# PhotoSort

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![On-device AI](https://img.shields.io/badge/On--device%20AI-FF6F00)

A swipe-to-clean photo app for Android. Open a folder and go through it one photo at a time:

- **left** to keep
- **right** to delete
- **up / down** to skip ahead or go back without deciding

Prefer right to keep? Flip it in Settings.

Nothing is deleted straight away. Photos you swipe right are marked, and when you're done you
move them to the Android trash in one go, where they stay restorable for 30 days. Photos you
keep are remembered and skipped next time (you can turn that off).

## What else it does

- **Undo** the last 50 decisions, from the button or the snackbar.
- **Review** everything marked for deletion before it goes to the trash, and pull back anything
  you swiped by mistake.
- **Zoom** with pinch or double-tap. It loads a sharper copy while zoomed, so you can check focus.
- **Videos** show up too, with a frame preview; tap to play. Can be turned off in Settings.
- **Similar photos**: bursts taken within a few seconds of each other are grouped, and you pick
  which ones to keep from a single sheet.
- **Sort order**: newest, oldest or largest first. Largest is the quick way to free up space.
- Each card shows the date, file size and video length. Settings shows how much space you've
  freed so far.
- Folders and categories can be shown as a grid or a list.
- Light, dark or system theme. The swipe screen stays dark so photos stand out.

## Categories

The Categories tab groups your library by what's in the photos, so you can go after the junk
directly instead of scrolling through everything:

- **Clean up**: screenshots, blurry shots, closed eyes, accidental shots, similar photos (bursts),
  bad framing or light, photos of screens, memes
- **Documents**: receipts, documents and IDs, whiteboards, slides, maps, things you photographed
  as a reminder
- **People**: selfies, portraits, group shots, everyone smiling
- **In your photos**: whatever else shows up a lot, like dogs, beaches, food or cars

You can also search for a tag by name. Each category opens in the normal swipe screen.

Tagging is done by [Eye](https://desertant.com/models/eye/) from Desert Ant Labs and runs
entirely on the phone; no photos are uploaded. The first scan downloads a 66 MB model. The scan
can be paused and picks up where it left off. Eye is free up to 100k monthly active devices
([license](https://license.desertant.com/)).

## Requirements

- Android 11 (API 30) or newer, needed for the system trash
- A 64-bit ARM phone (arm64). The Eye SDK only ships arm64 and x86_64 libraries, so those are
  the only APKs the build produces

## Building

Open the folder in Android Studio, or run:

```bash
./gradlew assembleDebug
```

You need JDK 17 or newer (`JAVA_HOME`). Eye's native library is about 57 MB per architecture,
so instead of one big APK the build makes one per ABI in `app/build/outputs/apk/debug/`:

- `app-arm64-v8a-debug.apk` for phones
- `app-x86_64-debug.apk` for the emulator

Built with Kotlin, Jetpack Compose and Coil.
