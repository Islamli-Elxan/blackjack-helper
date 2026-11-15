package az.nexgen.bjhelper

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the corner rank of each face-up card by colour ([CardFinder]) and reads each rank with
 * the built-in classifier ([RankClassifier]). Answers are cached by pixel content, so a static
 * table costs almost no work. A glyph the classifier is not sure about is reported as unsure
 * instead of guessed. The hand totals printed by the game are read too ([BadgeReader]) and
 * checked against the ranks ([Fusion]): they confirm a reading, and replace it when it is wrong
 * or missing.
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
        val missed: List<RoundLog.Missed> = emptyList(),
        /** The ranks agree with the printed total. */
        val dealerVerified: Boolean = false,
        val playerVerified: Boolean = false,
        /** The hand comes from the printed total alone; its real cards are not known. */
        val dealerApprox: Boolean = false,
        val playerApprox: Boolean = false,
        /** What the totals said, for the report: "d9 m15". */
        val badges: String = ""
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
        val items = classifyAll(found.glyphs, px, w, roiH, y0, CardFinder.INK_LIMIT)
        val zoneUnsure = flags(items, found.blobs, h)

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
        val rawDealer = items.filter { it.zone == 0 && it.rank != null }.sortedBy { it.box.l }.map { it.rank!! }
        val playerItems = items.filter { it.zone == 1 && it.rank != null }
        val gs = playerItems.map { Hands.G(it.box.l, it.box.t, it.box.b, it.rank!!) }
        val rawHands = Hands.group(gs, found.blobs, w)

        // the totals the game prints
        val db = readBand(bmp, BadgeReader.DEALER_TOP, BadgeReader.DEALER_BOTTOM, cover)
        val pb = readBand(bmp, BadgeReader.PLAYER_TOP, BadgeReader.PLAYER_BOTTOM, cover)
        // a total with no card on the table is a leftover of the animation, not a hand
        val dealerCards = found.blobs.any { (it.t + it.b) / 2f < CardFinder.DEALER_LIMIT * h }
        val playerCards = found.blobs.any { (it.t + it.b) / 2f >= CardFinder.DEALER_LIMIT * h }
        val dealerTotal = if (dealerCards && db.badges.size == 1) db.badges[0].value else null
        val playerTotals = if (playerCards) pb.badges.sortedBy { it.cx }.map { it.value } else emptyList()
        val dz = Fusion.dealer(rawDealer, !zoneUnsure[0], dealerTotal)
        val pz = Fusion.player(rawHands, !zoneUnsure[1], playerTotals)
        val dealer = dz.hands.firstOrNull() ?: emptyList()
        val hands = pz.hands
        val player = hands.flatten()
        val dealerSure = dz.sure
        val playerSure = pz.sure
        val badgeText = (if (db.badges.isEmpty()) "" else "d" + db.badges.joinToString(",") { it.value?.toString() ?: "?" }) +
            (if (pb.badges.isEmpty()) "" else " m" + pb.badges.sortedBy { it.cx }.joinToString(",") { it.value?.toString() ?: "?" })

        val sb = StringBuilder()
        sb.append("kart ").append(found.blobs.size).append(": ")
        for (it in items) {
            if (it.text == "X" && it.conf >= GlyphJudge.MIN_CONF) continue
            sb.append(if (it.zone == 0) "D" else "M").append(it.text).append(Math.round(it.conf * 100)).append(' ')
        }
        if (unsure) sb.append(if (zoneUnsure[0]) "(diler?)" else "").append(if (zoneUnsure[1]) "(mən?)" else "")
        if (badgeText.isNotEmpty()) sb.append(" [").append(badgeText).append(']')

        val layout = "D:" + items.filter { it.zone == 0 && it.rank != null }.sortedBy { it.box.l }.joinToString(",") { it.box.l.toString() } +
            " M:" + playerItems.sortedBy { it.box.l }.joinToString(",") { it.box.l.toString() } +
            (if (badgeText.isNotEmpty()) " B:" + badgeText else "") +
            (if (dz.approx) " ~D" else "") + (if (pz.approx) " ~M" else "")

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
            dealerSure || playerSure, dealer, player, hands, found.blobs, items, sb.toString().trim(),
            dealerSure, playerSure, layout, missed,
            dz.verified, pz.verified, dz.approx, pz.approx, badgeText
        )
    }

    private fun readBand(bmp: Bitmap, top: Float, bottom: Float, cover: CardFinder.Box?): BadgeReader.Band {
        val w = bmp.width
        val h = bmp.height
        val y0 = (top * h).toInt()
        val y1 = (bottom * h).toInt()
        val rows = y1 - y0
        if (rows < 8) return BadgeReader.Band(emptyList(), false)
        val px = IntArray(w * rows)
        bmp.getPixels(px, 0, w, 0, y0, w, rows)
        return BadgeReader.readBand(px, w, rows, y0, h, top, bottom, cover)
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
    }
}
