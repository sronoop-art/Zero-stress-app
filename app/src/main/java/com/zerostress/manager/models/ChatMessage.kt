package com.zerostress.manager.models

class ChatMessage {
    var id: String? = null
    var senderId: String? = null
    var senderName: String? = null
    var text: String? = null
    var timestamp: Long = 0
    var deleted: Boolean = false

    constructor()

    constructor(senderId: String, senderName: String, text: String) {
        this.senderId = senderId
        this.senderName = senderName
        this.text = text
        this.timestamp = System.currentTimeMillis()
        this.deleted = false
    }
}