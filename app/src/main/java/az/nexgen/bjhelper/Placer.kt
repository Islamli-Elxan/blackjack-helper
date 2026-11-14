package az.nexgen.bjhelper

import kotlin.math.max
import kotlin.math.min

/**
 * Chooses where the floating window sits so it stays off the cards.
 * Cards live in the middle of the screen; the strip above the dealer and the strip with the
 * game buttons are the quiet places. Pure Kotlin so it can be tested on the JVM.
 */
object Placer {

    /** Top of the area where cards can appear, as a fraction of the screen height. */
    const val CARD_TOP = 0.15f
    /** Bottom of the card area (the game buttons start below it). */
    const val CARD_BOTTOM = 0.72f
    /** Cards are never in the outer side margins. */
    const val SIDE = 0.12f

    class Pos(val x: Int, val y: Int)

    fun overlap(a: CardFinder.Box, b: CardFinder.Box): Long {
        val w = min(a.r, b.r) - max(a.l, b.l)
        val h = min(a.b, b.b) - max(a.t, b.t)
        return if (w > 0 && h > 0) w.toLong() * h else 0L
    }

    /** The part of the screen where a card may be, whether or not one is there right now. */
    fun cardZone(sw: Int, sh: Int): CardFinder.Box =
        CardFinder.Box((SIDE * sw).toInt(), (CARD_TOP * sh).toInt(), ((1f - SIDE) * sw).toInt(), (CARD_BOTTOM * sh).toInt())

    /** True if a window at this rectangle could cover a card corner. */
    fun covers(win: CardFinder.Box, sw: Int, sh: Int, blobs: List<CardFinder.Box>): Boolean {
        if (overlap(win, cardZone(sw, sh)) > 0) return true
        for (b in blobs) if (overlap(win, b) > 0) return true
        return false
    }

    /**
     * Best top-left corner for a window of ow x oh pixels. Candidates are scanned along both
     * side margins and the top strip; the one with the least overlap with known cards wins,
     * ties going to the highest position.
     */
    fun choose(sw: Int, sh: Int, ow: Int, oh: Int, blobs: List<CardFinder.Box>, topInset: Int): Pos {
        val margin = (0.02f * sw).toInt()
        val xs = listOf(margin, sw - ow - margin, (sw - ow) / 2).map { max(0, min(it, sw - ow)) }
        val zone = cardZone(sw, sh)
        var best = Pos(xs[0], topInset)
        var bestScore = Long.MAX_VALUE
        val step = max(1, (0.015f * sh).toInt())
        var y = topInset
        while (y + oh <= sh - margin) {
            for (x in xs) {
                val win = CardFinder.Box(x, y, x + ow, y + oh)
                var score = 0L
                for (b in blobs) score += overlap(win, padded(b, (0.01f * sw).toInt())) * 4L
                score += overlap(win, zone) / 2L
                if (y + oh > (0.74f * sh).toInt()) score += 3_000_000L // do not sit on the game buttons
                score += y / 10L // mild preference for the top
                if (score < bestScore) {
                    bestScore = score
                    best = Pos(x, y)
                }
            }
            y += step
        }
        return best
    }

    private fun padded(b: CardFinder.Box, p: Int): CardFinder.Box =
        CardFinder.Box(b.l - p, b.t - p, b.r + p, b.b + p)
}
