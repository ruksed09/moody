# Moody — design

Date: 2026-11-08. Status: draft for approval.

## Intent

Moody is a native Android media player for local video and music, at the level of VLC / MX Player, for people who
share the APK directly with friends. It should play any file they throw at it, feel calm and crafted (the warm
"screening room" look of the Kurogane mockup), and keep private videos behind a PIN. The owner must be able to push
an update to every install and have users see an "update available" popup on next open.

## Goals

1. Plays practically every local video format, including MKV with AC3/DTS/EAC3 audio, AVI, WMV, FLV, and styled ASS subtitles.
2. A full player feature set with gestures and controls like MX Player (list below).
3. A music player with a library, playlists, a mini player, and lock-screen and notification controls.
4. A private vault. Hidden videos move out of public storage, sit behind a PIN, and the vault entry can be disguised.
5. First-run flow: an animated logo, then welcome pages, then file permissions.
6. Theme modes plus illustrated backgrounds. Real vector icons everywhere, with no emoji. Skeleton loading, empty states, and progress bars.
7. Self-hosted updates through GitHub Releases. The app checks on open, shows a popup, downloads the APK with a progress bar, and installs it.

## Non-goals (v1)

Play Store release; Chromecast/DLNA; SMB/FTP/network-share browsing; online subtitle download (needs an OpenSubtitles
key); lyrics; video editing/conversion; cloud sync/backup of the vault; tablet/TV-specific layouts; iOS.

## Anti-goals (failure even if it "works")

- Losing a user's file. A vault move must copy, verify the size, then delete the original. It never deletes first.
- Emoji or clip-art icons. Purple-gradient, glow, or "AI-looking" UI.
- A janky player: lag when seeking, gestures that fight each other, controls that won't hide.
- Leaking hidden videos. They must not show in thumbnails, recents, screenshots, Gallery, or "recently played".

## Constraints

- Stack matches the owner's existing Modo app: Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, coroutines.
  Same proven versions (AGP 8.13.2, Kotlin 2.2.10, Gradle 8.13, JDK = Android Studio JBR). minSdk 26, target/compile 35.
- New standalone project and git repo at `C:\Users\USER\Downloads\Moody`, package `com.moody.player`.
- The APK is distributed outside Play. Every update must be signed with the same release key, which is generated once
  and kept outside git (see Updates).
- Test device: the owner's Realme RMX3998 (Android 16, arm64).

## Approaches considered (engine)

1. **libVLC for video + Media3 for music** — chosen by the owner. libVLC (the VLC core) gives "plays everything" and
   ASS rendering. Media3 `MediaSessionService` gives native notification and lock-screen controls, gapless playback,
   and headset buttons for music. Cost: two engines, and an APK of about 35 MB.
2. Media3 + FFmpeg audio extension. Smaller (about 15 MB) and one engine, but WMV/ASF, some AVI codecs, and ASS styling fail.
   Rejected: it breaks "play everything".
3. libVLC for everything. One engine, but music session and notification would be hand-built, and gapless playback is weaker. Rejected.

## Decisions made with the owner

- Distribution: APK shared directly. Updates are published as GitHub Releases.
- Hiding: the file moves into Moody's private vault. It disappears from Gallery and other apps. Uninstalling Moody deletes
  vault files, and the app warns about this when the vault is first set up and before uninstall-relevant actions.
- Lock: PIN code (4–6 digits) plus a disguised entry. Biometrics and pattern are not included.
- Formats: play everything (libVLC, version pinned to the latest stable 3.6.x on Maven Central, `org.videolan.android:libvlc-all`).
  Before building, check the phone's page size (`adb shell getconf PAGE_SIZE`). If it is 16 KB, the libVLC version must ship
  16 KB-aligned native libraries.
- Music formats: Media3 plays MP3, AAC/M4A, FLAC, OGG/Opus, WAV and ALAC. Files it cannot decode (WMA, APE and similar)
  are routed to libVLC instead of failing, so the Music tab also plays everything.

## Design

### Look and feel
- Tokens come from the Kurogane mockup. Dark: bg `#13100e`, surface `#1a1613`, raised `#221d19`, text `#f1e9e0`/`#b8ab9d`/`#877b70`,
  accent amber `#e6a766`, accent-ink `#1b130b`, hairline lines in warm translucent beige. Fonts: Fraunces (display),
  Hanken Grotesk (UI), DM Mono (times, codes, metadata). The font files are bundled in `res/font` and taken from the
  google/fonts repo under the OFL licence.
- *My call:* theme modes are System / Light / Dark / Midnight (true black for AMOLED). Light is warm paper (`#f6efe6`
  bg) with a deeper amber (`#b86b25`) so text meets AA contrast. Theme switches animate with a crossfade.
- *My call:* the background illustrations are custom vector art drawn in Compose: a crescent moon, soft layered hills
  or waves, and film-grain dots. They sit at low opacity behind headers, onboarding, and empty states. Each tab has its
  own variant: film strip and moon for Video, sound waves and moon for Music. Each theme has its own tint.
- Icons are hand-authored `ImageVector`s in the mockup's stroke style (1.6 stroke, round caps). Material icons are
  used only where the mockup has no equivalent, never emoji.
- Motion is calm: 150–280 ms ease-out. Shared-element-style expansion from the mini player to the now-playing screen.
  Reduced-motion is respected.
- Loading uses shimmer skeletons shaped like the real rows and grids. Progress shows as thin amber bars: resume
  position on thumbnails, scan progress, update download, and vault move.
- Every list has an empty state with an illustration, one line of copy, and an action, for example "No videos yet
  — Rescan" or "Vault is empty — Hide videos from your library".

### First run
1. **Animated logo.** The Moody mark (a rounded square with a play glyph merging into a crescent moon) draws in with path
   trim, the wordmark fades up, and the whole thing lasts about 1.6 s. The Android 12+ SplashScreen API shows the static mark first,
   then hands off to the Compose animation. *My call:* this is built with Compose path animation, not a hand-written
   Lottie JSON. The full animation plays on first launch only. Later launches get a 400 ms version.
2. **Welcome** — three swipeable pages: "Plays everything", "Your private vault", "Music that follows you". Each page
   has its own illustration, page dots, and Skip and Next buttons.
3. **Permissions** — a clear card for each permission, showing why it is needed and its granted state:
   - Videos and music: `READ_MEDIA_VIDEO` + `READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` on 12 and below. Required.
   - Notifications: `POST_NOTIFICATIONS` on 13+, for music controls and update downloads. Optional.
   - Denied twice: the app deep-links to its own system settings page. Without media access, the libraries show a
     "Moody needs access" empty state with a button.
4. Done. The app lands on the Video tab and the first scan runs, with skeletons shown until it finishes.

### Navigation
A bottom bar with two tabs, **Video** and **Music**. It has custom SVG icons and an amber indicator that slides
between tabs. The top bar shows the Moody logo, search, a sort/view menu, and Settings. The music mini player docks
above the bottom bar whenever something is playing.

### Video tab
- Two views: Folders (default), and a flat list of all videos. Each view can be shown as a list or a grid.
- Sort by name, date, size, or duration, ascending or descending.
- Search across titles.
- Thumbnails are generated lazily and cached. A video's card shows its duration badge, a resume progress bar, and a
  "New" dot if it has never been played.
- A "Continue watching" row appears at the top of the tab.
- Long-press starts multi-select. Selected videos can be hidden in the vault, shared, renamed, deleted (with the
  system confirm dialog), or opened in the info sheet. The info sheet shows codec, resolution, bitrate, size, and path.
- Pull to refresh rescans the library, and a MediaStore content observer refreshes it automatically.
- "Open network stream": paste a URL (http/https/rtsp/HLS). The last 10 URLs are remembered.
- Moody registers for `ACTION_VIEW` on video and audio types, so other apps and file managers can open files with it.

### Video player (libVLC)
- The player is fullscreen and immersive. The screen keeps the orientation of the video by default, with a rotate
  button and an orientation lock.
- Gestures:
  - Swipe vertically on the left half for brightness, on the right half for volume. Each shows a vertical level bar.
  - Swipe horizontally to seek, with a preview of the target time and the change.
  - Double-tap the left or right side to jump 10 s, with a ripple ping. Repeated taps add more.
  - Single tap toggles the controls. Long-press plays at 2× while held. Pinch zooms between fit and fill, then free zoom.
- Controls: play/pause, previous/next video in the folder, a seek bar with buffered and played segments, and the current
  and total time. Tapping the time switches it to time remaining.
- Lock button: hides all controls and gestures until the user unlocks.
- Aspect ratio cycles through Fit, Fill, Stretch, 16:9, 4:3, and Original.
- Speed: 0.25–4×, with presets and fine steps.
- Audio track picker, and an audio delay setting.
- Subtitles:
  - Choose an embedded track, or load an external SRT/ASS/SSA/VTT file through the file picker. A subtitle file with
    the same name as the video is picked up automatically.
  - Adjust subtitle delay, size (S, M, L, XL, as in the mockup), colour, and background (Shadow, Soft, Solid).
- Decoder: HW, HW+ or SW, as in MX Player, with automatic fallback to SW when the hardware decoder fails.
- 10-band equalizer with presets, using libVLC's Equalizer.
- A-B repeat and loop modes (off, one, folder).
- Sleep timer: 15/30/45/60 minutes or end of video.
- Picture-in-picture. Background play continues as audio when the user leaves the app. This runs in its own foreground
  service (`foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) with a notification.
- Starting a video pauses the music session, and starting music stops background video, so the two engines never play at once.
- Toasts in the player use the mockup's plain-language style, for example "1.5× speed", "Subtitles off", "Volume 60%".
  Error copy says what happened and what to do, as the mockup does.
- Resume where the video was left, as an inline "Resumed at 12:04 · Start over" chip. A video is marked watched past 95 %.
- Errors show an in-player sheet with Try again, Switch to SW decoder, or Next video.
- All settings persist per user in DataStore. Speed and tracks can optionally persist per file.

### Music tab
- Sub-tabs: Songs, Albums, Artists, Folders, and Playlists. Favourites is a built-in playlist.
- Album art comes from MediaStore or the embedded picture. A tinted placeholder illustration is used when there is none.
- Now playing (full screen):
  - Large artwork with a blurred backdrop tinted from the art's palette.
  - Title and artist, a seek bar, previous/play/next, shuffle, repeat (off/all/one), and favourite.
  - A queue sheet with drag-to-reorder and swipe-to-remove, plus speed and the sleep timer.
- Media3 `MediaSessionService` provides the notification, lock-screen, and Bluetooth/headset controls, and remembers the
  last queue and position.
- Gapless playback, audio focus handling, and pause when headphones are unplugged.
- Equalizer: Android `audiofx.Equalizer` and `BassBoost` on the session.
- Playlists: create, rename, delete, add songs from multi-select, and reorder.

### Vault
- First open asks the user to set a 4–6 digit PIN (entered twice) and a recovery question and answer. *My call:*
  without a recovery question, a forgotten PIN would lock files away for good. The uninstall warning is shown here.
- The PIN and the recovery answer are stored as salted PBKDF2-SHA256 hashes (≥120k iterations) in DataStore. They are
  never stored in plain text.
- After 5 wrong attempts the vault locks out: 30 s at first, then doubling.
- The vault auto-locks when the app goes to the background and after 2 minutes idle.
- Vault screens and vault playback set `FLAG_SECURE`, so they cannot be captured in screenshots and show blank in
  recents. Vault videos never appear in "Continue watching" or any library list.
- **Hiding a video:**
  1. Copy the file into `filesDir/vault/` under a random name.
  2. Verify the copy's byte size.
  3. Delete the original. On Android 11+ this goes through `MediaStore.createDeleteRequest`, which shows one system
     confirm dialog per batch. If the user declines, the copy is removed again and nothing is lost.
  4. Room stores the original name, path (RELATIVE_PATH), duration, and a thumbnail kept in private storage.
- **Free space:** before each copy the app checks for the file's size plus a 50 MB margin, because hiding briefly needs
  the space twice. If short, it stops with "Not enough space to hide <name> (needs 2.1 GB)".
- **Unhide:** the file goes back through MediaStore to its original folder *if that folder is writable*: DCIM, Movies
  and Pictures paths go through the Video collection, `Download/...` through the Downloads collection. Anything not
  writable (for example `Android/media/com.whatsapp/...`, or a folder that no longer exists) goes to `Movies/Moody`.
  The app tells the user where each file landed. The vault copy is deleted only after the write is verified.
- A progress sheet shows each move, and the user can cancel between files.
- **Disguised entry.** By default the vault shows as a "Vault" chip on the Video tab. When "Disguise vault" is turned on
  in Settings, the chip disappears. The vault is then opened by long-pressing the Moody logo in the Video tab's top bar
  for 1 s, which opens the PIN pad. *My call:* the gesture is fixed, not configurable.

### Updates (GitHub Releases)
- The release repository is set in `build.gradle.kts` as `UPDATE_REPO` (for example `ruksed09/moody`). It must be public,
  so that APK downloads need no sign-in.
- **The check:**
  1. On each cold start (at most once every 6 h) the app calls `api.github.com/repos/{repo}/releases/latest`.
  2. It reads `tag_name` (for example `v1.1.0`), the release notes, and the first `.apk` asset.
  3. It compares the tag with the app's `versionName` using semantic versioning.
- **The popup** is a branded dialog: new version, size, rendered notes, and Update or Later. "Later" snoozes that version for 24 h.
  If the release notes contain `[required]`, Later is hidden and the popup can't be dismissed.
- **Download:**
  1. OkHttp downloads the APK into the cache with a progress bar and a cancel button. A notification shows progress
     if the user leaves the app.
  2. The app verifies the size.
  3. It hands the file to the system installer through a FileProvider (`REQUEST_INSTALL_PACKAGES`). The first time,
     Android asks the user to allow "Install unknown apps" for Moody, and the popup explains this step.
- Settings has a "Check for updates" row with the current version and when the app last checked.
- **Signing:** a release keystore is generated once at `C:\Users\USER\Downloads\Moody-keys\moody-release.jks`, outside the repo.
  Its passwords go in `keystore.properties`, which is git-ignored. If this key is lost, existing installs can never
  update. The README says to back it up.
- **Release process** (written in `docs/RELEASING.md`):
  1. Bump `versionCode` and `versionName`.
  2. Run `./gradlew assembleRelease`.
  3. Create a GitHub Release with tag `vX.Y.Z` and attach the APK.
  The next time each user opens Moody, they see the popup.

### Settings
- Appearance: theme mode, illustrated backgrounds on or off, and grid or list default.
- Player: default decoder, double-tap seek amount, gestures on or off, resume on or off, remember per-file settings,
  background play, auto-PiP, and subtitle style defaults.
- Music: gapless, equalizer, and the minimum length for songs (hides clips under 30 s).
- Library: hidden folders (excluded from scan), plus "Show .nomedia folders".
- Vault: change PIN, change recovery, disguise vault, and auto-lock timeout.
- Updates, and About (version, licences).

### Architecture
- A single `app` module with package layering, as in Modo:
  - `data/`: MediaStore scanner, Room, DataStore, vault file ops, update client.
  - `domain/`: pure logic with no Android types, such as sorting, version compare, PIN hashing parameters, gesture math,
    time formatting, and resume rules.
  - `player/`: the libVLC wrapper and the Media3 service.
  - `ui/`: Compose screens and view models.
  - `design/`: tokens, typography, icons, illustrations, and components.
- Room tables: playback positions, playlists and their entries, favourites, vault items, recent stream URLs, and per-file
  settings. Thumbnails go in a disk cache.
- *My call:* only the arm64-v8a ABI is built, which keeps the APK at about 35 MB. 32-bit-only phones (rare, older than 2019) are
  not supported in v1.

### Testing
- Update flow: debug-signed installs cannot be updated by a release-signed APK. The update test uses two release-signed
  builds: install v1.0.0, publish v1.0.1 as a GitHub Release, open the app. `releases/latest` ignores drafts and pre-releases.
- JVM unit tests: version compare, PIN hash and verify plus lockout schedule, sorting and search, time formatting,
  gesture-to-delta math, resume rules, and update-JSON parsing.
- Instrumented tests on the Realme: onboarding to permissions, tab navigation, a vault hide and unhide round trip on a
  generated test video, and theme switching.
- Manual device pass, with a checklist in the README: MKV+AC3, AVI, WMV, ASS subtitles, gestures, PiP, lock-screen
  music controls, and the update popup against a test release.

## Left to the builder
Exact layouts and spacing within the tokens above, illustration shapes, copy wording, the skeleton shapes, and the
equalizer preset values.

## Open (owner)
- The GitHub repository that will host releases. It must be public.

## Visual direction (unslop pass, requested by the owner mid-build)
- **Product:** plays your own videos and music offline, and keeps private clips behind a PIN.
- **Character:** hushed, warm, exact.
- **World:** a late-night screening room, with projector light, film stock, an amber lamp and dark seats.
- **Moments:** the real video thumbnails and album art carry the colour. The onboarding and empty-state illustrations
  are the only painted scenes. Header backgrounds are quiet hairline drawings (a horizon and a moon outline) at low
  opacity. There are no glows, grain, blobs or radial washes.
- **Signature:** the "reel line", a thin 2 dp amber progress line. It is used for resume position, seek, scanning,
  downloads and vault moves, and nowhere else.
- **Rules:**
  - Sentence-case labels, with no uppercase tracked labels. This deliberately overrides the mockup's `.sec-title`.
  - Weights 400 and 500, with 600 used rarely.
  - Radii: controls 10, cards and thumbnails 12–14, sheets 20.
  - Spacing on a 4 dp grid.
  - The amber accent appears only on the primary action, the selection and the reel line.
  - Copy has no exclamation marks and no trailing periods on short strings.

## Typeface change (owner request, mid-build)
- The owner found Fraunces + Hanken Grotesk + DM Mono too "AI slop". They are replaced by **Geist** (UI and headings,
  weights 400/500/600, slight negative tracking on large sizes) and **Geist Mono** (times, sizes, counts), both OFL
  and bundled.
- Settings > Appearance gets **Font: App font / System font**. System font uses the phone's typeface with tabular digits
  for figures.
