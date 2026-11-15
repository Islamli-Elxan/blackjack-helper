package az.nexgen.bjhelper

/**
 * Puts the card ranks together with the hand totals the game prints (see [BadgeReader]).
 *  - ranks that add up to the printed total are confirmed ("verified");
 *  - ranks that disagree with the total, or could not be read, are replaced by a stand-in hand
 *    built from the total alone ("approx"): the advice still works, but the real cards are not
 *    known, so they are not added to the count;
 *  - with no readable total nothing changes.
 */
object Fusion {

    class Zone(
        /** One list per hand (the dealer has one). */
        val hands: List<List<Int>>,
        val verified: Boolean,
        val approx: Boolean,
        /** False when this row could not be read at all this frame. */
        val sure: Boolean
    )

    /** A hand shows its best total, or, with an ace, sometimes the low one. */
    fun consistent(cards: List<Int>, total: Int): Boolean {
        if (cards.isEmpty()) return false
        if (Strategy.evalHand(cards).total == total) return true
        var low = 0
        for (c in cards) low += if (c == 11) 1 else c
        return low == total
    }

    /** A printed total: [value] is the higher one of a soft hand ("7/17" has value 17, low 7). */
    class Total(val value: Int, val soft: Boolean = false, val low: Int = 0, val strong: Boolean = true)

    /** The hand as far as a total tells. A soft total keeps its ace, so the advice stays soft. */
    fun standIn(t: Total, dealer: Boolean): List<Int> {
        if (t.soft) {
            return when {
                t.low <= 1 -> listOf(11)
                t.low == 2 -> listOf(11, 11)
                else -> listOf(11, t.low - 1)
            }
        }
        return if (dealer) standInDealer(t.value) else standInPlayer(t.value)
    }

    /** The dealer's hand as far as a total tells: one card up to 11, else a plausible hand. */
    fun standInDealer(total: Int): List<Int> = when {
        total <= 11 -> listOf(total)
        total <= 20 -> listOf(10, total - 10)
        total == 21 -> listOf(10, 6, 5)
        else -> listOf(10, 10, total - 20)
    }

    /** The player's hand as far as a total tells: hard, two cards (three when 21 so it is no blackjack). */
    fun standInPlayer(total: Int): List<Int> = when {
        total <= 3 -> listOf(total)
        total <= 11 -> listOf(total - 2, 2)
        total <= 20 -> listOf(10, total - 10)
        total == 21 -> listOf(10, 6, 5)
        else -> listOf(10, 10, total - 20)
    }

    fun dealer(ranks: List<Int>, sure: Boolean, t: Total?): Zone {
        if (t == null) return Zone(if (ranks.isEmpty()) emptyList() else listOf(ranks), false, false, sure)
        if (sure && ranks.isNotEmpty() && consistent(ranks, t.value)) {
            return Zone(listOf(ranks), true, false, true)
        }
        // a total that was hard to read only confirms; it never overrules the cards
        if (!t.strong) return Zone(if (ranks.isEmpty()) emptyList() else listOf(ranks), false, false, sure)
        return Zone(listOf(standIn(t, true)), false, true, true)
    }

    /**
     * @param hands the player's hands as read (one list per pile of cards)
     * @param totals the printed totals from left to right, null where one could not be read
     */
    fun player(hands: List<List<Int>>, sure: Boolean, totals: List<Total?>): Zone {
        val plain = Zone(hands, false, false, sure)
        if (totals.isEmpty()) return plain
        val known = totals.filterNotNull()
        if (known.isEmpty()) return plain

        if (sure && hands.isNotEmpty()) {
            if (totals.size == hands.size) {
                var ok = true
                for (i in hands.indices) {
                    val t = totals[i] ?: continue
                    if (!consistent(hands[i], t.value)) ok = false
                }
                if (ok) return Zone(hands, true, false, true)
            } else if (totals.size == 1 && totals[0] != null) {
                // one printed total: the "hands" are one pile that was cut in two by mistake
                val merged = hands.flatten()
                if (consistent(merged, totals[0]!!.value)) return Zone(listOf(merged), true, false, true)
            }
        }
        if (known.size != totals.size || known.any { !it.strong }) return plain
        return Zone(known.map { standIn(it, false) }, false, true, true)
    }
}
