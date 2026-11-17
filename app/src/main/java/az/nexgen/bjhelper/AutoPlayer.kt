package az.nexgen.bjhelper

import java.util.Random

/**
 * Decides when to press which of the game's buttons. Pure logic: the screen reading comes in as a
 * [State], the answer is [Decision]; the actual tap is made elsewhere.
 *
 * It only plays hands whose cards were confirmed by the printed totals, waits until the table has
 * settled, pauses a moment like a person would, and gives up when a tap has no effect.
 *
 * After a split the game plays the hands from left to right: the buttons always belong to the
 * hand in play, so the player keeps track of which one that is.
 */
class AutoPlayer(private val rnd: Random = Random()) {

    class State(
        val dealer: List<Int>,
        val hands: List<List<Int>>,
        val dealerChecked: Boolean,
        val playerChecked: Boolean,
        /** When the accepted cards last changed. */
        val changedAt: Long,
        val buttons: List<ButtonFinder.Button>,
        /** Our own window; a tap under it would hit the window instead of the game. */
        val cover: CardFinder.Box?,
        val useCount: Boolean,
        val tc: Double
    )

    sealed class Decision {
        class Wait(val why: String) : Decision()
        class Tap(val kind: ButtonFinder.Kind, val x: Int, val y: Int, val label: String) : Decision()
        class Stuck(val why: String) : Decision()
    }

    companion object {
        /** The cards must have been the same for this long (longer for the later hands of a split). */
        const val SETTLE_MS = 700L
        const val SETTLE_LATER_MS = 1400L
        /** The buttons must have stood still for this long (they slide in). */
        const val BUTTONS_MS = 350L
        /** A person does not answer instantly. */
        const val DELAY_MIN_MS = 450
        const val DELAY_SPAN_MS = 700
        /** A tap that changed nothing is repeated after this long, up to [MAX_TRIES] times. */
        const val RETRY_MS = 2600L
        const val MAX_TRIES = 3
        /** After one hand is done the game deals the next one its second card: wait for that. */
        const val NEXT_HAND_MS = 2500L
        const val MAX_HANDS = 4
    }

    private var pendingSig = ""
    private var due = 0L
    private var lastSig = ""
    private var lastTapAt = 0L
    private var tries = 0
    private var buttonSig = ""
    private var buttonSince = 0L

    /** The hand in play after a split (0 = the left one). */
    private var active = 0
    private var prevHands = 0
    private var waitHand: List<Int>? = null
    private var waitSince = 0L
    private var advanced = false
    private var advancedFrom = 0
    /** The round started while the hands were already split: it is not ours to play. */
    private var foreign = false

    fun reset() {
        pendingSig = ""
        lastSig = ""
        tries = 0
        endRound()
    }

    private fun endRound() {
        active = 0
        prevHands = 0
        waitHand = null
        advanced = false
        foreign = false
    }

    fun decide(now: Long, s: State): Decision {
        val bsig = s.buttons.joinToString(",") { it.kind.name + (it.cx / 12) + ":" + (it.cy / 12) }
        if (bsig != buttonSig) {
            buttonSig = bsig
            buttonSince = now
        }

        val n = s.hands.size
        if (n == 0) {
            endRound()
            return waiting("kartlar")
        }
        if (s.dealer.size != 1) return waiting("diler kartı")
        if (!s.dealerChecked || !s.playerChecked) return waiting("cəm təsdiqlənmir")
        if (n > MAX_HANDS) return waiting("çox əl: özün oyna")

        // which hand is in play
        if (n == 1) {
            active = 0
            prevHands = 1
            foreign = false
        } else {
            if (prevHands == 0) foreign = true
            if (prevHands <= 1 && !foreign) active = 0
            prevHands = n
        }
        if (foreign) return waiting("bölünmüş əl: özün oyna")

        val sig = s.dealer.joinToString(" ") + "|" + s.hands.joinToString(";") { it.joinToString(" ") }
        if (sig != lastSig) {
            advanced = false
        } else if (advanced && now - lastTapAt >= RETRY_MS) {
            // the press that ended the hand did not do anything: that hand is still in play
            active = advancedFrom
            advanced = false
            waitHand = null
        }

        if (n > 1) {
            // a hand that is over (21 or bust) is skipped by the game
            while (active < n && Strategy.evalHand(s.hands[active]).total >= 21) {
                active++
                waitHand = s.hands.getOrNull(active)
                waitSince = now
            }
            if (active >= n) return waiting("bütün əllər bitib")
        }
        val idx = active
        val hand = s.hands[idx]
        if (hand.size < 2) return waiting("kartlar")
        val wh = waitHand
        if (wh != null) {
            if (hand == wh && now - waitSince < NEXT_HAND_MS) return Decision.Wait("növbəti əl")
            waitHand = null
        }

        val up = s.dealer[0]
        val adv = Strategy.advise(hand, up, false, s.useCount, s.tc)
        if (adv.act == "BUST" || adv.act == "BJ") return waiting("əl bitib")

        if (sig != pendingSig) {
            pendingSig = sig
            due = now + DELAY_MIN_MS + rnd.nextInt(DELAY_SPAN_MS)
        }
        val settle = if (active > 0) SETTLE_LATER_MS else SETTLE_MS
        if (now - s.changedAt < settle) return Decision.Wait("oturuşur")
        if (now < due) return Decision.Wait("fikirləşir")
        if (now - buttonSince < BUTTONS_MS) return Decision.Wait("düymələr")

        // a decision screen has at least two of the buttons; a missing one only matters if it is the one wanted
        if (s.buttons.size < 2) return Decision.Wait("düymə görünmür")
        fun find(k: ButtonFinder.Kind) = s.buttons.firstOrNull { it.kind == k }
        val stand = find(ButtonFinder.Kind.STAND)
        val hit = find(ButtonFinder.Kind.HIT)
        val dbl = find(ButtonFinder.Kind.DOUBLE)
        val split = find(ButtonFinder.Kind.SPLIT)

        var act = adv.act
        if (act == "P") {
            if (split == null) {
                // no Split button (not allowed any more): play the cards as they add up
                val e = Strategy.evalHand(hand)
                act = if (e.soft) Strategy.softCode(e.total, up, false) else Strategy.hardCode(e.total, up, false)
            }
        }

        val target: ButtonFinder.Button?
        val label: String
        when (act) {
            "P" -> { target = split; label = "böl" }
            "H" -> { target = hit; label = "kart al" }
            "S" -> { target = stand; label = "dayan" }
            "D" -> if (dbl != null) { target = dbl; label = "ikiqat" } else { target = hit; label = "kart al" }
            "Ds" -> if (dbl != null) { target = dbl; label = "ikiqat" } else { target = stand; label = "dayan" }
            else -> return Decision.Wait("?")
        }
        if (target == null) return Decision.Wait("düymə görünmür")

        val c = s.cover
        if (c != null) {
            val b = target.box
            val over = b.l < c.r + 6 && b.r > c.l - 6 && b.t < c.b + 6 && b.b > c.t - 6
            if (over) return Decision.Wait("pəncərə düymənin üstündədir")
        }

        if (sig == lastSig) {
            if (now - lastTapAt < RETRY_MS) return Decision.Wait("nəticə gözlənir")
            if (tries >= MAX_TRIES) return Decision.Stuck("düymə basılmır")
            tries++
        } else {
            lastSig = sig
            tries = 1
        }
        lastTapAt = now

        // standing or doubling ends this hand; the game moves on to the next one
        if (n > 1 && (target.kind == ButtonFinder.Kind.STAND || target.kind == ButtonFinder.Kind.DOUBLE)) {
            advanced = true
            advancedFrom = active
            active++
            waitHand = s.hands.getOrNull(active)
            waitSince = now
        }

        val bw = target.box.r - target.box.l
        val bh = target.box.b - target.box.t
        val x = target.cx + ((rnd.nextDouble() - 0.5) * 0.4 * bw).toInt()
        val y = target.cy + ((rnd.nextDouble() - 0.5) * 0.4 * bh).toInt()
        val which = if (n > 1) ", " + (idx + 1) + ". əl" else ""
        return Decision.Tap(target.kind, x, y, label + " (" + adv.kind + " " + adv.total + " : diler " + up + which + ")")
    }

    private fun waiting(why: String): Decision.Wait {
        // the table moved on: the next decision starts fresh
        pendingSig = ""
        return Decision.Wait(why)
    }
}
