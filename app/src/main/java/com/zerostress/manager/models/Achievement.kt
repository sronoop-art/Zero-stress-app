package com.zerostress.manager.models

class Achievement {
    var id: String? = null
    var name: String? = null
    var description: String? = null
    var xpReward: Int = 0
    var coinReward: Int = 0
    var icon: String? = null
    var requirement: String? = null // e.g. "kills:100", "wins:50"

    constructor()

    constructor(
        name: String,
        description: String,
        xpReward: Int,
        coinReward: Int,
        icon: String,
        requirement: String
    ) {
        this.name = name
        this.description = description
        this.xpReward = xpReward
        this.coinReward = coinReward
        this.icon = icon
        this.requirement = requirement
    }
}