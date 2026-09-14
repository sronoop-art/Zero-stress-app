package com.zerostress.manager

import com.zerostress.manager.models.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the score formula and rank-tier ladder shared by dashboards and leaderboards. */
class ScoreLogicTest {

    @Test
    fun `calculateScore empty player`() {
        assertEquals(0L, Player.calculateScore(0, 0, 0))
    }

    @Test
    fun `calculateScore components`() {
        assertEquals(10L, Player.calculateScore(1, 0, 0))
        assertEquals(50L, Player.calculateScore(0, 0, 1))
        assertEquals(1L, Player.calculateScore(0, 100, 0)) // damage/100
        assertEquals(0L, Player.calculateScore(0, 99, 0)) // floor rounding
    }

    @Test
    fun `calculateScore combined match`() {
        // 10 kills * 10 + 1000 damage/100 + 1 win * 50 = 100 + 10 + 50
        assertEquals(160L, Player.calculateScore(10, 1000, 1))
    }

    @Test
    fun `rank tier ladder boundaries`() {
        assertEquals("Iron", Player.getRankTier(599))
        assertEquals("Bronze", Player.getRankTier(600))
        assertEquals("Bronze", Player.getRankTier(1199))
        assertEquals("Silver", Player.getRankTier(1200))
        assertEquals("Gold", Player.getRankTier(2000))
        assertEquals("Platinum", Player.getRankTier(3000))
        assertEquals("Diamond", Player.getRankTier(4000))
        assertEquals("Mythic", Player.getRankTier(5000))
    }

    @Test
    fun `rank tiers align with the shared rank-title thresholds`() {
        // Title thresholds 600..5000 must land exactly on rank-tier boundaries,
        // so a title unlocks the same moment the matching rank shows on profiles.
        assertEquals("Bronze", Player.getRankTier(600))
        assertEquals("Silver", Player.getRankTier(1200))
        assertEquals("Gold", Player.getRankTier(2000))
        assertEquals("Platinum", Player.getRankTier(3000))
        assertEquals("Diamond", Player.getRankTier(4000))
        assertEquals("Mythic", Player.getRankTier(5000))
    }
}
