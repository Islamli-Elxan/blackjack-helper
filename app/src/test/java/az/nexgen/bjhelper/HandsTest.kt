package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class HandsTest {

    private val w = 1080

    private fun g(l: Int, rank: Int) = Hands.G(l, 1180, 1230, rank)

    @Test
    fun oneFanIsOneHand() {
        // the real screenshot: king, 4, 2 with steps of about 90 and 96 px
        val hands = Hands.group(listOf(g(364, 10), g(454, 4), g(550, 2)), emptyList(), w)
        assertEquals(listOf(listOf(10, 4, 2)), hands)
    }

    @Test
    fun wideGapStartsASecondHand() {
        val hands = Hands.group(listOf(g(120, 8), g(210, 3), g(560, 8), g(650, 5)), emptyList(), w)
        assertEquals(listOf(listOf(8, 3), listOf(8, 5)), hands)
    }

    @Test
    fun separateBlobsStartASecondHand() {
        val a = CardFinder.Box(100, 1160, 260, 1570)
        val b = CardFinder.Box(262, 1160, 700, 1570)
        // 150 px apart is more than a fan step, and the corners sit in different blobs
        val hands = Hands.group(listOf(g(120, 9), g(270, 9)), listOf(a, b), w)
        assertEquals(listOf(listOf(9), listOf(9)), hands)
    }

    @Test
    fun emptyAndSingle() {
        assertEquals(emptyList<List<Int>>(), Hands.group(emptyList(), emptyList(), w))
        assertEquals(listOf(listOf(7)), Hands.group(listOf(g(300, 7)), emptyList(), w))
    }
}
