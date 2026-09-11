# Voice Call — Agora WebRTC (Discord-style)

The old experimental raw-WebRTC peer layer (`VoiceCallPeer.kt` / `VoiceCallSignaling.kt`)
has been **removed** and replaced with the **Agora RTC SDK**, which gives real
live audio: every player/admin in a channel can talk and hear each other, with
Agora's servers handling NAT traversal — no STUN/TURN config needed.

## What is in this version

- Discord-style channel list: tap a channel to join, live participant list,
  speaking rings (green ring = talking), mute / deafen / hand-raise / call chat
- Real group voice via Agora (`io.agora.rtc:agora-rtc-sdk:4.1.0`):
  - `voice/AgoraVoiceManager.kt` owns the `RtcEngine`, joins the channel, and
    publishes/subscribes audio for everyone in the room
  - Speaking detection uses Agora's `onAudioVolumeIndication` (no mute-flag hacks)
  - Stable numeric UIDs derived from each Firebase UID
- Admin tools (visible only to `role == "admin"`):
  - ➕ create channels · ⚙️ enable/disable/delete channels
  - 👥 manage who can access voice (`players/{uid}.voiceAllowed`)
  - Tap a participant → mute · kick from call · ban from voice
- Firestore paths unchanged: `voice_channels/{id}/participants/{uid}` and
  `.../call_chat/{msg}` — the old `calls/{callId}` signaling docs are no longer used

## Agora credentials

The Agora **App ID** is already committed in the repo's `gradle.properties`, so a
fresh clone builds voice-ready with zero setup. To override it, set this in
`gradle.properties` (project root, or `~/.gradle/gradle.properties`):

```properties
AGORA_APP_ID=f8159b2c6adc4a468f269897bec01748
# AGORA_APP_CERTIFICATE=<your-certificate>  ← add locally ONLY if you enable token mode
```

- `AGORA_APP_ID` is required — without it the join fails with
  *"Could not reach voice servers"*.
- `AGORA_APP_CERTIFICATE` is kept out of code by design. While your Agora
  project still has the **App Certificate disabled** (test mode), the app joins
  without tokens and the certificate value is unused.
- If you later **enable** the certificate in the Agora Console, you must fetch
  tokens from a server (`agora-token-service`) and pass them to
  `AgoraVoiceManager.join(..., token)` — client-side token generation with a
  certificate is not possible by design.

> ⚠️ The App ID + token you pasted in chat are already in your Agora console.
> Treat the certificate as a secret: don't commit it anywhere public.

## How the call actually connects (2 devices test)

1. Both phones: open **Voice Channels**, tap the same channel.
2. Watch Logcat for `AgoraVoiceManager` — you want:
   `join success channel=zs_<name> uid=<n>`
3. When the second device joins, the first gets `user joined uid=...` and the
   participant list updates. Speak — the green ring appears around whoever is
   talking, and audio plays through the speakerphone.
4. If a device is on mobile data and the other on Wi-Fi, Agora's servers
   relay the media automatically — that's the point of using Agora instead of
   the old P2P WebRTC layer.

## Capacity

Agora communication channels support **up to 128 simultaneous audio publishers**
per channel (16 by default for interactive streams; raise it in the Agora
Console → Usage → "16-bit UID / large channel" if you ever need more).
For a squad-comms app the practical sweet spot is 4–16 talkers.

## Firestore rules

Allow authenticated users to read/write the voice subcollections:

```
match /voice_channels/{channelId} {
  allow read, write: if request.auth != null;
  match /participants/{uid}   { allow read, write: if request.auth != null; }
  match /call_chat/{msg}      { allow read, write: if request.auth != null; }
}
```

## Build & verify (AndroidIDE)

1. No configuration needed — the App ID ships in `gradle.properties`.
2. Build → **Build APK** in AndroidIDE.
3. Install on two devices, sign in with two different players (one can be the
   admin), and join the same channel.
4. Confirm both sides hear each other and the speaking ring tracks the talker.
