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
| Gradle | Wrapper pinned to **8.4** (works with AndroidIDE's bundled Gradle) |
| AGP / Kotlin | AGP 8.2.2, Kotlin 2.0.20 + Compose compiler plugin |
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
2. **Wait for sync** — the Gradle wrapper (8.4) will be used automatically.
   If AndroidIDE asks for a Gradle version, pick **8.4** or "from wrapper".
3. **Add your Firebase config**: copy your `google-services.json` from the Firebase
   console (project `zerostress-manager`) into **`app/`**.
   > The build **fails without this file** — it is gitignored on purpose.
4. **Build**: menu → **Build APK**. AndroidIDE compiles, signs with the debug key and
   gives you an installable APK.

Requirements:

- AndroidIDE **2.6+** (bundles Gradle 8.4).
- ~1–2 GB of free RAM on the device for the Gradle daemon
  (`org.gradle.jvmargs=-Xmx1536m` is already tuned in `gradle.properties`).
- Internet on the device (first build downloads dependencies).

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
- **App Check:** the app currently uses the **Debug** provider (it logs the debug
  token to Logcat on startup). Add that token in Firebase Console → App Check, and
  switch to Play Integrity / DeviceCheck before releasing.
- **FCM:** `functions/` contains the Cloud Functions that forward Firestore
  notifications to devices (`fcmToken` is saved to each player doc).

## Local development

```bash
# Android SDK + JDK 17 required on the machine, then:
./gradlew :app:assembleDebug
```

The Gradle wrapper downloads Gradle 8.4 on first run.

---

Zero Stress Manager © 2026 — Built with Kotlin + Jetpack Compose + Firebase.