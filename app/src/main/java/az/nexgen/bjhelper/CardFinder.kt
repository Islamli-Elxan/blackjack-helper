package az.nexgen.bjhelper

import kotlin.math.max
import kotlin.math.min

/**
 * Finds the corner rank of every face-up card by colour alone, with no text recognition:
 *  1. cream card faces are found as large blobs (the white lines on a card back are too thin to survive),
 *  2. dark or red marks in the top band of each blob are grouped into glyphs,
 *  3. only the top row of glyphs is kept, which drops suits, pips and the rotated bottom index.
 * Pure Kotlin so it can be tested on the JVM against a real screenshot.
 */
object CardFinder {

    class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
        val w: Int get() = r - l
        val h: Int get() = b - t
    }

    /** zone 0 = dealer, zone 1 = player */
    class Glyph(val box: Box, val zone: Int)

    class Result(val blobs: List<Box>, val glyphs: List<Glyph>)

    const val DEALER_LIMIT = 0.41f

    fun isCream(p: Int): Boolean {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        return mn >= 200 && mx - mn <= 45
    }

    const val INK_LIMIT = 190

    /**
     * Dark or strongly red. A lower [limit] ignores shadows on the card, a higher one catches grey marks.
     * A dark pixel with the green or blue of the table is not ink: it is the table showing past a
     * card's rounded corner or edge, and it must not stick to the mark beside it.
     */
    fun isInk(p: Int, limit: Int = INK_LIMIT): Boolean {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val mx = max(r, max(g, b))
        val table = max(g - r, b - r) > 25
        return (mx < limit && !table) || r - max(g, b) > 60
    }

    /**
     * @param px ARGB pixels of the table area, row by row
     * @param w width of the area, roiH its height, fullH the height of the whole screen,
     * yOff the screen row where the area starts
     */
    fun find(px: IntArray, w: Int, roiH: Int, fullH: Int, yOff: Int, inkLimit: Int = INK_LIMIT): Result {
        val blobs = extendUp(findBlobs(px, w, roiH, fullH, yOff), px, w, roiH, fullH, yOff)
        val comps = ArrayList<Box>()
        for (blob in blobs) collectGlyphs(px, w, roiH, fullH, yOff, blob, comps, inkLimit)

        val glyphs = ArrayList<Glyph>()
        for (zone in 0..1) {
            val zc = comps.filter { zoneOf(it, fullH) == zone }
            if (zc.isEmpty()) continue
            var best = zc[0]
            for (c in zc) if (c.t < best.t) best = c
            val tol = max(12, best.h / 2)
            val row = zc.filter { it.t - best.t <= tol }.sortedBy { it.l }

            // the 1 and the 0 of a ten are separate marks
            val gap = (0.009f * w).toInt()
            val merged = ArrayList<Box>()
            for (c in row) {
                val last = if (merged.isEmpty()) null else merged[merged.size - 1]
                if (last != null && c.l - last.r <= gap) {
                    merged[merged.size - 1] =
                        Box(min(last.l, c.l), min(last.t, c.t), max(last.r, c.r), max(last.b, c.b))
                } else {
                    merged.add(c)
                }
            }

            // two card indexes are never closer than this; closer marks are pip fragments
            val spacing = (0.055f * w).toInt()
            var lastLeft = 0
            var any = false
            for (c in merged) {
                if (any && c.l - lastLeft < spacing) continue
                glyphs.add(Glyph(c, zone))
                lastLeft = c.l
                any = true
            }
        }
        return Result(blobs, glyphs)
    }

    /**
     * A dark or red rank with its suit underneath can cut the narrow visible strip of a card, so the
     * cream blob starts below the rank. Walk up from the blob's top while the rows still look like
     * card (some cream), and take the highest row that is almost all cream (the card's top margin).
     */
    private fun extendUp(blobs: List<Box>, px: IntArray, w: Int, roiH: Int, fullH: Int, yOff: Int): List<Box> {
        val bs = max(3, Math.round(w / 270f))
        val out = ArrayList<Box>()
        for (b in blobs) {
            val x0 = max(b.l + bs, 0)
            val x1 = min(b.r - bs, w)
            if (x1 - x0 < 4) {
                out.add(b)
                continue
            }
            val lo = max(yOff, b.t - (0.06f * fullH).toInt())
            var newTop = b.t
            var y = b.t - 1
            while (y >= lo) {
                val ry = y - yOff
                if (ry < 0 || ry >= roiH) break
                var cnt = 0
                val row = ry * w
                for (x in x0 until x1) {
                    if (isCream(px[row + x])) cnt++
                }
                val fr = cnt.toFloat() / (x1 - x0)
                if (fr < 0.12f) break
                if (fr >= 0.7f) newTop = y
                y--
            }
            out.add(if (newTop == b.t) b else Box(b.l, newTop, b.r, b.b))
        }
        return out
    }

    private fun zoneOf(c: Box, fullH: Int): Int =
        if ((c.t + c.b) / 2f < DEALER_LIMIT * fullH) 0 else 1

    private fun findBlobs(px: IntArray, w: Int, roiH: Int, fullH: Int, yOff: Int): List<Box> {
        val bs = max(3, Math.round(w / 270f))
        val hb = roiH / bs
        val wb = w / bs
        if (hb < 3 || wb < 3) return emptyList()

        val cream = BooleanArray(hb * wb)
        for (by in 0 until hb) {
            for (bx in 0 until wb) {
                var cnt = 0
                for (y in 0 until bs) {
                    val row = (by * bs + y) * w + bx * bs
                    for (x in 0 until bs) {
                        if (isCream(px[row + x])) cnt++
                    }
                }
                cream[by * wb + bx] = cnt * 10 >= bs * bs * 9
            }
        }

        val eroded = BooleanArray(hb * wb)
        for (by in 1 until hb - 1) {
            for (bx in 1 until wb - 1) {
                var all = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (!cream[(by + dy) * wb + bx + dx]) {
                            all = false
                            break@loop
                        }
                    }
                }
                eroded[by * wb + bx] = all
            }
        }

        val seen = BooleanArray(hb * wb)
        val stack = IntArray(hb * wb)
        val out = ArrayList<Box>()
        for (start in 0 until hb * wb) {
            if (!eroded[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var minX = wb
            var maxX = -1
            var minY = hb
            var maxY = -1
            while (sp > 0) {
                val c = stack[--sp]
                val cy = c / wb
                val cx = c % wb
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                if (cx > 0 && eroded[c - 1] && !seen[c - 1]) { seen[c - 1] = true; stack[sp++] = c - 1 }
                if (cx < wb - 1 && eroded[c + 1] && !seen[c + 1]) { seen[c + 1] = true; stack[sp++] = c + 1 }
                if (cy > 0 && eroded[c - wb] && !seen[c - wb]) { seen[c - wb] = true; stack[sp++] = c - wb }
                if (cy < hb - 1 && eroded[c + wb] && !seen[c + wb]) { seen[c + wb] = true; stack[sp++] = c + wb }
            }
            val left = max((minX - 1) * bs, 0)
            val widthPx = (maxX - minX + 3) * bs
            val top = (minY - 1) * bs + yOff
            val heightPx = (maxY - minY + 3) * bs
            val centerX = left + widthPx / 2
            if (heightPx >= 0.10f * fullH && widthPx >= 0.04f * w &&
                centerX >= 0.12f * w && centerX <= 0.88f * w
            ) {
                out.add(Box(left, top, left + widthPx, top + heightPx))
            }
        }
        return out
    }

    /** Dark or red marks of rank-glyph size in the top band of a card blob. */
    private fun collectGlyphs(
        px: IntArray, w: Int, roiH: Int, fullH: Int, yOff: Int, blob: Box, out: MutableList<Box>, inkLimit: Int
    ) {
        val x0 = max(blob.l, 0)
        val x1 = min(blob.r, w)
        val ry0 = max(blob.t - yOff, 0)
        val ry1 = min(blob.t - yOff + (0.10f * fullH).toInt(), roiH)
        val bw = x1 - x0
        val bh = ry1 - ry0
        if (bw <= 0 || bh <= 0) return

        val n = bw * bh
        val ink = BooleanArray(n)
        for (y in 0 until bh) {
            val row = (ry0 + y) * w + x0
            for (x in 0 until bw) ink[y * bw + x] = isInk(px[row + x], inkLimit)
        }

        val seen = BooleanArray(n)
        val stack = IntArray(n)
        for (s in 0 until n) {
            if (!ink[s] || seen[s]) continue
            var sp = 0
            stack[sp++] = s
            seen[s] = true
            var minX = bw
            var maxX = -1
            var minY = bh
            var maxY = -1
            while (sp > 0) {
                val c = stack[--sp]
                val cy = c / bw
                val cx = c % bw
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val ny = cy + dy
                        val nx = cx + dx
                        if (ny < 0 || ny >= bh || nx < 0 || nx >= bw) continue
                        val ni = ny * bw + nx
                        if (ink[ni] && !seen[ni]) {
                            seen[ni] = true
                            stack[sp++] = ni
                        }
                    }
                }
            }
            val gw = maxX - minX + 1
            val gh = maxY - minY + 1
            if (gh < 0.012f * fullH || gh > 0.03f * fullH) continue
            if (gw < 0.006f * w || gw > 0.075f * w) continue
            out.add(Box(x0 + minX, ry0 + minY + yOff, x0 + maxX + 1, ry0 + maxY + 1 + yOff))
        }
    }
}
