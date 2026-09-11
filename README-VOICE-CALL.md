# Voice Call / WebRTC Notes

## What is in this version

- Discord-style voice call screen: channels, participants, join/leave, mute/deafen/talking/hand, call chat
- Admin management for voice channels: create, rename, enable/disable, delete, manage who can access voice
- WebRTC signaling + peer layer:
  - `VoiceCallSignaling` writes offer / answer / ICE to Firestore under `voice_channels/{channelId}/calls/{callId}`
  - `VoiceCallPeer` creates local audio, builds the WebRTC `PeerConnection`, creates the local offer, sets the remote description, and adds remote ICE candidates
- Voice call start now kicks off when a participant joins a channel with at least one other participant already there

## What still needs a real audio connect test

- Local mic is started and the local audio track is added to the `PeerConnection`.
- Remote audio still depends on receiving a remote `MediaStream`/`Track` from the other side and routing it to device playback.
- If two devices cannot directly connect, you may need STUN/TURN services added to the `PeerConnection` config.

## Firestore paths used by voice calls

- `voice_channels/{channelId}/participants/{userId}`
- `voice_channels/{channelId}/call_chat/{msg}`
- `voice_channels/{channelId}/calls/{callId}`

Make sure your Firestore rules allow authenticated reads/writes for those paths.

## Next verification step

1. Build the APK
2. Open the voice call screen on two devices in the same active voice channel
3. Watch `VoiceCallPeer` and `VoiceCallSignaling` logs
4. Confirm call state changes from `Starting call…` to `In call`
