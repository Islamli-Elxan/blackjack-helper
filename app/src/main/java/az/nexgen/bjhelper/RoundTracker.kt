package az.nexgen.bjhelper

/**
 * Turns noisy per-frame card readings into a stable current round and a running Hi-Lo count.
 * A reading is accepted after it stays unchanged for [stableMs]. A round is added to the count
 * after the table has been empty for [clearMs]. The player may hold several hands after a split.
 */
class RoundTracker(private val stableMs: Long = 300L, private val clearMs: Long = 2500L) {

    var baseRc = 0
        private set
    var baseCards = 0
        private set
    var dealer: List<Int> = emptyList()
        private set
    var hands: List<List<Int>> = emptyList()
        private set

    /** All the player's cards, hand after hand. */
    val player: List<Int> get() = hands.flatten()

    private var pendD: List<Int> = emptyList()
    private var pendH: List<List<Int>> = emptyList()
    private var pendSince = 0L
    private var emptySince = -1L
    private var emptyCount = 0

    /** One hand only (no split). */
    fun onReading(d: List<Int>, p: List<Int>, now: Long) =
        onHands(d, if (p.isEmpty()) emptyList() else listOf(p), now)

    fun onHands(d: List<Int>, h: List<List<Int>>, now: Long) {
        if (d != pendD || h != pendH) {
            pendD = d
            pendH = h
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
            if (pendD.isNotEmpty() && pendD.size >= dealer.size && pendD != dealer) {
                dealer = pendD
                changed = true
            }
            // cards never disappear during a round, so a smaller reading is a hidden card
            if (pendH.isNotEmpty() && pendH.sumOf { it.size } >= player.size && pendH != hands) {
                hands = pendH
                changed = true
            }
        }
        if (emptyCount >= 2 && now - emptySince >= clearMs && (dealer.isNotEmpty() || hands.isNotEmpty())) {
            commitRound()
            changed = true
        }
        return changed
    }

    private fun commitRound() {
        for (c in dealer) baseRc += Strategy.hilo(c)
        for (c in player) baseRc += Strategy.hilo(c)
        baseCards += dealer.size + player.size
        dealer = emptyList()
        hands = emptyList()
    }

    /** Forget the current round without adding it to the count. */
    fun clearRound() {
        dealer = emptyList()
        hands = emptyList()
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
        for (c in dealer) v += Strategy.hilo(c)
        for (c in player) v += Strategy.hilo(c)
        return v
    }

    fun seen(): Int = baseCards + dealer.size + player.size
}
