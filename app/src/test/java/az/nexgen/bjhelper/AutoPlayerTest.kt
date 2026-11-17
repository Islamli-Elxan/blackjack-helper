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
    fun doublesEvenWhenTheStandButtonIsNotSeen() {
        val noStand = listOf(btn(ButtonFinder.Kind.DOUBLE, 670), btn(ButtonFinder.Kind.HIT, 930))
        // 5,5 against 7 is a hard ten: double
        val d = run(AutoPlayer(Random(15)), state(listOf(7), listOf(5, 5), noStand)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.DOUBLE, d.kind)
    }

    private val splitBtn = btn(ButtonFinder.Kind.SPLIT, 410)
    private val four = listOf(
        btn(ButtonFinder.Kind.STAND, 150), splitBtn, btn(ButtonFinder.Kind.DOUBLE, 670), btn(ButtonFinder.Kind.HIT, 930)
    )

    private fun states(dealer: List<Int>, hands: List<List<Int>>, buttons: List<ButtonFinder.Button>, changedAt: Long) =
        AutoPlayer.State(dealer, hands, true, true, changedAt, buttons, null, false, 0.0)

    /** Like [run], but also says when the decision came. */
    private fun runT(p: AutoPlayer, s: AutoPlayer.State, from: Long): Pair<Long, AutoPlayer.Decision> {
        var t = from
        while (t < from + 6000) {
            val d = p.decide(t, s)
            if (d !is AutoPlayer.Decision.Wait) return Pair(t, d)
            t += 120
        }
        return Pair(t, AutoPlayer.Decision.Wait("none"))
    }

    @Test
    fun splitsWhenTheButtonIsThere() {
        val d = run(AutoPlayer(Random(6)), state(listOf(6), listOf(8, 8), four)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.SPLIT, d.kind)
        assertTrue(d.x in 310..510)
    }

    @Test
    fun playsAPairAsATotalWhenThereIsNoSplitButton() {
        // 8,8 against 10 is a split; without the button it is a hard 16: hit
        val d = run(AutoPlayer(Random(16)), state(listOf(10), listOf(8, 8), three)) as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.HIT, d.kind)
    }

    @Test
    fun playsASplitHandAfterHand() {
        val p = AutoPlayer(Random(17))
        val up = listOf(4)
        // 7,7 against 4: split
        val a = runT(p, states(up, listOf(listOf(7, 7)), four, 0L), 1000L)
        assertEquals(ButtonFinder.Kind.SPLIT, (a.second as AutoPlayer.Decision.Tap).kind)
        // left hand 10,6 against 4: stand
        val left = states(up, listOf(listOf(10, 6), listOf(5, 2)), three, 9000L)
        val b = runT(p, left, 10000L)
        assertEquals(ButtonFinder.Kind.STAND, (b.second as AutoPlayer.Decision.Tap).kind)
        // the right hand has not had its second card yet: nothing is pressed
        assertTrue(p.decide(b.first + 600L, left) is AutoPlayer.Decision.Wait)
        assertTrue(p.decide(b.first + 1500L, left) is AutoPlayer.Decision.Wait)
        // right hand 9,2 = 11 against 4: double
        val right = states(up, listOf(listOf(10, 6), listOf(9, 2)), three + listOf(), b.first + 1600L)
        val c = runT(p, right, b.first + 1700L)
        val tap = c.second as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.DOUBLE, tap.kind)
        assertTrue(tap.label.contains("2. əl"))
    }

    @Test
    fun aStandThatDidNothingIsPressedAgainOnTheSameHand() {
        val p = AutoPlayer(Random(18))
        val up = listOf(4)
        runT(p, states(up, listOf(listOf(7, 7)), four, 0L), 1000L)
        val left = states(up, listOf(listOf(10, 6), listOf(5, 2)), three, 9000L)
        val b = runT(p, left, 10000L)
        val again = runT(p, left, b.first + AutoPlayer.RETRY_MS)
        val tap = again.second as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.STAND, tap.kind)
        assertTrue(tap.label.contains("1. əl"))
    }

    @Test
    fun skipsASplitHandThatIsOver() {
        val p = AutoPlayer(Random(19))
        val up = listOf(4)
        runT(p, states(up, listOf(listOf(7, 7)), four, 0L), 1000L)
        // the left hand has just gone bust; the right one is waited for, then played: 7 against 4 is a hit
        val bust = states(up, listOf(listOf(10, 6, 9), listOf(5, 2)), three, 9000L)
        val first = p.decide(10000L, bust)
        assertTrue(first is AutoPlayer.Decision.Wait)
        val r = runT(p, bust, 10100L)
        val tap = r.second as AutoPlayer.Decision.Tap
        assertEquals(ButtonFinder.Kind.HIT, tap.kind)
        assertTrue(r.first >= 10000L + AutoPlayer.NEXT_HAND_MS)
    }

    @Test
    fun leavesARoundThatStartedSplitToThePerson() {
        val s = states(listOf(4), listOf(listOf(10, 6), listOf(9, 2)), three, 0L)
        assertTrue(run(AutoPlayer(Random(20)), s) is AutoPlayer.Decision.Wait)
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
