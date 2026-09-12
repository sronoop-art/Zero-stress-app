# Audio Files — required files

The app plays short sounds on three events. The code is already wired —
you only need to drop the audio files into:

```
app/src/main/res/raw/
```

(Create the `raw` folder if it does not exist yet.)

## Required file names (exact, lowercase, no spaces)

| File name | Played when |
|---|---|
| `app_start.mp3` | The splash / loading screen appears (app start) |
| `login_success.mp3` | Player signs in successfully |
| `register_success.mp3` | Player creates an account successfully |

Supported formats: **.mp3**, **.ogg**, or **.wav** — if you use a different
extension, rename the entry in the table above accordingly (the code resolves
the resource by name `app_start`, `login_success`, `register_success`).

## Tips

- Keep each clip **2–4 seconds** — long intros feel slow on every app launch.
- Use **96–128 kbps MP3** or **OGG** to keep the APK small (a 3 s mono clip ≈ 30–60 KB).
- Audio plays on the **media volume**; the user's silent/vibrate mode is respected
  by the system only for ringtones, so keep clips short and pleasant.
- Missing files are **skipped silently** — the app builds and runs fine without
  them; the sound simply does not play until you add the file.

## How the code plays them

`app/src/main/java/com/zerostress/manager/audio/ZsSoundManager.kt`

- `ZsSoundManager.playAppStart(context)` — called in `SplashScreenActivity.onCreate`
- `ZsSoundManager.playLoginSuccess(context)` — called in `LoginActivity` on sign-in success
- `ZsSoundManager.playRegisterSuccess(context)` — called in `RegisterActivity` on account creation
- Only one clip plays at a time; a new sound stops the previous one.
