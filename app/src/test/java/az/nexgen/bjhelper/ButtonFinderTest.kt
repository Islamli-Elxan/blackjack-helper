package az.nexgen.bjhelper

import java.util.zip.GZIPInputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finds the Stand / Double / Hit buttons on three real 1080x2400 screenshots (rows 1968..2327). */
class ButtonFinderTest {

    private val w = 1080
    private val h = 2400
    private val rows = 360
    private val yOff = 1968

    private fun find(name: String): List<ButtonFinder.Button> {
        val raw = GZIPInputStream(javaClass.getResourceAsStream("/buttons_$name.rgb.gz")).readBytes()
        assertEquals(w * rows * 3, raw.size)
        val px = IntArray(w * rows)
        for (i in px.indices) {
            px[i] = (0xFF shl 24) or ((raw[i * 3].toInt() and 0xFF) shl 16) or
                ((raw[i * 3 + 1].toInt() and 0xFF) shl 8) or (raw[i * 3 + 2].toInt() and 0xFF)
        }
        return ButtonFinder.find(px, w, rows, yOff, h)
    }

    private fun near(b: ButtonFinder.Button, cx: Int) {
        assertTrue("x " + b.cx + " vs " + cx, abs(b.cx - cx) <= 20)
        assertTrue("y " + b.cy, b.cy in 2150..2230)
    }

    @Test
    fun threeButtonsAtTheStartOfAHand() {
        val b = find("clean")
        assertEquals(listOf(ButtonFinder.Kind.STAND, ButtonFinder.Kind.DOUBLE, ButtonFinder.Kind.HIT), b.map { it.kind })
        near(b[0], 280)
        near(b[1], 540)
        near(b[2], 788)
    }

    @Test
    fun doubleIsGoneAfterAHit() {
        val b = find("hit")
        assertEquals(listOf(ButtonFinder.Kind.STAND, ButtonFinder.Kind.HIT), b.map { it.kind })
        near(b[0], 410)
        near(b[1], 670)
    }

    @Test
    fun monteCarloTable() {
        val b = find("mc")
        assertEquals(setOf(ButtonFinder.Kind.STAND, ButtonFinder.Kind.DOUBLE, ButtonFinder.Kind.HIT), b.map { it.kind }.toSet())
        for (x in b) assertTrue("y " + x.cy, x.cy in 2150..2230)
    }

    @Test
    fun fourButtonsWithSplitOnTheVegasTable() {
        val b = find("vegas")
        assertEquals(listOf(ButtonFinder.Kind.STAND, ButtonFinder.Kind.DOUBLE, ButtonFinder.Kind.HIT), b.map { it.kind })
        near(b[0], 150)
        near(b[1], 670)
        near(b[2], 930)
    }

    @Test
    fun emptyScreenHasNoButtons() {
        val px = IntArray(w * rows)
        for (i in px.indices) px[i] = 0xFF1A5A2A.toInt()
        assertTrue(ButtonFinder.find(px, w, rows, yOff, h).isEmpty())
    }
}
