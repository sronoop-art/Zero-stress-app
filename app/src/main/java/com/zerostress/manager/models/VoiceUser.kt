package com.zerostress.manager.models

class VoiceUser {
    enum class Status { ONLINE, IDLE, DO_NOT_DISTURB }

    enum class VoiceState { CONNECTED, MUTED, DEAFENED, SCREEN_SHARING, HAND_RAISED, SPEAKING }

    var userId: String? = null
    var userName: String? = null
    var userStatus: String? = null
    var voiceState: String? = null
    var isMuted: Boolean = false
    var isDeafened: Boolean = false
    var isScreenSharing: Boolean = false
    var isHandRaised: Boolean = false
    var isSpeaking: Boolean = false
    var isHost: Boolean = false
    var isSpeaker: Boolean = false
    var joinedAt: Long = 0
    var lastActive: Long = 0

    constructor()

    constructor(userId: String, userName: String) {
        this.userId = userId
        this.userName = userName
        this.userStatus = Status.ONLINE.name
        this.voiceState = VoiceState.CONNECTED.name
        this.isMuted = false
        this.isDeafened = false
        this.isScreenSharing = false
        this.isHandRaised = false
        this.isSpeaking = false
        this.isHost = false
        this.isSpeaker = false
        this.joinedAt = System.currentTimeMillis()
        this.lastActive = System.currentTimeMillis()
    }
}