package az.nexgen.bjhelper

import java.util.Locale

/**
 * Keeps a detailed record of every round (cards, advice at each step, result) and the win/lose
 * counters. Pure Kotlin: the caller supplies the clock and does the file work.
 */
class RoundLog(
    private val settleMs: Long = 800L,
    private val formatTime: (Long) -> String = { it.toString() }
) {

    class Stats(var win: Int = 0, var lose: Int = 0, var push: Int = 0, var blackjack: Int = 0, var rounds: Int = 0) {
        val hands: Int get() = win + lose + push

        /** Short text for the window, such as "23W 17L". */
        fun short(): String = "" + win + "W " + lose + "L"

        fun winRate(): Double = if (win + lose == 0) 0.0 else 100.0 * win / (win + lose)
    }

    class Event(
        val t: Long,
        val dealer: List<Int>,
        val hands: List<List<Int>>,
        val rc: Int,
        val tc: Double,
        val advice: List<String>,
        val layout: String
    )

    class Missed(val zone: Int, val label: String, val conf: Float, val w: Int, val h: Int, val mask: List<String>)

    class Round(val id: Int, val epoch: Long, val rc0: Int, val tc0: Double) {
        val events = ArrayList<Event>()
        val notes = ArrayList<String>()
        val missed = ArrayList<Missed>()
        var results: List<Outcome.Kind>? = null
        var finalDealer: List<Int> = emptyList()
        var finalHands: List<List<Int>> = emptyList()
        var durationMs = 0L
        var dealerUp = 0
    }

    val stats = Stats()
    private var nextId = 1
    private var round: Round? = null
    private var t0 = 0L
    private var lastDealer: List<Int> = emptyList()
    private var lastHands: List<List<Int>> = emptyList()
    private var lastAdvice: List<String> = emptyList()
    private var lastNow = 0L
    private var decidedKey = ""
    private var decidedSince = 0L
    private var counted = false

    fun start(nextId: Int, prior: Stats) {
        this.nextId = nextId
        stats.win = prior.win
        stats.lose = prior.lose
        stats.push = prior.push
        stats.blackjack = prior.blackjack
        stats.rounds = prior.rounds
    }

    fun active(): Boolean = round != null

    /** Result of each of the player's hands, or null for a hand that is still going. */
    fun outcomes(dealer: List<Int>, hands: List<List<Int>>): List<Outcome.Result?> =
        hands.map { Outcome.decide(it, dealer) }

    /**
     * Call with the accepted state after every change. Returns the finished round when the table
     * has just been cleared, otherwise null.
     */
    fun update(
        now: Long, epoch: Long, dealer: List<Int>, hands: List<List<Int>>,
        rc: Int, tc: Double, advice: List<String>, layout: String, decks: Int, counting: Boolean
    ): Round? {
        lastNow = now
        val empty = dealer.isEmpty() && hands.isEmpty()
        if (empty) return finish(now)

        var r = round
        if (r == null) {
            r = Round(nextId++, epoch, rc, tc)
            round = r
            t0 = now
            counted = false
            decidedKey = ""
            lastDealer = emptyList()
            lastHands = emptyList()
            lastAdvice = emptyList()
        }
        val changed = dealer != lastDealer || hands != lastHands || advice != lastAdvice
        if (changed && r.events.size < MAX_EVENTS) {
            r.events.add(Event(now - t0, dealer, hands, rc, tc, advice, layout))
        }
        if (r.dealerUp == 0 && dealer.isNotEmpty()) r.dealerUp = dealer[0]
        lastDealer = dealer
        lastHands = hands
        lastAdvice = advice

        // count the result once the dealer is done and nothing has changed for a moment
        if (!counted && hands.isNotEmpty()) {
            val res = outcomes(dealer, hands)
            if (res.all { it != null }) {
                val key = dealer.toString() + hands.toString()
                if (key != decidedKey) {
                    decidedKey = key
                    decidedSince = now
                } else if (now - decidedSince >= settleMs) {
                    counted = true
                    val kinds = res.map { it!!.kind }
                    r.results = kinds
                    for (k in kinds) addToStats(k)
                    stats.rounds++
                }
            } else {
                decidedKey = ""
            }
        }
        return null
    }

    /** What the window should show as the result of the round, or null. */
    fun countedKinds(): List<Outcome.Kind>? = if (counted) round?.results else null

    fun note(msg: String) {
        val r = round ?: return
        if (r.notes.size < MAX_NOTES) r.notes.add((lastNow - t0).toString() + "ms " + msg)
    }

    fun missed(m: Missed) {
        val r = round ?: return
        if (r.missed.size < MAX_MISSED) r.missed.add(m)
    }

    private fun finish(now: Long): Round? {
        val r = round ?: return null
        round = null
        r.finalDealer = lastDealer
        r.finalHands = lastHands
        r.durationMs = now - t0
        return r
    }

    private fun addToStats(k: Outcome.Kind) {
        when (k) {
            Outcome.Kind.WIN, Outcome.Kind.DEALER_BUST -> stats.win++
            Outcome.Kind.BLACKJACK -> {
                stats.win++
                stats.blackjack++
            }
            Outcome.Kind.LOSE, Outcome.Kind.BUST -> stats.lose++
            Outcome.Kind.PUSH -> stats.push++
        }
    }

    fun toJson(r: Round, version: String, decks: Int, counting: Boolean): String {
        val sb = StringBuilder()
        sb.append("{\"id\":").append(r.id)
        sb.append(",\"v\":").append(str(version))
        sb.append(",\"time\":").append(str(formatTime(r.epoch)))
        sb.append(",\"durMs\":").append(r.durationMs)
        sb.append(",\"decks\":").append(decks)
        sb.append(",\"count\":").append(counting)
        sb.append(",\"rc0\":").append(r.rc0)
        sb.append(",\"tc0\":").append(num(r.tc0))
        sb.append(",\"dealerUp\":").append(r.dealerUp)
        sb.append(",\"events\":[")
        for ((i, e) in r.events.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append("{\"t\":").append(e.t)
            sb.append(",\"d\":").append(e.dealer.toString().replace(" ", ""))
            sb.append(",\"h\":").append(e.hands.toString().replace(" ", ""))
            sb.append(",\"rc\":").append(e.rc)
            sb.append(",\"tc\":").append(num(e.tc))
            sb.append(",\"adv\":[")
            for ((j, a) in e.advice.withIndex()) {
                if (j > 0) sb.append(',')
                sb.append(str(a))
            }
            sb.append("],\"pos\":").append(str(e.layout)).append('}')
        }
        sb.append("],\"final\":{\"dealer\":").append(r.finalDealer.toString().replace(" ", ""))
        sb.append(",\"hands\":").append(r.finalHands.toString().replace(" ", ""))
        sb.append(",\"dealerTotal\":").append(if (r.finalDealer.isEmpty()) 0 else Strategy.evalHand(r.finalDealer).total)
        sb.append(",\"totals\":[")
        for ((i, h) in r.finalHands.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append(Strategy.evalHand(h).total)
        }
        sb.append("]},\"results\":")
        val kinds = r.results
        if (kinds == null) {
            sb.append("null")
        } else {
            sb.append('[')
            for ((i, k) in kinds.withIndex()) {
                if (i > 0) sb.append(',')
                sb.append(str(k.name))
            }
            sb.append(']')
        }
        sb.append(",\"notes\":[")
        for ((i, n) in r.notes.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append(str(n))
        }
        sb.append("],\"missed\":[")
        for ((i, m) in r.missed.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append("{\"zone\":").append(m.zone).append(",\"label\":").append(str(m.label))
            sb.append(",\"conf\":").append(num(m.conf.toDouble()))
            sb.append(",\"w\":").append(m.w).append(",\"h\":").append(m.h).append(",\"mask\":[")
            for ((j, row) in m.mask.withIndex()) {
                if (j > 0) sb.append(',')
                sb.append(str(row))
            }
            sb.append("]}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun num(d: Double): String = String.format(Locale.US, "%.2f", d)

    private fun str(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    companion object {
        const val MAX_EVENTS = 60
        const val MAX_NOTES = 8
        const val MAX_MISSED = 4
    }
}
