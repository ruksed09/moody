# Releasing a Moody update

Every installed copy of Moody checks `https://api.github.com/repos/ruksed09/moody/releases/latest` when it opens
(at most every 6 hours). If that release is newer than the app, the user sees the "A new Moody is ready" popup, the
APK downloads with a progress bar, and Android's installer updates the app in place.

## One-time setup

1. On github.com, create a **public** repository named `moody` under `ruksed09`. It must be public, because the app
   downloads the APK without signing in. You can push this project there, or keep the repo empty and use it only for
   releases.
2. **Back up the signing key.** The key is in `C:\Users\USER\Downloads\Moody-keys\`:
   - `moody-release.jks` is the key.
   - `keystore.properties.backup` holds its passwords.

   Copy both files somewhere safe, such as a password manager or an offline drive. **If this key is lost, no
   installed copy can ever be updated again.** Users would have to uninstall, and uninstalling deletes their vault.
   Never commit these files. `.gitignore` already blocks them.

## Each release

1. In `app/build.gradle.kts`, raise both version numbers:
   ```kotlin
   versionCode = 2          // +1 every release
   versionName = "1.1.0"    // what users see; must be higher than the last release
   ```
2. Build the signed APK:
   ```bash
   ./gradlew assembleRelease
   ```
   The APK is written to `app/build/outputs/apk/release/app-release.apk`. Rename it to `moody-1.1.0.apk`, since the
   name must end in `.apk`.
3. On github.com/ruksed09/moody, go to **Releases** and choose **Draft a new release**:
   - **Tag:** `v1.1.0`. It must match `versionName`, with a leading `v`.
   - **Title:** anything, for example `Moody 1.1.0`.
   - **Notes:** short lines starting with `- `. `**bold**` works. Users see these notes in the popup.
   - Attach `moody-1.1.0.apk`.
   - Leave "Set as a pre-release" **off**, and keep "Set as the latest release" **on**.
   - Click **Publish release**.
4. That's it. Each user sees the popup the next time they open Moody, within 6 hours of their last check.

## Forcing an update

Put `[required]` anywhere in the release notes. The popup then has no "Later" button and can't be dismissed. Use
this only for broken builds. The marker is not shown to users.

## Things that stop the popup

- The release is a draft or a pre-release. `releases/latest` ignores both.
- No attached file ends in `.apk`.
- The tag is not higher than the installed `versionName`.
- The user tapped "Later" on this exact version less than 24 hours ago. A check from Settings ignores the snooze.

## Testing an update before shipping it

Debug builds are signed with a different key, so a release APK can't update a debug install. To test:

1. Uninstall any debug build first.
2. Install a release-signed build of the current version:
   ```bash
   adb install app-release.apk
   ```
3. Publish the next version as above, then open Moody and check that the popup appears, the download finishes, and
   the installer updates the app.
