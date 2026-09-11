package com.zerostress.manager.voice

import android.content.Context
import android.util.Log
import com.zerostress.manager.BuildConfig
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * Thin singleton wrapper around the Agora RTC SDK (4.1.0) for the ZERO STRESS
 * Discord-style voice channels.
 *
 * - Everyone in the same channel publishes and receives audio (group call).
 * - The client UID is derived from the Firebase UID so it is stable per user.
 * - Token auth is used only if a token is passed to [join]; otherwise joins
 *   without a token (works while the Agora project runs in App-Certificate-less
 *   test mode).
 *
 * Admin controls (mute / kick / ban) are enforced through Firestore flags on
 * `voice_channels/{id}/participants/{uid}` — [com.zerostress.manager.VoiceActivity]
 * observes those flags on every member's device and calls [setMuted] / [leave],
 * so an admin mute actually silences the victim's mic.
 */
object AgoraVoiceManager {

    private const val TAG = "AgoraVoiceManager"

    private var engine: RtcEngine? = null
    private var joinedChannel: String? = null
    private var isLocalMuted = false

    /** Stable numeric UID derived from the Firebase UID string. */
    fun uidFor(firebaseUid: String): Int {
        return (firebaseUid.hashCode().toLong() and 0x7FFFFFFFL).toInt().coerceAtLeast(1)
    }

    /** Callbacks fired from Agora's internal threads — marshal to UI in the caller. */
    interface Listener {
        fun onJoinedChannel(channel: String) {}
        fun onUserJoined(uid: Int) {}
        fun onUserOffline(uid: Int) {}
        fun onUserMuted(uid: Int, muted: Boolean) {}
        fun onUserSpeaking(uid: Int, volume: Int) {}
        fun onError(code: Int) {}
        fun onLeftChannel() {}
    }

    @Volatile
    private var listener: Listener? = null

    fun setListener(l: Listener?) {
        listener = l
    }

    /** Lazily creates the RtcEngine. Returns false if the App ID is missing or init failed. */
    @Synchronized
    fun ensureEngine(context: Context): Boolean {
        if (engine != null) return true
        val appId = BuildConfig.AGORA_APP_ID
        if (appId.isBlank()) {
            Log.e(TAG, "BuildConfig.AGORA_APP_ID is empty — set AGORA_APP_ID in gradle.properties")
            return false
        }
        return try {
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.i(TAG, "join success channel=$channel uid=$uid")
                        joinedChannel = channel
                        try {
                            engine?.setEnableSpeakerphone(true)
                        } catch (e: Exception) {
                            Log.w(TAG, "speakerphone failed", e)
                        }
                        listener?.onJoinedChannel(channel ?: "")
                    }

                    override fun onLeaveChannel(stats: IRtcEngineEventHandler.RtcStats?) {
                        Log.i(TAG, "left channel")
                        joinedChannel = null
                        listener?.onLeftChannel()
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.d(TAG, "user joined uid=$uid")
                        listener?.onUserJoined(uid)
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.d(TAG, "user offline uid=$uid reason=$reason")
                        listener?.onUserOffline(uid)
                    }

                    override fun onUserMuteAudio(uid: Int, muted: Boolean) {
                        Log.d(TAG, "user muted uid=$uid muted=$muted")
                        listener?.onUserMuted(uid, muted)
                    }

                    override fun onAudioVolumeIndication(
                        speakers: Array<out IRtcEngineEventHandler.AudioVolumeInfo>?,
                        totalVolume: Int
                    ) {
                        speakers?.forEach { s ->
                            // uid 0 == local user
                            listener?.onUserSpeaking(s.uid, s.volume)
                        }
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "agora error=$err")
                        listener?.onError(err)
                    }
                }
            }
            engine = RtcEngine.create(config)
            // Enable the mic volume meter (200ms interval) so speaking rings work.
            engine?.enableAudioVolumeIndication(200, 3, true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine.create failed", e)
            false
        }
    }

    /**
     * Joins the given channel and starts publishing the mic.
     * `token` may be null when the Agora project is in test (certificate-less) mode.
     * Uses the 4-arg joinChannel(token, channelName, optionalInfo, optionalUid).
     */
    @Synchronized
    fun join(context: Context, channelName: String, uid: Int, token: String?): Boolean {
        if (!ensureEngine(context)) return false
        if (joinedChannel != null) return true // already in a channel
        return try {
            // In 4.1.0 the ChannelMediaOptions fields are java Boolean/Integer objects;
            // Kotlin assigns fine, but read them back via the manager if ever needed.
            val options = ChannelMediaOptions().apply {
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                autoSubscribeAudio = true
                publishMicrophoneTrack = true
            }
            // 4.1.0 offers joinChannel(token, channelId, uid, options) — use that so
            // the media options (publish mic, autosubscribe) actually apply.
            val code = engine?.joinChannel(token, channelName, uid, options) ?: -1
            if (code == 0) {
                setMuted(isLocalMuted) // re-apply mute state on rejoin
                true
            } else {
                Log.e(TAG, "joinChannel failed code=$code")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "join failed", e)
            false
        }
    }

    @Synchronized
    fun leave() {
        try {
            engine?.leaveChannel()
        } catch (e: Exception) {
            Log.w(TAG, "leave failed", e)
        }
        joinedChannel = null
    }

    fun setMuted(muted: Boolean) {
        isLocalMuted = muted
        try {
            engine?.muteLocalAudioStream(muted)
        } catch (e: Exception) {
            Log.w(TAG, "setMuted($muted) failed", e)
        }
    }

    fun isMuted(): Boolean = isLocalMuted

    /** Mute playback of one remote user (local side only). */
    fun muteRemote(uid: Int, muted: Boolean) {
        try {
            engine?.muteRemoteAudioStream(uid, muted)
        } catch (e: Exception) {
            Log.w(TAG, "muteRemote($uid) failed", e)
        }
    }

    /** Route audio to speakerphone/earpiece. */
    fun setSpeakerphone(on: Boolean) {
        try {
            engine?.setEnableSpeakerphone(on)
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerphone($on) failed", e)
        }
    }

    @Synchronized
    fun release() {
        leave()
        try {
            RtcEngine.destroy()
        } catch (_: Exception) {}
        engine = null
    }

    fun inChannel(): String? = joinedChannel
}
