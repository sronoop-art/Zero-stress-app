# Building in AndroidIDE (phone only)

You do **not** need the whole GitHub repository. This ZIP contains only the
files Gradle needs:

```
ZeroStress-AndroidIDE/
├── app/                    <- ALL the app code, icons, fonts, sounds
├── gradle/wrapper/          <- Gradle version the build expects
├── gradlew / gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── ANDROIDIDE-README.md     <- this file
```

Not in the ZIP on purpose: `.github/`, `functions/`, `scripts/`, `server/`,
`firestore.rules`, `firestore.indexes.json`, `firebase.json` and the READMEs.
Those are for the **backend** (Firestore rules + the push relay) and are
deployed from GitHub, not from AndroidIDE.

## Steps

1. Unzip `ZS3.1.zip` in AndroidIDE (or open the folder).
2. **Add your Firebase file:** copy `google-services.json` to
   `app/google-services.json`.
   The build fails without it - the Google Services plugin reads the project
   id and API keys from that file, and it is deliberately never uploaded to
   GitHub. Use the same file you used for your earlier builds.
3. Build the debug APK: **Build > Build Project(s)** / the hammer icon.
4. Install on the phone and open the app.

## Release APK (optional)

`app/zerostress.jks` is not in the repo either. Drop your keystore there and
run a release build; without it the build still succeeds and signs with the
debug key, which is fine for testing but Play Store will not accept it.

## After installing

- Allow **notifications** when asked (the app asks once, on the dashboard).
  Without it Android 13+ drops every push silently.
- Open the dashboard once so the app stores its FCM token - that token is what
  the push relay uses to reach this device.
- Your display name in **Profile** is what teammates type to `@mention` you.
