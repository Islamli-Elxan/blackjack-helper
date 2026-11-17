package az.nexgen.bjhelper

import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The zones are the middle of the table, 648 x 600 pixels cut from real 1080 x 2400 screenshots. */
class ShuffleDetectorTest {

    private val zw = 648
    private val zh = 600

    private fun measure(name: String): ShuffleDetector.Result {
        val raw = GZIPInputStream(javaClass.getResourceAsStream("/zone_$name.rgb.gz")).readBytes()
        assertEquals(zw * zh * 3, raw.size)
        val px = IntArray(zw * zh)
        for (i in px.indices) {
            px[i] = (0xFF shl 24) or ((raw[i * 3].toInt() and 0xFF) shl 16) or
                ((raw[i * 3 + 1].toInt() and 0xFF) shl 8) or (raw[i * 3 + 2].toInt() and 0xFF)
        }
        return ShuffleDetector.measure(px, zw, zh)
    }

    @Test
    fun zoneIsTheMiddleOfTheTable() {
        val z = ShuffleDetector.zone(1080, 2400)
        assertEquals(216, z.x)
        assertEquals(1128, z.y)
        assertEquals(648, z.w)
        assertEquals(600, z.h)
    }

    @Test
    fun seesTheFanOfCardBacks() {
        val r = measure("shuffle")
        assertTrue("share " + r.share, r.share > 0.12f)
        assertTrue("extent " + r.extent, r.extent > 450)
        assertTrue(r.flurry)
    }

    @Test
    fun aTableWithCardsOnItIsNotAShuffle() {
        assertFalse(measure("london").flurry)
        assertFalse(measure("vegas").flurry)
    }

    @Test
    fun oneCardBackIsNotAShuffle() {
        // a card back flying in covers as much as the fan, but it is only a card wide
        val r = measure("single_back")
        assertTrue("share " + r.share, r.share > 0.1f)
        assertTrue("extent " + r.extent, r.extent < 330)
        assertFalse(r.flurry)
    }

    @Test
    fun watchFiresOncePerAnimation() {
        val w = ShuffleWatch()
        var fired = 0
        var t = 0L
        // the animation: many frames, one event
        repeat(15) {
            if (w.onFrame(t, true)) fired++
            t += 150L
        }
        assertEquals(1, fired)
        // quiet for long enough, then a second animation
        t += 6000L
        w.onFrame(t, false)
        repeat(6) {
            if (w.onFrame(t, true)) fired++
            t += 150L
        }
        assertEquals(2, fired)
    }

    @Test
    fun watchIgnoresAFewScatteredFrames() {
        val w = ShuffleWatch()
        var fired = 0
        var t = 0L
        // a card or two flying by during a deal: three frames, spread over seconds
        for (i in 0 until 3) {
            if (w.onFrame(t, true)) fired++
            t += 1500L
        }
        assertEquals(0, fired)
    }

    @Test
    fun gateIgnoresNoiseButSeesChanges() {
        val w = 1080
        val h = 2400
        val buf = ByteBuffer.allocate(w * h * 4)
        val gate = FrameGate()
        assertTrue(gate.changed(buf, w * 4, 4, w, h))
        // nothing changed
        assertFalse(gate.changed(buf, w * 4, 4, w, h))
        // a few stray pixels on the grid are noise
        for (k in 0 until 3) buf.putInt(((300 + k * 4) * w + 400) * 4, 0x7F7F7F7F)
        assertFalse(gate.changed(buf, w * 4, 4, w, h))
        // a glyph-sized patch is a change
        for (y in 1200 until 1230) for (x in 500 until 520) buf.putInt((y * w + x) * 4, 0x11223344)
        assertTrue(gate.changed(buf, w * 4, 4, w, h))
        // and the new picture is the reference now
        assertFalse(gate.changed(buf, w * 4, 4, w, h))
    }

    @Test
    fun gateIgnoresTheEdgesOfTheScreen() {
        val w = 1080
        val h = 2400
        val buf = ByteBuffer.allocate(w * h * 4)
        val gate = FrameGate()
        gate.changed(buf, w * 4, 4, w, h)
        // the strip at the right edge animates all the time: not our business
        for (y in 0 until h) for (x in 1000 until 1080) buf.putInt((y * w + x) * 4, y * 31 + x)
        assertFalse(gate.changed(buf, w * 4, 4, w, h))
    }
}
