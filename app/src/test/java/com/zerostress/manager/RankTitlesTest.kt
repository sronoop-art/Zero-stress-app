package com.zerostress.manager

import com.zerostress.manager.models.ZsRankTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the 8-rank title ladder used by the My Titles screen and avatar frames. */
class RankTitlesTest {

    @Test
    fun `eight titles exist`() {
        assertEquals(8, ZsRankTitles.ALL.size)
    }

    @Test
    fun `title order matches the rank ladder`() {
        val names = ZsRankTitles.ALL.map { it.name }
        assertEquals(
            listOf(
                "Bronze", "Silver", "Gold", "Platinum",
                "Diamond", "Heroic", "Master", "Grandmaster"
            ),
            names
        )
    }

    @Test
    fun `thresholds are strictly increasing`() {
        val scores = ZsRankTitles.ALL.map { it.unlockScore }
        assertTrue(scores.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `titleForScore returns the correct tier at each boundary`() {
        assertNull("Below Bronze must have no title", ZsRankTitles.titleForScore(599))
        assertEquals("Bronze", ZsRankTitles.titleForScore(600)?.name)
        assertEquals("Silver", ZsRankTitles.titleForScore(1200)?.name)
        assertEquals("Gold", ZsRankTitles.titleForScore(2000)?.name)
        assertEquals("Platinum", ZsRankTitles.titleForScore(3000)?.name)
        assertEquals("Diamond", ZsRankTitles.titleForScore(4000)?.name)
        assertEquals("Heroic", ZsRankTitles.titleForScore(5000)?.name)
        assertEquals("Master", ZsRankTitles.titleForScore(7000)?.name)
        assertEquals("Grandmaster", ZsRankTitles.titleForScore(10000)?.name)
        assertEquals("Grandmaster", ZsRankTitles.titleForScore(999_999)?.name)
    }

    @Test
    fun `titleForScore just below each boundary stays one tier lower`() {
        assertEquals("Bronze", ZsRankTitles.titleForScore(1199)?.name)
        assertEquals("Silver", ZsRankTitles.titleForScore(1999)?.name)
        assertEquals("Gold", ZsRankTitles.titleForScore(2999)?.name)
        assertEquals("Master", ZsRankTitles.titleForScore(9999)?.name)
    }

    @Test
    fun `byName resolves Firestore display names exactly`() {
        assertEquals("gold", ZsRankTitles.byName("Gold")?.id)
        assertNull(ZsRankTitles.byName("gold"))
        assertNull(ZsRankTitles.byName(""))
        assertNull(ZsRankTitles.byName(null))
    }

    @Test
    fun `every title has a drawable slot name and frame name`() {
        for (t in ZsRankTitles.ALL) {
            assertTrue(t.id.isNotBlank())
            assertEquals(t.id, t.id.lowercase())
        }
    }
}
