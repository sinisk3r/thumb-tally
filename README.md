# Thumb Tally

A sideloaded Android app that counts the YouTube Shorts, Instagram Reels (Reels tab and feed videos) and Snapchat
Spotlight videos you watch, plus regular YouTube videos kept separate: how many, for how long, which creators, when in
the day, viewing-habit badges, and (optionally) a small screenshot of each. A counter pops up on each new Short with
an occasional cheeky nudge (milestones, limit, late night, skipping sprees). Everything stays on the phone.

The UI uses the same design system as the promo page (`docs/promo.html`): tokens in `ui/Theme.kt`, bundled
Big Shoulders / Figtree / JetBrains Mono fonts in `res/font`, springs in `ui/Motion.kt`.

## How it works

An **accessibility service** (`tracker/ShortsAccessibilityService.kt`) reads the YouTube/Instagram/Snapchat view
tree a moment after each change. Its config limits events to those three apps (battery), so leaving them is detected by a
2-second foreground check while a video is open. It records its own CPU time (`data/TrackerHealth.kt`), which
Settings shows next to the battery-optimisation status. `tracker/ScreenParser.kt` decides whether that's the Shorts/Reels player and
returns a key for the playing video, built only from labels that belong to it ("Go to channel @x" + title on a Short,
the author row on a Reel). Comments, search and the home feed never produce a key, so they can't create rows. A new
key starts a new row in a Room database; returning to a video from the last 10 minutes continues its row. Instagram
feed videos must stay on screen 2 s to count. Leaving
the app or turning the screen off closes the current video and stores how long you watched it (capped at 5 min for
shorts, 3 h for regular videos). The pop-up counter (`tracker/CounterOverlay.kt`, five animation styles including
"From the camera", which grows out of the punch-hole) uses the accessibility overlay window type, so it needs no
"draw over other apps" permission and ignores touches. Nudges live in `tracker/Nudges.kt`.

Screenshots use `AccessibilityService.takeScreenshot` (Android 11+), are scaled to 540px wide JPEGs in app-private
storage, and are pruned after the retention period. They are **off by default**.

The apps rename their internal view IDs now and then. `src/test/resources/screens/` holds real screens captured from
a phone; `ScreenParserTest` runs the parser over them. When counting breaks, install a debug build (it writes every
screen it reads to `files/trace/`), pull a dump with `adb exec-out run-as com.srik.shortstracker cat files/trace/<file>`,
save it there, and add a test. Users can share a dump from Settings → Troubleshooting.

## Build

Needs the Android SDK (Android Studio's first-run setup installs it to `~/Library/Android/sdk`).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest   # parser, nudge and stats tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

Library versions match `~/dev/SMS-Forwarder` so both projects share one Gradle cache.

## Install on the phone

1. Copy the APK to Google Drive `My Drive/dev/android-shorts-tracker/ShortsTracker-<version>.apk` and open it from the Drive app on the phone (or `adb install` over USB).
2. Open it and allow "install unknown apps" for whichever app opened it.
3. Open Thumb Tally → **Accessibility** → Thumb Tally → On.
   On Android 13+, if the switch is greyed out ("restricted setting"): **App info → ⋮ → Allow restricted settings**,
   then try again.
4. Some phones (Xiaomi, Oppo, Vivo, OnePlus) kill background services; set battery usage to "Unrestricted".

## Later

- **Friends leaderboard**: a Supabase table of daily counts per person (never screenshots), joined via invite code.
- **Lifeboard**: push the daily summary to lifeboard over Tailscale.

More in `docs/roadmap.md`; Play Store launch gaps in `docs/play-store.md`.

## Licence and contributing

Thumb Tally is free software under the [GNU GPL v3](LICENSE): you can use, study, change and share it, and anything
built from it must stay open under the same licence.

Contributions are welcome, especially fixes when YouTube, Instagram or Snapchat change their screens. Read
`CLAUDE.md` first: detection changes need a captured screen and a test, and nothing may send data off the phone.
Captured screens must not contain real people's handles or messages; replace them with placeholders.
