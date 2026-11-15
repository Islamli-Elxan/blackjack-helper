package az.nexgen.bjhelper

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the corner rank of each face-up card by colour ([CardFinder]) and reads each rank with
 * the built-in classifier ([RankClassifier]). Answers are cached by pixel content, so a static
 * table costs almost no work. When a row cannot be read, the marks are looked at again with a
 * different darkness limit (shadows on a card can glue a mark to its surroundings). A glyph the
 * classifier is not sure about is reported as unsure instead of guessed.
 */
class CardReader {

    class Item(
        val box: CardFinder.Box, val zone: Int, val text: String, val rank: Int?, val conf: Float,
        /** The classifier was not sure what this mark is. */
        val doubt: Boolean = false
    )

    class Reading(
        val ok: Boolean,
        val dealer: List<Int>,
        /** The player's cards, hand after hand. */
        val player: List<Int>,
        val hands: List<List<Int>>,
        val blobs: List<CardFinder.Box>,
        val items: List<Item>,
        /** Short text for the window: what was found and how sure. */
        val diag: String = "",
        /** False when the dealer's (or the player's) row could not be read this frame. */
        val dealerSure: Boolean = true,
        val playerSure: Boolean = true,
        /** Where the corners are, for the report: "D:365 M:364,454,550". */
        val layout: String = "",
        val missed: List<RoundLog.Missed> = emptyList()
    )

    private val classifier = RankClassifier.load()
    private val cache = HashMap<Long, RankClassifier.Result>()

    /** @param cover screen rectangle of our own window; marks under it cannot be trusted */
    fun read(bmp: Bitmap, cover: CardFinder.Box? = null): Reading {
        val w = bmp.width
        val h = bmp.height
        val y0 = (h * CROP_TOP).toInt()
        val y1 = (h * CROP_BOTTOM).toInt()
        val roiH = y1 - y0
        val px = IntArray(w * roiH)
        bmp.getPixels(px, 0, w, 0, y0, w, roiH)

        val found = CardFinder.find(px, w, roiH, h, y0)
        var items = classifyAll(found.glyphs, px, w, roiH, y0, CardFinder.INK_LIMIT)
        val zoneUnsure = flags(items, found.blobs, h)

        // another darkness limit may separate the marks from a shadow
        if (zoneUnsure[0] || zoneUnsure[1]) {
            for (limit in RETRY_LIMITS) {
                val again = CardFinder.find(px, w, roiH, h, y0, limit)
                val items2 = classifyAll(again.glyphs, px, w, roiH, y0, limit)
                val flags2 = flags(items2, found.blobs, h)
                var fixed = false
                for (z in 0..1) {
                    if (zoneUnsure[z] && !flags2[z] && items2.any { it.zone == z && it.rank != null }) {
                        items = items.filter { it.zone != z } + items2.filter { it.zone == z }
                        zoneUnsure[z] = false
                        fixed = true
                    }
                }
                if (fixed && !zoneUnsure[0] && !zoneUnsure[1]) break
            }
        }

        // anything our own window sits on is unreliable
        if (cover != null) {
            val pad = 12
            val c = CardFinder.Box(cover.l - pad, cover.t - pad, cover.r + pad, cover.b + pad)
            for (b in found.blobs) {
                val top = CardFinder.Box(b.l, b.t, b.r, b.t + (0.10f * h).toInt())
                if (Placer.overlap(c, top) > 0) {
                    zoneUnsure[0] = true
                    zoneUnsure[1] = true
                }
            }
        }

        val unsure = zoneUnsure[0] || zoneUnsure[1]
        val dealer = items.filter { it.zone == 0 && it.rank != null }.sortedBy { it.box.l }.map { it.rank!! }
        val playerItems = items.filter { it.zone == 1 && it.rank != null }
        val gs = playerItems.map { Hands.G(it.box.l, it.box.t, it.box.b, it.rank!!) }
        val hands = Hands.group(gs, found.blobs, w)
        val player = hands.flatten()

        val sb = StringBuilder()
        sb.append("kart ").append(found.blobs.size).append(": ")
        for (it in items) {
            if (it.text == "X" && it.conf >= GlyphJudge.MIN_CONF) continue
            sb.append(if (it.zone == 0) "D" else "M").append(it.text).append(Math.round(it.conf * 100)).append(' ')
        }
        if (unsure) sb.append(if (zoneUnsure[0]) "(diler?)" else "").append(if (zoneUnsure[1]) "(mən?)" else "")

        val layout = "D:" + items.filter { it.zone == 0 && it.rank != null }.sortedBy { it.box.l }.joinToString(",") { it.box.l.toString() } +
            " M:" + playerItems.sortedBy { it.box.l }.joinToString(",") { it.box.l.toString() }

        val missed = ArrayList<RoundLog.Missed>()
        if (unsure) {
            for (it in items) {
                if (it.rank == null && zoneUnsure[it.zone]) {
                    missed.add(
                        RoundLog.Missed(
                            it.zone, it.text, it.conf, it.box.w, it.box.h,
                            ascii(px, w, roiH, y0, it.box)
                        )
                    )
                }
            }
        }

        return Reading(
            !(zoneUnsure[0] && zoneUnsure[1]), dealer, player, hands, found.blobs, items, sb.toString().trim(),
            !zoneUnsure[0], !zoneUnsure[1], layout, missed
        )
    }

    private fun classifyAll(
        glyphs: List<CardFinder.Glyph>, px: IntArray, w: Int, roiH: Int, y0: Int, limit: Int
    ): List<Item> {
        val items = ArrayList<Item>()
        for (g in glyphs) {
            val res = classify(px, w, roiH, y0, g.box, limit)
            val verdict = GlyphJudge.judge(res)
            val rank = if (verdict == GlyphJudge.Verdict.RANK) res.rank else null
            items.add(Item(g.box, g.zone, res.label.toString(), rank, res.confidence, verdict == GlyphJudge.Verdict.UNSURE))
        }
        return items
    }

    /** For each row: true when it holds a card whose corner could not be read. */
    private fun flags(items: List<Item>, blobs: List<CardFinder.Box>, h: Int): BooleanArray {
        val z = booleanArrayOf(false, false)
        for (it in items) {
            if (it.doubt) z[it.zone] = true
        }
        // a card without a readable corner (still sliding in, or hidden) is not an empty row
        for (b in blobs) {
            val zone = if ((b.t + b.b) / 2f < CardFinder.DEALER_LIMIT * h) 0 else 1
            if (items.none { it.zone == zone && it.rank != null }) z[zone] = true
        }
        return z
    }

    private fun mask(px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box, limit: Int): Triple<BooleanArray, Int, Int>? {
        val l = max(box.l, 0)
        val r = min(box.r, w)
        val t = max(box.t - yOff, 0)
        val b = min(box.b - yOff, roiH)
        val gw = r - l
        val gh = b - t
        if (gw <= 0 || gh <= 0) return null
        val m = BooleanArray(gw * gh)
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                m[y * gw + x] = CardFinder.isInk(px[(t + y) * w + l + x], limit)
            }
        }
        return Triple(m, gw, gh)
    }

    private fun classify(
        px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box, limit: Int
    ): RankClassifier.Result {
        val mk = mask(px, w, roiH, yOff, box, limit) ?: return RankClassifier.Result('X', 1f)
        val (m, gw, gh) = mk
        var hash = 17L
        for (v in m) hash = hash * 31 + (if (v) 1 else 0)
        hash = hash * 31 + gw * 1000 + gh
        hash = hash * 31 + limit
        val cached = cache[hash]
        if (cached != null) return cached
        val res = classifier.classify(m, gw, gh)
        if (cache.size > 400) cache.clear()
        cache[hash] = res
        return res
    }

    /** The mark as text, half size, for the report. */
    private fun ascii(px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box): List<String> {
        val mk = mask(px, w, roiH, yOff, box, CardFinder.INK_LIMIT) ?: return emptyList()
        val (m, gw, gh) = mk
        val rows = ArrayList<String>()
        var y = 0
        while (y < gh && rows.size < 40) {
            val sb = StringBuilder()
            var x = 0
            while (x < gw && sb.length < 40) {
                var any = false
                for (dy in 0..1) for (dx in 0..1) {
                    if (y + dy < gh && x + dx < gw && m[(y + dy) * gw + x + dx]) any = true
                }
                sb.append(if (any) '#' else '.')
                x += 2
            }
            rows.add(sb.toString())
            y += 2
        }
        return rows
    }

    companion object {
        const val CROP_TOP = 0.08f
        const val CROP_BOTTOM = 0.74f
        private val RETRY_LIMITS = intArrayOf(130, 100, 225)
    }
}
