package az.nexgen.bjhelper

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Reads the rank printed in the top-left corner of every face-up card on the table.
 * It runs on-device text recognition on the table area, keeps only text that sits on a cream
 * card face, keeps the top row of corner indexes and sorts them left to right.
 */
class CardReader {

    class Cand(
        val text: String,
        val box: Rect,
        val rank: Int?,
        val cream: Float,
        var accepted: Boolean = false,
        var reason: String = ""
    )

    class Reading(
        val ok: Boolean,
        val dealer: List<Int>,
        val player: List<Int>,
        val cands: List<Cand>
    )

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun read(bmp: Bitmap): Reading {
        val w = bmp.width
        val h = bmp.height
        val top = (h * CROP_TOP).toInt()
        val bottom = (h * CROP_BOTTOM).toInt()
        val crop = Bitmap.createBitmap(bmp, 0, top, w, bottom - top)

        val result = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "text recognition failed", e)
            null
        } finally {
            crop.recycle()
        }
        if (result == null) return Reading(false, emptyList(), emptyList(), emptyList())

        val cands = ArrayList<Cand>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (el in line.elements) {
                    val b = el.boundingBox ?: continue
                    val box = Rect(b.left, b.top + top, b.right, b.bottom + top)
                    cands.add(Cand(el.text, box, RankParser.parse(el.text), creamFraction(bmp, box)))
                }
            }
        }

        val minH = h * 0.012f
        val maxH = h * 0.07f
        val valid = ArrayList<Cand>()
        for (c in cands) {
            val bh = c.box.height()
            when {
                c.rank == null -> c.reason = "no-rank"
                bh < minH || bh > maxH -> c.reason = "size"
                c.cream < CREAM_MIN -> c.reason = "not-cream"
                else -> valid.add(c)
            }
        }

        val split = h * DEALER_LIMIT
        val dealerCards = pickRow(valid.filter { it.box.centerY() < split })
        val playerCards = pickRow(valid.filter { it.box.centerY() >= split })
        for (c in valid) {
            if (c in dealerCards || c in playerCards) c.accepted = true else c.reason = "other-row"
        }
        return Reading(
            true,
            dealerCards.map { it.rank!! },
            playerCards.map { it.rank!! },
            cands
        )
    }

    /** Keeps only the top row of corner indexes; the rotated index in the bottom corner sits far lower. */
    private fun pickRow(list: List<Cand>): List<Cand> {
        if (list.isEmpty()) return emptyList()
        var best = list[0]
        for (c in list) if (c.box.top < best.box.top) best = c
        val tol = maxOf(12, best.box.height() / 2)
        return list.filter { it.box.top - best.box.top <= tol }.sortedBy { it.box.left }
    }

    /** Share of near-white, low-saturation pixels around the text box. Text on a card face scores high. */
    private fun creamFraction(bmp: Bitmap, box: Rect): Float {
        val r = Rect(box)
        r.inset(-4, -4)
        val l = r.left.coerceIn(0, bmp.width - 1)
        val t = r.top.coerceIn(0, bmp.height - 1)
        val rr = r.right.coerceIn(l + 1, bmp.width)
        val bb = r.bottom.coerceIn(t + 1, bmp.height)
        var good = 0
        var total = 0
        var y = t
        while (y < bb) {
            var x = l
            while (x < rr) {
                val p = bmp.getPixel(x, y)
                val red = (p shr 16) and 0xFF
                val green = (p shr 8) and 0xFF
                val blue = p and 0xFF
                val mx = maxOf(red, maxOf(green, blue))
                val mn = minOf(red, minOf(green, blue))
                if (mn >= 200 && mx - mn <= 45) good++
                total++
                x += 3
            }
            y += 3
        }
        return if (total == 0) 0f else good.toFloat() / total
    }

    companion object {
        private const val TAG = "BJHelper"
        const val CROP_TOP = 0.08f
        const val CROP_BOTTOM = 0.74f
        const val DEALER_LIMIT = 0.41f
        private const val CREAM_MIN = 0.5f
    }
}
