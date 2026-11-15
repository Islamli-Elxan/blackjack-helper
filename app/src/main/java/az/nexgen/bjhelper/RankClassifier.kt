package az.nexgen.bjhelper

import java.io.DataInputStream
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Tiny neural network (385 -> 96 -> 11) that reads one corner rank from its black/white mask.
 * Trained offline on rendered digits; no text recognition library is involved.
 * Classes: A, 2..9, T (ten, jack, queen, king) and X (not a rank: suit, pip piece, noise).
 */
class RankClassifier private constructor(
    private val nIn: Int,
    private val nHid: Int,
    private val nOut: Int,
    private val w1: FloatArray,
    private val b1: FloatArray,
    private val w2: FloatArray,
    private val b2: FloatArray,
    private val labels: String
) {

    class Result(val label: Char, val confidence: Float) {
        /** Blackjack value: 11 for an ace, 10 for ten and faces, else the digit. Null if not a rank. */
        val rank: Int?
            get() = when (label) {
                'A' -> 11
                'T' -> 10
                'X' -> null
                else -> label - '0'
            }
    }

    /** @param mask row-major ink mask (true = ink) of size w*h */
    fun classify(mask: BooleanArray, w: Int, h: Int): Result {
        val v = normalize(mask, w, h) ?: return Result('X', 1f)
        val hid = FloatArray(nHid)
        for (j in 0 until nHid) hid[j] = b1[j]
        for (i in 0 until nIn) {
            val x = v[i]
            if (x == 0f) continue
            val row = i * nHid
            for (j in 0 until nHid) hid[j] += x * w1[row + j]
        }
        for (j in 0 until nHid) if (hid[j] < 0f) hid[j] = 0f
        val out = FloatArray(nOut)
        for (k in 0 until nOut) out[k] = b2[k]
        for (j in 0 until nHid) {
            val x = hid[j]
            if (x == 0f) continue
            val row = j * nOut
            for (k in 0 until nOut) out[k] += x * w2[row + k]
        }
        var mx = out[0]
        for (k in 1 until nOut) if (out[k] > mx) mx = out[k]
        var sum = 0f
        for (k in 0 until nOut) {
            out[k] = exp(out[k] - mx)
            sum += out[k]
        }
        var best = 0
        for (k in 1 until nOut) if (out[k] > out[best]) best = k
        return Result(labels[best], out[best] / sum)
    }

    companion object {
        const val GW = 16
        const val GH = 24
        private const val RANK_LABELS = "A23456789TX"
        const val DIGIT_LABELS = "0123456789/"

        fun load(): RankClassifier = load("/rank_model.bin", RANK_LABELS)

        /** Reads the digits of the hand-total badges (italic, white on a dark disc). */
        fun loadDigits(): RankClassifier = load("/digit_model.bin", DIGIT_LABELS)

        private fun load(resource: String, labels: String): RankClassifier {
            val stream = RankClassifier::class.java.getResourceAsStream(resource)
                ?: throw IllegalStateException(resource + " missing")
            DataInputStream(stream.buffered()).use { d ->
                val nIn = d.readInt()
                val nHid = d.readInt()
                val nOut = d.readInt()
                fun floats(n: Int): FloatArray {
                    val a = FloatArray(n)
                    for (i in 0 until n) a[i] = d.readFloat()
                    return a
                }
                val w1 = floats(nIn * nHid)
                val b1 = floats(nHid)
                val w2 = floats(nHid * nOut)
                val b2 = floats(nOut)
                return RankClassifier(nIn, nHid, nOut, w1, b1, w2, b2, labels)
            }
        }

        /** Same algorithm as glyphnorm.py: tight box, exact area average onto 16x24, plus aspect. */
        fun normalize(mask: BooleanArray, w: Int, h: Int): FloatArray? {
            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (mask[y * w + x]) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0) return null
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val out = FloatArray(GW * GH + 1)
            for (oy in 0 until GH) {
                val y0 = oy * bh.toDouble() / GH
                val y1 = (oy + 1) * bh.toDouble() / GH
                for (ox in 0 until GW) {
                    val x0 = ox * bw.toDouble() / GW
                    val x1 = (ox + 1) * bw.toDouble() / GW
                    var s = 0.0
                    val iy1 = min(ceil(y1).toInt(), bh)
                    val ix1 = min(ceil(x1).toInt(), bw)
                    for (iy in floor(y0).toInt() until iy1) {
                        val wy = min(y1, iy + 1.0) - max(y0, iy.toDouble())
                        if (wy <= 0) continue
                        for (ix in floor(x0).toInt() until ix1) {
                            val wx = min(x1, ix + 1.0) - max(x0, ix.toDouble())
                            if (wx <= 0) continue
                            if (mask[(minY + iy) * w + minX + ix]) s += wy * wx
                        }
                    }
                    out[oy * GW + ox] = (s / ((y1 - y0) * (x1 - x0))).toFloat()
                }
            }
            out[GW * GH] = (min(bw.toDouble() / bh, 2.0) / 2.0).toFloat()
            return out
        }
    }
}
