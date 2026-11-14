package az.nexgen.bjhelper

/**
 * Turns noisy per-frame card readings into a stable current round and a running Hi-Lo count.
 * A reading is accepted after it stays unchanged for [stableMs]. A round is added to the count
 * after the table has been empty for [clearMs].
 */
class RoundTracker(private val stableMs: Long = 600L, private val clearMs: Long = 900L) {

    var baseRc = 0
        private set
    var baseCards = 0
        private set
    var dealer: List<Int> = emptyList()
        private set
    var player: List<Int> = emptyList()
        private set

    private var pendD: List<Int> = emptyList()
    private var pendP: List<Int> = emptyList()
    private var pendSince = 0L
    private var emptySince = -1L
    private var emptyCount = 0

    fun onReading(d: List<Int>, p: List<Int>, now: Long) {
        if (d != pendD || p != pendP) {
            pendD = d
            pendP = p
            pendSince = now
        }
        if (d.isEmpty() && p.isEmpty()) {
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
        val hasPending = pendD.isNotEmpty() || pendP.isNotEmpty()
        if (hasPending && now - pendSince >= stableMs) {
            if (pendD.isNotEmpty() && pendD.size >= dealer.size && pendD != dealer) {
                dealer = pendD
                changed = true
            }
            if (pendP.isNotEmpty() && pendP.size >= player.size && pendP != player) {
                player = pendP
                changed = true
            }
        }
        if (emptyCount >= 2 && now - emptySince >= clearMs && (dealer.isNotEmpty() || player.isNotEmpty())) {
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
        player = emptyList()
    }

    /** Forget the current round without adding it to the count. */
    fun clearRound() {
        dealer = emptyList()
        player = emptyList()
        pendD = emptyList()
        pendP = emptyList()
        emptyCount = 0
        emptySince = -1L
    }

    fun resetShoe() {
        clearRound()
        baseRc = 0
        baseCards = 0
    }

    fun rc(): Int {
        var v = baseRc
        for (c in dealer) v += Strategy.hilo(c)
        for (c in player) v += Strategy.hilo(c)
        return v
    }

    fun seen(): Int = baseCards + dealer.size + player.size
}
