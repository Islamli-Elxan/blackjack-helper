package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionTest {

    @Test
    fun ranksThatMatchTheTotalAreVerified() {
        val z = Fusion.dealer(listOf(9), true, 9)
        assertTrue(z.verified)
        assertFalse(z.approx)
        assertEquals(listOf(listOf(9)), z.hands)
    }

    @Test
    fun wrongRankIsReplacedByTheTotal() {
        // the 9 was read as a 6, the printed total says 9
        val z = Fusion.dealer(listOf(6), true, 9)
        assertTrue(z.approx)
        assertEquals(listOf(9), z.hands[0])
    }

    @Test
    fun unreadableRowFallsBackToTheTotal() {
        val z = Fusion.player(emptyList(), false, listOf(15))
        assertTrue(z.approx)
        assertTrue(z.sure)
        assertEquals(15, Strategy.evalHand(z.hands[0]).total)
    }

    @Test
    fun noTotalMeansNoChange() {
        val z = Fusion.player(listOf(listOf(5, 10)), true, emptyList())
        assertFalse(z.verified)
        assertFalse(z.approx)
        assertEquals(listOf(listOf(5, 10)), z.hands)
        val u = Fusion.player(emptyList(), false, listOf(null))
        assertFalse(u.sure)
    }

    @Test
    fun falseSplitIsMergedWhenOneTotalIsShown() {
        val z = Fusion.player(listOf(listOf(5), listOf(10)), true, listOf(15))
        assertTrue(z.verified)
        assertEquals(listOf(listOf(5, 10)), z.hands)
    }

    @Test
    fun realSplitIsVerifiedPerHand() {
        val z = Fusion.player(listOf(listOf(8, 3), listOf(8, 2)), true, listOf(11, 10))
        assertTrue(z.verified)
        assertEquals(2, z.hands.size)
    }

    @Test
    fun softAceMatchesEitherTotal() {
        assertTrue(Fusion.consistent(listOf(11, 6), 17))
        assertTrue(Fusion.consistent(listOf(11, 6), 7))
        assertFalse(Fusion.consistent(listOf(11, 6), 16))
    }

    @Test
    fun standInsAreNeverBlackjackOrPairs() {
        for (t in 4..21) {
            val p = Fusion.standInPlayer(t)
            assertEquals(t, Strategy.evalHand(p).total)
            if (t == 21) assertFalse(Strategy.evalHand(p).bj)
        }
        assertEquals(listOf(10), Fusion.standInDealer(10))
        assertEquals(listOf(10, 6), Fusion.standInDealer(16))
    }

    @Test
    fun trackerAcceptsVerifiedFixAndSkipsApproxCards() {
        val t = RoundTracker(stableMs = 300L, clearMs = 900L)
        // a wrong, unchecked reading with an extra card sticks ...
        t.onHands(listOf(9), listOf(listOf(5, 10, 2)), 0L)
        t.tick(400L)
        assertEquals(listOf(listOf(5, 10, 2)), t.hands)
        // ... until a reading checked against the total replaces it
        t.onHands(listOf(9), listOf(listOf(5, 10)), 500L, RoundTracker.Flags(true, true))
        t.tick(900L)
        assertEquals(listOf(listOf(5, 10)), t.hands)
        // an approximate hand is not added to the count
        t.onHands(listOf(10), listOf(listOf(10, 6)), 1000L, RoundTracker.Flags(false, false, true, true))
        t.tick(1400L)
        assertTrue(t.playerApprox)
        assertEquals(0, t.rc())
        assertEquals(0, t.seen())
    }
}
