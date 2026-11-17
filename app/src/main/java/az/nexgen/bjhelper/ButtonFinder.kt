package az.nexgen.bjhelper

import kotlin.math.max
import kotlin.math.min

/**
 * Finds the game's action buttons by colour: Stand is red, Double is blue, Hit is green (the table may also have an orange Split). The set
 * of buttons changes (Double is gone after the first card), so what is on screen is looked up
 * every time and the centre of the button is returned as a screen position.
 */
object ButtonFinder {

    enum class Kind { STAND, DOUBLE, HIT }

    class Button(val kind: Kind, val box: CardFinder.Box) {
        val cx: Int get() = (box.l + box.r) / 2
        val cy: Int get() = (box.t + box.b) / 2
    }

    /** Where the buttons live, as fractions of the screen height. */
    const val TOP = 0.82f
    const val BOTTOM = 0.97f

    private const val BLOCK = 4

    /**
     * Which button colour a pixel has, by hue: the tables differ in brightness (dark red, olive
     * green), but never in hue. Felt is a pure green (hue 120) and stays out of the button's range.
     * @return 0 stand (red), 1 double (blue), 2 hit (green), -1 none
     */
    private fun kindOf(r: Int, g: Int, b: Int): Int {
        val mx = max(r, max(g, b))
        val d = mx - min(r, min(g, b))
        if (mx < 80 || d * 10 < 6 * mx) return -1
        var h = when {
            mx == r -> 60f * (g - b).toFloat() / d
            mx == g -> 60f * ((b - r).toFloat() / d + 2f)
            else -> 60f * ((r - g).toFloat() / d + 4f)
        }
        if (h < 0f) h += 360f
        if ((h >= 348f || h <= 12f) && mx >= 95 && d * 10 >= 7 * mx) return 0
        if (h in 70f..108f) return 2
        if (h in 205f..230f && mx >= 100) return 1
        return -1
    }

    /**
     * @param px rows [yOff, yOff + rows) of the screen, full width
     * @return the buttons that are fully visible, left to right
     */
    fun find(px: IntArray, w: Int, rows: Int, yOff: Int, fullH: Int): List<Button> {
        val out = ArrayList<Button>()
        val bw = w / BLOCK
        val bh = rows / BLOCK
        if (bw == 0 || bh == 0) return out
        val minW = (0.17f * w).toInt()
        val maxW = (0.30f * w).toInt()
        val minH = (0.02f * fullH).toInt()
        val cls = ByteArray(w * rows)
        for (i in cls.indices) {
            val p = px[i]
            cls[i] = kindOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF).toByte()
        }
        for (kind in Kind.values()) {
            val want = when (kind) {
                Kind.STAND -> 0
                Kind.DOUBLE -> 1
                Kind.HIT -> 2
            }
            val on = BooleanArray(bw * bh)
            for (by in 0 until bh) for (bx in 0 until bw) {
                var n = 0
                for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) {
                    if (cls[(by * BLOCK + dy) * w + bx * BLOCK + dx].toInt() == want) n++
                }
                on[by * bw + bx] = n >= 5
            }
            // the icon on a button splits its colour in two: bridge short gaps along each row
            val gap = 12
            for (by in 0 until bh) {
                var last = -1000
                for (bx in 0 until bw) {
                    if (on[by * bw + bx]) {
                        if (bx - last in 2..gap) for (k in last + 1 until bx) on[by * bw + k] = true
                        last = bx
                    }
                }
            }
            val seen = BooleanArray(bw * bh)
            val stack = IntArray(bw * bh)
            for (start in on.indices) {
                if (!on[start] || seen[start]) continue
                var sp = 0
                stack[sp++] = start
                seen[start] = true
                var l = bw
                var r = -1
                var t = bh
                var b = -1
                var count = 0
                while (sp > 0) {
                    val cur = stack[--sp]
                    count++
                    val cx = cur % bw
                    val cy = cur / bw
                    l = min(l, cx); r = max(r, cx); t = min(t, cy); b = max(b, cy)
                    if (cx > 0 && on[cur - 1] && !seen[cur - 1]) { seen[cur - 1] = true; stack[sp++] = cur - 1 }
                    if (cx < bw - 1 && on[cur + 1] && !seen[cur + 1]) { seen[cur + 1] = true; stack[sp++] = cur + 1 }
                    if (cy > 0 && on[cur - bw] && !seen[cur - bw]) { seen[cur - bw] = true; stack[sp++] = cur - bw }
                    if (cy < bh - 1 && on[cur + bw] && !seen[cur + bw]) { seen[cur + bw] = true; stack[sp++] = cur + bw }
                }
                val boxW = (r - l + 1) * BLOCK
                val boxH = (b - t + 1) * BLOCK
                if (boxW < minW || boxW > maxW || boxH < minH || boxH > boxW) continue
                // a real button is mostly filled; scattered patches of the colour are not
                if (count < 0.6f * (r - l + 1) * (b - t + 1)) continue
                out.add(Button(kind, CardFinder.Box(l * BLOCK, yOff + t * BLOCK, (r + 1) * BLOCK, yOff + (b + 1) * BLOCK)))
            }
        }
        return out.sortedBy { it.box.l }
    }
}
