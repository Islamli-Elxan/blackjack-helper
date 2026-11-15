package az.nexgen.bjhelper

/**
 * Who won the round, worked out from the visible cards once the dealer has finished.
 * The dealer stands on every 17 (also soft 17). Split hands cannot be told apart on screen,
 * so a result is only given for a single hand.
 */
object Outcome {

    enum class Kind { WIN, LOSE, PUSH, BLACKJACK, BUST, DEALER_BUST }

    class Result(val kind: Kind, val player: Int, val dealer: Int)

    /** Null while the round is still going. */
    fun decide(player: List<Int>, dealer: List<Int>): Result? {
        if (player.size < 2 || dealer.isEmpty()) return null
        val p = Strategy.evalHand(player)
        if (p.bust) return Result(Kind.BUST, p.total, 0)
        if (dealer.size < 2) return null
        val d = Strategy.evalHand(dealer)
        if (d.bust) return Result(Kind.DEALER_BUST, p.total, d.total)
        val dealerBj = dealer.size == 2 && d.total == 21
        val playerBj = player.size == 2 && p.total == 21
        if (playerBj || dealerBj) {
            return when {
                playerBj && dealerBj -> Result(Kind.PUSH, p.total, d.total)
                playerBj -> Result(Kind.BLACKJACK, p.total, d.total)
                else -> Result(Kind.LOSE, p.total, d.total)
            }
        }
        if (d.total < 17) return null // dealer still draws
        return when {
            p.total > d.total -> Result(Kind.WIN, p.total, d.total)
            p.total < d.total -> Result(Kind.LOSE, p.total, d.total)
            else -> Result(Kind.PUSH, p.total, d.total)
        }
    }
}
