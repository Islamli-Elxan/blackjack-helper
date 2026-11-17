package az.nexgen.bjhelper

import kotlin.math.max
import kotlin.math.min

/**
 * Finds the game's action buttons by colour: Stand is red, Double is blue, Hit is green. The set
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

    private fun isRed(r: Int, g: Int, b: Int) = r > 130 && g < 85 && b < 85 && r > g + 70
    private fun isBlue(r: Int, g: Int, b: Int) = b > 140 && r < 70 && g in 55..165 && b > r + 90
    private fun isGreen(r: Int, g: Int, b: Int) = g > 140 && r in 55..160 && b < 90 && g > r + 40

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
        for (kind in Kind.values()) {
            val on = BooleanArray(bw * bh)
            for (by in 0 until bh) for (bx in 0 until bw) {
                var n = 0
                for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) {
                    val p = px[(by * BLOCK + dy) * w + bx * BLOCK + dx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val hit = when (kind) {
                        Kind.STAND -> isRed(r, g, b)
                        Kind.DOUBLE -> isBlue(r, g, b)
                        Kind.HIT -> isGreen(r, g, b)
                    }
                    if (hit) n++
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
