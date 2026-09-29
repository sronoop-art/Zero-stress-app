# ZERO STRESS — iPhone / Web Companion (PWA)

The ZERO STRESS Android app is Jetpack Compose, which iPhones cannot run.
This folder is the answer: a **web app** that iPhone users open in Safari and
**Add to Home Screen** — it then behaves like a real app (own icon, full
screen, no browser bars) and talks to the **exact same Firebase backend** as
the Android app.

Same accounts. Same rank ladder. Same coins, DMs, history, shop.

## What works on iPhone

| Feature | Status | Notes |
| --- | --- | --- |
| Sign in / register | ✅ | Same ID & password as the app (`phone@zerostress.local` mapping included) |
| Dashboard | ✅ | Rank hex, stats, next-rank progress, coins — NEXUS theme ported 1:1 |
| Leaderboard | ✅ | Lifetime / daily / weekly / monthly |
| Match history | ✅ | Same `match_logs` data, same score rule as `ZsScore` |
| Team chat | ✅ | Live; mentions push via the normal relay |
| Direct messages | ✅ | Same `dm_threads` documents and push notification as the app |
| Schedule | ✅ | Read-only list |
| Notifications | ✅ | In-app always; **status-bar push** once web push is enabled (below) |
| Coin shop | ✅ | Same transactional purchase; icons load from `iconUrl` |
| Profile | ✅ | Game role + region editable |
| Voice channels | ➖ Android-only for now | Agora Web SDK is a later add-on |

## Install on iPhone (no App Store, no fees)

1. Open the site URL in **Safari** (must be Safari — not Chrome).
2. Tap the **Share** button → **Add to Home Screen**.
3. Launch it from the home screen icon like any other app.

Push notifications require **iOS 16.4+** and the installed (home-screen) app.

## Deploy — free, two options

**Option A — Firebase Hosting (recommended, free Spark plan).**
Already wired: `.github/workflows/firebase-deploy.yml` now also runs
`firebase deploy --only hosting` on every push to `ZS3.1` / `main`, using the
same `FIREBASE_SERVICE_ACCOUNT` secret the rules deploy uses. Live URL:
`https://zerostress-3a536.web.app`.

**Option B — any static host** (GitHub Pages, Netlify, …): upload the contents
of `web/` as-is. No build step, no server code — it is plain static files.

## Enable status-bar push for web users (free, ~3 minutes)

1. Firebase Console → ⚙️ Project settings → **Cloud Messaging** tab →
   **Web Push certificates** → **Generate key pair**.
2. Copy the **Public key** and **Private key** (base64url strings).
3. GitHub repo → Settings → Secrets and variables → Actions → add BOTH:
   - `ZS_WEB_PUSH_PUBLIC_KEY`
   - `ZS_WEB_PUSH_PRIVATE_KEY`
4. Done. The existing free-cron relay (`functions/cron.js`) now delivers
   every notification to web subscribers through the standard Web Push
   protocol (VAPID) — same latency as Android pushes (one cron tick).

The public key is not a secret (every browser receives it); the private key
must stay in GitHub secrets only.

## Files

```
web/
  index.html          app shell: auth + 9 screens + bottom nav
  styles.css          NEXUS theme (port of ui/theme/Theme.kt palette)
  app.js              all logic: auth, live listeners, writes, push
  manifest.webmanifest  home-screen install metadata
  sw.js               service worker: push receive + offline shell
  icons/              generated NEXUS icons (node scripts/gen-icons.js)
  scripts/gen-icons.js  SVG -> PNG icon generator (dev only)
```

## Local run

```
npm --prefix web start     # serves web/ on $PORT (default 3000)
```
