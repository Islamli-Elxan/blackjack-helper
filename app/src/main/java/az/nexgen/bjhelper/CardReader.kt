package az.nexgen.bjhelper

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the corner rank of each face-up card by colour ([CardFinder]) and reads every rank
 * separately with on-device text recognition. A cleaned black-on-white copy of each rank is read,
 * and the answer is cached by pixel content so a static table costs no recognition work.
 */
class CardReader {

    class Item(val box: CardFinder.Box, val zone: Int, val text: String, val rank: Int?)

    class Reading(
        val ok: Boolean,
        val dealer: List<Int>,
        val player: List<Int>,
        val blobs: List<CardFinder.Box>,
        val items: List<Item>
    )

    private class Img(val pix: IntArray, val w: Int, val h: Int)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val cache = HashMap<Int, String>()

    fun read(bmp: Bitmap): Reading {
        val w = bmp.width
        val h = bmp.height
        val y0 = (h * CROP_TOP).toInt()
        val y1 = (h * CROP_BOTTOM).toInt()
        val roiH = y1 - y0
        val px = IntArray(w * roiH)
        bmp.getPixels(px, 0, w, 0, y0, w, roiH)

        val found = CardFinder.find(px, w, roiH, h, y0)
        val items = ArrayList<Item>()
        var failed = false
        for (g in found.glyphs) {
            val text = recognize(px, w, roiH, y0, g.box)
            if (text == null) {
                failed = true
                continue
            }
            items.add(Item(g.box, g.zone, text, RankParser.parse(text)))
        }
        val dealer = items.filter { it.zone == 0 }.mapNotNull { it.rank }
        val player = items.filter { it.zone == 1 }.mapNotNull { it.rank }
        return Reading(!failed, dealer, player, found.blobs, items)
    }

    private fun recognize(px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box): String? {
        val first = glyphImage(px, w, roiH, yOff, box, 2, 30) ?: return ""
        val key = first.pix.contentHashCode()
        val cached = cache[key]
        if (cached != null) return cached

        var text = ocr(first) ?: return null
        if (RankParser.parse(text) == null) {
            val second = glyphImage(px, w, roiH, yOff, box, 3, 60)
            if (second != null) {
                val t2 = ocr(second)
                if (t2 != null && RankParser.parse(t2) != null) text = t2
            }
        }
        if (cache.size > 300) cache.clear()
        cache[key] = text
        return text
    }

    /** Black marks on white, only inside the glyph's own box, scaled up with a white margin. */
    private fun glyphImage(
        px: IntArray, w: Int, roiH: Int, yOff: Int, box: CardFinder.Box, scale: Int, margin: Int
    ): Img? {
        val l = max(box.l, 0)
        val r = min(box.r, w)
        val t = max(box.t - yOff, 0)
        val b = min(box.b - yOff, roiH)
        val gw = r - l
        val gh = b - t
        if (gw <= 0 || gh <= 0) return null
        val ow = gw * scale + 2 * margin
        val oh = gh * scale + 2 * margin
        val out = IntArray(ow * oh)
        java.util.Arrays.fill(out, -1)
        val black = 0xFF000000.toInt()
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                if (CardFinder.isInk(px[(t + y) * w + l + x])) {
                    for (dy in 0 until scale) {
                        val row = (margin + y * scale + dy) * ow + margin + x * scale
                        for (dx in 0 until scale) out[row + dx] = black
                    }
                }
            }
        }
        return Img(out, ow, oh)
    }

    private fun ocr(img: Img): String? {
        val bmp = Bitmap.createBitmap(img.pix, img.w, img.h, Bitmap.Config.ARGB_8888)
        return try {
            val res = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 3, TimeUnit.SECONDS)
            res.text.replace(Regex("\\s"), "")
        } catch (e: Exception) {
            Log.w(TAG, "glyph recognition failed", e)
            null
        } finally {
            bmp.recycle()
        }
    }

    companion object {
        private const val TAG = "BJHelper"
        const val CROP_TOP = 0.08f
        const val CROP_BOTTOM = 0.74f
    }
}
