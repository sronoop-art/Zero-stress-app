package com.zerostress.manager.models

/**
 * Minimal voice-channel model retained only for Firestore document mapping.
 * The UI layer now stores call presence under voice_channels/{id}/participants/{uid}
 * and call chat under voice_channels/{id}/call_chat, so most fields from the old
 * voice channel screen are no longer used.
 */
class VoiceChannel {
    var id: String? = null
    var name: String? = null
    var active: Boolean = true
    var createdAt: Long = 0
    var createdBy: String? = null

    constructor()

    constructor(id: String, name: String) {
        this.id = id
        this.name = name
        this.active = true
        this.createdAt = System.currentTimeMillis()
    }
}