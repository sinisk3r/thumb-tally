---
name: add-tracked-app
description: Add support for counting videos (or other media) in another app — e.g. Facebook Reels, Reddit, TikTok, YouTube Music, Spotify. Covers deciding between the accessibility parser and media-session tracking, the files to touch, fixtures and tests. Use when the owner asks to track a new app or a new kind of thing.
---

# Adding an app

## 1. Pick the right signal

| What's being tracked | Best signal | Why |
|---|---|---|
| Short-form video feeds (Reels, Shorts, Spotlight, TikTok, Facebook Reels, Reddit video) | Accessibility view tree → `ScreenParser` | No public "now playing" for video; the screen is the only source. |
| Music / podcasts (Spotify, YouTube Music, Apple Music, podcasts) | `MediaSessionManager` via a `NotificationListenerService` | Players publish title, artist, album, duration and play state in their media session. Works for any player, survives UI redesigns, no view parsing. |
| Exact listening history with IDs | Spotify Web API "recently played" (OAuth, network) | Accurate but needs a server-free OAuth flow and a network call; breaks the "nothing leaves the phone" promise unless opt-in. |

Don't parse a music app's screen when a media session exists. See `docs/roadmap.md` for the music design.

## 2. Video apps (accessibility)

1. **Capture first.** Install the debug build with the new package added (step 3), open the app, and pull real
   screens with skill `phone-testing`: the player on several different videos, the player with comments open, with
   the description open, search typing, the home feed, an ad, and the app's main non-video screens.
2. **Find the anchor.** Look for a view ID or content description that exists only on the player and names the
   video's creator (e.g. YouTube's `Go to channel @x`, Instagram's `clips_author_username`). Note where the caption
   sits relative to it. Ignore anything inside comment sheets or search.
3. **Wire it up:**
   - `tracker/ShortsApp.kt`: add the enum entry (package name, label shown in the UI).
   - `res/xml/accessibility_service_config.xml`: add the package to `packageNames` (the service sleeps for every
     other app, which is the battery story).
   - `tracker/ScreenParser.kt`: a `parseX` function returning `ScreenInfo`. `kind = null` off the player,
     `key = null` when the player is covered, `settleMs` if the video autoplays while scrolling past.
   - `ui/Theme.kt`: a brand-ish colour in `ShortsApp.color` (not red-alarm semantics; it's only an identity colour).
   - `res/values/strings.xml`: mention the app in `service_description` (users see it when enabling the service,
     and the Play disclosure must match).
   - `TodayScreen`/`SettingsScreen` copy that lists the apps.
   - `CounterOverlay`: check text contrast for the APP colour option (yellow needed dark text).
4. **Test:** fixtures + `ScreenParserTest` cases for: each swipe a new key, comments/description keep the key null,
   ads ignored, leaving gives null kind.
5. **Live check** on the phone: swipe 5, open comments, go back, search, leave the app. Count rows in the DB.
6. Update README, `docs/promo.html` app list if it's user-facing, and the install txt in Drive on the next release.

## 3. Music apps (media session) — new subsystem

Not built yet. Sketch:

- New `tracker/MediaListener : NotificationListenerService`; on connect, `MediaSessionManager.getActiveSessions(
  ComponentName(this, MediaListener::class.java))` and `addOnActiveSessionsChangedListener`.
- Per controller, a `MediaController.Callback`: on `onMetadataChanged` take `METADATA_KEY_TITLE`, `ARTIST`,
  `ALBUM`, `DURATION`; on `onPlaybackStateChanged` track playing time.
- Count a "listen" when a track plays ≥ 30 s or half its length (Spotify's own rule is 30 s), keyed by
  `package + title + artist`, with the same resume window idea as videos.
- Store in a new `ListenEvent` table (migration!), not `ViewEvent`; `Stats` grows listening patterns.
- Filter packages with an allow-list in settings (Spotify `com.spotify.music`, YouTube Music
  `com.google.android.apps.youtube.music`, …) so podcasts/video apps don't double count.
- Permission: notification access, which needs its own in-app disclosure and a Play declaration (see
  `docs/play-store.md`).
