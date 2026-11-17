package az.nexgen.bjhelper

import java.util.ArrayDeque

/**
 * Recognises the shuffling animation: a fan of card backs (pale pink on the London table, pale
 * blue on Las Vegas) appears in the middle of the table, wider than a single card. One card back
 * flying in during a deal covers as much of the area but is only a card wide, so the width is
 * what tells them apart.
 */
object ShuffleDetector {

    class Zone(val x: Int, val y: Int, val w: Int, val h: Int)

    /** The middle of the table below the dealer's cards. */
    fun zone(w: Int, h: Int) = Zone(w / 5, h * 47 / 100, w * 3 / 5, h * 25 / 100)

    class Result(val share: Float, val extent: Int, private val zoneW: Int) {
        /** Backs cover enough of the zone and spread over most of its width. */
        val flurry: Boolean get() = share >= MIN_SHARE && extent >= zoneW * 63 / 100
    }

    const val MIN_SHARE = 0.09f
    /** Worth a line in the report when it is not a full match. */
    const val NEAR_SHARE = 0.06f

    /** @param px the zone's pixels, row by row, [zw] x [zh] */
    fun measure(px: IntArray, zw: Int, zh: Int): Result {
        val cols = IntArray(zw / 2 + 1)
        var hits = 0
        var total = 0
        var y = 0
        while (y < zh) {
            val base = y * zw
            var x = 0
            while (x < zw) {
                val p = px[base + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val mx = maxOf(r, g, b)
                val d = mx - minOf(r, g, b)
                // a pale tint: some colour, but not the strong red of a heart or the cream of a card
                if (d * 5 >= mx && d * 100 <= 62 * mx) {
                    val pink = r >= g + 40 && r >= b + 25 && mx >= 170
                    val blue = b >= r + 40 && mx >= 150
                    if (pink || blue) {
                        hits++
                        cols[x / 2]++
                    }
                }
                total++
                x += 2
            }
            y += 2
        }
        val need = maxOf((zh / 2) / 20, 1)
        var first = -1
        var last = -1
        for (i in cols.indices) {
            if (cols[i] >= need) {
                if (first < 0) first = i
                last = i
            }
        }
        val extent = if (first < 0) 0 else (last - first + 1) * 2
        return Result(if (total == 0) 0f else hits.toFloat() / total, extent, zw)
    }
}

/** Turns a stream of per-frame answers into one "the cards are being shuffled" event per animation. */
class ShuffleWatch {
    private val hits = ArrayDeque<Long>()
    private var latched = false
    private var lastHit = 0L

    /** @return true once, when the animation has been seen on enough frames */
    fun onFrame(now: Long, flurry: Boolean): Boolean {
        if (flurry) {
            hits.addLast(now)
            lastHit = now
        }
        while (hits.isNotEmpty() && now - hits.first > WINDOW_MS) hits.removeFirst()
        if (latched) {
            if (now - lastHit > QUIET_MS) latched = false
            return false
        }
        if (hits.size >= NEEDED) {
            latched = true
            hits.clear()
            return true
        }
        return false
    }

    companion object {
        const val NEEDED = 4
        const val WINDOW_MS = 2500L
        /** The animation is over when no frame has shown it for this long. */
        const val QUIET_MS = 4000L
    }
}
