# ZERO STRESS — Performance & Leaderboard Manager

A gaming-community manager app: match tracking, live leaderboards, team chat, voice
channels, friends, achievements, titles, battle pass, daily challenges/rewards,
match scheduling, announcements, seasons, admin tools and push notifications —
all backed by **Firebase** (Auth + Firestore + Messaging + App Check).

> This repository is the **Kotlin + Jetpack Compose** rewrite. The previous Java +
> XML layout version has been fully migrated.

## What's inside

| Area | Details |
|---|---|
| Language | 100% Kotlin (was Java) |
| UI | 100% Jetpack Compose (was XML layouts + RecyclerViews) |
| Build files | Kotlin DSL — `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` |
| Gradle | Wrapper pinned to **9.6.1** |
| AGP / Kotlin | **AGP 9.3.1 with built-in Kotlin 2.3.21** (Code On The Go build stack; no `kotlin("android")` plugin line — Kotlin is compiled by AGP itself) |
| Backend | Firebase Auth, Firestore, Messaging (FCM), App Check (debug provider) |
| Cloud | `functions/` — Firebase Cloud Functions that push FCM notifications |

### App screens (all migrated to Compose)

- **Auth:** Splash (animated), Login, Register
- **Dashboards:** Player dashboard (stats + menu grid), Admin dashboard (stats, player
  management: approve/reject/ban, edit name, roles, game roles, delete)
- **Community:** Leaderboard (daily/weekly/monthly + admin reset & rewards), Team Chat
  (@mentions, typing indicator, admin clear), Voice Chat (Firestore presence channels),
  Friends (requests, online status), Profile (stats, edit, avatar picker)
- **Progression:** Achievements, Player Titles, Battle Pass, Daily Challenges,
  Daily Login Rewards, Performance (match logs + summary graphs)
- **Admin/content:** Daily Input (score entry), Submit Match, Match Schedule,
  Announcements, Seasons (view + manage), Voice Channels (manage), Send Notification,
  View All Players Stats, Notifications, Settings (delete account, notification prefs)

## Building with AndroidIDE

AndroidIDE builds this project directly on your Android phone.

1. **Open the project**: AndroidIDE → Open Project → select the repository root
   (the folder containing `settings.gradle.kts`).
2. **Wait for sync** — the Gradle wrapper (**9.6.1**) will be used automatically.
   If AndroidIDE asks for a Gradle version, pick **9.6.1** or "from wrapper".
3. **Add your Firebase config**: copy your `google-services.json` from the Firebase
   console (project `zerostress-manager`) into **`app/`**.
   > The build **fails without this file** — it is gitignored on purpose.
4. **Build**: menu → **Build APK**. AndroidIDE compiles, signs with the debug key and
   gives you an installable APK.

Requirements:

- AndroidIDE / Code On The Go with the **Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.3.21**
  build stack (see the
  [CoGo upgrade wiki](https://github.com/appdevforall/CodeOnTheGo/wiki/IMPORTANT:-Fix-project-breaking-changes-after-the-Gradle-AGP-Kotlin-toolchain-upgrade)).
- ~1–2 GB of free RAM on the device for the Gradle daemon
  (`org.gradle.jvmargs=-Xmx1536m` is already tuned in `gradle.properties`).
- Internet on the device (first build downloads dependencies).

#### Upgrading from the old Gradle 8.4 / AGP 8.x project version?

The build files in this repo are already migrated (`buildscript` with
`kotlin-gradle-plugin:2.3.21`, AGP `9.3.1`, no Kotlin plugin line, no
`kotlinOptions` block). If a build fails with:

```
Could not find customview-1.0.0.aar (androidx.customview:customview:1.0.0)
Searched in: .../maven/localMvnRepository/androidx/customview/...
```

that is a stale artifact in AndroidIDE's on-device Maven cache from the old
dependency graph — the current dependencies don't use `customview` at all.
Clear it and let Gradle re-download what it actually needs:

```bash
rm -rf ~/maven/localMvnRepository/androidx/customview
gradlew :app:assembleDebug --refresh-dependencies
```

### Release builds (optional)

- Place `app/zerostress.jks` (keystore) in `app/` — the release signing config in
  `app/build.gradle.kts` expects it (`storePassword`/`keyPassword` = `zerostress123`
  as in the original project). Change these if you use your own keystore.
- If you build release without the keystore, comment out the
  `signingConfig = signingConfigs.getByName("release")` line.

## Firebase setup notes

- **Auth:** the app signs in with `phone@zerostress.local` + password (phone number
  is used as the email). Enable Email/Password in Firebase Auth.
- **Firestore collections used:** `players`, `match_logs`, `daily_logs`,
  `chat_messages`, `chat_typing`, `announcements`, `match_schedules`, `friendships`,
  `friend_requests`, `player_achievements`, `player_titles`, `seasons`,
  `voice_channels` (+ `participants` / `chat` subcollections), `notifications`.
- **App Check:** debug builds use the **Debug** provider (the token is logged to
  Logcat on startup), release builds use **Play Integrity**. Until the app is
  registered in Firebase Console → App Check, `hasAppCheck()` in `firestore.rules`
  evaluates to false and **every client write fails** with `PERMISSION_DENIED:
  Missing or insufficient permissions.` while reads keep working — sending a chat
  message, typing indicators, mentions and reports are all rejected. The rules
  therefore ship with `appcheckEnabled()` returning `false` (the documented kill
  switch) so the app stays usable; flip it back to `true` only once the installed
  build actually attests. Pushing `firestore.rules` to `ZS3.1` deploys the change
  through `.github/workflows/firebase-deploy.yml` — free on the Spark plan.
- **FCM:** `functions/` contains the Cloud Functions that forward Firestore
  notifications to devices (`fcmToken` is saved to each player doc).

## Recovering a deleted Firestore collection

Deleting a collection in the Firebase console is permanent — there is no undo,
and the Spark plan has neither managed backups nor point-in-time restore. What
survives a Firestore delete is **Firebase Authentication**: it is a separate
product with its own database, so every account (uid, email, phone) is still
listed after `players/` is gone.

Rebuild the roster from that list. A phone is enough — Cloud Shell is a browser
terminal with `gcloud` and Node preinstalled:

```bash
# https://shell.cloud.google.com
git clone -b ZS3.1 https://github.com/sronoop-art/Zero-stress-app.git
cd Zero-stress-app
node scripts/rebuild-players.mjs                            # dry run, prints a plan
node scripts/rebuild-players.mjs --admin you@example.com --apply
```

`scripts/rebuild-players.mjs` recreates one `players/{uid}` document per Auth
account with exactly the fields `RegisterActivity` writes. It is a dry run
unless `--apply` is passed, never overwrites an existing document, and gives
your own account `role: admin` (every admin screen reads that field, so without
it the app looks locked). FCM tokens return by themselves on the next app
launch, and the free cron job auto-creates the `seasons` document. Chat history,
notifications, match logs, daily stats, titles and achievements are **not**
recoverable without an export.

**Take an export before the next accident.** The managed export/import service
requires a billing account to be linked (an export costs a few cents; it does
not move the project off the Spark plan or change the app's own quotas):

```bash
gcloud storage buckets create gs://zerostress-backups --location=us-central1
gcloud firestore export gs://zerostress-backups/$(date +%F)   # restore:
gcloud firestore import gs://zerostress-backups/2026-09-25
```

## Instant push notifications (Knock bridge)

Firestore notification documents (`notifications/{id}`) are still the single
source of truth. On the free Spark plan the checked-in GitHub Actions relay can
only poll every 30 minutes — that interval **is** the push latency — so `server/`
is an optional always-on Node.js service that watches Firestore and triggers the
Knock workflow (`zs-push`) the moment a document is written, turning delivery
into a couple of seconds.

It reuses the existing `players/{uid}.fcmToken` field and passes it to Knock as
inline FCM channel data, so **the Android app needs no changes**, no Knock SDK is
added, and the Knock API key never leaves the server environment. If Knock ever
fails the service falls back to the same direct FCM send the GitHub relay uses;
the relay itself keeps running as the 30-minute safety net (both senders share
the `pushSent` flag, so nothing is delivered twice).

Deployment, verification and troubleshooting: **[`server/README.md`](server/README.md)**.
The short version is `docker build -t zs-knock-bridge ./server` and run that
container on any host that keeps a process alive 24/7 (a Google Cloud always-free
`e2-micro` VM is the cheapest fit; sleep-after-inactivity free tiers will not work).

### Server environment variables

| Variable | Required | Value |
| --- | --- | --- |
| `KNOCK_API_KEY` | yes | Knock secret API key for the environment that owns `zs-push` |
| `FIREBASE_SERVICE_ACCOUNT` | yes | Firebase service-account JSON, base64 of it, or a path to the file (same credentials the GitHub relay uses) |
| `KNOCK_WORKFLOW_KEY` | no | Defaults to `zs-push` |
| `KNOCK_FCM_CHANNEL_ID` | no | Defaults to the FCM channel UUID created in Knock |
| `DIRECT_FCM_FALLBACK` | no | `false` disables the direct-FCM safety net |
| `PORT` | no | Health-check port, defaults to `8080` |

```bash
cd server && npm install && npm test && npm start   # GET /health on :8080
```

`server/lib.test.js` covers the payload/recipient helpers offline (no Firebase or
Knock credentials needed), which is how the FCM data contract below stays
honest.

### Knock dashboard requirements

Both the channel and the workflow must live in the **same environment** as the
`KNOCK_API_KEY` you deploy.

1. The FCM channel is configured with Firebase project
   `zerostress-3a536` and its complete service-account JSON.
2. The Push step in the `zs-push` workflow renders the notification data, e.g.
   title `{{ data.title }}`, body `{{ data.message }}`, and forwards `type`,
   `uid`, `channelId` and `notificationId` in the FCM data payload —
   `ZSFCMService.onMessageReceived` reads the keys `title`, `body`, `type` and
   `uid`, and drops any push whose `uid` differs from the signed-in user.
   Broadcasts therefore carry **no** `uid` key (never the literal `"all"`).
3. To keep Android notification groups correct, set the channel-level payload
   override to include the channel ID passed by the bridge:

```json
{
  "android": {
    "priority": "high",
    "notification": {
      "channel_id": "{{ data.channelId }}",
      "notification_priority": "PRIORITY_HIGH"
    }
  }
}
```

If the override is not set, background notifications fall back to the default
`zs_notifications` channel. Tap routing uses the `type` value already present in
the notification document.

## Local development

```bash
# Android SDK + JDK 17 required on the machine, then:
./gradlew :app:assembleDebug
```

The Knock bridge can be checked without any credentials (syntax + unit tests
for the Knock/FCM payload contract):

```bash
cd server && npm install && npm test
```

The Gradle wrapper downloads Gradle 9.6.1 on first run.

---

Zero Stress Manager © 2026 — Built with Kotlin + Jetpack Compose + Firebase.