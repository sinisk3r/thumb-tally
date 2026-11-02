# Thumb Tally (formerly Shorts Tracker; package names still say shortstracker)

An Android app that counts the short videos a person watches (YouTube Shorts, Instagram Reels and feed videos,
Snapchat Spotlight, plus regular YouTube videos kept separate), times them, and shows habits, badges and a pop-up
counter with cheeky nudges. Everything stays on the phone. The goal is a free Play Store app with an optional
$1–2 "supporter" unlock (fancier counter designs), plus a sideloaded build on itch.io.

README.md explains how it works for humans. This file holds what an agent needs to work on it safely.

## Map

```
app/src/main/java/com/srik/shortstracker/
  tracker/  ShortsAccessibilityService  reads the watched app's view tree, decides "new video / same video / left"
            ScreenParser                pure function: view tree → ScreenInfo(kind, creator, caption, key, settleMs)
            NodeSnap                    plain copy of the view tree (testable, dumpable)
            ShortsApp, ViewKind         supported apps; SHORT vs VIDEO rows
            CounterOverlay              the pop-up pill (5 animation styles, colours, limit bar, rolling number)
            Nudges                      pure function: counts → optional cheeky line
            ScreenshotStore             optional screenshots (off by default)
            PaymentApps, TrackingGuard  switch off for UPI/bank apps; "tracking is off" notifications + 5-min check
                                        (non-waking alarm, only while the service is off)
  data/     Room DB (ViewEvent, ViewDao, AppDatabase + migrations), Stats (patterns), Badges (13 good/cheeky badges),
            TodayLines (the quip under "Today"), TrackerSettings (prefs)
  ui/       Compose screens (Today, Feed, Patterns + BadgesScreen, Settings, Disclosure), Theme.kt tokens, Motion.kt springs
app/src/test/  ScreenParserTest over real captured screens in resources/screens/, NudgesTest, StatsTest
docs/       promo.html (design source of truth), roadmap.md (ideas, scope growth), play-store.md (launch gaps)
.claude/skills/  phone-testing, add-tracked-app, release
```

## Commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest      # must pass before any build is handed over
./gradlew assembleDebug          # debug build: logs + writes every parsed screen to files/trace/
./gradlew assembleRelease        # what users get
ADB=~/Library/Android/sdk/platform-tools/adb
```

## Rules

- **Detection changes need a captured screen and a test.** Never tune `ScreenParser` from guesses. Capture the real
  screen on the phone (skill `phone-testing`), save it under `src/test/resources/screens/`, add a test, then change
  the parser. The apps rename view IDs often; fixtures are what keep regressions visible.
- **A key identifies one video.** Build keys only from labels that belong to the playing video (channel + title,
  Reel author + caption). Anything a comment panel, search box or feed can show must not feed into a key. A null key
  means "player covered, keep the current video"; a null kind means "left the player".
- **Database changes need a Room migration** (bump `AppDatabase` version, add `MIGRATION_x_y`). Users have history;
  never use destructive migration.
- **Tone:** nudges are playful, never shaming or scary. No red for over-limit states: use `extraColors.caution`
  (amber). The owner likes trying options themselves, so prefer a setting + "Try it" over picking one variant.
- **Design:** UI follows `docs/promo.html`. Use tokens from `ui/Theme.kt` and springs from `ui/Motion.kt`; don't
  hard-code colours in screens.
- **Battery:** the service only receives events from the packages in `res/xml/accessibility_service_config.xml`.
  Keep work per event small (snapshot budget `MAX_NODES`), debounce, and don't add wake locks or polling outside a
  watched app.
- **Privacy:** no network calls, no analytics. Screenshots stay opt-in. Any future sync (leaderboard) sends daily
  counts only, never creators, captions or images. Play policy depends on this (see `docs/play-store.md`).
- **Debug-only code** is gated on `BuildConfig.DEBUG`. Release builds must not log screen contents.
- Library versions match `~/dev/SMS-Forwarder` (shared Gradle cache; the owner's internet is a phone hotspot, so
  avoid new dependencies and big downloads, and say so before one).
- Shared files (APKs, install notes) go to Google Drive `My Drive/dev/android-shorts-tracker/`, never the repo.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for every build handed to anyone (skill `release`).

## Gotchas

- Wireless adb goes stale: `adb kill-server && adb start-server`. `screenrecord` is blocked on the owner's OnePlus;
  `uiautomator dump` fails while video plays (use the debug build's trace dumps instead).
- `ViewPropertyAnimator` keeps `startDelay` between animations; reset it before each new one.
- The overlay window is larger than the pill (`ROOM_DP` padding) so bounces aren't clipped. Keep it that way.
- Android 13+ blocks enabling accessibility for sideloaded apps until "Allow restricted settings" in App info.
