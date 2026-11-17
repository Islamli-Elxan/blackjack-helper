package az.nexgen.bjhelper

import java.nio.ByteBuffer

/**
 * Tells whether the screen really changed since the last frame that was read. The game animates
 * small things all the time (a glow, a badge on the side); reading the cards again for those would
 * only heat the phone. A sparse grid of the table area is compared; a few differing samples are noise.
 */
class FrameGate(private val step: Int = 4, private val tolerance: Int = 6) {

    private var prev: IntArray? = null

    /** @param buf the frame as the capture delivers it (4 bytes per pixel) */
    fun changed(buf: ByteBuffer, rowStride: Int, pixelStride: Int, w: Int, h: Int): Boolean {
        val x0 = w / 10
        val x1 = w * 9 / 10
        val y0 = h * 8 / 100
        val y1 = h * 97 / 100
        val nx = (x1 - x0) / step
        val ny = (y1 - y0) / step
        var cur = prev
        val fresh = cur == null || cur.size != nx * ny
        if (fresh) cur = IntArray(nx * ny)
        val next = cur!!
        var diff = 0
        var i = 0
        for (j in 0 until ny) {
            val row = (y0 + j * step) * rowStride
            for (k in 0 until nx) {
                val v = buf.getInt(row + (x0 + k * step) * pixelStride)
                if (fresh) next[i] = v
                else if (next[i] != v) {
                    diff++
                    if (diff > tolerance) {
                        // a real change: keep the new grid as the reference
                        return copyRest(buf, rowStride, pixelStride, x0, y0, nx, ny, next)
                    }
                }
                i++
            }
        }
        prev = next
        return fresh
    }

    private fun copyRest(
        buf: ByteBuffer, rowStride: Int, pixelStride: Int, x0: Int, y0: Int, nx: Int, ny: Int, into: IntArray
    ): Boolean {
        var i = 0
        for (j in 0 until ny) {
            val row = (y0 + j * step) * rowStride
            for (k in 0 until nx) {
                into[i++] = buf.getInt(row + (x0 + k * step) * pixelStride)
            }
        }
        prev = into
        return true
    }

    fun forget() {
        prev = null
    }
}
