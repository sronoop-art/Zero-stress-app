package com.zerostress.manager.models

class MatchLog {
    var id: String? = null
    var playerId: String? = null
    var playerName: String? = null
    var kills: Int = 0
    var deaths: Int = 0
    var assists: Int = 0
    var damage: Long = 0
    var win: Boolean = false
    var matchType: String? = null
    var date: Long = 0

    constructor()

    constructor(
        playerId: String,
        playerName: String,
        kills: Int,
        deaths: Int,
        assists: Int,
        damage: Long,
        win: Boolean
    ) {
        this.playerId = playerId
        this.playerName = playerName
        this.kills = kills
        this.deaths = deaths
        this.assists = assists
        this.damage = damage
        this.win = win
        this.date = System.currentTimeMillis()
    }

    fun getScore(): Long = (kills * 10 + damage / 100 + if (win) 200 else 0).toLong()
}