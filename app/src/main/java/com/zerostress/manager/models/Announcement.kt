package com.zerostress.manager.models

class Announcement {
    var id: String? = null
    var text: String? = null
    var author: String? = null
    var timestamp: Long = 0

    constructor()

    constructor(text: String, author: String) {
        this.text = text
        this.author = author
        this.timestamp = System.currentTimeMillis()
    }
}