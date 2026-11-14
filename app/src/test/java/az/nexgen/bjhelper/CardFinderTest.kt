package az.nexgen.bjhelper

import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the finder on a real screenshot of the table (1080x2400): dealer 9, player 5 and 10.
 * The resource holds the RGB pixels of the table area (rows 192 to 1775), gzip compressed.
 */
class CardFinderTest {

    private val w = 1080
    private val h = 2400
    private val y0 = 192
    private val roiH = 1584

    private fun loadPixels(): IntArray {
        val stream = javaClass.getResourceAsStream("/table_clean_roi.rgb.gz")
        val raw = GZIPInputStream(stream).readBytes()
        assertEquals(w * roiH * 3, raw.size)
        val px = IntArray(w * roiH)
        for (i in px.indices) {
            val r = raw[i * 3].toInt() and 0xFF
            val g = raw[i * 3 + 1].toInt() and 0xFF
            val b = raw[i * 3 + 2].toInt() and 0xFF
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return px
    }

    private fun near(expected: Int, actual: Int) {
        assertTrue("expected about $expected but was $actual", Math.abs(expected - actual) <= 8)
    }

    @Test
    fun findsEveryCornerRank() {
        val res = CardFinder.find(loadPixels(), w, roiH, h, y0)

        val dealer = res.glyphs.filter { it.zone == 0 }
        val player = res.glyphs.filter { it.zone == 1 }
        assertEquals("dealer glyphs", 1, dealer.size)
        assertEquals("player glyphs", 2, player.size)

        // dealer 9
        near(366, dealer[0].box.l)
        near(491, dealer[0].box.t)
        near(398, dealer[0].box.r)
        near(535, dealer[0].box.b)
        // player 5
        near(365, player[0].box.l)
        near(1185, player[0].box.t)
        near(394, player[0].box.r)
        near(1230, player[0].box.b)
        // player 10 (the 1 and the 0 merged)
        near(443, player[1].box.l)
        near(1183, player[1].box.t)
        near(502, player[1].box.r)
        near(1231, player[1].box.b)
    }

    @Test
    fun readsEveryCornerRank() {
        val px = loadPixels()
        val res = CardFinder.find(px, w, roiH, h, y0)
        val clf = RankClassifier.load()
        val labels = ArrayList<Char>()
        for (g in res.glyphs) {
            val gw = g.box.w
            val gh = g.box.h
            val mask = BooleanArray(gw * gh)
            for (y in 0 until gh) {
                for (x in 0 until gw) {
                    mask[y * gw + x] = CardFinder.isInk(px[(g.box.t - y0 + y) * w + g.box.l + x])
                }
            }
            val r = clf.classify(mask, gw, gh)
            assertTrue("low confidence " + r.confidence, r.confidence > 0.9f)
            labels.add(r.label)
        }
        // dealer 9, player 5 and 10
        assertEquals(listOf('9', '5', 'T'), labels)
    }

    @Test
    fun emptyMaskIsNotARank() {
        val r = RankClassifier.load().classify(BooleanArray(100), 10, 10)
        assertEquals('X', r.label)
        assertEquals(null, r.rank)
    }
}
