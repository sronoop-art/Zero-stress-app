package com.zerostress.manager.voice

import android.util.Log
import io.agora.rtc2.AgoraAnalyticsEventListener
import io.agora.rtc2.AudioVolumeInfo
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineEventHandler
import io.agora.rtc2.RtcEngineListener
import io.agora.rtc2.UserInfo

object AgoraVoiceManager {
    private const val TAG = "AgoraVoiceManager"

    interface Listener {
        fun onJoined(channelId: String, uid: Int)
        fun onUserJoined(uid: Int)
        fun onUserOffline(uid: Int)
        fun onVoiceVolumeChanged(volumes: List<AudioVolumeInfo>)
        fun onError(code: Int, message: String)
        fun onMuteCallRemoteAudioResult(result: Int)
    }

    private var engine: RtcEngine? = null
    private var currentListener: Listener? = null
    private var currentChannelId: String = ""

    fun init(appId: String): Boolean {
        if (appId.isBlank()) {
            Log.e(TAG, "AGORA_APP_ID is blank — voice won't work")
            return false
        }
        return try {
            engine = RtcEngine.create(appId, RtcEngineConfig())
            engine?.setRtcEngineListener(RtcEngineListenerAdapter())
            engine?.enableEncryption(RtcEngine.ENCRYPTION_TYPE_NONE, "")
            true
        } catch (e: Exception) {
            Log.e(TAG, "RtcEngine init failed", e)
            false
        }
    }

    fun isJoined(): Boolean = currentChannelId.isNotBlank()

    fun leave() {
        try {
            engine?.leaveChannel()
        } catch (_: Exception) {
        }
        currentChannelId = ""
        currentListener = null
        Log.d(TAG, "left voice channel")
    }

    fun join(
        channelId: String,
        uid: Int,
        token: String?,
        listener: Listener
    ): Boolean {
        if (engine == null) {
            Log.e(TAG, "Agora engine not initialized")
            listener.onError(110, "Agora engine not initialized")
            return false
        }
        currentListener = listener
        currentChannelId = channelId

        val options = ChannelMediaOptions().apply {
            clientRoleType = io.agora.rtc2.ClientRole.CLIENT_ROLE_BROADCASTER
            channelProfile = io.agora.rtc2.ChannelProfile.CHANNEL_PROFILE_COMMUNICATION
            upstreamAudio = true
            upstreamVideo = false
            downstreamAudio = true
            downstreamVideo = false
        }

        return try {
            val result = if (token.isNullOrBlank()) {
                engine?.joinChannel(channelId, null, uid, options)
            } else {
                engine?.joinChannel(token, channelId, null, uid, options)
            }
            if (result == 0) {
                Log.d(TAG, "join success channel=$channelId uid=$uid")
                true
            } else {
                Log.e(TAG, "joinChannel failed code=$result")
                listener.onError(result, "joinChannel failed code=$result")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "join failed", e)
            listener.onError(110, e.message ?: "join failed")
            false
        }
    }

    fun muteLocalAudio(mute: Boolean) {
        try {
            engine?.muteLocalAudioStream(mute)
        } catch (_: Exception) {
        }
    }

    fun setEnableAudioVolumeIndication(enable: Boolean) {
        try {
            engine?.enableAudioVolumeIndication(enable, 300, false)
        } catch (_: Exception) {
        }
    }

    fun startCallChat() {
        try {
            engine?.startCallChat()
        } catch (_: Exception) {
        }
    }

    fun stopCallChat() {
        try {
            engine?.stopCallChat()
        } catch (_: Exception) {
        }
    }

    fun getCurrentUid(): Int? {
        return try {
            engine?.getUid()
        } catch (_: Exception) {
            null
        }
    }

    private inner class RtcEngineListenerAdapter : RtcEngineListener {
        override fun onJoinChannelSuccess() {
            try {
                val uid = engine?.getUid() ?: 0
                currentListener?.onJoined(currentChannelId, uid)
            } catch (_: Exception) {
            }
        }

        override fun onUserJoined(uid: Int) {
            currentListener?.onUserJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            currentListener?.onUserOffline(uid)
        }

        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            currentListener?.onUserJoined(uid)
        }

        override fun onError(code: Int, msg: String) {
            currentListener?.onError(code, msg)
        }

        override fun onAudioVolumeIndication(volumes: Array<AudioVolumeInfo>, totalVolume: Int) {
            currentListener?.onVoiceVolumeChanged(
                volumes.asList()
            )
        }

        override fun onRtcStats(stats: io.agora.rtc2.RtcStats) {
        }

        override fun onTokenPrivilegeWillExpire(token: String) {
        }

        override fun onRequestToken() {
        }

        override fun onFirstRemoteVideoFrameOfUid(uid: Int, width: Int, height: Int, elapsed: Long) {
        }

        override fun onFirstLocalVideoFrame(width: Int, height: Int, elapsed: Long) {
        }

        override fun onVideoSizeChangedOfUid(
            uid: Int,
            width: Int,
            height: Int,
            rotation: Int,
            elapsed: Long
        ) {
        }

        override fun onRemoteVideoStateChanged(
            uid: Int,
            state: Int,
            reason: Int,
            elapsed: Long
        ) {
        }

        override fun onRemoteAudioStateChanged(
            uid: Int,
            state: Int,
            reason: Int,
            elapsed: Long
        ) {
        }

        override fun onLocalVideoStateChanged(state: Int, reason: Int) {
        }

        override fun onLocalAudioStateChanged(state: Int, reason: Int) {
        }

        override fun onAudioDeviceStateChanged(
            deviceType: Int,
            deviceState: Int,
            deviceIndex: Int
        ) {
        }

        override fun onCameraSdkRemoved() {
        }

        override fun onCallDuplicateRejected() {
        }

        override fun onInterrupted() {
        }

        override fun onMediaStreamPublished(url: String) {
        }

        override fun onMediaStreamUnpublished(url: String) {
        }

        override fun onMediaStreamInUse(url: String, inUse: Boolean) {
        }

        override fun onAudioMixingStateChanged(state: Int) {
        }

        override fun onActiveSpeaker(uid: Int) {
        }

        override fun onFaceDetectionResult(
            imagePath: String,
            faceCount: Int,
            faceRectList: List<FloatArray>,
            faceDegreeList: List<FloatArray>,
            detectionTime: Long
        ) {
        }

        override fun onFaceDetectionResourceReady() {
        }

        override fun onFaceDetectionResourceReleased(reason: Int) {
        }

        override fun onInsertPrivateLink(key: String, value: String) {
        }

        override fun onAutoSpellingFriendsChanged(friends: List<UserInfo>) {
        }

        override fun onAgoraAnalyticsEventListener(event: AgoraAnalyticsEventListener) {
        }

        override fun onUserInfoUpdated(userInfo: UserInfo) {
        }

        override fun onVolumeIndication(userId: Int, volume: Int, expired: Boolean) {
        }
    }
}
