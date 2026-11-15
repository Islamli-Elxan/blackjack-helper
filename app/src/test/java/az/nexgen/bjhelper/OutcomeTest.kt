package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {

    private fun kind(p: List<Int>, d: List<Int>) = Outcome.decide(p, d)?.kind

    @Test
    fun stillPlayingGivesNothing() {
        assertNull(kind(listOf(10, 6), listOf(10)))          // dealer hole card hidden
        assertNull(kind(listOf(10, 6), listOf(10, 5)))       // dealer must still draw
        assertNull(kind(listOf(10), listOf(10, 7)))          // player has one card
    }

    @Test
    fun comparesTotalsWhenDealerStands() {
        assertEquals(Outcome.Kind.WIN, kind(listOf(10, 9), listOf(10, 8)))
        assertEquals(Outcome.Kind.LOSE, kind(listOf(10, 7), listOf(10, 8)))
        assertEquals(Outcome.Kind.PUSH, kind(listOf(10, 8), listOf(9, 9)))
        // dealer stands on soft 17
        assertEquals(Outcome.Kind.LOSE, kind(listOf(10, 6), listOf(11, 6)))
    }

    @Test
    fun bustsAndBlackjacks() {
        assertEquals(Outcome.Kind.BUST, kind(listOf(10, 4, 8), listOf(10)))
        assertEquals(Outcome.Kind.DEALER_BUST, kind(listOf(10, 6), listOf(10, 6, 9)))
        assertEquals(Outcome.Kind.BLACKJACK, kind(listOf(11, 10), listOf(10, 9)))
        assertEquals(Outcome.Kind.PUSH, kind(listOf(11, 10), listOf(11, 10)))
        assertEquals(Outcome.Kind.LOSE, kind(listOf(10, 9), listOf(11, 10)))
        // 21 with three cards is not a blackjack
        assertEquals(Outcome.Kind.WIN, kind(listOf(7, 7, 7), listOf(10, 9)))
    }

    @Test
    fun theScreenshotHand() {
        // king, 4, 2 = 16 against a ten: dealer has not finished
        assertNull(kind(listOf(10, 4, 2), listOf(10)))
    }
}
