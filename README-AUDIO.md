# Audio Files — required files

The app plays sounds on several events. The code is already wired —
you only need to drop the audio files into:

```
app/src/main/res/raw/
```

(Create the `raw` folder if it does not exist yet.)

## Required file names (exact, lowercase, no spaces)

| File name | Played when |
|---|---|
| `app_start.mp3` | **LOOPS** from the splash/loading screen and keeps looping on the Login and Register screens — stops only when login completes (or a dashboard opens) |
| `login_success.mp3` | Player signs in successfully (also stops the loading loop) |
| `register_success.mp3` | Account created (the loading loop keeps playing — login not complete yet) |

Supported formats: **.mp3**, **.ogg**, or **.wav** — if you use a different
extension, rename the entry in the table above accordingly (the code resolves
the resource by name `app_start`, `login_success`, `register_success`).

## How the loop works

- `SplashScreenActivity.onCreate()` → `ZsSoundManager.startLoadingLoop(this)`
  — starts `app_start` with `MediaPlayer.isLooping = true` (seamless repeat).
- Navigating splash → login → register keeps the loop alive across screens.
- **Login success** → `playLoginSuccess()` stops the loop, then plays the
  login jingle once.
- **Register success** → the jingle plays over the loop; the loop continues
  because the player still needs to sign in.
- **Dashboards** → the splash stops the loop before opening them, so a
  signed-in player who relaunches the app never hears it.
- Leaving the app (back from the login screen / app killed) stops the audio.
- If the audio file is missing, the loop is skipped silently — the app
  builds and runs fine without it.

## Tips

- The looping clip should be **seamless**: make the end flow into the
  beginning (no silence gap), 4–8 seconds is ideal.
- Use **96–128 kbps MP3** or **OGG** to keep the APK small.
- Audio plays on the **media volume**.
- The jingles (`login_success`, `register_success`) should stay short: 2–4 s.

## How the code plays them

`app/src/main/java/com/zerostress/manager/audio/ZsSoundManager.kt`

- `ZsSoundManager.startLoadingLoop(context)` — called in `SplashScreenActivity.onCreate`
  and `LoginActivity.onCreate` (keeps the loop running on the login screen)
- `ZsSoundManager.stopLoadingLoop()` — called when a dashboard opens or the
  login screen is destroyed while finishing
- `ZsSoundManager.playLoginSuccess(context)` — `LoginActivity` sign-in success (stops the loop)
- `ZsSoundManager.playRegisterSuccess(context)` — `RegisterActivity` account creation
