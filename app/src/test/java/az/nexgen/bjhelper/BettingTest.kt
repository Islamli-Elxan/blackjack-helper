package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BettingTest {

    @Test
    fun flatBetWhenCountIsLow() {
        // normal risk: 10000 * 0.4% * 0.5 = 20
        assertEquals(20L, Betting.recommend(10_000L, 0.0, true, 1).amount)
        assertEquals(20L, Betting.recommend(10_000L, -3.0, true, 1).amount)
        assertEquals(20L, Betting.recommend(10_000L, 1.0, true, 1).amount)
        assertEquals(20L, Betting.recommend(10_000L, 6.0, false, 1).amount)
    }

    @Test
    fun betGrowsWithCount() {
        val a = Betting.recommend(10_000L, 2.0, true, 1).amount
        val b = Betting.recommend(10_000L, 4.0, true, 1).amount
        val c = Betting.recommend(10_000L, 6.0, true, 1).amount
        assertTrue(a <= b && b < c)
        assertTrue(Betting.recommend(10_000L, 4.0, true, 1).raised)
        assertFalse(Betting.recommend(10_000L, 0.0, true, 1).raised)
    }

    @Test
    fun riskScalesTheBet() {
        val careful = Betting.recommend(50_000L, 5.0, true, 0).amount
        val normal = Betting.recommend(50_000L, 5.0, true, 1).amount
        val bold = Betting.recommend(50_000L, 5.0, true, 2).amount
        assertTrue(careful < normal && normal < bold)
    }

    @Test
    fun limitsAreRespected() {
        assertEquals(100L, Betting.recommend(10_000L, 0.0, true, 1, minBet = 100L).amount)
        assertEquals(50L, Betting.recommend(1_000_000L, 8.0, true, 2, maxBet = 50L).amount)
        assertEquals(30L, Betting.recommend(30L, 8.0, true, 2, minBet = 100L).amount)
        assertEquals(0L, Betting.recommend(0L, 3.0, true, 1).amount)
    }

    @Test
    fun smallBalanceNeverBetsZero() {
        assertEquals(1L, Betting.recommend(50L, 0.0, true, 0).amount)
    }

    @Test
    fun niceRounding() {
        assertEquals(35L, Betting.nice(37.2))
        assertEquals(150L, Betting.nice(137.0))
        assertEquals(500L, Betting.nice(480.0))
        assertEquals(7L, Betting.nice(6.8))
    }

    @Test
    fun shortNumbers() {
        assertEquals("950", Betting.format(950L))
        assertEquals("1.5K", Betting.format(1500L))
        assertEquals("25K", Betting.format(25_000L))
        assertEquals("2.5M", Betting.format(2_500_000L))
    }
}
