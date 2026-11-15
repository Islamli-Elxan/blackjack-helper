package az.nexgen.bjhelper

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Bet size from the balance and the Hi-Lo true count.
 *
 * Idea: with a true count of +1 the player's edge is about zero, and every extra count point
 * adds roughly half a percent. The Kelly bet is edge divided by the variance of one hand (about
 * 1.3). Only a fraction of Kelly is used, chosen by the risk level, because the count estimate is
 * rough. When the count is low or counting is off, a small flat base bet is used.
 */
object Betting {

    /** Share of the full Kelly bet for each risk level: careful, normal, bold. */
    val KELLY = doubleArrayOf(0.25, 0.5, 0.75)
    val RISK_NAMES = arrayOf("Ehtiyatlı", "Orta", "Cəsarətli")

    private const val EDGE_PER_COUNT = 0.005
    private const val VARIANCE = 1.3
    /** The flat bet is this share of the balance, scaled by the risk level. */
    private const val BASE_SHARE = 0.004

    class Plan(val amount: Long, val raised: Boolean)

    fun recommend(
        balance: Long,
        tc: Double,
        counting: Boolean,
        risk: Int,
        minBet: Long = 0L,
        maxBet: Long = 0L
    ): Plan {
        if (balance <= 0L) return Plan(0L, false)
        val k = KELLY[risk.coerceIn(0, KELLY.size - 1)]
        val base = balance * BASE_SHARE * k
        var raw = base
        var raised = false
        if (counting) {
            val edge = (tc - 1.0) * EDGE_PER_COUNT
            if (edge > 0) {
                val kelly = k * edge / VARIANCE * balance
                if (kelly > base) {
                    raw = kelly
                    raised = true
                }
            }
        }
        var bet = nice(raw)
        if (minBet > 0L) bet = max(bet, minBet)
        if (maxBet > 0L) bet = min(bet, maxBet)
        bet = min(bet, balance)
        return Plan(max(bet, 1L), raised)
    }

    /** Rounds to a bet that is easy to build from chips: 1.0, 1.5, 2.0 ... times a power of ten. */
    fun nice(v: Double): Long {
        if (v < 10.0) return max(1L, Math.round(v))
        val k = floor(log10(v)).toInt()
        val step = 10.0.pow(k) / 2.0
        return (Math.round(v / step) * step).toLong()
    }

    fun format(v: Long): String {
        if (v >= 10_000_000L) return (v / 1_000_000L).toString() + "M"
        if (v >= 1_000_000L) return trim(v / 1_000_000.0) + "M"
        if (v >= 10_000L) return (v / 1000L).toString() + "K"
        if (v >= 1000L) return trim(v / 1000.0) + "K"
        return v.toString()
    }

    private fun trim(d: Double): String {
        val s = String.format(java.util.Locale.US, "%.1f", d)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
