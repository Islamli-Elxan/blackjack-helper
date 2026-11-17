package az.nexgen.bjhelper

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlayerTest {

    private fun btn(kind: ButtonFinder.Kind, cx: Int): ButtonFinder.Button =
        ButtonFinder.Button(kind, CardFinder.Box(cx - 100, 2150, cx + 100, 2230))

    private val three = listOf(
        btn(ButtonFinder.Kind.STAND, 280), btn(ButtonFinder.Kind.DOUBLE, 540), btn(ButtonFinder.Kind.HIT, 788)
    )
    private val two = listOf(btn(ButtonFinder.Kind.STAND, 410), btn(ButtonFinder.Kind.HIT, 670))

    private fun state(
        dealer: List<Int>, hand: List<Int>, buttons: List<ButtonFinder.Button>,
        changedAt: Long = 0L, cover: CardFinder.Box? = null, checked: Boolean = true
    ) = AutoPlayer.State(dealer, listOf(hand), checked, checked, changedAt, buttons, cover, false, 0.0)

    /** Runs the clock until the player decides, as the service does every 120 ms. */
    private fun run(p: AutoPlayer, s: AutoPlayer.State, from: Long = 1000L): AutoPlayer.Decision {
        var t = from
        var d: AutoPlayer.Decision = AutoPlayer.Decision.Wait("start")
        while (t < from + 5000) {
            d = p.decide(t, s)
            if (d !is AutoPlayer.Decision.Wait) return d
            t += 120
        }
        return d
    }

    @Test
    fun hitsTwelveAgainstTen() {
        val d = run(AutoPlayer(Random(1)), state(listOf(10), listOf(5, 7), three))
        assertTrue(d is AutoPlayer.Decision.Tap)
        d as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.HIT, d.kind)
        assertTrue(d.x in 688..888)
        assertTrue(d.y in 2150..2230)
    }

    @Test
    fun standsOnSeventeen() {
        val d = run(AutoPlayer(Random(2)), state(listOf(6), listOf(10, 7), three)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.STAND, d.kind)
    }

    @Test
    fun doublesElevenWhenAllowed() {
        val d = run(AutoPlayer(Random(3)), state(listOf(6), listOf(5, 6), three)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.DOUBLE, d.kind)
    }

    @Test
    fun hitsInsteadOfDoublingWhenThereIsNoDoubleButton() {
        val d = run(AutoPlayer(Random(4)), state(listOf(6), listOf(5, 6), two)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.HIT, d.kind)
    }

    @Test
    fun softEighteenDoublesOrStands() {
        // A,7 against 5: double, else stand
        val with = run(AutoPlayer(Random(5)), state(listOf(5), listOf(11, 7), three)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.DOUBLE, with.kind)
        val without = run(AutoPlayer(Random(5)), state(listOf(5), listOf(11, 7), two)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.STAND, without.kind)
    }

    @Test
    fun leavesSplitsToThePerson() {
        val d = run(AutoPlayer(Random(6)), state(listOf(6), listOf(8, 8), three))
        assertTrue(d is AutoPlayer.Decision.Wait)
    }

    @Test
    fun waitsForConfirmedCards() {
        val d = run(AutoPlayer(Random(7)), state(listOf(10), listOf(5, 7), three, checked = false))
        assertTrue(d is AutoPlayer.Decision.Wait)
    }

    @Test
    fun doesNotPlayWhileTheDealerPlays() {
        val d = run(AutoPlayer(Random(8)), state(listOf(10, 6), listOf(5, 7), two))
        assertTrue(d is AutoPlayer.Decision.Wait)
    }

    @Test
    fun doesNotTapWhenTheCardsJustChanged() {
        val p = AutoPlayer(Random(9))
        val s = state(listOf(10), listOf(5, 7), three, changedAt = 1000L)
        assertTrue(p.decide(1100L, s) is AutoPlayer.Decision.Wait)
        assertTrue(p.decide(1300L, s) is AutoPlayer.Decision.Wait)
    }

    @Test
    fun doesNotTapUnderItsOwnWindow() {
        val cover = CardFinder.Box(600, 2100, 1000, 2300)
        val d = run(AutoPlayer(Random(10)), state(listOf(10), listOf(5, 7), three, cover = cover))
        assertTrue(d is AutoPlayer.Decision.Wait)
    }

    @Test
    fun waitsAfterATapThenRetriesThenGivesUp() {
        val p = AutoPlayer(Random(11))
        val s = state(listOf(10), listOf(5, 7), three)
        var t = 1000L
        var taps = 0
        var stuck = false
        while (t < 30_000L && !stuck) {
            val d = p.decide(t, s)
            if (d is AutoPlayer.Decision.Tap) taps++
            if (d is AutoPlayer.Decision.Stuck) stuck = true
            t += 120
        }
        assertEquals(AutoPlayer.MAX_TRIES, taps)
        assertTrue(stuck)
    }

    @Test
    fun aNewHandResetsTheTries() {
        val p = AutoPlayer(Random(12))
        val first = run(p, state(listOf(10), listOf(5, 7), three), 1000L)
        assertTrue(first is AutoPlayer.Decision.Tap)
        val next = run(p, state(listOf(10), listOf(5, 7, 4), three), 4000L)
        assertTrue(next is AutoPlayer.Decision.Tap)
    }

    @Test
    fun bustAndBlackjackAreLeftAlone() {
        assertTrue(run(AutoPlayer(Random(13)), state(listOf(10), listOf(10, 6, 9), two)) is AutoPlayer.Decision.Wait)
        assertTrue(run(AutoPlayer(Random(14)), state(listOf(10), listOf(11, 10), two)) is AutoPlayer.Decision.Wait)
    }
}
