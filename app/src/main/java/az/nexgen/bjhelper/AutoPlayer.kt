package az.nexgen.bjhelper

import java.util.Random

/**
 * Decides when to press which of the game's buttons. Pure logic: the screen reading comes in as a
 * [State], the answer is [Decision]; the actual tap is made elsewhere.
 *
 * It only plays a single hand whose cards were confirmed by the printed totals, waits until the
 * table has settled, pauses a moment like a person would, and gives up when a tap has no effect.
 * Splits are left to the person (the buttons for them are not known).
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
        /** The cards must have been the same for this long. */
        const val SETTLE_MS = 700L
        /** The buttons must have stood still for this long (they slide in). */
        const val BUTTONS_MS = 350L
        /** A person does not answer instantly. */
        const val DELAY_MIN_MS = 450
        const val DELAY_SPAN_MS = 700
        /** A tap that changed nothing is repeated after this long, up to [MAX_TRIES] times. */
        const val RETRY_MS = 2600L
        const val MAX_TRIES = 3
    }

    private var pendingSig = ""
    private var due = 0L
    private var lastSig = ""
    private var lastTapAt = 0L
    private var tries = 0
    private var buttonSig = ""
    private var buttonSince = 0L

    fun reset() {
        pendingSig = ""
        lastSig = ""
        tries = 0
    }

    fun decide(now: Long, s: State): Decision {
        val bsig = s.buttons.joinToString(",") { it.kind.name + (it.cx / 12) + ":" + (it.cy / 12) }
        if (bsig != buttonSig) {
            buttonSig = bsig
            buttonSince = now
        }

        if (s.hands.size != 1) return waiting("bir əl yoxdur")
        val hand = s.hands[0]
        if (s.dealer.size != 1) return waiting("diler kartı")
        if (hand.size < 2) return waiting("kartlar")
        if (!s.dealerChecked || !s.playerChecked) return waiting("cəm təsdiqlənmir")

        val adv = Strategy.advise(hand, s.dealer[0], false, s.useCount, s.tc)
        when (adv.act) {
            "BUST", "BJ" -> return waiting("əl bitib")
            "P" -> return waiting("böl: özün bas")
        }

        val sig = s.dealer.joinToString(" ") + "|" + hand.joinToString(" ")
        if (sig != pendingSig) {
            pendingSig = sig
            due = now + DELAY_MIN_MS + rnd.nextInt(DELAY_SPAN_MS)
        }
        if (now - s.changedAt < SETTLE_MS) return Decision.Wait("oturuşur")
        if (now < due) return Decision.Wait("fikirləşir")
        if (now - buttonSince < BUTTONS_MS) return Decision.Wait("düymələr")

        val stand = s.buttons.firstOrNull { it.kind == ButtonFinder.Kind.STAND }
        val hit = s.buttons.firstOrNull { it.kind == ButtonFinder.Kind.HIT }
        val dbl = s.buttons.firstOrNull { it.kind == ButtonFinder.Kind.DOUBLE }
        // a decision screen has at least two of the buttons; a missing one only matters if it is the one wanted
        if (s.buttons.size < 2) return Decision.Wait("düymə görünmür")

        val target: ButtonFinder.Button?
        val label: String
        when (adv.act) {
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

        val bw = target.box.r - target.box.l
        val bh = target.box.b - target.box.t
        val x = target.cx + ((rnd.nextDouble() - 0.5) * 0.4 * bw).toInt()
        val y = target.cy + ((rnd.nextDouble() - 0.5) * 0.4 * bh).toInt()
        return Decision.Tap(target.kind, x, y, label + " (" + adv.kind + " " + adv.total + " : diler " + s.dealer[0] + ")")
    }

    private fun waiting(why: String): Decision.Wait {
        // the table moved on: the next decision starts fresh
        pendingSig = ""
        return Decision.Wait(why)
    }
}
