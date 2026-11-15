package az.nexgen.bjhelper

/**
 * Splits the player's corner ranks into hands. After a split the game shows two (or more) separate
 * piles; a pile is told apart by a wide gap between neighbouring corners, or by a gap between
 * separate card blobs. Cards of one hand overlap in a fan with a small, even step.
 */
object Hands {

    class G(val l: Int, val t: Int, val b: Int, val rank: Int)

    /** Step between two corners of one hand never gets near this share of the screen width. */
    const val SAME_HAND_MAX = 0.12f
    const val NEW_HAND_MIN = 0.19f

    fun group(gs: List<G>, blobs: List<CardFinder.Box>, screenW: Int): List<List<Int>> {
        if (gs.isEmpty()) return emptyList()
        val sorted = gs.sortedBy { it.l }
        val hands = ArrayList<ArrayList<Int>>()
        var cur = ArrayList<Int>()
        cur.add(sorted[0].rank)
        hands.add(cur)
        for (i in 1 until sorted.size) {
            val gap = sorted[i].l - sorted[i - 1].l
            val sameBlob = blobOf(sorted[i - 1], blobs) == blobOf(sorted[i], blobs)
            val newHand = gap > NEW_HAND_MIN * screenW ||
                (!sameBlob && gap > SAME_HAND_MAX * screenW)
            if (newHand) {
                cur = ArrayList()
                hands.add(cur)
            }
            cur.add(sorted[i].rank)
        }
        return hands
    }

    private fun blobOf(g: G, blobs: List<CardFinder.Box>): Int {
        val cx = g.l + 10
        val cy = (g.t + g.b) / 2
        for ((i, b) in blobs.withIndex()) {
            if (cx >= b.l && cx <= b.r && cy >= b.t && cy <= b.b) return i
        }
        return -1
    }
}
