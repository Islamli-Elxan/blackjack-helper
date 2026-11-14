package az.nexgen.bjhelper

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the finder on a real screenshot of the table: dealer 9, player 5 and 10. */
class CardFinderTest {

    private fun load(): BufferedImage {
        val stream = javaClass.getResourceAsStream("/table_clean.jpg")
        return ImageIO.read(stream)
    }

    private fun near(expected: Int, actual: Int, tol: Int = 8) {
        assertTrue("expected about $expected but was $actual", Math.abs(expected - actual) <= tol)
    }

    @Test
    fun findsEveryCornerRank() {
        val img = load()
        val w = img.width
        val h = img.height
        assertEquals(1080, w)
        assertEquals(2400, h)

        val y0 = (h * CardReader.CROP_TOP).toInt()
        val y1 = (h * CardReader.CROP_BOTTOM).toInt()
        val roiH = y1 - y0
        val px = IntArray(w * roiH)
        for (y in 0 until roiH) {
            for (x in 0 until w) px[y * w + x] = img.getRGB(x, y0 + y)
        }

        val res = CardFinder.find(px, w, roiH, h, y0)

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
}
