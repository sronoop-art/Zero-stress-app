package com.zerostress.manager.models

class FriendRequest {
    var id: String? = null
    var fromUserId: String? = null
    var fromUserName: String? = null
    var toUserId: String? = null
    var status: String? = null // pending, accepted, rejected
    var timestamp: Long = 0

    constructor()

    constructor(fromUserId: String, fromUserName: String, toUserId: String) {
        this.fromUserId = fromUserId
        this.fromUserName = fromUserName
        this.toUserId = toUserId
        this.status = "pending"
        this.timestamp = System.currentTimeMillis()
    }
}