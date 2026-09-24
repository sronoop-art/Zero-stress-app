# Production Release Checklist — Zero Stress (ZS3.1)

This app is close to publishable. The code path is solid: signed-release APK path, R8 on, Crashlytics wired, Play Integrity installed in release, FCM v1, HTTPS enforced, Firestore rules hardened, player match submission removed. What remains is a handful of **configuration / store / Firebase-console** steps — none of them are code bugs, but a few will break a real release if skipped.

---

## 1. Local build prerequisites (on your machine, not this sandbox)

This workspace has no Android SDK / Java toolchain — the repo is designed to build on-device or on your PC. Run from the repo root:

```bash
# 1. Unit tests (logic, no device)
gradlew :app:testDebugUnitTest

# 2. Debug APK for on-device smoke testing
gradlew :app:assembleDebug
#    → app/build/outputs/apk/debug/app-debug.apk

# 3. Signed release APK — requires app/zerostress.jks present next to app/build.gradle.kts
gradlew :app:assembleRelease
#    → app/build/outputs/apk/release/app-release.apk
```

If step 3 prints `app/zerostress.jks NOT FOUND`, the APK will be **unsigned** and cannot be updated as the same app on user devices. See section 2.

---

## 2. Signing keystore — required for a real release

The release build is configured to sign with `app/zerostress.jks` (alias `zerostress`, store/key password `zerostress123`). That file is **gitignored by design** and lives on your machine only.

To create it (run from the repo root, requires `keytool` from a Java JDK):

```bash
keytool -genkeypair -v \
  -keystore app/zerostress.jks \
  -alias zerostress \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass zerostress123 \
  -keypass zerostress123 \
  -dname "CN=Zero Stress, OU=Games, O=ZeroStress, C=IN"
```

Then verify:

```bash
keytool -list -v -keystore app/zerostress.jks -alias zerostress
```

**Back it up now.** If you lose it, every future update forces users to uninstall and reinstall. If you later publish to Google Play, enroll in Play App Signing — the `.jks` becomes your upload key, but the backup advice stays the same.

---

## 3. Play Integrity / Firebase App Check — required for release

The app installs `PlayIntegrityAppCheckProviderFactory` in release builds (`ZeroStressApp.setupAppCheck()`). That means Firebase will mint App Check tokens via Play Integrity **only after you complete this console step**:

1. Firebase Console → **App Check** → your Android app (`com.zerostress.manager`)
2. Choose **Play Integrity** as the provider
3. Follow the enrollment steps (Google Play account linked, app signed with the same keystore you intend to ship)
4. After enrollment, **enable App Check enforcement** on Firestore / Storage when you're ready (start in test mode / grace period if you have existing players)

Until this is done, release builds will still run, but App Check tokens won't be minted and any App Check–guarded backend access will fail. In debug builds the app uses the debug provider, so local testing is unaffected.

---

## 4. Push notifications — already on FCM HTTP v1

The app sends pushes through the modern FCM HTTP v1 API. No legacy server key is involved. For production:

- Make sure the Firebase project's **FCM** is enabled for the Android app
- Make sure the service account used by `functions/` / the cron job has `Firebase Authentication` + `Cloud Messaging` scope
- Confirm the Android app requests `POST_NOTIFICATIONS` at runtime (Android 13+). The app prompts from Settings; the manifest declares the permission

### 4a. How fast a push actually arrives (read this if notifications feel "late")

There are two delivery paths, and the one you are on decides the latency:

| Path | Who runs it | Latency |
|------|-------------|---------|
| Cloud Functions trigger `sendPushNotification` (Firestore `onDocumentCreated`) | Firebase, once the **Blaze** plan is active and the function is deployed | seconds |
| `.github/workflows/free-cron.yml` relay (`node functions/cron.js`) | GitHub Actions on the **Spark (free)** plan | up to the cron interval — currently **30 minutes** |

This project is on Spark, so the Cloud Functions deploy step in `firebase-deploy.yml` logs a warning and is skipped, and **30 minutes is the floor for push latency** until you either upgrade to Blaze (then pushes are near-instant) or shorten the cron schedule.

If you want it faster without Blaze, change the `cron:` schedule in `.github/workflows/free-cron.yml` (GitHub's minimum is `*/5 * * * *`). Note that scheduled-workflow minutes are metered on private repos, and GitHub may delay scheduled runs under load; the relay is also the only sender for admin broadcasts, so a shorter interval shortens that wait too.

### 4b. Background presentation (system-drawn notifications)

When the app is backgrounded or killed, Android (not the app) draws the push, and it only lands on a high-importance channel — status bar + heads-up — if the message names one. That is now wired end to end:

- `AndroidManifest.xml` declares `com.google.firebase.messaging.default_notification_channel_id` = `zs_notifications` and `default_notification_icon` = `@drawable/ic_notification`, so a payload without a channel still uses a high-importance channel instead of FCM's silent `Miscellaneous` fallback
- `functions/index.js` (`buildMessage`) and `functions/cron.js` (`channelFor`) both set `android.notification.channelId`, so chat / schedule / general pushes reach `zs_chat` / `zs_schedule` / `zs_notifications`
- Admin **broadcasts** (`uid: null` in the `notifications` doc) are pushed by the cron relay with `sendEachForMulticast` over every registered `fcmToken`, instead of being marked `pushSent` and dropped

Tokens are (re)saved with a 4-attempt retry on sign-in, splash, dashboard open, chat send, and admin send, and `onNewToken` refreshes them — a device with no stored `fcmToken` receives nothing, so these paths are what keep older installs deliverable.

---

## 5. Cloudinary avatar uploads — feature flag, not required

Avatar uploads go through an **unsigned** Cloudinary preset. The feature is silent when `cloudinary_cloud_name` and `cloudinary_upload_preset` are blank in Remote Config. If you want profile pictures in production:

1. Cloudinary account → Settings → Upload → Upload Preset → create an **unsigned** preset (folder `zs_avatars`, reasonable max file size)
2. Firebase Console → Remote Config → set:
   - `cloudinary_cloud_name`
   - `cloudinary_upload_preset`

If you leave both blank, the app keeps local avatars and nothing breaks.

---

## 6. Remote Config defaults that matter at launch

Before shipping, set at least these in Firebase Console → Remote Config:

| Key | Recommended | Why |
|-----|-------------|-----|
| `min_version_code` | current `versionCode` (currently 3) | Blocks old installs from continuing — set this **after** you confirm the new APK works |
| `update_url` | your APK download / Play store link | Shown in the blocking update dialog |
| `update_message` | short human text | Text in that dialog |
| `content_pack_url` / `content_pack_version` | only if you ship OTA frames/badges | Otherwise leave blank |
| `intro_video_url` / `intro_video_version` | only if you use the intro video | Otherwise leave blank |
| `audio_app_start_url` / `audio_login_success_url` / `audio_register_success_url` | only if you want remote sounds | Otherwise bundled `res/raw` sounds are used |

Rank-title unlock scores (`title_bronze_score` … `title_grandmaster_score`) already have sensible defaults in code. Override them only if you want to tune the ladder without another APK.

---

## 7. Firestore rules — deploy them

The latest rules change (`match_logs` create is admin-only; player-doc writes are locked to the owning uid; chat/notification sender forgery is closed) live in `firestore.rules`. They take effect only after deploy. Either:

- push to `ZS3.1` (your GitHub Actions workflow deployes rules automatically), or
- run `firebase deploy --only firestore:rules` from a machine with the Firebase CLI authenticated

Also deploy the indexes if you changed `firestore.indexes.json`:

```bash
firebase deploy --only firestore:indexes
```

The dashboard's upcoming-match countdown query (`match_schedules` where status=Upcoming orderBy matchTime) needs the `status + matchTime` index — it is already declared in `firestore.indexes.json`.

---

## 8. Cloud Functions / cron — deploy them

If you changed `functions/`, deploy:

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

The scheduled jobs (push relay, match reminders, season auto-reset, leaderboard resets) run as a GitHub Actions cron in this repo. If you rely on that instead of a Firebase scheduled function, confirm the workflow secret `FIREBASE_SERVICE_ACCOUNT` is still set and valid.

---

## 9. Store / Play Console readiness (if publishing to Play)

- **Target SDK**: the app targets API 36 — make sure it passes Play's current target SDK and policy requirements at the time you upload
- **Permissions**: the manifest requests `POST_NOTIFICATIONS`, `RECORD_AUDIO`, foreground service microphone, Bluetooth, and internet. Make sure the Play Console permission declarations match what the app actually does (microphone / audio recording is the one reviewers look at closely)
- **Icons**: launcher icons are adaptive vector icons (`mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`). Confirm you also have fallbacks for older devices if you support them — the repo currently only ships the anydpi vector pair, so verify your `minSdk` / device mix
- **Notification icon**: status-bar icon is `R.drawable.ic_notification` (vector). It is usable, but if you see a solid blue square on a specific device/OEM, replace it with a proper alpha-only silhouette PNG in `mipmap-`

---

## 10. Before you bump `min_version_code`

Do not set `min_version_code` to the new version until you have:

1. Built the signed release APK
2. Installed it on a clean device or after uninstalling the old one
3. Confirmed login / register / dashboard / leaderboard / chat / voice / notifications all work
4. Confirmed the update dialog only appears when you intend it to

Once `min_version_code` is set above the old version, older installs get blocked at splash.

---

## 11. Crash reporting

Crashlytics is wired in release (`firebase-crashlytics` dependency + plugin). After your first release build:

- upload `app/build/outputs/mapping/release/mapping.txt` to Firebase Console (or keep it safe) so stack traces stay readable
- test a forced crash in a release build to confirm the pipeline works before you rely on it in production

---

## 12. Quick code-level sanity notes (already addressed in this branch)

- Player match submission removed; `match_logs` create is admin-only
- HTTPS enforced app-wide (`network_security_config.xml`, manifest wired)
- Coin / daily-claim / leaderboard reward writes use server increments and first-claim idempotency
- Scoring delegates to the shared `ZsScore` rules (no more `wins*50` drift)
- Stale `submit_match` string resource removed from `strings.xml`
- No hardcoded secrets in source; Agora keys and the keystore stay in local properties / gitignore
- No `http://` URLs anywhere in app code — all remote endpoints are HTTPS or Remote-Config-driven
- FCM token is saved with retry (sign-in, splash, dashboard, chat send, admin send, token refresh) and topics are (re)subscribed on every save
- Background pushes land on a high-importance channel via the manifest FCM meta-data + `channelId` in both senders
- Admin broadcasts reach every device through the cron relay (previously in-app only on Spark)

---

## 13. What is still on you (not code)

- Create / back up `app/zerostress.jks` if you want signed releases
- Enroll Play Integrity in Firebase App Check and optionally enforce it
- Configure Remote Config keys before launch (`min_version_code`, `update_url`, etc.)
- Deploy Firestore rules + indexes
- Deploy Cloud Functions / confirm the cron secret
- Optionally set up Cloudinary for avatars
- Provide launcher icon fallbacks if you support pre-v26 devices or want a heavier icon set
- If publishing to Play, complete the store listing / permission declarations / target SDK review

---

That's the whole list. The app itself is in good shape for a production release — the remaining items are the usual launch-day wiring, not bugs.
