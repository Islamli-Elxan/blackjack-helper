package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacerTest {

    private val sw = 1080
    private val sh = 2400

    @Test
    fun topStripIsOffTheCards() {
        // a compact window right under the status bar
        val win = CardFinder.Box(30, 80, 30 + 650, 80 + 200)
        assertFalse(Placer.covers(win, sw, sh, emptyList()))
    }

    @Test
    fun middleOfTheTableCoversCards() {
        val win = CardFinder.Box(30, 900, 680, 1200)
        assertTrue(Placer.covers(win, sw, sh, emptyList()))
    }

    @Test
    fun chosenSpotAvoidsTheCards() {
        val dealer = CardFinder.Box(300, 430, 620, 800)
        val player = CardFinder.Box(300, 1130, 800, 1500)
        val blobs = listOf(dealer, player)
        val ow = 650
        val oh = 330
        val pos = Placer.choose(sw, sh, ow, oh, blobs, 80)
        val win = CardFinder.Box(pos.x, pos.y, pos.x + ow, pos.y + oh)
        for (b in blobs) assertEquals("overlaps " + pos.x + "," + pos.y, 0L, Placer.overlap(win, b))
    }
}
