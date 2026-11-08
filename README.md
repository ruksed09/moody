# Moody

A video and music player for Android. Plays practically any local file (libVLC), keeps private videos in a
PIN-locked vault, and updates itself from GitHub Releases.

Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, libVLC 3.7.7 for video, AndroidX Media3 for music.
minSdk 26, targetSdk 35, compileSdk 36.1. arm64-v8a only.

## Build

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 36.1.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests, plus screenshots written to app/build/screenshots
./gradlew connectedDebugAndroidTest   # device tests (phone connected over USB)
./gradlew assembleRelease      # signed with the key from keystore.properties
```

`keystore.properties` (git-ignored) points at `C:\Users\USER\Downloads\Moody-keys\moody-release.jks`. Back up
both files. See [docs/RELEASING.md](docs/RELEASING.md) for publishing updates.

## Features

- **Video:** folders or all videos, in a grid or a list, with sorting, search, continue watching, resume, thumbnails,
  and share, rename, delete and details.
- **Player:** everything libVLC plays (MKV, AVI, WMV, FLV, HEVC, AC3, DTS, ASS subtitles). It includes:
  - Gestures: brightness, volume, seek with a preview, double-tap seeking that adds up, hold for 2×, and pinch to
    switch between fit and fill.
  - Track control: audio and subtitle tracks, loading subtitle files, subtitle style, and timing for audio and
    subtitles.
  - Playback options: speed from 0.25× to 4×, HW, HW+ or SW decoding with an automatic switch to SW, an equalizer,
    A-B repeat, loop, a sleep timer, a control lock, aspect modes and rotation.
  - Picture-in-picture, background play, and playing network streams.
- **Music:** songs, albums, artists, folders, playlists and favourites. It has:
  - A mini player and a Now Playing screen, plus a queue you can reorder and swipe to remove from.
  - Speed, a sleep timer, an equalizer and bass boost.
  - Lock-screen, notification and headset controls.
  - WMA, APE and similar files, which go through libVLC.
- **Vault:** set a PIN with a recovery question.
  - Hidden videos move into app storage. A copy is checked before the original is deleted, and the original is only
    deleted after the system asks you.
  - Unhide puts a video back in its own folder, or in `Movies/Moody` when Android doesn't allow that folder.
  - The vault locks after repeated wrong PINs and when you leave the app, and it is hidden from screenshots.
  - The disguise option removes the Vault button. Press and hold the Moody logo on Videos to open the vault.
- **First run:** an animated logo, three welcome pages, and permissions.
- **Appearance:** System, Light, Dark and Midnight themes. App font (Geist) or the system font. Quiet background
  drawings.
- **Updates:** a popup with release notes, a download with progress, and the Android installer.
  Releases marked `[required]` can't be skipped.

## Status

- **Verified on the JVM:** about 60 unit tests and Robolectric screenshot renders. They cover:
  - Time and size formatting, sorting, gesture math and resume rules.
  - PIN hashing and lockout, vault restore paths and crash recovery.
  - Release parsing and version compare, music grouping and audio routing.
  - Stream link parsing and Room DAOs.
- **Verified in the release build:** R8 shrinking passes, the APK is about 27 MB, and the libraries use a 16 KB page
  alignment.
- **On-device checks:** see the checklist below. Results are filled in after the phone run.

## Device checklist

- [ ] MKV with AC3/DTS audio, AVI, WMV, FLV, and ASS subtitles all play
- [ ] Gestures, double-tap, hold for 2×, pinch, and the control lock work
- [ ] Picture-in-picture, background play and the notification work
- [ ] Music: lock-screen controls, pausing when headphones are unplugged, and playing a WMA file
- [ ] Vault: hide from DCIM and from Download, then unhide; the disguise entry works
- [ ] All four themes and both fonts look right
- [ ] Update popup: install release v1.0.0, publish v1.0.1, then open the app
