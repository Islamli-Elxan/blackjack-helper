package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrategyTest {

    private val ace = 11

    private fun act(hand: List<Int>, up: Int, h17: Boolean = false, count: Boolean = false, tc: Double = 0.0): String =
        Strategy.advise(hand, up, h17, count, tc).act

    @Test
    fun hardHands() {
        assertEquals("H", act(listOf(10, 6), 10))
        assertEquals("S", act(listOf(10, 6), 6))
        assertEquals("S", act(listOf(10, 2), 5))
        assertEquals("H", act(listOf(10, 2), 2))
        assertEquals("H", act(listOf(5, 3), 6))
        assertEquals("D", act(listOf(4, 5), 5))
        assertEquals("H", act(listOf(6, 5), ace))
        assertEquals("D", act(listOf(6, 5), ace, h17 = true))
        assertEquals("S", act(listOf(10, 7), ace))
    }

    @Test
    fun softHands() {
        assertEquals("Ds", act(listOf(ace, 7), 6))
        assertEquals("H", act(listOf(ace, 7), 9))
        assertEquals("S", act(listOf(ace, 7), 2))
        assertEquals("Ds", act(listOf(ace, 7), 2, h17 = true))
        assertEquals("S", act(listOf(ace, 8), 6))
        assertEquals("Ds", act(listOf(ace, 8), 6, h17 = true))
        assertEquals("D", act(listOf(ace, 6), 4))
        assertEquals("H", act(listOf(ace, 6), 2))
    }

    @Test
    fun pairs() {
        assertEquals("P", act(listOf(8, 8), 10))
        assertEquals("P", act(listOf(ace, ace), 6))
        assertEquals("S", act(listOf(10, 10), 6))
        assertEquals("S", act(listOf(9, 9), 7))
        assertEquals("P", act(listOf(9, 9), 8))
        assertEquals("H", act(listOf(7, 7), 8))
        assertEquals("P", act(listOf(4, 4), 5))
        assertEquals("D", act(listOf(5, 5), 6))
        assertEquals("H", act(listOf(5, 5), 10))
    }

    @Test
    fun specialHands() {
        assertEquals("BJ", act(listOf(ace, 10), 6))
        assertEquals("BUST", act(listOf(10, 6, 8), 6))
        assertEquals("S", act(listOf(ace, ace, 9), 6))
    }

    @Test
    fun doubleFallsBackAfterTwoCards() {
        assertEquals("H", act(listOf(2, 3, 4), 5))
        assertEquals("S", act(listOf(ace, 3, 4), 6))
    }

    @Test
    fun countIndexPlays() {
        assertEquals("H", act(listOf(10, 6), 10, count = true, tc = -1.0))
        assertEquals("S", act(listOf(10, 6), 10, count = true, tc = 0.5))
        assertEquals("H", act(listOf(10, 2), 4, count = true, tc = -1.0))
        assertEquals("S", act(listOf(10, 2), 4, count = true, tc = 0.0))
        assertEquals("D", act(listOf(6, 5), ace, count = true, tc = 1.0))
        assertEquals("D", act(listOf(6, 4), 10, count = true, tc = 4.0))
        assertEquals("H", act(listOf(6, 4), 10, count = true, tc = 3.0))
    }

    @Test
    fun hiLoValues() {
        assertEquals(1, Strategy.hilo(2))
        assertEquals(1, Strategy.hilo(6))
        assertEquals(0, Strategy.hilo(7))
        assertEquals(0, Strategy.hilo(9))
        assertEquals(-1, Strategy.hilo(10))
        assertEquals(-1, Strategy.hilo(11))
    }

    @Test
    fun rankParsing() {
        assertEquals(11, RankParser.parse("A"))
        assertEquals(10, RankParser.parse("10"))
        assertEquals(10, RankParser.parse("K"))
        assertEquals(10, RankParser.parse("1O"))
        assertEquals(9, RankParser.parse(" 9 "))
        assertEquals(5, RankParser.parse("5♣"))
        assertNull(RankParser.parse("LONDON"))
        assertNull(RankParser.parse("25"))
        assertNull(RankParser.parse("1"))
    }

    @Test
    fun trackerAcceptsAndCommitsRound() {
        val t = RoundTracker(stableMs = 600L, clearMs = 900L)
        t.onReading(listOf(6), listOf(2, 3), 0L)
        t.tick(300L)
        assertEquals(emptyList<Int>(), t.player)
        t.onReading(listOf(6), listOf(2, 3), 450L)
        t.tick(700L)
        assertEquals(listOf(6), t.dealer)
        assertEquals(listOf(2, 3), t.player)
        assertEquals(3, t.rc())

        t.onReading(emptyList(), emptyList(), 1000L)
        t.onReading(emptyList(), emptyList(), 1450L)
        t.tick(2000L)
        assertEquals(emptyList<Int>(), t.player)
        assertEquals(3, t.baseRc)
        assertEquals(3, t.baseCards)
    }

    @Test
    fun trackerIgnoresShrinkingReadings() {
        val t = RoundTracker(stableMs = 600L, clearMs = 900L)
        t.onReading(listOf(9), listOf(5, 10), 0L)
        t.tick(700L)
        t.onReading(listOf(9), listOf(5), 800L)
        t.tick(1500L)
        assertEquals(listOf(5, 10), t.player)
    }
}
