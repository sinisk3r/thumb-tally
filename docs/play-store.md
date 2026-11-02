# Getting onto the Play Store

Status (2026-10-22): sideloaded only, release builds signed with the debug key, no privacy policy, no disclosure
screen. Policy details below were checked on that date but change often — re-read the linked Google pages before
submitting.

## Blockers (fix before the first submission)

| # | Gap | Why it matters | Fix |
|---|---|---|---|
| 1 | **No accessibility disclosure screen** — *done 2026-11-03: `ui/Disclosure.kt`, shown before every "turn on"; not yet seen on a phone* | Play allows non-accessibility apps to use the Accessibility API only with an in-app *prominent disclosure* and explicit consent shown before the user is sent to settings ([policy](https://support.google.com/googleplay/android-developer/answer/10964491)). Today the app jumps straight to Accessibility settings (`ui/MainActivity.kt`). | A full-screen step: what is read (titles, creators of videos in YouTube/Instagram/Snapchat), that it stays on the phone, screenshots only if turned on, then "Agree" / "No thanks". Do not set `isAccessibilityTool`; this app isn't one. |
| 2 | **Accessibility declaration + review** | Play Console asks for an Accessibility API declaration, often with a short video of the feature and the disclosure. Reviews of accessibility apps are strict and can take several rounds. | Record a screen video showing disclosure → enabling → counter. Keep `service_description` matching it. |
| 3 | **Release signed with the debug key** | Play rejects debug-signed uploads. | Owner creates an upload keystore (kept outside the repo, backed up), enrol in Play App Signing, add a release `signingConfig` read from `local.properties`/env. Ship `bundleRelease` (AAB), not APK. |
| 4 | **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission** | Play restricts this permission to a few use cases (messaging, device-companion, etc.); a tracker likely doesn't qualify and it's a common rejection reason. | Remove it from the Play build and open `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` instead (no permission needed); keep the one-tap dialog only in the sideload build. |
| 5 | **No privacy policy** | Required for any app touching personal or sensitive data, and for the accessibility declaration. | A short page (GitHub Pages works) stating: data stays on device, what's read, screenshots opt-in, no ads/analytics, how to delete (uninstall / Settings → clear). |
| 6 | **Name uses another company's product name** — *decided 2026-11-03: **Thumb Tally**; `applicationId` for Play still to set when flavors land* | "Shorts" is YouTube's product name; Play's impersonation/metadata rules and Google's trademark rules make a store title like "Shorts Tracker" with YouTube/Instagram imagery risky. | Pick an own brand (e.g. "Scrollcount", "Thumb Tally"); mention apps only descriptively ("works with YouTube, Instagram and Snapchat"), no third-party logos in icon or screenshots. Decide the `applicationId` too: it can never change after the first upload (`com.srik.shortstracker` works but is narrow if music is added). |
| 7 | **New personal developer account testing rule** | Personal accounts created after Nov 2023 must run a closed test with at least 12 testers opted in for 14 continuous days before applying for production. | Recruit the friends who already sideload it; they install from the Play test link (needs uninstalling the sideloaded build because the key differs). |
| 8 | **Payment apps refuse to work while the tracker is on** | Paytm and a growing number of UPI/bank apps refuse to pay while *any* third-party accessibility service is on, Play-installed or not. Breaking someone's payments is the fastest way to an uninstall and a 1-star review. | Done (2026-11-03; verified on the owner's OnePlus 13: Google Pay, PhonePe and one more app worked with the pause; "switched off" alert and the off-reminder fired): `tracker/PaymentApps.kt` switches the service off (`disableSelf`) when a listed app opens and posts a notification; `tracker/TrackingGuard.kt` reminds while it's off (only when YouTube/Instagram/Snapchat is opened, if Usage access is granted). Still to do: test against real bank apps, mention it in the disclosure screen and the Play accessibility declaration, and declare Usage access in the store listing. Some apps check at launch, before the pause lands, and may warn once. |

## Money ($1–2 supporter unlock)

- Anything that unlocks features or content *inside* a Play-installed app must use **Google Play Billing** (one-time
  in-app product, e.g. "Supporter – fancy counters"). Google takes 15% on the first $1M/year.
- Linking out to Ko-fi/itch/PayPal for a tip that unlocks something is not allowed outside the regions with
  exceptions (US users since Oct 2025 while a court injunction holds; Japan's external-payments program). Plain
  external donation links are a grey area; Google only clearly permits them for registered charities. Simplest
  safe path: Play Billing in the Play build, itch.io's pay-what-you-want in the sideload build.
- Unlock check must work offline (Play Billing caches purchases); keep the free app fully useful.

## itch.io / sideloading

- Same app, separate **product flavor** (`play` vs `sideload`): sideload keeps the battery dialog, debug dump
  sharing, and itch payment; play uses Play Billing and the stricter permission set. Different signing keys means a
  user can't switch between them without reinstalling.
- **Android developer verification:** Google is requiring registered/verified developers even for sideloaded apps:
  enforcement starts 2026-09-30 in Brazil, Indonesia, Singapore and Thailand and is planned globally in 2027
  ([Android Authority timeline](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/)).
  Register the sideload package name and signing key in the Android Developer Console before then, or itch.io users
  will hit the "advanced" install flow.

## Store listing checklist

- [ ] Play Console developer account ($25 one-time, identity verification)
- [ ] App name, short description (80 chars), full description; category Health & Fitness or Lifestyle
- [ ] Icon 512×512, feature graphic 1024×500, 2–8 phone screenshots (from the app, not the promo mockups)
- [ ] Privacy policy URL
- [ ] Data safety form: no data collected or shared (true today); declare screenshots/app activity stored on device.
      Update it the day the friends leaderboard ships (that *is* collection, and needs account deletion support)
- [ ] Content rating questionnaire (IARC)
- [ ] Target audience: 13+ or 18+ (avoid the Families policy unless deliberately targeting kids)
- [ ] Accessibility API declaration + video
- [ ] Ads declaration: no ads
- [ ] Closed test: 12+ testers, 14 days
- [ ] `targetSdk` within Play's yearly requirement (36 is fine for 2026)
- [ ] R8 (`isMinifyEnabled = true`) with Room/Compose keep rules checked, to shrink the 7.7 MB APK
- [ ] Bundled fonts' OFL licences shown in an "About / licences" screen

## Every release after launch

- `versionCode` above every previous upload; release notes.
- If a new app or data type is read (e.g. music via notification access), update the disclosure screen,
  `service_description`, privacy policy, data safety form and declarations *before* the release.
