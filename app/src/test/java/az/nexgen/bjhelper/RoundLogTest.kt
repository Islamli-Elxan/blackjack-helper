package az.nexgen.bjhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundLogTest {

    private fun upd(log: RoundLog, now: Long, d: List<Int>, h: List<List<Int>>, adv: List<String> = emptyList()) =
        log.update(now, 1_000_000L + now, d, h, 0, 0.0, adv, "", 6, true)

    @Test
    fun countsAWinOnceAndWritesTheRound() {
        val log = RoundLog(settleMs = 500L)
        assertNull(upd(log, 0, listOf(10), listOf(listOf(10, 6)), listOf("H:hard16")))
        upd(log, 400, listOf(10), listOf(listOf(10, 6, 5)), listOf("S:hard21"))
        // dealer reveals 10 + 7 = 17, player has 21
        upd(log, 900, listOf(10, 7), listOf(listOf(10, 6, 5)))
        assertEquals(0, log.stats.win)
        upd(log, 1500, listOf(10, 7), listOf(listOf(10, 6, 5)))
        assertEquals(1, log.stats.win)
        assertEquals(0, log.stats.lose)
        // more frames do not count it again
        upd(log, 2500, listOf(10, 7), listOf(listOf(10, 6, 5)))
        assertEquals(1, log.stats.win)
        assertEquals("1W 0L", log.stats.short())

        val done = upd(log, 6000, emptyList(), emptyList())
        assertNotNull(done)
        val json = log.toJson(done!!, "0.8", 6, true)
        assertTrue(json.contains("\"results\":[\"WIN\"]"))
        assertTrue(json.contains("\"dealerUp\":10"))
        assertTrue(json.contains("\"hands\":[[10,6,5]]"))
        assertTrue(json.contains("\"adv\":[\"H:hard16\"]"))
    }

    @Test
    fun splitHandsAreCountedSeparately() {
        val log = RoundLog(settleMs = 100L)
        val d = listOf(10, 8)
        val h = listOf(listOf(10, 9), listOf(10, 7))
        upd(log, 0, d, h)
        upd(log, 300, d, h)
        // 19 beats 18, 17 loses to 18
        assertEquals(1, log.stats.win)
        assertEquals(1, log.stats.lose)
    }

    @Test
    fun unfinishedRoundIsNotCounted() {
        val log = RoundLog(settleMs = 100L)
        upd(log, 0, listOf(10), listOf(listOf(10, 6)))
        upd(log, 500, listOf(10), listOf(listOf(10, 6)))
        val done = upd(log, 3000, emptyList(), emptyList())
        assertNotNull(done)
        assertEquals(0, log.stats.hands)
        assertTrue(log.toJson(done!!, "0.8", 6, true).contains("\"results\":null"))
    }

    @Test
    fun bustAndPushAndBlackjack() {
        val log = RoundLog(settleMs = 100L)
        upd(log, 0, listOf(10), listOf(listOf(10, 6, 9)))
        upd(log, 300, listOf(10), listOf(listOf(10, 6, 9)))
        upd(log, 1000, emptyList(), emptyList())
        upd(log, 2000, listOf(10, 8), listOf(listOf(10, 8)))
        upd(log, 2300, listOf(10, 8), listOf(listOf(10, 8)))
        upd(log, 3000, emptyList(), emptyList())
        upd(log, 4000, listOf(9, 8), listOf(listOf(11, 10)))
        upd(log, 4300, listOf(9, 8), listOf(listOf(11, 10)))
        assertEquals(1, log.stats.win)
        assertEquals(1, log.stats.blackjack)
        assertEquals(1, log.stats.lose)
        assertEquals(1, log.stats.push)
    }

    @Test
    fun priorStatsAreKept() {
        val log = RoundLog(settleMs = 100L)
        log.start(5, RoundLog.Stats(win = 22, lose = 17))
        upd(log, 0, listOf(10, 7), listOf(listOf(10, 9)))
        upd(log, 300, listOf(10, 7), listOf(listOf(10, 9)))
        assertEquals("23W 17L", log.stats.short())
        val done = upd(log, 2000, emptyList(), emptyList())
        assertEquals(5, done!!.id)
    }
}
