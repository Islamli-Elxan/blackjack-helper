package az.nexgen.bjhelper

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the corner rank of each face-up card by colour ([CardFinder]) and reads each rank with
 * the built-in classifier ([RankClassifier]). Answers are cached by pixel content, so a static
 * table costs almost no work. A glyph the classifier is not sure about is reported as unsure
 * (the frame is then ignored) instead of guessed.
 */
class CardReader {

    class Item(val box: CardFinder.Box, val zone: Int, val text: String, val rank: Int?, val conf: Float)

    class Reading(
        val ok: Boolean,
        val dealer: List<Int>,
        val player: List<Int>,
        val blobs: List<CardFinder.Box>,
        val items: List<Item>,
        /** Short text for the window: what was found and how sure. */
        val diag: String = ""
    )

    private val classifier = RankClassifier.load()
    private val cache = HashMap<Int, RankClassifier.Result>()

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
        val items = ArrayList<Item>()
        var unsure = false
        for (g in found.glyphs) {
            val res = classify(px, w, roiH, y0, g.box)
            val verdict = GlyphJudge.judge(res)
            if (verdict == GlyphJudge.Verdict.UNSURE) unsure = true
            val rank = if (verdict == GlyphJudge.Verdict.RANK) res.rank else null
            items.add(Item(g.box, g.zone, res.label.toString(), rank, res.confidence))
        }
        // cards without any readable corner (still sliding in, or hidden) are not an empty table
        if (found.blobs.isNotEmpty() && items.none { it.rank != null }) unsure = true
        // anything our own window sits on is unreliable
        if (cover != null) {
            val pad = 12
            val c = CardFinder.Box(cover.l - pad, cover.t - pad, cover.r + pad, cover.b + pad)
            for (b in found.blobs) {
                val top = CardFinder.Box(b.l, b.t, b.r, b.t + (0.10f * h).toInt())
                if (Placer.overlap(c, top) > 0) unsure = true
            }
        }
        val dealer = items.filter { it.zone == 0 }.mapNotNull { it.rank }
        val player = items.filter { it.zone == 1 }.mapNotNull { it.rank }
        val sb = StringBuilder()
        sb.append("kart ").append(found.blobs.size).append(": ")
        for (it in items) {
            if (it.text == "X" && it.conf >= GlyphJudge.MIN_CONF) continue
            sb.append(if (it.zone == 0) "D" else "M").append(it.text).append(Math.round(it.conf * 100)).append(' ')
        }
        if (unsure) sb.append("(əmin deyil)")
        return Reading(!unsure, dealer, player, found.blobs, items, sb.toString().trim())
    }

    private fun classify(px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box): RankClassifier.Result {
        val l = max(box.l, 0)
        val r = min(box.r, w)
        val t = max(box.t - yOff, 0)
        val b = min(box.b - yOff, roiH)
        val gw = r - l
        val gh = b - t
        if (gw <= 0 || gh <= 0) return RankClassifier.Result('X', 1f)
        val mask = BooleanArray(gw * gh)
        var hash = 17
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                val ink = CardFinder.isInk(px[(t + y) * w + l + x])
                mask[y * gw + x] = ink
                hash = hash * 31 + (if (ink) 1 else 0)
            }
        }
        hash = hash * 31 + gw * 1000 + gh
        val cached = cache[hash]
        if (cached != null) return cached
        val res = classifier.classify(mask, gw, gh)
        if (cache.size > 300) cache.clear()
        cache[hash] = res
        return res
    }

    companion object {
        const val CROP_TOP = 0.08f
        const val CROP_BOTTOM = 0.74f
    }
}
