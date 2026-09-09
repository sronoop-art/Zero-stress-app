package com.zerostress.manager.models

class VoiceChannel {
    var id: String? = null
    var name: String? = null
    var category: String? = null
    var active: Boolean = true
    var maxUsers: Int = 10
    var isStage: Boolean = false
    var allowScreenShare: Boolean = true
    var allowRecording: Boolean = false
    var participants: MutableList<String> = mutableListOf()
    var maxParticipants: Int = 10
    var createdAt: Long = 0

    constructor()

    constructor(id: String, name: String, category: String) {
        this.id = id
        this.name = name
        this.category = category
        this.active = true
        this.maxUsers = 10
        this.isStage = false
        this.allowScreenShare = true
        this.allowRecording = false
        this.participants = mutableListOf()
        this.maxParticipants = 10
        this.createdAt = System.currentTimeMillis()
    }

    fun getParticipants(): List<String> = participants.ifEmpty { mutableListOf() }
}