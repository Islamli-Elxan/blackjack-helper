package az.nexgen.bjhelper

import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the printed hand totals on three real 1080x2400 screenshots. */
class BadgeReaderTest {

    private val w = 1080
    private val h = 2400
    private val dealerRows = 168
    private val playerRows = 144

    private fun load(name: String): Pair<IntArray, IntArray> {
        val raw = GZIPInputStream(javaClass.getResourceAsStream("/badges_$name.rgb.gz")).readBytes()
        assertEquals(w * (dealerRows + playerRows) * 3, raw.size)
        fun px(from: Int, rows: Int): IntArray {
            val out = IntArray(w * rows)
            for (i in out.indices) {
                val o = (from * w + i) * 3
                out[i] = (0xFF shl 24) or ((raw[o].toInt() and 0xFF) shl 16) or
                    ((raw[o + 1].toInt() and 0xFF) shl 8) or (raw[o + 2].toInt() and 0xFF)
            }
            return out
        }
        return Pair(px(0, dealerRows), px(dealerRows, playerRows))
    }

    private fun totals(name: String): Pair<List<Int?>, List<Int?>> {
        val (d, p) = load(name)
        val db = BadgeReader.readBand(d, w, dealerRows, 276, h, BadgeReader.DEALER_TOP, BadgeReader.DEALER_BOTTOM)
        val pb = BadgeReader.readBand(p, w, playerRows, 1620, h, BadgeReader.PLAYER_TOP, BadgeReader.PLAYER_BOTTOM)
        return Pair(db.badges.map { it.value }, pb.badges.sortedBy { it.cx }.map { it.value })
    }

    @Test
    fun londonCleanTable() {
        val (d, p) = totals("clean")
        assertEquals(listOf<Int?>(9), d)
        assertEquals(listOf<Int?>(15), p)
    }

    @Test
    fun londonAfterHit() {
        val (d, p) = totals("hit")
        assertEquals(listOf<Int?>(10), d)
        assertEquals(listOf<Int?>(16), p)
    }

    @Test
    fun monteCarlo() {
        val (d, p) = totals("mc")
        assertEquals(listOf<Int?>(10), d)
        assertEquals(listOf<Int?>(12), p)
    }

    @Test
    fun coveredBandIsBlocked() {
        val (d, _) = load("clean")
        val cover = CardFinder.Box(0, 250, 1080, 400)
        val db = BadgeReader.readBand(d, w, dealerRows, 276, h, BadgeReader.DEALER_TOP, BadgeReader.DEALER_BOTTOM, cover)
        assertTrue(db.blocked)
        assertTrue(db.badges.isEmpty())
    }
}
