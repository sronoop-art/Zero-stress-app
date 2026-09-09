package com.zerostress.manager.models

class MatchSchedule {
    var id: String? = null
    var title: String? = null
    var description: String? = null
    var matchTime: Long = 0
    var status: String? = null // upcoming, ongoing, completed
    var createdBy: String? = null

    constructor()

    constructor(title: String, description: String, matchTime: Long, createdBy: String) {
        this.title = title
        this.description = description
        this.matchTime = matchTime
        this.createdBy = createdBy
        this.status = "upcoming"
    }
}