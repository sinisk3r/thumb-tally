---
name: phone-testing
description: Connect to the owner's Android phone over wireless adb, install a debug build, drive YouTube/Instagram/Snapchat, pull the tracker's screen dumps and database, and turn real screens into test fixtures. Use when detection or the pop-up counter needs checking on a real device, or a bug report says counts are wrong.
---

# Testing on the phone

The owner's phone is a OnePlus CPH2649 on Android 16, connected with wireless debugging. Their Mac's internet is the
phone's hotspot, so the phone and Mac are on the same network.

## Connect

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices                     # expect one "device" line (adb-…_adb-tls-connect._tcp)
```

- No device, or commands hang: `$ADB kill-server; $ADB start-server; $ADB devices`.
- Still nothing: ask the owner to open Developer options → Wireless debugging (pair via Android Studio's QR if the
  pairing was lost).
- Screen locked (screenshots come back black or the lock screen): ask the owner to unlock. Don't change their lock
  or timeout settings without saying so, and restore anything you change (note original values first with
  `settings get`).

## Install and watch

```bash
./gradlew assembleDebug && $ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB logcat -c; $ADB logcat -s ShortsTracker      # events, parses, starts/stops (debug only)
```

Installing over the release build keeps data only if the signing key matches (both currently use the debug key).
Reinstalling can unbind the accessibility service: check Settings → Accessibility → Shorts Tracker is on.

Drive the apps with input events, then screenshot to see what happened:

```bash
$ADB shell monkey -p com.google.android.youtube 1
$ADB shell input swipe 540 2200 540 600 200        # next Short / Reel
$ADB shell input keyevent KEYCODE_BACK
$ADB exec-out screencap -p > /path/in/scratchpad/shot.png   # then Read it
```

Screen size: `$ADB shell wm size`. Scale swipe coordinates to it.

`screenrecord` is blocked on this phone and `uiautomator dump` fails while video plays. To judge an animation, slow it
with `settings put global animator_duration_scale 10` and take several screenshots, then put it back to the
original value (it was 1.0).

## Read what the tracker saw

The debug build writes every parsed screen to `files/trace/<timestamp>.txt` (first lines: the parse result, then a
blank line, then the tree).

```bash
$ADB exec-out run-as com.srik.shortstracker ls files/trace | tail
$ADB exec-out run-as com.srik.shortstracker cat files/trace/<ts>.txt > app/src/test/resources/screens/<name>.txt
$ADB exec-out run-as com.srik.shortstracker cat databases/shorts.db > /path/in/scratchpad/shorts.db
```

Room keeps recent writes in `shorts.db-wal`; pull it too (or stop the app first) for up-to-date rows. Query the copy
with `sqlite3` on the Mac, never the live file.

## Turn a bug into a fixture

1. Reproduce it on the phone; find the trace file(s) at that moment.
2. Save them under `app/src/test/resources/screens/` with a descriptive name (`yt_short_comments_scrolled.txt`).
   Prefix by app: `yt_`, `ig_`, `snap_`.
3. Add a test in `ScreenParserTest` that states the expected behaviour (same key, null key, different key, null kind).
4. Fix `ScreenParser`, run `./gradlew testDebugUnitTest`.
5. Re-check live on the phone.

## Finish

- Delete trace dumps (`run-as com.srik.shortstracker rm -r files/trace`) — they hold what the owner watched.
- Put the release build back (skill `release`) unless the owner asked to keep the debug build.
- Restore any phone setting you changed.
