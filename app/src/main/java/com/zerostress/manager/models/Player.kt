package com.zerostress.manager.models

class Player {
    var id: String? = null
    var name: String? = null
    var phone: String? = null
    var role: String? = null
    var status: String? = null
    var score: Long = 0
    var kills: Int = 0
    var deaths: Int = 0
    var assists: Int = 0
    var damage: Long = 0
    var wins: Int = 0
    var matches: Int = 0
    var xp: Int = 0
    var level: Int = 1
    var coins: Int = 0
    var rank: String? = null
    var fcmToken: String? = null

    constructor()

    constructor(id: String, name: String, phone: String) {
        this.id = id
        this.name = name
        this.phone = phone
        this.role = "player"
        this.status = "pending"
        this.score = 0
        this.kills = 0
        this.deaths = 0
        this.assists = 0
        this.damage = 0
        this.wins = 0
        this.matches = 0
        this.xp = 0
        this.level = 1
        this.coins = 0
        this.rank = "Iron"
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to id,
        "name" to name,
        "phone" to phone,
        "role" to role,
        "status" to status,
        "score" to score,
        "kills" to kills,
        "deaths" to deaths,
        "assists" to assists,
        "damage" to damage,
        "wins" to wins,
        "matches" to matches,
        "xp" to xp,
        "level" to level,
        "coins" to coins,
        "rank" to rank
    )

    companion object {
        fun calculateScore(kills: Int, damage: Long, wins: Int): Long =
            (kills * 10 + damage / 100 + wins * 50).toLong()

        fun getRankTier(score: Long): String = when {
            score >= 5000 -> "Mythic"
            score >= 4000 -> "Diamond"
            score >= 3000 -> "Platinum"
            score >= 2000 -> "Gold"
            score >= 1200 -> "Silver"
            score >= 600 -> "Bronze"
            else -> "Iron"
        }

        fun xpForLevel(level: Int): Int = level * 500
    }

    fun getWinRate(): Double = if (matches > 0) wins * 100.0 / matches else 0.0

    fun getAvgDamage(): Double = if (matches > 0) damage * 1.0 / matches else 0.0
}