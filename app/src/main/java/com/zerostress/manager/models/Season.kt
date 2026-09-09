package com.zerostress.manager.models

class Season {
    var id: String? = null
    var name: String? = null
    var startDate: Long = 0
    var endDate: Long = 0
    var active: Boolean = true
    var topRewardCoins: Int = 500

    constructor()

    constructor(name: String, startDate: Long, endDate: Long) {
        this.name = name
        this.startDate = startDate
        this.endDate = endDate
        this.active = true
        this.topRewardCoins = 500
    }
}