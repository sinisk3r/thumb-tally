# Roadmap and ideas

What the app is for: a free, private, slightly cheeky mirror of how much short-form media someone consumes. It
counts, it times, it notices patterns, and it teases (never shames). The data never leaves the phone unless the
user joins something social on purpose.

Ordered roughly by value ÷ effort. Each item notes the files it touches.

## Next

- **Bug: opening the keyboard counts a new Reel** (owner report, 2026-11-05). Typing a comment or search inside
  Instagram makes the counter tick. Capture the screen with the keyboard open (skill `phone-testing`, debug trace
  dumps), add a fixture + test, then fix in `tracker/ScreenParser.kt` / `ShortsAccessibilityService`. Likely the IME
  window or a resized view tree yields a different key; a covered player should give a null key.
- **Find the song** (owner idea, 2026-11-05). Lots of Reels are music; the song name is usually in the caption or
  the audio label. Tap a feed row's song to search Spotify / YouTube Music (plain search intents, no network from
  this app). Needs the audio label captured from real screens first. `ui/FeedScreen.kt`.
- **Instant "you opened Instagram while paused" reminder** (parked 2026-11-03). Today the check is a non-waking
  5-minute alarm, so the reminder lands 5–8 min late. Option: while paused, a foreground service whose ongoing
  notification is "Thumb Tally is paused — tap to resume", polling `UsageStatsManager` every ~5 s with the screen on,
  stopped on screen-off or resume. Needs a `specialUse` foreground-service declaration for Play. `tracker/TrackingGuard.kt`.
- **Show paused time** (parked 2026-11-03). While paused, read app time from Usage access and show
  "Instagram: 14 min while paused (videos not counted)" in amber on Today, so pauses aren't silent gaps. Time only
  fills gaps; counting still needs accessibility (owner: per-swipe counter is the product).
- **Play Store readiness** — see `play-store.md`. Disclosure screen, signing, flavors, rename.
- **Clean up inflated history** from pre-0.3 duplicate rows (owner to decide: wipe before 2026-10-22 or keep).
- **"Take me back"** — tap a feed row to reopen it. YouTube: search intent for `title + channel` (nearly always the
  top hit) or the channel's Shorts tab. Instagram/Snapchat: the creator's profile (the exact video ID isn't on
  screen). A "danger zone" interstitial with rotating quotes before opening. `ui/FeedScreen.kt`, new `ui/TakeMeBack.kt`.
- **Badges collection** — done 2026-11-03: 13 badges (`data/Badges.kt`), tap "Your habits" on Patterns. Still to
  add: streaks, favourite creators.
- **Badges and streaks** — rewatch badge (same `videoKey` ≥ 10 times), per-app streaks, longest streak, favourite
  creators (pin from feed). Pure logic in `data/Stats.kt` with tests; cards in `PatternsScreen`.
- **Achievements via the pop-up** — the sarcastic "achievement unlocked" moments reuse `CounterOverlay` with the nudge
  slot. Keep them rare.

## Grow what's tracked

- **Music listening** (Spotify, YouTube Music, any player): media sessions via a notification listener, not screen
  parsing. Count a listen at ≥30 s or half the track. New `ListenEvent` table, new Today/Patterns sections
  ("42 songs today, 2 h 10 m, top artist…"), nudges like "That's the 9th play of this song today." Design in
  `.claude/skills/add-tracked-app/SKILL.md`. Needs notification-access disclosure.
- **Podcasts** fall out of the same media-session work (filter by app or duration).
- **More short-video apps**: Facebook Reels, Reddit video, TikTok (where available), Pinterest idea pins, LinkedIn
  video. Each needs captured screens first.
- **Regular YouTube** deeper: watch-time per channel, "videos started vs finished".
- **Screen-time context**: total minutes in each app from `UsageStatsManager` (needs usage access), to show "Shorts
  were 70% of your YouTube time".
- At that point the name should become broader than "Shorts" (see play-store.md #6).

## Social

- **Friends leaderboard**: Supabase table of daily totals per person, join by invite code, no creators/captions/
  images ever. Needs accounts, account deletion, data-safety update. Optional "lowest wins" mode so it rewards
  watching less.
- **Weekly recap card** to share as an image (like Spotify Wrapped, but for doomscrolling).

## Fun / supporter

- **Premium counter designs** (owner idea): animated bars, equaliser, odometer themes, seasonal skins, unlocked by a
  $1–2 supporter purchase (Play Billing on Play, pay-what-you-want on itch). Free version stays complete.
- More animation styles and colours; always previewable with "Try it".

## Integrations

- **Lifeboard** (`~/dev/lifeboard`, FastAPI + SQLite): push the daily summary over Tailscale. Lifeboard itself needs a
  visual overhaul first; reuse this app's design system (`docs/promo.html`).
- **Home-screen widget** with today's count (Glance).
- **iOS**: only Screen Time minutes per app are possible (no per-video counts); a companion app could join the
  leaderboard with minutes only.

## Principles to keep while growing

- Detection is evidence-based: real captured screens + tests before parser changes.
- One subsystem per signal (accessibility for video feeds, media sessions for audio, usage stats for minutes).
- Every new data type gets a disclosure, a privacy-policy line and a setting to turn it off.
- Battery: sleep outside watched apps; no polling, no wake locks.
