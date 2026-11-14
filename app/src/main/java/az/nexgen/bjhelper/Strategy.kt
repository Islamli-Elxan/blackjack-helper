package az.nexgen.bjhelper

/**
 * Basic strategy (multi-deck, double after split allowed, no surrender) plus a few Hi-Lo index plays.
 * Ranks: 2..9, 10 for every ten-value card, 11 for the ace.
 * Codes: H = hit, S = stand, D = double (hit if not allowed), Ds = double (stand if not allowed), P = split.
 */
object Strategy {

    fun hilo(r: Int): Int = when {
        r in 2..6 -> 1
        r in 7..9 -> 0
        else -> -1
    }

    fun hardCode(t: Int, u: Int, h17: Boolean): String {
        if (t <= 8) return "H"
        if (t == 9) return if (u in 3..6) "D" else "H"
        if (t == 10) return if (u <= 9) "D" else "H"
        if (t == 11) return if (u <= 10) "D" else (if (h17) "D" else "H")
        if (t == 12) return if (u in 4..6) "S" else "H"
        if (t <= 16) return if (u <= 6) "S" else "H"
        return "S"
    }

    fun softCode(t: Int, u: Int, h17: Boolean): String {
        if (t <= 14) return if (u == 5 || u == 6) "D" else "H"
        if (t <= 16) return if (u in 4..6) "D" else "H"
        if (t == 17) return if (u in 3..6) "D" else "H"
        if (t == 18) {
            if (u in 3..6) return "Ds"
            if (u == 2) return if (h17) "Ds" else "S"
            if (u == 7 || u == 8) return "S"
            return "H"
        }
        if (t == 19) return if (h17 && u == 6) "Ds" else "S"
        return "S"
    }

    fun pairCode(r: Int, u: Int, h17: Boolean): String {
        if (r == 11 || r == 8) return "P"
        if (r == 10) return "S"
        if (r == 9) return if (u == 7 || u >= 10) "S" else "P"
        if (r == 7) return if (u <= 7) "P" else "H"
        if (r == 6) return if (u <= 6) "P" else "H"
        if (r == 5) return hardCode(10, u, h17)
        if (r == 4) return if (u == 5 || u == 6) "P" else "H"
        return if (u <= 7) "P" else "H"
    }

    data class Eval(val total: Int, val soft: Boolean, val bj: Boolean, val bust: Boolean)

    fun evalHand(cards: List<Int>): Eval {
        var total = 0
        var aces = 0
        for (c in cards) {
            total += c
            if (c == 11) aces++
        }
        while (total > 21 && aces > 0) {
            total -= 10
            aces--
        }
        return Eval(total, aces > 0, cards.size == 2 && total == 21, total > 21)
    }

    private class Idx(val t: Int, val u: Int, val act: String, val test: (Double) -> Boolean)

    // Hi-Lo index plays for hard hands (subset of the Illustrious 18)
    private val indexPlays = listOf(
        Idx(16, 10, "S") { tc -> tc >= 0.0 },
        Idx(15, 10, "S") { tc -> tc >= 4.0 },
        Idx(16, 9, "S") { tc -> tc >= 5.0 },
        Idx(12, 3, "S") { tc -> tc >= 2.0 },
        Idx(12, 2, "S") { tc -> tc >= 3.0 },
        Idx(10, 10, "D") { tc -> tc >= 4.0 },
        Idx(10, 11, "D") { tc -> tc >= 4.0 },
        Idx(11, 11, "D") { tc -> tc >= 1.0 },
        Idx(9, 2, "D") { tc -> tc >= 1.0 },
        Idx(9, 7, "D") { tc -> tc >= 3.0 },
        Idx(12, 4, "H") { tc -> tc < 0.0 },
        Idx(13, 2, "H") { tc -> tc <= -1.0 }
    )

    data class Advice(
        val act: String,            // H S D Ds P BUST BJ
        val kind: String = "",      // hard soft pair
        val total: Int = 0,
        val twentyOne: Boolean = false,
        val fallback: String? = null,   // what to do when double is not available
        val idxBase: String? = null     // the table's answer when a count index changed it
    )

    fun advise(hand: List<Int>, up: Int, h17: Boolean, count: Boolean, tc: Double): Advice {
        val e = evalHand(hand)
        if (e.bust) return Advice("BUST", total = e.total)
        if (e.bj) return Advice("BJ", total = 21)
        if (e.total == 21) return Advice("S", "hard", 21, twentyOne = true)

        val n = hand.size
        val kind: String
        var code: String
        if (n == 2 && hand[0] == hand[1] && hand[0] != 5) {
            kind = "pair"
            code = pairCode(hand[0], up, h17)
        } else if (e.soft) {
            kind = "soft"
            code = softCode(e.total, up, h17)
        } else {
            kind = "hard"
            code = hardCode(e.total, up, h17)
        }

        val base = code
        var idxBase: String? = null
        if (count && kind == "hard") {
            for (x in indexPlays) {
                if (x.t == e.total && x.u == up && x.test(tc)) {
                    if (x.act != base) {
                        code = x.act
                        idxBase = base
                    }
                    break
                }
            }
        }

        var fb: String? = null
        if (n > 2) {
            if (code == "D") {
                code = "H"
            } else if (code == "Ds") {
                code = "S"
            }
        } else if (code == "D") {
            fb = "H"
        } else if (code == "Ds") {
            fb = "S"
        }
        return Advice(code, kind, e.total, false, fb, idxBase)
    }
}
