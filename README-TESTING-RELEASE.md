# Testing & Releasing

## 1. Unit tests (logic, no device needed)

Pure JVM tests live in `app/src/test/java/com/zerostress/manager/`:

- `RankTitlesTest` — the 8 rank titles: order, thresholds, boundary scores, name lookup
- `ScoreLogicTest` — score formula (`kills*10 + damage/100 + wins*50`) and rank tiers,
  including the alignment between ranks and title unlocks

Run them in AndroidIDE terminal:

```bash
gradlew :app:testDebugUnitTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`

## 2. Release build (what you distribute)

```bash
gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

- Signed with `app/zerostress.jks` (config in `app/build.gradle.kts`).
  **Keep the keystore + passwords safe** — you need the same keystore for every
  future update, otherwise players must uninstall/reinstall.
- R8 minify + resource shrink are ON. Upload/keep
  `app/build/outputs/mapping/release/mapping.txt` so crash logs stay readable.
- The release build strips x86/x86_64 Agora libs — only ARM phones supported (fine for players).

## 3. Distribute & force updates

1. Host `app-release.apk` anywhere with a direct link (GitHub release, Drive, site).
2. In Firebase Console → Remote Config:
   - `update_url` = your APK link
   - `min_version_code` = `2` (matches this release) — older installs get a
     blocking "Update Required" dialog at next launch
3. Version is now `versionCode 2` / `versionName "3.1"`.

## 4. Deploy the new Cloud Functions

The push pipeline changed (targeted per-user pushes). From the repo root:

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

Also publish the updated `firestore.rules` (targeted notification reads,
admin daily-stat writes):

```bash
firebase deploy --only firestore:rules
```

## 5. Test the notification system (2 devices)

1. Android 13+ device: open **Settings screen in-app** once → permission prompt appears → Allow.
   (The manifest permission alone is not enough on Android 13+.)
2. Device A sends a chat message mentioning Device B's name → B gets a status-bar
   notification; other players do not.
3. Admin sends a notification via the Notify tile → all players get it.
4. Turn off "Chat Notifications" in Settings on B → mentions no longer arrive as pushes.
