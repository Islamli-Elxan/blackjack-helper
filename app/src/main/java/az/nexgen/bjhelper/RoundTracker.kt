package az.nexgen.bjhelper

/**
 * Turns noisy per-frame card readings into a stable current round and a running Hi-Lo count.
 * A reading is accepted after it stays unchanged for [stableMs]. A round is added to the count
 * after the table has been empty for [clearMs]. The player may hold several hands after a split.
 */
class RoundTracker(private val stableMs: Long = 450L, private val clearMs: Long = 2500L) {

    /** How sure a reading is: confirmed by the printed totals, or built from them alone. */
    class Flags(
        val dealerVerified: Boolean = false,
        val playerVerified: Boolean = false,
        val dealerApprox: Boolean = false,
        val playerApprox: Boolean = false
    )

    var baseRc = 0
        private set
    var baseCards = 0
        private set
    var dealer: List<Int> = emptyList()
        private set
    var hands: List<List<Int>> = emptyList()
        private set

    /** The hand is known only by its printed total; its real cards are not counted. */
    var dealerApprox = false
        private set
    var playerApprox = false
        private set

    /** The accepted hand was confirmed by (or built from) the printed total, not read alone. */
    var dealerChecked = false
        private set
    var playerChecked = false
        private set

    /** When the accepted state last changed; the auto player waits for it to settle. */
    var lastChangeAt = 0L
        private set

    /** All the player's cards, hand after hand. */
    val player: List<Int> get() = hands.flatten()

    private var pendD: List<Int> = emptyList()
    private var pendH: List<List<Int>> = emptyList()
    private var pendFlags = Flags()
    private var pendSince = 0L
    private var emptySince = -1L
    private var emptyCount = 0

    /** One hand only (no split). */
    fun onReading(d: List<Int>, p: List<Int>, now: Long) =
        onHands(d, if (p.isEmpty()) emptyList() else listOf(p), now)

    fun onHands(d: List<Int>, h: List<List<Int>>, now: Long, flags: Flags = Flags()) {
        if (d != pendD || h != pendH || flags.dealerApprox != pendFlags.dealerApprox ||
            flags.playerApprox != pendFlags.playerApprox || flags.dealerVerified != pendFlags.dealerVerified ||
            flags.playerVerified != pendFlags.playerVerified
        ) {
            pendD = d
            pendH = h
            pendFlags = flags
            pendSince = now
        }
        if (d.isEmpty() && h.isEmpty()) {
            if (emptyCount == 0) emptySince = now
            emptyCount++
        } else {
            // The table was cleared between two readings and cards are back: close the old round first.
            if (emptyCount >= 2 && now - emptySince >= clearMs / 2) commitRound()
            emptyCount = 0
            emptySince = -1L
        }
    }

    /** Returns true when the accepted state changed. */
    fun tick(now: Long): Boolean {
        var changed = false
        val hasPending = pendD.isNotEmpty() || pendH.isNotEmpty()
        if (hasPending && now - pendSince >= stableMs) {
            // A reading checked against the printed total (or built from it) may replace anything. An
            // unchecked one may only add cards: they never disappear during a round, so a smaller
            // reading is a hidden card.
            val dFree = pendFlags.dealerVerified || pendFlags.dealerApprox
            // cards sliding away leave a partial reading that only the printed total still matches;
            // it must not replace the real cards already known with the same total
            val dKeep = pendFlags.dealerApprox && !dealerApprox && dealer.isNotEmpty() &&
                sameTotals(listOf(dealer), listOf(pendD))
            if (pendD.isNotEmpty() && !dKeep && (pendD != dealer || pendFlags.dealerApprox != dealerApprox) &&
                (dFree || pendD.size >= dealer.size)
            ) {
                dealer = pendD
                dealerApprox = pendFlags.dealerApprox
                dealerChecked = dFree
                changed = true
            }
            val pFree = pendFlags.playerVerified || pendFlags.playerApprox
            val pKeep = pendFlags.playerApprox && !playerApprox && hands.isNotEmpty() && sameTotals(hands, pendH)
            if (pendH.isNotEmpty() && !pKeep && (pendH != hands || pendFlags.playerApprox != playerApprox) &&
                (pFree || pendH.sumOf { it.size } >= player.size)
            ) {
                hands = pendH
                playerApprox = pendFlags.playerApprox
                playerChecked = pFree
                changed = true
            }
        }
        if (emptyCount >= 2 && now - emptySince >= clearMs && (dealer.isNotEmpty() || hands.isNotEmpty())) {
            commitRound()
            changed = true
        }
        if (changed) lastChangeAt = now
        return changed
    }

    private fun sameTotals(a: List<List<Int>>, b: List<List<Int>>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (Strategy.evalHand(a[i]).total != Strategy.evalHand(b[i]).total) return false
        }
        return true
    }

    private fun commitRound() {
        // an approximate hand has no known ranks: it adds no count, but its cards were dealt
        if (!dealerApprox) for (c in dealer) baseRc += Strategy.hilo(c)
        if (!playerApprox) for (c in player) baseRc += Strategy.hilo(c)
        baseCards += dealer.size + player.size
        dealer = emptyList()
        hands = emptyList()
        dealerApprox = false
        playerApprox = false
        dealerChecked = false
        playerChecked = false
    }

    /** Forget the current round without adding it to the count. */
    fun clearRound() {
        dealer = emptyList()
        hands = emptyList()
        dealerApprox = false
        playerApprox = false
        pendD = emptyList()
        pendH = emptyList()
        emptyCount = 0
        emptySince = -1L
    }

    fun resetShoe() {
        clearRound()
        baseRc = 0
        baseCards = 0
    }

    /** Bring back a count saved earlier (the app was restarted in the middle of a shoe). */
    fun restore(rc: Int, cards: Int) {
        baseRc = rc
        baseCards = cards
    }

    fun rc(): Int {
        var v = baseRc
        if (!dealerApprox) for (c in dealer) v += Strategy.hilo(c)
        if (!playerApprox) for (c in player) v += Strategy.hilo(c)
        return v
    }

    fun seen(): Int = baseCards + dealer.size + player.size
}
