package az.nexgen.bjhelper

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the hand totals the game prints in a dark badge above the dealer's cards and below the
 * player's cards (white italic digits). A total is a second, independent view of the hand: it
 * confirms the card ranks, and stands in for them when a card cannot be read.
 * A hand with an ace counted as 11 shows both totals, such as "7/17".
 */
object BadgeReader {

    /**
     * @param value the total (for "7/17" the higher one); null when it could not be read at all
     * @param soft shown as "low/high"
     * @param strong every mark was recognised with high confidence
     * @param dump the marks as text when the reading was not strong, for the report
     */
    class Badge(
        val value: Int?,
        val cx: Int,
        val soft: Boolean = false,
        val strong: Boolean = false,
        val dump: List<String> = emptyList()
    ) {
        /** The lower total of a soft hand ("7" of "7/17"). */
        var low: Int = 0
    }

    class Band(val badges: List<Badge>, val blocked: Boolean)

    const val DEALER_TOP = 0.115f
    const val DEALER_BOTTOM = 0.185f
    const val PLAYER_TOP = 0.675f
    const val PLAYER_BOTTOM = 0.735f

    private const val STRONG_CONF = 0.85f
    private const val MIN_CONF = 0.5f

    private val digits: RankClassifier by lazy { RankClassifier.loadDigits() }

    private class Comp(val l: Int, val t: Int, val r: Int, val b: Int, val mask: BooleanArray) {
        val w: Int get() = r - l
        val h: Int get() = b - t
    }

    /**
     * @param px  rows [yOff, yOff + rows) of the screen, full width
     * @param cover our own window; a total under it cannot be seen
     */
    fun readBand(
        px: IntArray, w: Int, rows: Int, yOff: Int, fullH: Int,
        topF: Float, bottomF: Float, cover: CardFinder.Box? = null
    ): Band {
        val y0 = max(yOff, (topF * fullH).toInt())
        val y1 = min(yOff + rows, (bottomF * fullH).toInt())
        if (y1 - y0 < 8) return Band(emptyList(), false)
        val bh = y1 - y0

        var blocked = false
        var pad: CardFinder.Box? = null
        if (cover != null) {
            pad = CardFinder.Box(cover.l - 6, cover.t - 6, cover.r + 6, cover.b + 6)
            if (pad.b > y0 && pad.t < y1) blocked = true
        }

        val white = BooleanArray(w * bh)
        for (y in 0 until bh) {
            val base = (y0 - yOff + y) * w
            for (x in 0 until w) {
                val p = px[base + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val mn = min(r, min(g, b))
                val mx = max(r, max(g, b))
                white[y * w + x] = mn >= 185 && mx - mn <= 40
            }
        }

        val minH = (0.013f * fullH).toInt()
        val maxH = (0.026f * fullH).toInt()
        val minW = max(4, (0.007f * w).toInt())
        val maxW = (0.05f * w).toInt()
        val seen = BooleanArray(w * bh)
        val comps = ArrayList<Comp>()
        val stack = IntArray(w * bh)
        for (start in white.indices) {
            if (!white[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            val members = ArrayList<Int>()
            var l = w
            var r = -1
            var t = bh
            var b = -1
            while (sp > 0) {
                val cur = stack[--sp]
                members.add(cur)
                val cx = cur % w
                val cy = cur / w
                if (cx < l) l = cx
                if (cx > r) r = cx
                if (cy < t) t = cy
                if (cy > b) b = cy
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= bh) continue
                    val ni = ny * w + nx
                    if (white[ni] && !seen[ni]) {
                        seen[ni] = true
                        stack[sp++] = ni
                    }
                }
            }
            val cw = r - l + 1
            val ch = b - t + 1
            if (ch < minH || ch > maxH || cw < minW || cw > maxW) continue
            val box = CardFinder.Box(l, y0 + t, r + 1, y0 + b + 1)
            if (pad != null && Placer.overlap(pad, box) > 0) continue
            val m = BooleanArray(cw * ch)
            for (i in members) m[(i / w - t) * cw + (i % w - l)] = true
            comps.add(Comp(l, y0 + t, r + 1, y0 + b + 1, m))
        }
        if (comps.isEmpty()) return Band(emptyList(), blocked)

        comps.sortBy { it.l }
        val gapMax = (0.03f * w).toInt()
        val badges = ArrayList<Badge>()
        var cluster = ArrayList<Comp>()
        fun flush() {
            if (cluster.isEmpty()) return
            badges.add(toBadge(cluster))
            cluster = ArrayList()
        }
        for (c in comps) {
            val prev = cluster.lastOrNull()
            if (prev != null && (c.l - prev.r > gapMax || abs(c.t - prev.t) > 0.4f * prev.h)) flush()
            cluster.add(c)
        }
        flush()
        return Band(badges, blocked)
    }

    private fun toBadge(cluster: List<Comp>): Badge {
        val cx = (cluster.first().l + cluster.last().r) / 2
        if (cluster.size > 5) return Badge(null, cx)
        val sb = StringBuilder()
        var minConf = 1f
        for (c in cluster) {
            // two digits that touch form one wide piece
            if (c.w > 0.9f * c.h) return Badge(null, cx)
            val res = digits.classify(c.mask, c.w, c.h)
            if (res.confidence < MIN_CONF) return Badge(null, cx, dump = dump(cluster))
            if (res.confidence < minConf) minConf = res.confidence
            sb.append(res.label)
        }
        val text = sb.toString()
        val strong = minConf >= STRONG_CONF
        val parsed = parse(text) ?: return Badge(null, cx, dump = dump(cluster))
        val badge = Badge(parsed.first, cx, parsed.second != 0, strong, if (strong) emptyList() else dump(cluster))
        badge.low = parsed.second
        return badge
    }

    /** "17" -> (17, 0); "7/17" -> (17, 7); anything else null. Totals are 2..30, soft ones 11..21. */
    fun parse(text: String): Pair<Int, Int>? {
        val slash = text.indexOf('/')
        if (slash < 0) {
            if (text.length !in 1..2) return null
            val v = text.toIntOrNull() ?: return null
            return if (v in 2..30) Pair(v, 0) else null
        }
        if (slash == 0 || slash == text.length - 1 || text.indexOf('/', slash + 1) >= 0) return null
        val lo = text.substring(0, slash).toIntOrNull() ?: return null
        val hi = text.substring(slash + 1).toIntOrNull() ?: return null
        if (hi != lo + 10 || hi !in 11..21) return null
        return Pair(hi, lo)
    }

    /** Half-size picture of the marks, so a misread can be studied later. */
    private fun dump(cluster: List<Comp>): List<String> {
        val rows = ArrayList<String>()
        for (c in cluster.take(2)) {
            var y = 0
            while (y < c.h && rows.size < 30) {
                val sb = StringBuilder()
                var x = 0
                while (x < c.w) {
                    var any = false
                    for (dy in 0..1) for (dx in 0..1) {
                        if (y + dy < c.h && x + dx < c.w && c.mask[(y + dy) * c.w + x + dx]) any = true
                    }
                    sb.append(if (any) '#' else '.')
                    x += 2
                }
                rows.add(sb.toString())
                y += 2
            }
            rows.add("-")
        }
        return rows
    }
}
