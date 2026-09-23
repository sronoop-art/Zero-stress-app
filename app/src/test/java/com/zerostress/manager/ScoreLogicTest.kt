package com.zerostress.manager

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the single source of truth for scoring, XP, coins and the rank ladder
 * (ScoreMath.kt). All stat writers (DailyInputActivity = admin-logged entries,
 * mirrored to match_logs) MUST produce identical numbers through these
 * helpers, so the lifetime score, the daily/weekly/monthly leaderboard
 * buckets and the match-log charts always agree.
 */
class ScoreLogicTest {

    @Test
    fun `entryScore empty match`() {
        assertEquals(0L, ZsScore.entryScore(0, 0, false))
    }

    @Test
    fun `entryScore components`() {
        assertEquals(10L, ZsScore.entryScore(1, 0, false))
        assertEquals(200L, ZsScore.entryScore(0, 0, true)) // win bonus
        assertEquals(1L, ZsScore.entryScore(0, 100, false)) // damage/100
        assertEquals(0L, ZsScore.entryScore(0, 99, false)) // floor rounding
    }

    @Test
    fun `entryScore combined match`() {
        // 10 kills * 10 + 1000 damage/100 + 1 win * 200 = 100 + 10 + 200
        assertEquals(310L, ZsScore.entryScore(10, 1000, true))
    }

    @Test
    fun `entryXp keeps the classic curve`() {
        // 10 kills * 5 + 1000 damage/50 + 100 win = 50 + 20 + 100
        assertEquals(170L, ZsScore.entryXp(10, 1000, true))
        assertEquals(20L, ZsScore.entryXp(0, 0, false)) // participation
    }

    @Test
    fun `entryCoins player rate`() {
        assertEquals(45L, ZsScore.entryCoins(10, true)) // 10*2 + 25
        assertEquals(5L, ZsScore.entryCoins(0, false))
    }

    @Test
    fun `dailyCoins is 70 percent of the player rate`() {
        assertEquals(31L, ZsScore.dailyCoins(10, true)) // (20+25)*7/10 = 31
        assertEquals(3L, ZsScore.dailyCoins(0, false)) // 5*7/10 = 3
    }

    @Test
    fun `rank ladder boundaries`() {
        assertEquals("Iron", ZsScore.rankFor(599))
        assertEquals("Bronze", ZsScore.rankFor(600))
        assertEquals("Bronze", ZsScore.rankFor(1199))
        assertEquals("Silver", ZsScore.rankFor(1200))
        assertEquals("Gold", ZsScore.rankFor(2000))
        assertEquals("Platinum", ZsScore.rankFor(3000))
        assertEquals("Diamond", ZsScore.rankFor(4000))
        assertEquals("Mythic", ZsScore.rankFor(5000))
    }

    @Test
    fun `legacy Player helpers delegate to ZsScore`() {
        // Player.calculateScore/getRankTier must agree with the shared rules so
        // every code path (current and legacy) produces the same numbers.
        assertEquals(ZsScore.entryScore(10, 1000, true), com.zerostress.manager.models.Player.calculateScore(10, 1000, 1))
        assertEquals(ZsScore.entryScore(0, 0, false), com.zerostress.manager.models.Player.calculateScore(0, 0, 0))
        assertEquals(ZsScore.rankFor(2000), com.zerostress.manager.models.Player.getRankTier(2000))
    }

    @Test
    fun `rank tiers align with the shared rank-title thresholds`() {
        // Title thresholds 600..5000 must land exactly on rank-tier boundaries,
        // so a title unlocks the same moment the matching rank shows on profiles.
        assertEquals("Bronze", ZsScore.rankFor(600))
        assertEquals("Silver", ZsScore.rankFor(1200))
        assertEquals("Gold", ZsScore.rankFor(2000))
        assertEquals("Platinum", ZsScore.rankFor(3000))
        assertEquals("Diamond", ZsScore.rankFor(4000))
        assertEquals("Mythic", ZsScore.rankFor(5000))
    }
}
