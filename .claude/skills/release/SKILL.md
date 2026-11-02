---
name: release
description: Cut a build of Shorts Tracker for the owner or their friends — bump the version, run tests, build the release APK, copy it to Google Drive with the install notes, and put it on the owner's phone. Also lists the extra steps for a Play Store (AAB) or itch.io release. Use when asked to ship, share, hand over, or "give me" a build.
---

# Releasing

## Every release

1. **Tests pass:** `./gradlew testDebugUnitTest`. Don't hand over a build with failing tests.
2. **Bump** `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Update the title string in
   `ui/SettingsScreen.kt` if it shows the version.
3. **Build:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
4. **Share:** copy to `~/Library/CloudStorage/GoogleDrive-*/My Drive/dev/android-shorts-tracker/ShortsTracker-<version>.apk`
   (never commit APKs). Update `How to install Shorts Tracker.txt` there if steps or supported apps changed.
5. **Owner's phone:** delete debug traces, then `adb install -r` the release APK (skill `phone-testing`). Confirm the
   accessibility service is still bound (Settings → Accessibility).
6. Update README for user-visible changes. Tell the owner what changed and what's unverified.

## Signing

From 0.4 the app id is `com.srik.thumbtally` (debug builds add `.debug`). Release builds are signed with the
owner's upload key `~/.config/thumb-tally/upload.jks` (alias `thumbtally`); `app/build.gradle.kts` reads its
password from the macOS Keychain item `thumb-tally-keystore`. Without that item the release silently falls back
to the debug key: check with `apksigner verify --print-certs` (expect `CN=Srikar Kandikonda`) before sharing.
Backup of the keystore + setup steps: Drive `My Drive/dev/android-shorts-tracker/signing-key-private/` (owner-only;
never share that folder). Never print, invent or commit the password.

## Play Store (once set up — see docs/play-store.md)

- Build an App Bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, signed with
  the upload key. Play re-signs with the app signing key.
- `versionCode` must be higher than any upload ever made, including closed-testing tracks.
- Release notes per track; roll out to internal → closed testing → production.
- Re-check `docs/play-store.md` "every release" items (data safety, accessibility declaration still accurate).

## itch.io

Upload the release APK (same key as previous itch builds) with butler or the web UI; the page should repeat the
install and "Allow restricted settings" steps from the Drive txt.
