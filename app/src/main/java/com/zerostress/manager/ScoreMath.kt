package com.zerostress.manager

/**
 * Single source of truth for scoring, XP, coins and the rank ladder.
 * All stat writers (DailyInputActivity = admin-logged entries, mirrored to
 * match_logs, and FirebaseRepository.addMatchLog) MUST use these helpers so a
 * player's lifetime `score`, the daily/weekly/monthly leaderboard buckets, and
 * the match-log charts always move together and match each other.
 *
 * Entry score  = kills*10 + damage/100 + win*200
 * Lifetime     = sum of entry scores (server-side increments)
 * Rank ladder  = same thresholds as models/Player.kt
 */
object ZsScore {
    /** Score for a single match entry. */
    fun entryScore(kills: Int, damage: Long, isWin: Boolean): Long =
        kills.toLong() * 10L + damage / 100L + if (isWin) 200L else 0L

    /** XP gained for a single match entry (same curve as before). */
    fun entryXp(kills: Int, damage: Long, isWin: Boolean): Long =
        kills.toLong() * 5L + damage / 50L + if (isWin) 100L else 20L

    /** Coins for a match at the full player rate. */
    fun entryCoins(kills: Int, isWin: Boolean): Long =
        kills.toLong() * 2L + if (isWin) 25L else 5L

    /** Coins for an admin-logged daily entry (70% of the full rate). */
    fun dailyCoins(kills: Int, isWin: Boolean): Long = entryCoins(kills, isWin) * 7L / 10L

    /** Map a lifetime score to its rank name (same ladder as models/Player.kt). */
    fun rankFor(score: Long): String = when {
        score >= 5000 -> "Mythic"
        score >= 4000 -> "Diamond"
        score >= 3000 -> "Platinum"
        score >= 2000 -> "Gold"
        score >= 1200 -> "Silver"
        score >= 600 -> "Bronze"
        else -> "Iron"
    }
}
